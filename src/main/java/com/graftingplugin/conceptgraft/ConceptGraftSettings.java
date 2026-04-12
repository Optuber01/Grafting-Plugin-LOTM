package com.graftingplugin.conceptgraft;

import org.bukkit.configuration.file.FileConfiguration;

public record ConceptGraftSettings(
    int zoneDurationTicks,
    double zoneRadius,
    int pulseIntervalTicks,
    int cooldownTicks,
    int loopDurationTicks,
    double loopActivationRadius,
    int loopCooldownTicks,
    int maxActivePerPlayer
) {

    public static ConceptGraftSettings fromConfig(FileConfiguration config) {
        int zoneDurationSeconds = Math.max(5, config.getInt("conceptual-graft.zone-duration-seconds", 45));
        double zoneRadius = Math.max(2.0, config.getDouble("conceptual-graft.zone-radius", 8.0));
        int pulseIntervalTicks = Math.max(5, config.getInt("conceptual-graft.pulse-interval-ticks", 20));
        int cooldownSeconds = Math.max(1, config.getInt("conceptual-graft.cooldown-seconds", 30));
        int loopDurationSeconds = Math.max(5, config.getInt("conceptual-graft.loop-duration-seconds", 60));
        double loopActivationRadius = Math.max(0.5, config.getDouble("conceptual-graft.loop-activation-radius", 1.5));
        int loopCooldownTicks = Math.max(5, config.getInt("conceptual-graft.loop-cooldown-ticks", 30));
        int maxActive = Math.max(1, config.getInt("conceptual-graft.max-active-per-player", 1));
        return new ConceptGraftSettings(
            zoneDurationSeconds * 20,
            zoneRadius,
            pulseIntervalTicks,
            cooldownSeconds * 20,
            loopDurationSeconds * 20,
            loopActivationRadius,
            loopCooldownTicks,
            maxActive
        );
    }
}
