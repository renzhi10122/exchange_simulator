package com.company;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Prepare logging files
        Path workDir = Paths.get(System.getProperty("user.dir"));
        Path logDir = Paths.get(workDir.toString(), "logging");
        Path simulatorDir = Paths.get(logDir.toString(), "simulator");
        prepareLoggingDir(logDir);
        prepareLoggingDir(simulatorDir);

        // Prepare parameters through user input
        Scanner scanner = new Scanner(System.in);
        System.out.println("Number of simulators (10):\n");
        String input = scanner.nextLine();
        int numSimulators = Integer.parseInt(input.equals("") ? "10" : input);
        System.out.println("Update time for swap pricer /ms (1000):\n");
        input = scanner.nextLine();
        int swapPricerThreadWait = Integer.parseInt(input.equals("") ? "1000" : input);
        System.out.println("Update time for simulator clients /ms (100):\n");
        input = scanner.nextLine();
        int simulatorClientThreadWait = Integer.parseInt(input.equals("") ? "100" : input);
        System.out.println("Update time for requestor client /ms (100):\n");
        input = scanner.nextLine();
        int requestorClientThreadWait = Integer.parseInt(input.equals("") ? "100" : input);
        System.out.println("Start bond price (25000):\n");
        input = scanner.nextLine();
        int startBondPrice = Integer.parseInt(input.equals("") ? "25000" : input);
        System.out.println("Start asset swap spread price (25000):\n");
        input = scanner.nextLine();
        int startAssetSwapSpreadPrice = Integer.parseInt(input.equals("") ? "25000" : input);
        System.out.println("Max price change step (100):\n");
        input = scanner.nextLine();
        int maxStepSize = Integer.parseInt(input.equals("") ? "100" : input);
        System.out.println("Standard deviation of price changes (300.0):\n");
        input = scanner.nextLine();
        double standardDeviation = Double.parseDouble(input.equals("") ? "300.0" : input);
        System.out.println("Max trade size of simulators (100):\n");
        input = scanner.nextLine();
        int simulatorSizeLimit = Integer.parseInt(input.equals("") ? "100" : input);
        System.out.println("Max trade size of requestor (1000):\n");
        input = scanner.nextLine();
        int requestorSizeLimit = Integer.parseInt(input.equals("") ? "1000" : input);

        // Initialize
        Exchange exchange = new Exchange(
            100000,
            swapPricerThreadWait,
            logDir,
            startBondPrice,
            startAssetSwapSpreadPrice,
            maxStepSize,
            standardDeviation
        );
        // The clients send in a request between 1 and 20 times the threadWait
        Simulator simulator = new Simulator(
            numSimulators, exchange, simulatorSizeLimit, simulatorClientThreadWait, simulatorDir
        );
        RequestorClient client = new RequestorClient(exchange, requestorSizeLimit, requestorClientThreadWait, logDir);
        client.start();

        // Stop all threads
        System.out.println("Press return to end price changes");
        scanner.nextLine();
        exchange.swapPricer.interrupt();
        System.out.println("Press return to end simulators");
        scanner.nextLine();
        for(SimulatorClient simulatedClient: simulator.clients) {
            simulatedClient.t.interrupt();
        }
        System.out.println("Press return to end requestor");
        scanner.nextLine();
        client.t.interrupt();
    }

    static void prepareLoggingDir(Path logDir) {
        // Prepare directories for logging
        File directory = new File(logDir.toString());
        if(directory.mkdirs()) {
            System.out.println("Created directory at " + logDir.toString());
        }
        if(Files.exists(logDir)) {
            for(File file: Objects.requireNonNull(directory.listFiles())) {
                assert file.isDirectory() || file.delete();
            }
            System.out.println("Cleaned directory at " + logDir.toString());
        } else {
            System.out.println("Failed to create directory at " + logDir.toString());
        }
    }
}
