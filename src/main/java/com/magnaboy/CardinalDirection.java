package com.magnaboy;

import java.util.HashMap;

public enum CardinalDirection {
	South(0),
	West(512),
	North(1024),
	NorthWest(768),
	East(1536);

	private final int angle;

    private static final HashMap<Integer, CardinalDirection> intToType = new HashMap<>();
    static {
        for (CardinalDirection type : CardinalDirection.values()){
            intToType.put(type.getAngle(), type);
        }
    }

	CardinalDirection(int angle) {
		this.angle = angle;
	}

	public int getAngle() {
		return this.angle;
	}

    public static CardinalDirection fromInteger(Integer i)
    {
        return i == null ? CardinalDirection.South : intToType.get(i);
    }
}
