package com.magnaboy.scripting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.magnaboy.Util;

import java.io.*;
import java.util.HashMap;

public final class ScriptLoader {
    private static final String SCRIPTS_DIRECTORY = new File("src/main/resources/Scripts/").getAbsolutePath();
    private final static HashMap<String, ScriptFile> scriptCache = new HashMap<>();

    private ScriptLoader() {
    }

    public static ScriptFile loadScript(String scriptName) {
        ScriptFile script;
        if (scriptCache.containsKey(scriptName)) {
            Util.log("Loaded Script: " + scriptName + " from cache");
            script = scriptCache.get(scriptName);
            return script;
        }

        InputStream inputStream;
        try {
            inputStream = new FileInputStream(SCRIPTS_DIRECTORY + File.separator + scriptName + ".json");
        } catch (FileNotFoundException e) {
            Util.log("Script: " + scriptName + ".json not found");
            return null;
        }

        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            Gson gson = new GsonBuilder().create();
            script = gson.fromJson(reader, ScriptFile.class);
            if (script == null) {
                Util.log("Script file found but didn't deserialize");
            }
            scriptCache.put(scriptName, script);
        } catch (IOException e) {
            Util.log("Script Loading Error: " + e.getMessage());
            return null;
        }
        script.name = scriptName;
        Util.log("Loaded Script: " + scriptName + " from file");
        return script;
    }
}
