package com.company;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;


public class Test {
    static Path workDir = Paths.get(System.getProperty("user.dir"));
    static Path logDir = Paths.get(workDir.toString(), "logging");
    static Path simulatorDir = Paths.get(logDir.toString(), "simulator");

    static void testLimitOrder() {
        Exchange exchange = new Exchange(5, -1, logDir);
        Client client = new Client(exchange);
        OrderInformation orderInformation = client.sendLimitOrder(3, true, 1);
        assert orderInformation.totalPrice == 0;
        assert orderInformation.numFilled == 0;
        assert exchange.printOrderBook()[1] == 3;
        orderInformation = client.sendLimitOrder(1, false, 2);
        assert orderInformation.totalPrice == 0;
        assert orderInformation.numFilled == 0;
        assert exchange.printOrderBook()[1] == 3;
        assert exchange.printOrderBook()[2] == -1;
        orderInformation = client.sendLimitOrder(1, false, 1);
        assert orderInformation.totalPrice == 1;
        assert orderInformation.numFilled == 1;
        assert exchange.printOrderBook()[1] == 2;
        assert exchange.printOrderBook()[2] == -1;
        orderInformation = client.sendLimitOrder(4, true, 3);
        assert orderInformation.totalPrice == 2;
        assert orderInformation.numFilled == 1;
        assert exchange.printOrderBook()[1] == 2;
        assert exchange.printOrderBook()[2] == 0;
        assert exchange.printOrderBook()[3] == 3;
        orderInformation = client.sendLimitOrder(4, false, 1);
        assert orderInformation.totalPrice == 10;
        assert orderInformation.numFilled == 4;
        assert exchange.printOrderBook()[1] == 1;
        assert exchange.printOrderBook()[2] == 0;
        assert exchange.printOrderBook()[3] == 0;
    }

    static void testOrderPositions() {
        Exchange exchange = new Exchange(5, -1, logDir);
        Client client = new Client(exchange);
        Client otherClient = new Client(exchange);
        Client anotherClient = new Client(exchange);
        client.sendLimitOrder(1, true, 1);
        otherClient.sendLimitOrder(1, true, 1);
        anotherClient.sendLimitOrder(1, false, 1);
        assert client.orderPositions.size() == 0;
        assert otherClient.orderPositions.size() == 1;
        assert anotherClient.orderPositions.size() == 0;
    }

    static void testCancelOrder() {
        Exchange exchange = new Exchange(5, -1, logDir);
        Client client = new Client(exchange);
        Client otherClient = new Client(exchange);
        UUID firstOrderID = client.sendLimitOrder(4, true, 1).orderID;
        otherClient.sendLimitOrder(4, true, 2);
        UUID secondOrderID = client.sendLimitOrder(4, true, 2).orderID;
        UUID thirdOrderID = client.sendLimitOrder(4, false, 3).orderID;
        client.cancelOrder(firstOrderID, 2);
        client.cancelOrder(secondOrderID, 4);
        client.cancelOrder(thirdOrderID, 0);

        int[] signedOrderBook = exchange.printOrderBook();

        assert signedOrderBook[1] == 2;
        assert signedOrderBook[2] == 4;
        assert signedOrderBook[3] == -4;

    }

    static void testNextNormalInteger(DiscreteRandom rand) {
        int nextInteger;
        for(int i = 0; i < 10000; i ++) {
            nextInteger = rand.nextNormalInteger(100, 3.5, 3);
            assert nextInteger > 97;
            assert nextInteger < 103;
            nextInteger = rand.nextNormalInteger(1, 100, 0);
            assert nextInteger == 1;
        }
    }

    static void testOrderBook() {
        int maxTicks = 100;
        Exchange exchange = new Exchange(maxTicks, -1, logDir);
        Simulator simulator = new Simulator(1000, exchange, 2, -1, simulatorDir);
        simulator.initOrderBook();
        Client client = new Client(exchange);

        assert client.requestForQuotes(100, true).totalPrice > client.requestForQuotes(100, false).totalPrice;
        assert client.requestForQuotes(1000000, true).numFilled < 1000000;
        OrderInformation requestForSale = client.requestForQuotes(100, false);
        assert requestForSale.totalPrice == client.sendLimitOrder(100, false, 5).totalPrice;
        assert requestForSale.totalPrice > client.requestForQuotes(100, false).totalPrice;

        int[] signedOrderBook = exchange.printOrderBook();
        int[] sizedOrderBook = exchange.printSizes();
        for(int i = 0; i < maxTicks; i ++) {
            assert Math.abs(signedOrderBook[i]) == sizedOrderBook[i];
        }
    }

    public static void main(String[] args) {
        Main.prepareLoggingDir(logDir);
        Main.prepareLoggingDir(simulatorDir);
        DiscreteRandom rand = new DiscreteRandom();
        testLimitOrder();
        testOrderPositions();
        testCancelOrder();
        testNextNormalInteger(rand);
        testOrderBook();
    }
}
