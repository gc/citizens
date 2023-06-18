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
		this.boundingBox = new WorldArea(bottomLeft, Math.abs(bottomLeft.getX() - topRight.getX()), Math.abs(bottomLeft.getY() - topRight.getY()));
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
		final int x = getRandom(this.boundingBox.getX(), this.boundingBox.getX() + this.boundingBox.getWidth());
		final int y = getRandom(this.boundingBox.getY(), this.boundingBox.getY() + this.boundingBox.getHeight());
		return new WorldPoint(x, y, plane);
	}


	public void wander() {
		WorldPoint randomSpot = getRandomInBoundingBox();
		this.moveTo(randomSpot);
	}

}
