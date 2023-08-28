package com.magnaboy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.runelite.api.Perspective;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Random;

public final class Util {
	public final static int JAU_FULL_ROTATION = 2048;
	public final static int MAX_ENTITY_RENDER_DISTANCE = 25;
	private final static String animDataFilePath = "src/main/resources/animationData.json";
	private static final Map<String, AnimData> animData;
	public static Random rng = new Random();

	static {
		try {
			animData = readAnimData();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Util() throws IOException {
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

	public static int radToJau(double a) {
		int j = (int) Math.round(a / Perspective.UNIT);
		return j & 2047;
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

	public static WorldArea calculateBoundingBox(WorldPoint bottomLeft, WorldPoint topRight) {
		int width = Math.abs(bottomLeft.getX() - topRight.getX());
		int height = Math.abs(bottomLeft.getY() - topRight.getY());
		String debugString = "BottomLeft[" + bottomLeft + "] TopRight[" + topRight + "] Width[" + width + "] Height["
			+ height + "]";

		if (bottomLeft.getX() > topRight.getX() || bottomLeft.getY() > topRight.getY()) {
			throw new IllegalArgumentException("BottomLeft must be to the bottom/left of topRight. " + debugString);
		}

		if (width <= 1 && height <= 1) {
			throw new IllegalArgumentException("The size of the bounding box must be greater than 1x1. " + debugString);
		}

		return new WorldArea(bottomLeft, width, height);
	}

	public static AnimData getAnimData(int id) {
		return animData.get(String.valueOf(id));
	}

	private static Map<String, AnimData> readAnimData() throws IOException {
		Gson gson = new Gson();
		Type type = new TypeToken<Map<String, AnimData>>() {
		}.getType();
		Map<String, AnimData> map = gson.fromJson(new FileReader(animDataFilePath), type);

		return map;
	}

	public static class AnimData {
		public int id;
		public int frameCount;
		public int clientTicks;
		public int realDurationMillis;
	}

}
