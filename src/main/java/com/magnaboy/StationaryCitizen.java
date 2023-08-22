package com.magnaboy;

public class StationaryCitizen extends Citizen<StationaryCitizen> {
    public StationaryCitizen(CitizensPlugin plugin) {
        super(plugin);
        entityType = EntityType.StationaryCitizen;
    }
}
