package com.graftingplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

public record TopologyGraftSettings(
    int durationTicks,
    double activationRadius,
    int activationCooldownTicks
) {

    public static TopologyGraftSettings fromConfig(FileConfiguration config) {
        int durationTicks = secondsToTicks(config.getInt("topology-graft.duration-seconds", 30));
        double activationRadius = Math.max(0.5D, config.getDouble("topology-graft.activation-radius", 1.35D));
        int activationCooldownTicks = Math.max(5, config.getInt("topology-graft.activation-cooldown-ticks", 20));
        return new TopologyGraftSettings(durationTicks, activationRadius, activationCooldownTicks);
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }
}
