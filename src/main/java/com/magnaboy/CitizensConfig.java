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
}
