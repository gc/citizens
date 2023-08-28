package com.magnaboy.scripting;

import com.google.gson.Gson;
import com.magnaboy.CitizensPlugin;

import java.io.*;
import java.util.HashMap;

public final class ScriptLoader {
	private static final String SCRIPTS_DIRECTORY = new File("src/main/resources/Scripts/").getAbsolutePath();
	private final static HashMap<String, ScriptFile> scriptCache = new HashMap<>();

	private ScriptLoader() {
	}

	public static ScriptFile loadScript(CitizensPlugin plugin, String scriptName) {
		ScriptFile script;
		if (scriptCache.containsKey(scriptName)) {
			script = scriptCache.get(scriptName);
			return script;
		}

		InputStream inputStream;
		try {
			inputStream = new FileInputStream(SCRIPTS_DIRECTORY + File.separator + scriptName + ".json");
		} catch (FileNotFoundException e) {
			return null;
		}

		try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			Gson gson = plugin.gson;
			script = gson.fromJson(reader, ScriptFile.class);
			scriptCache.put(scriptName, script);
		} catch (IOException e) {
			System.out.println("Script Loading Error: " + e.getMessage());
			return null;
		}
		script.name = scriptName;
		return script;
	}
}
