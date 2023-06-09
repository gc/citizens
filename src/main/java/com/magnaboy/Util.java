package com.magnaboy;

import java.util.Random;

public final class Util {
    public static Random rng = new Random();

    // Prevent instantiation
    private Util() {
    }

    public static int getRandom(int min, int max) {
        if (min == max) {
            return min;
        }
        return rng.nextInt((max - min) + 1) + min;
    }

    public static <T> T getRandomItem(T[] items) {
        int index = rng.nextInt(items.length);
        return items[index];
    }

    public static int getRandomItem(int[] items) {
        int index = rng.nextInt(items.length);
        return items[index];
    }

}
