package com.magnaboy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.logging.Logger;
import net.runelite.api.Perspective;
import net.runelite.api.coords.WorldPoint;

public final class Util {
	public static Random rng = new Random();
	private final static Logger logger = Logger.getLogger("Citizens");
	public final static int TILES_WALKED_PER_GAME_TICK = 1;
	public final static int GAME_TICK_MILLIS = 600;

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

	static int radToJau(double a) {
		int j = (int) Math.round(a / Perspective.UNIT);
		return j & 2047;
	}

	static void log(String message) {
		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("citizens.log", true)))) {
			out.println(message);
		} catch (IOException e) {
			System.err.println("Error occurred while logging: " + e.getMessage());
		}
	}

	public static float truncateFloat(int digits, float number) {
		BigDecimal bd = new BigDecimal(Float.toString(number));
		bd = bd.setScale(digits, RoundingMode.DOWN);
		return bd.floatValue();
	}

	public static String worldPointToShortCoord(WorldPoint point) {
		return String.format("%d, %d, %d", point.getX(), point.getY(), point.getPlane());
	}

	public static String intArrayToString(int[] array) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			sb.append(array[i]);
			if (i < array.length - 1) {
				sb.append(",");
			}
		}
		return sb.toString().replaceAll("\\s+", "");
	}
}
