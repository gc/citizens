package com.magnaboy;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import static com.magnaboy.Util.getRandom;

public class WanderingCitizen extends Citizen<WanderingCitizen> {
    public WorldArea boundingBox;

    public WanderingCitizen(CitizensPlugin plugin) {
        super(plugin);
        entityType = EntityType.WanderingCitizen;
    }

    public WanderingCitizen setBoundingBox(WorldPoint bottomLeft, WorldPoint topRight) {
        this.boundingBox = new WorldArea(bottomLeft, Math.abs(bottomLeft.getX() - topRight.getX()), Math.abs(bottomLeft.getY() - topRight.getY()));
        worldLocation = getRandomInBoundingBox();
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
