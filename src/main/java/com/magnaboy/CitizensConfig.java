package com.magnaboy;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("citizens")
public interface CitizensConfig extends Config {
    @ConfigItem(
            keyName = "autostartQuests",
            name = "Auto start helper",
            description = "Automatically start the quest helper when you start a quest"
    )
    default boolean autoStartQuests() {
        return true;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Toggles the overlay showing citizen tiles and wander regions"
    )
    default boolean showOverlay() {
        return true;
    }
}
