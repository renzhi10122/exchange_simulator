package com.company;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;

class OrderInformation {
    final UUID orderID;
    final int numFilled;
    final int totalPrice;
    final int direction;

    public OrderInformation(UUID orderID, int numFilled, int totalPrice, int direction) {
        this.orderID = orderID;
        this.numFilled = numFilled;
        this.totalPrice = totalPrice;
        this.direction = direction;
    }

    BigDecimal getAveragePrice(BigDecimal tickSize) {
        if(numFilled == 0) {
            return null;
        }
        return tickSize.multiply(BigDecimal.valueOf(totalPrice).divide(BigDecimal.valueOf(numFilled), RoundingMode.HALF_UP));
    }
}


class PurchaseInformation {
    final UUID orderID;
    final UUID clientID;
    final Timestamp orderTimestamp;
    final int direction;

    public PurchaseInformation(UUID orderID, UUID clientID, int direction) {
        this.orderID = orderID;
        this.clientID = clientID;
        this.direction = direction;
        this.orderTimestamp = new Timestamp(System.currentTimeMillis());
    }

    public String toString() {
        return "order ID: " + orderID + ", client ID: " + clientID + ", direction: " + direction + ", trade timestamp: " + orderTimestamp;
    }
}


public class Exchange {
    private LinkedList<PurchaseInformation>[] orderBook;
    int maxPrice;
    // We assume the tick size of bonds and asset swap spreads are equal, and also the 10 year swap.
    final BigDecimal tickSize = new BigDecimal("0.0001");
    SwapPricer swapPricer;

    private HashMap<UUID, Client> registeredClients;
    private HashMap<UUID, Integer> orderPrices;
    private HashMap<UUID, Integer> orderPositions;

    private final Object lock = new Object();
    LogWriter exchangeLogger;

    public Exchange(int maxTicks, int threadWait, Path logDir) {
        initExchange(maxTicks, logDir);
        swapPricer = new SwapPricer(
                threadWait,
                maxPrice,
                maxTicks / 4,
                maxTicks / 4,
                100,
                300,
                logDir
        );
        if(threadWait > -1) {
            swapPricer.start();
        }
    }

    public Exchange(
        int maxTicks,
        int threadWait,
        Path logDir,
        int meanBondPrice,
        int meanAssetSwapSpreadPrice,
        int maxStepSize,
        double sd
    ) {
        initExchange(maxTicks, logDir);
        swapPricer = new SwapPricer(
                threadWait,
                maxPrice,
                meanBondPrice,
                meanAssetSwapSpreadPrice,
                maxStepSize,
                sd,
                logDir
        );
        if(threadWait > -1) {
            swapPricer.start();
        }
    }

    @SuppressWarnings("unchecked")
    private void initExchange(int maxTicks, Path logDir) {
        maxPrice = maxTicks;

        // Initialize order related objects
        orderBook = new LinkedList[maxTicks];
        for(int i = 0; i < maxTicks; i ++) {
            orderBook[i] = new LinkedList<>();
        }
        orderPositions = new HashMap<>();
        orderPrices = new HashMap<>();

        registeredClients = new HashMap<>();

        try {
            String logFilePath = Paths.get(logDir.toString(), "exchange.txt").toString();
            File exchangeLogFile = new File(logFilePath);
            assert exchangeLogFile.exists() || exchangeLogFile.createNewFile();
            exchangeLogger = new LogWriter(logFilePath, "exchange");
        } catch(IOException e) {
            System.out.println("No logging available for exchange");
        }
    }

    Object getLock() { return lock; }

    void registerClient(UUID clientID, Client client) {
        // Register a client with the exchange, if not already existing
        if(!registeredClients.containsKey(clientID)) {
            registeredClients.put(clientID, client);
        }
    }

    int[] printOrderBook() {
        int[] intOrderBook = new int[maxPrice];
        Arrays.fill(intOrderBook, 0);
        for(int i = 0; i < maxPrice; i ++) {
            for(PurchaseInformation trade: orderBook[i]) {
                intOrderBook[i] += trade.direction;
            }
        }
        return intOrderBook;
    }

    int[] printSizes() {
        int[] intOrderBook = new int[maxPrice];
        Arrays.fill(intOrderBook, 0);
        for(int i = 0; i < maxPrice; i ++) {
            intOrderBook[i] = orderBook[i].size();
        }
        return intOrderBook;
    }

    private void completeTrades(int price, int size) {
        if(orderBook[price].size() < size) {
            exchangeLogger.write("Unable to complete " + size + " orders at price " + price);
            return;
        }

        for(int i = 0; i < size; i ++) {
            PurchaseInformation trade = orderBook[price].removeFirst();
            orderPositions.put(trade.orderID, orderPositions.get(trade.orderID) - trade.direction);
            Client client = registeredClients.get(trade.clientID);
            client.completeTrade(trade);
            exchangeLogger.write("Trade completed, " + trade.toString());
        }
    }

    private void startTrades(int price, int size, UUID orderID, UUID clientID, int direction) {
        orderPositions.put(orderID, size * direction);
        orderPrices.put(orderID, price);
        for(int i = 0; i < size; i ++) {
            PurchaseInformation trade = new PurchaseInformation(orderID, clientID, direction);
            orderBook[price].addLast(trade);
            Client client = registeredClients.get(clientID);
            client.startTrade(trade);
            exchangeLogger.write("Trade started, " + trade.toString());
        }
    }

    boolean cancelOrder(UUID clientID, UUID orderID, int size) {
        // Handle request to cancel order under orderID
        synchronized(lock) {
            if(!orderPrices.containsKey(orderID) || Math.abs(orderPositions.get(orderID)) < size) {
                return false;
            } else {
                exchangeLogger.write("Cancelling order ID: " + orderID + ", client ID: " + clientID + ", size: " + size + ", available: " + Math.abs(orderPositions.get(orderID)));
                int leftToFill = size;
                int price = orderPrices.get(orderID);
                int direction = (int) Math.signum(orderPositions.get(orderID));
                orderPositions.put(orderID, orderPositions.get(orderID) - direction * size);
                for(Iterator<PurchaseInformation> iterator = orderBook[price].iterator(); iterator.hasNext();) {
                    if(leftToFill == 0) {
                        break;
                    }

                    PurchaseInformation trade = iterator.next();
                    if(trade.orderID == orderID && trade.clientID == clientID) {
                        Client client = registeredClients.get(trade.clientID);
                        client.completeTrade(trade);
                        exchangeLogger.write("Trade cancelled, " + trade.toString());
                        iterator.remove();
                        leftToFill -= 1;
                    }
                }
                return true;
            }
        }
    }

    OrderInformation limitOrder(UUID clientID, int size, boolean buying, int tickPrice, boolean dryRun) {
        // Takes in a limit order and partially fills it, adding the remainder
        // to the order book.
        //  clientID: UUID of the client making the request
        //  size: size of order to fill
        //  buying: true if buying, false if selling
        //  tickPrice: price to set limit at
        //  dryRun: if true, orders are executed, else only information is returned and no orders executed

        int totalPrice = 0;
        int leftToFill = size;
        UUID orderID = UUID.randomUUID();

        // direction allows us to take advantage of the symmetry of bid and ask
        int direction = buying ? 1 : -1;
        synchronized(lock) {
            for(int i = buying ? 0 : maxPrice - 1; i != tickPrice + direction; i += direction) {
                // Check to see if there are orders opposite of my direction
                PurchaseInformation firstOrder = orderBook[i].peekFirst();
                if(firstOrder == null || firstOrder.direction == direction) {
                    continue;
                }

                int numAtPrice = orderBook[i].size();
                int maxTradeableAtPrice = Math.min(leftToFill, numAtPrice);
                if(maxTradeableAtPrice > 0) {
                    totalPrice += maxTradeableAtPrice * i;
                    leftToFill -= maxTradeableAtPrice;

                    if(!dryRun) {
                        completeTrades(i, maxTradeableAtPrice);
                    }
                }
                if(leftToFill == 0) {
                    break;
                }
            }

            if(!dryRun) {
                // Add any left over order to the order book
                startTrades(tickPrice, leftToFill, orderID, clientID, direction);
            }
        }

        return new OrderInformation(orderID, size - leftToFill, totalPrice, direction);
    }
}
