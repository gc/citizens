package com.magnaboy.scripting;

import com.magnaboy.AnimationID;
import com.magnaboy.CardinalDirection;
import net.runelite.api.coords.WorldPoint;

public class ScriptAction {

	public ActionType action;
	public float secondsTilNextAction;

	// 'Parameters'
	public Integer timesToLoop;
	public WorldPoint targetPosition;
	public String message;
	public AnimationID animationId;
	public String scriptName;
	public CardinalDirection targetRotation;
}

