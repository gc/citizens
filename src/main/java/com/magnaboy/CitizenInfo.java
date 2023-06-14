package com.magnaboy;
import net.runelite.api.coords.WorldPoint;

public class CitizenInfo {
    public String name = "name";
    public String examineText = "examine text";
    public String[] remarks = {};

    public int[] modelRecolorFind = {};
    public int[] modelRecolorReplace = {};
    public WorldPoint worldLocation;
    public int[] modelIds = {};
    public AnimationID idleAnimation;
    public AnimationID moveAnimation;
    public Integer baseOrientation;
    public float[] scale;
    public float[] translate;
}