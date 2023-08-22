package com.magnaboy;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CitizensPluginTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(CitizensPlugin.class);
		RuneLite.main(args);
	}
}
