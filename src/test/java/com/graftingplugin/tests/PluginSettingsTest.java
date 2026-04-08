package com.graftingplugin.tests;

import com.graftingplugin.config.PluginSettings;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PluginSettingsTest {

    private PluginSettingsTest() {
    }

    public static void run() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("focus.material", "BLAZE_ROD");
        config.set("focus.name", "Mystic Focus");
        config.set("focus.interaction-range", 10);
        config.set("state-transfer.effect-duration-seconds", 15);
        config.set("state-transfer.field-duration-seconds", 8);
        config.set("state-transfer.field-radius", 5);
        config.set("state-transfer.pulse-interval-ticks", 30);
        config.set("state-transfer.ignite-fire-ticks", 140);
        config.set("state-transfer.heat-damage", 2.5D);
        config.set("state-transfer.heal-amount", 6.0D);
        config.set("state-transfer.bounce-count", 4);

        PluginSettings settings = PluginSettings.fromConfig(config);

        if (settings.interactionRange() != 10) {
            throw new AssertionError("Interaction range was not loaded correctly");
        }
        if (settings.stateTransferSettings().effectDurationTicks() != 300) {
            throw new AssertionError("Effect duration seconds were not converted to ticks");
        }
        if (settings.stateTransferSettings().fieldDurationTicks() != 160) {
            throw new AssertionError("Field duration seconds were not converted to ticks");
        }
        if (settings.stateTransferSettings().fieldRadius() != 5 || settings.stateTransferSettings().bounceCount() != 4) {
            throw new AssertionError("State transfer settings were not loaded correctly");
        }
    }
}
