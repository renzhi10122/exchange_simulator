package com.company;

import java.nio.file.Path;


public class Simulator {
    SimulatorClient[] clients;
    Exchange exchange;

    public Simulator(int numSimulators, Exchange exchange_object, int sizeLimit, int threadWait, Path simulatorDir) {
        // Cap numSimulators at 1000 for memory constraints
        numSimulators = Math.min(numSimulators, 1000);

        // Given an exchange, create numSimulators clients to start trading
        this.clients = new SimulatorClient[numSimulators];
        for(int i = 0; i < numSimulators; i++) {
            clients[i] = new SimulatorClient(exchange_object, sizeLimit, threadWait, simulatorDir);
        }
        if(threadWait > -1) {
            for(int i = 0; i < numSimulators; i++) {
                clients[i].start();
            }
        }
        this.exchange = exchange_object;
    }

    void initOrderBook() {
        for(SimulatorClient client: clients) {
            client.simulateLimitOrder();
        }
    }
}
