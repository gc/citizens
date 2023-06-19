package com.magnaboy;

import static com.magnaboy.Util.getRandom;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class WanderingCitizen extends Citizen<WanderingCitizen> {
	public WorldArea boundingBox;

	public WorldPoint wanderRegionBL;
	public WorldPoint wanderRegionTR;

	public WanderingCitizen(CitizensPlugin plugin) {
		super(plugin);
		entityType = EntityType.WanderingCitizen;
	}

	public WanderingCitizen setBoundingBox(WorldPoint bottomLeft, WorldPoint topRight) {
		int width = Math.abs(bottomLeft.getX() - topRight.getX());
		int height = Math.abs(bottomLeft.getY() - topRight.getY());
		String debugString = "BottomLeft[" + bottomLeft + "] TopRight[" + topRight + "] Width[" + width + "] Height[" + height + "]";

		if (bottomLeft.getX() > topRight.getX() || bottomLeft.getY() > topRight.getY()) {
			throw new IllegalArgumentException("BottomLeft must be to the bottom/left of topRight. " + debugString);
		}

		if (width <= 1 && height <= 1) {
			throw new IllegalArgumentException("The size of the bounding box must be greater than 1x1. " + debugString);
		}

		this.boundingBox = new WorldArea(bottomLeft, width, height);
		worldLocation = getRandomInBoundingBox();
		return this;
	}

	public WanderingCitizen setWanderRegionBL(WorldPoint wp) {
		wanderRegionBL = wp;
		return this;
	}

	public WanderingCitizen setWanderRegionTR(WorldPoint wp) {
		wanderRegionTR = wp;
		return this;
	}

	private WorldPoint getRandomInBoundingBox() {
		WorldPoint randomPoint;
		do {
			final int x = getRandom(this.boundingBox.getX(), this.boundingBox.getX() + this.boundingBox.getWidth());
			final int y = getRandom(this.boundingBox.getY(), this.boundingBox.getY() + this.boundingBox.getHeight());
			randomPoint = new WorldPoint(x, y, getPlane());
		} while (randomPoint.equals(worldLocation));

		return randomPoint;
	}

	public void wander() {
		WorldPoint randomSpot = getRandomInBoundingBox();
		this.moveTo(randomSpot);
	}

}
