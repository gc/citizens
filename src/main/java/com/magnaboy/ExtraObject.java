package com.magnaboy;

public class ExtraObject extends Entity<ExtraObject> {
    private Citizen citizen;

    public ExtraObject(CitizensPlugin plugin) {
        super(plugin);
    }

    public ExtraObject setCitizen(Citizen citizen) {
        this.citizen = citizen;
        return this;
    }
}
