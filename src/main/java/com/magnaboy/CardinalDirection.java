package com.magnaboy;

import lombok.Getter;

import java.util.HashMap;

@Getter
public enum CardinalDirection {
	South(0),
	SouthWest(256),
	West(512),
	NorthWest(768),
	North(1024),
	NorthEast(1280),
	East(1536),
	SouthEast(1792);

	private static final HashMap<Integer, CardinalDirection> intToType = new HashMap<>();

	static {
		for (CardinalDirection type : CardinalDirection.values()) {
			intToType.put(type.getAngle(), type);
		}
	}

	private final int angle;

	CardinalDirection(int angle) {
		this.angle = angle;
	}

	public static CardinalDirection fromInteger(Integer i) {
		return i == null ? CardinalDirection.South : intToType.get(i);
	}

}
