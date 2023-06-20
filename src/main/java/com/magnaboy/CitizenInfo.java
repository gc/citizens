package com.magnaboy;

import net.runelite.api.coords.WorldPoint;

public class CitizenInfo extends EntityInfo {
	public String name;
	public String examineText;
	public String[] remarks = {};
	public AnimationID moveAnimation;

	public WorldPoint wanderBoxBL;
	public WorldPoint wanderBoxTR;
}