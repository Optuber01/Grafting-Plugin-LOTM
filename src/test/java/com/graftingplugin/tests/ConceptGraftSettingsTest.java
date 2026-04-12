package com.graftingplugin.tests;

import com.graftingplugin.conceptgraft.ConceptGraftSettings;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConceptGraftSettingsTest {

    private ConceptGraftSettingsTest() {
    }

    public static void run() {
        YamlConfiguration config = new YamlConfiguration();
        ConceptGraftSettings defaults = ConceptGraftSettings.fromConfig(config);

        assert defaults.zoneDurationTicks() == 45 * 20 : "Default zone duration should be 45s in ticks, got " + defaults.zoneDurationTicks();
        assert defaults.zoneRadius() == 8.0 : "Default zone radius should be 8.0, got " + defaults.zoneRadius();
        assert defaults.pulseIntervalTicks() == 20 : "Default pulse interval should be 20, got " + defaults.pulseIntervalTicks();
        assert defaults.cooldownTicks() == 30 * 20 : "Default cooldown should be 30s in ticks, got " + defaults.cooldownTicks();
        assert defaults.loopDurationTicks() == 60 * 20 : "Default loop duration should be 60s in ticks, got " + defaults.loopDurationTicks();
        assert defaults.loopActivationRadius() == 1.5 : "Default loop activation radius should be 1.5, got " + defaults.loopActivationRadius();
        assert defaults.loopCooldownTicks() == 30 : "Default loop cooldown should be 30, got " + defaults.loopCooldownTicks();
        assert defaults.maxActivePerPlayer() == 1 : "Default max active should be 1, got " + defaults.maxActivePerPlayer();

        config.set("conceptual-graft.zone-duration-seconds", 20);
        config.set("conceptual-graft.zone-radius", 5.0);
        config.set("conceptual-graft.max-active-per-player", 3);
        ConceptGraftSettings custom = ConceptGraftSettings.fromConfig(config);

        assert custom.zoneDurationTicks() == 20 * 20 : "Custom zone duration mismatch";
        assert custom.zoneRadius() == 5.0 : "Custom zone radius mismatch";
        assert custom.maxActivePerPlayer() == 3 : "Custom max active mismatch";

        config.set("conceptual-graft.zone-radius", 0.5);
        ConceptGraftSettings clamped = ConceptGraftSettings.fromConfig(config);
        assert clamped.zoneRadius() == 2.0 : "Zone radius should be clamped to 2.0 minimum, got " + clamped.zoneRadius();
    }
}
