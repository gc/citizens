package com.magnaboy;

import static com.magnaboy.Util.getRandom;
import java.time.Instant;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class WanderingCitizen extends Citizen<WanderingCitizen> {
	public WorldArea boundingBox;
	public WorldPoint wanderRegionBL;
	public WorldPoint wanderRegionTR;
	private Instant lastWanderTime;

	public WanderingCitizen(CitizensPlugin plugin) {
		super(plugin);
		entityType = EntityType.WanderingCitizen;
		lastWanderTime = Instant.now().minusSeconds(6);
	}

	public WanderingCitizen setBoundingBox(WorldPoint bottomLeft, WorldPoint topRight) {
		this.boundingBox = Util.calculateBoundingBox(bottomLeft, topRight);
		setWorldLocation(getRandomInBoundingBox());
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
		} while (randomPoint.equals(getWorldLocation()));

		return randomPoint;
	}

	public void wander() {
		if (getCurrentTarget() != null || Instant.now().isBefore(lastWanderTime.plusSeconds(6))) {
			return;
		}
		WorldPoint randomSpot = getRandomInBoundingBox();
		this.moveTo(randomSpot);
		lastWanderTime = Instant.now();
	}
}
