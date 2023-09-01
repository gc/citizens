package com.magnaboy.serialization;

import com.magnaboy.AnimationID;
import com.magnaboy.EntityType;
import com.magnaboy.MergedObject;
import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.UUID;

public class EntityInfo {
	public UUID uuid;
	public int regionId;
	public EntityType entityType;
	public WorldPoint worldLocation;
	public int[] modelIds = {};
	public Integer baseOrientation;
	public float[] scale;
	public Integer radius;
	public float[] translate;
	public int[] modelRecolorFind = {};
	public int[] modelRecolorReplace = {};
	public AnimationID idleAnimation;
	public Integer removedObject;
	public List<MergedObject> mergedObjects;
}
