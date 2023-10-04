package com.magnaboy.scripting;

import com.google.gson.Gson;
import com.magnaboy.CitizensPlugin;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;

public final class ScriptLoader {
	private final static HashMap<String, ScriptFile> scriptCache = new HashMap<>();

	private ScriptLoader() {
	}

	public static ScriptFile loadScript(CitizensPlugin plugin, String scriptName) {
		if (scriptCache.containsKey(scriptName)) {
			return scriptCache.get(scriptName);
		}

		InputStream inputStream = plugin.getClass().getResourceAsStream("/Scripts/" + scriptName + ".json");

		if (inputStream == null) {
			return null;
		}

		try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			Gson gson = plugin.gson;
			ScriptFile script = gson.fromJson(reader, ScriptFile.class);
			scriptCache.put(scriptName, script);
			script.name = scriptName;
			return script;
		} catch (IOException e) {
			System.out.println("Script Loading Error: " + e.getMessage());
			return null;
		}
	}
}
