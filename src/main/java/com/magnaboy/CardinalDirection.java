package com.magnaboy;

public enum CardinalDirection {
	South(0),
	West(512),
	North(1024),
	NorthWest(768),
	East(1536);

	private final int angle;

	CardinalDirection(int angle) {
		this.angle = angle;
	}

	public int getAngle() {
		return this.angle;
	}
}
