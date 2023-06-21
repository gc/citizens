package com.magnaboy;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import static com.magnaboy.Util.getRandom;

public class WanderingCitizen extends Citizen<WanderingCitizen> {
	public WorldArea boundingBox;
	public WorldPoint wanderRegionBL;
	public WorldPoint wanderRegionTR;

	public WanderingCitizen(CitizensPlugin plugin) {
		super(plugin);
		entityType = EntityType.WanderingCitizen;
	}

	public WanderingCitizen setBoundingBox(WorldPoint bottomLeft, WorldPoint topRight) {
		this.boundingBox = Util.calculateBoundingBox(bottomLeft, topRight);
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
