package com.company;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SwapPricer extends Thread {
    DiscreteRandom rand = new DiscreteRandom();
    // We assume the tick size of bonds and asset swap spreads are equal, and also the 10 year swap.
    private int maxPrice;
    private int currentBondPrice;
    private int currentAssetSwapSpreadPrice;
    private int maxStepSize;
    private double sd;
    private int threadWait;
    LogWriter pricerLogger;
    final Object pricerLock = new Object();

    public SwapPricer(
            int threadWait,
            int maxPrice,
            int meanBondPrice,
            int meanAssetSwapSpreadPrice,
            int maxStepSize,
            double sd,
            Path logDir
            ) {
        this.threadWait = threadWait;
        this.maxPrice = maxPrice;
        // Arbitrary initialization of bond and asset swap spread prices
        this.currentBondPrice = meanBondPrice;
        this.currentAssetSwapSpreadPrice = meanAssetSwapSpreadPrice;
        this.maxStepSize = maxStepSize;
        this.sd = sd;
        try {
            String logFilePath = Paths.get(logDir.toString(), "swap_pricer.txt").toString();
            File swapPricerLogFile = new File(logFilePath);
            assert swapPricerLogFile.exists() || swapPricerLogFile.createNewFile();
            pricerLogger = new LogWriter(logFilePath, "swap pricer");
        } catch(IOException e) {
            System.out.println("No logging available for swap pricer");
            System.out.println(e.toString());
        }
    }

    void stepToNewPrice() {
        int bondStep;
        int assetSwapSpreadStep;
        do {
            bondStep = rand.nextNormalInteger(0, this.sd, this.maxStepSize);
            assetSwapSpreadStep = rand.nextNormalInteger(0, this.sd, this.maxStepSize);
        } while(
                getTenYearSwapPrice() + bondStep + assetSwapSpreadStep < 0 ||
                        this.currentBondPrice + bondStep < 0 ||
                        this.currentAssetSwapSpreadPrice + assetSwapSpreadStep < 0 ||
                        // We force the prices to be below the maxPrice. In reality this doesn't happen
                        // and we would need to defend against this
                        getTenYearSwapPrice() + bondStep + assetSwapSpreadStep >= maxPrice ||
                        this.currentBondPrice + bondStep >= maxPrice ||
                        this.currentAssetSwapSpreadPrice + assetSwapSpreadStep >= maxPrice
        );

        synchronized(pricerLock) {
            this.currentBondPrice += bondStep;
            this.currentAssetSwapSpreadPrice += assetSwapSpreadStep;
        }

        String logString = "New bond price: " +
                currentBondPrice +
                ", new asset swap spread price: " +
                currentAssetSwapSpreadPrice +
                ", new 10 year swap price: " +
                getTenYearSwapPrice();
        System.out.println(logString);
        pricerLogger.write(logString);
    }

    public void run() {
        System.out.println("Starting swap pricer");
        while(!interrupted()) {
            try {
                stepToNewPrice();
                Thread.sleep(threadWait);
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println("Swap pricer exited");
    }

    public void start() {
        System.out.println("Starting swap pricer");
        super.start();
    }

    public int getTenYearSwapPrice() {
        synchronized(pricerLock) {
            return this.currentAssetSwapSpreadPrice + this.currentBondPrice;
        }
    }
}