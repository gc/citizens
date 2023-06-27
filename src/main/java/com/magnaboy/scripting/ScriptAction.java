package com.magnaboy.scripting;

import com.magnaboy.AnimationID;
import net.runelite.api.coords.WorldPoint;

public class ScriptAction {

	public ActionType action;
	public float timeTilNextAction;

	//'Parameters'
	public boolean loopAnimation;
	public WorldPoint targetPosition;
	public String message;
	public AnimationID animationId;
	public String scriptName;
}

