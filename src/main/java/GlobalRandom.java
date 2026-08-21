package main.java;

import java.util.Random;

public class GlobalRandom {
    public static Random rand = new Random(42);

    public static void setSeed(long seed) {
        rand = new Random(seed);
    }
}