package com.magnaboy;

public class ScriptedCitizen extends Citizen<ScriptedCitizen> {
    CitizenScript script;
    boolean hasStarted = false;

    public ScriptedCitizen(CitizensPlugin plugin) {
        super(plugin);
    }

    public ScriptedCitizen setScript(CitizenScript script) {
        this.script = script;
        script.setCitizen(this);
        entityType = EntityType.ScriptedCitizen;
        return this;
    }

    @Override
    public boolean spawn() {
        boolean didSpawn = super.spawn();
        if (!hasStarted) {
            script.run();
            hasStarted = true;
            return true;
        }
        return didSpawn;
    }
}
