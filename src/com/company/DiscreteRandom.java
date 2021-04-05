package com.company;

import java.util.Random;

public class DiscreteRandom extends Random {
    int nextNormalInteger(int mean, double sd, int spread) {
        // Get a random 'normally distributed' integer, bounded in [mean-spread/2, mean+spread/2]
        int nextInteger;
        // A bit dirty, but it does the job
        do {
            nextInteger = (int) Math.round(this.nextGaussian() * sd + mean);
        }
        while(nextInteger < mean - spread / 2.0 || nextInteger > mean + spread / 2.0);
        return nextInteger;
    }
}
