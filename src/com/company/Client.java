package com.company;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.*;


class LogWriter extends FileWriter {
    String identifier;

    public LogWriter(String fileName, String identifier) throws IOException {
        super(fileName);
        this.identifier = identifier;
    }

    public void write(String str) {
        try {
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            super.write(timestamp + ", " + str + "\n");
            super.flush();
        } catch(IOException e) {
            System.out.println("Logging failed for " + identifier);
        }
    }
}

public class Client {
    protected UUID clientID;
    Exchange exchange;
    protected HashMap<UUID, Integer> orderPositions;
    private final Object lock = new Object();

    public Client(Exchange exchange_object) {
        clientID = UUID.randomUUID();
        exchange = exchange_object;
        orderPositions = new HashMap<UUID, Integer>();

        // We must let the exchange know about us to receive trade information
        exchange.registerClient(clientID, this);
    }

    UUID getClientID() {
        return clientID;
    }

    void completeTrade(PurchaseInformation trade) {
        // Exchange can tell us when a trade has completed
        synchronized (lock) {
            int newPosition = orderPositions.get(trade.orderID) - trade.direction;
            if (newPosition == 0) {
                orderPositions.remove(trade.orderID);
            } else {
                orderPositions.put(trade.orderID, newPosition);
            }
        }
    }

    void startTrade(PurchaseInformation trade) {
        // Exchange can tell us when a trade has been ordered
        synchronized (lock) {
            int newPosition = trade.direction;
            if(orderPositions.containsKey(trade.orderID)) {
                newPosition += orderPositions.get(trade.orderID);
            }

            if (newPosition == 0) {
                orderPositions.remove(trade.orderID);
            } else {
                orderPositions.put(trade.orderID, newPosition);
            }
        }
    }

    OrderInformation requestForQuotes(int size, boolean buying) {
        int tickPrice = buying ? exchange.maxPrice - 1 : 0;

        return exchange.limitOrder(clientID, size, buying, tickPrice, true);
    }

    OrderInformation sendLimitOrder(int size, boolean buying, int tickPrice) {
        return exchange.limitOrder(clientID, size, buying, tickPrice, false);
    }

    boolean cancelOrder(UUID orderID, int size) { return exchange.cancelOrder(clientID, orderID, size); }
}

class SimulatorClient extends Client implements Runnable {
    DiscreteRandom rand = new DiscreteRandom();
    private int sizeLimit;
    Thread t;
    int threadWait;
    LogWriter simulatorLogger;

    public SimulatorClient(Exchange exchange_object, int sizeLimit, int threadWait, Path logDir) {
        super(exchange_object);
        this.sizeLimit = sizeLimit;
        this.threadWait = threadWait;
        try {
            String logFilePath = Paths.get(logDir.toString(), clientID.toString() + ".txt").toString();
            File simulatorLogFile = new File(logFilePath);
            assert simulatorLogFile.exists() || simulatorLogFile.createNewFile();
            simulatorLogger = new LogWriter(logFilePath, clientID.toString());
        } catch(IOException e) {
            System.out.println("No logging available for simulator " + clientID.toString());
            System.out.println(e.toString());
        }
    }

    void completeTrade(PurchaseInformation trade) {
        super.completeTrade(trade);
        simulatorLogger.write(
            "Trade completed, order ID: " + trade.orderID
        );
    }

    void startTrade(PurchaseInformation trade) {
        super.startTrade(trade);
        simulatorLogger.write(
            "Trade started, order ID: " + trade.orderID
        );
    }

    void simulateLimitOrder() {
        int tickPrice;
        do {
            tickPrice = rand.nextNormalInteger(
                    exchange.swapPricer.getTenYearSwapPrice(), 5, 5
            );
        } while(tickPrice < 0 || tickPrice >= exchange.maxPrice);
        int size = rand.nextInt(sizeLimit) + 1;
        boolean buying = rand.nextBoolean();
        OrderInformation order = this.sendLimitOrder(size, buying, tickPrice);
        simulatorLogger.write(
            "Order made, order ID: " + order.orderID +
                    ", requested " + size +
                    ", filled " + order.numFilled +
                    ", ordered: " + (size - order.numFilled) +
                    ", direction: " + ((order.direction == 1) ? "buying" : "selling") +
                    ", requested price: " + (exchange.tickSize.multiply(BigDecimal.valueOf(tickPrice))) +
                    ", average price: " + order.getAveragePrice(exchange.tickSize)
        );

        // We cancel an order if there are too many
        if(orderPositions.keySet().size() > 5) {
            synchronized (exchange.getLock()) {
                List<UUID> keysAsArray = new ArrayList<UUID>(orderPositions.keySet());
                Random r = new Random();
                UUID orderIDToCancel = keysAsArray.get(r.nextInt(keysAsArray.size()));
                int orderSize = Math.abs(orderPositions.get(orderIDToCancel));
                if(cancelOrder(orderIDToCancel, orderSize)) {
                    simulatorLogger.write(
                            "Order cancelled, order ID: " + orderIDToCancel.toString() + ", order size: " + orderSize
                    );
                } else {
                    simulatorLogger.write(
                            "Unable to cancel order, order ID: " + orderIDToCancel.toString() + ", order size: " + orderSize
                    );
                }
            }
        }
    }

    public void run() {
        while(!Thread.interrupted()) {
            try {
                simulateLimitOrder();
                Thread.sleep(threadWait + rand.nextInt(threadWait * 19));
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println("Simulator " +  clientID + " exited");
    }

    public void start() {
        System.out.println("Starting simulator client: " +  clientID);
        if (t == null) {
            t = new Thread (this, clientID.toString());
            t.start();
        }
    }
}

class RequestorClient extends Client implements Runnable {
    DiscreteRandom rand = new DiscreteRandom();
    private int sizeLimit;
    Thread t;
    int threadWait;
    LogWriter requestorLogger;

    public RequestorClient(Exchange exchange_object, int sizeLimit, int threadWait, Path logDir) {
        super(exchange_object);
        this.sizeLimit = sizeLimit;
        this.threadWait = threadWait;
        try {
            String logFilePath = Paths.get(logDir.toString(), "requestor.txt").toString();
            File requestorLogFile = new File(logFilePath);
            assert requestorLogFile.exists() || requestorLogFile.createNewFile();
            requestorLogger = new LogWriter(logFilePath, clientID.toString());
        } catch(IOException e) {
            System.out.println("No logging available for requestor " + clientID.toString());
            System.out.println(e.toString());
        }
    }

    void requestRandomOrder() {
        int size = rand.nextInt(sizeLimit) + 1;
        boolean buying = rand.nextBoolean();
        OrderInformation order = this.requestForQuotes(size, buying);
        String direction = buying ? "buy" : "sell";
        String logString = "Requested " + size + " to " + direction + ", got " + order.numFilled;
        logString += " for an average price of " + order.getAveragePrice(exchange.tickSize);
        System.out.println(logString);
        requestorLogger.write(logString);
    }

    public void run() {
        while(!Thread.interrupted()) {
            try {
                requestRandomOrder();
                Thread.sleep(threadWait + rand.nextInt(threadWait * 19));
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println("Requestor " +  clientID + " exited");
    }

    public void start() {
        System.out.println("Starting requestor client: " +  clientID);
        if (t == null) {
            t = new Thread (this, clientID.toString());
            t.start();
        }
    }
}
