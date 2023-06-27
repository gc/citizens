package com.magnaboy.scripting;

public enum ActionType {
	Idle,        //Do literally nothing. Can be used to pause between actions
	WalkTo,        //Go somewhere
	Animation,  //Sets idle animation, can be one-shot or remain as idle anim
	Say,        //Overhead text message
	StartNewScript,//Can start a completely new script from a script
}
