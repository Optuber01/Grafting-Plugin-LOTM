package com.graftingplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

public record StateTransferSettings(
    int effectDurationTicks,
    int fieldDurationTicks,
    int fieldRadius,
    int pulseIntervalTicks,
    int igniteFireTicks,
    double heatDamage,
    double healAmount,
    int bounceCount
) {

    public static StateTransferSettings fromConfig(FileConfiguration config) {
        int effectDurationTicks = secondsToTicks(config.getInt("state-transfer.effect-duration-seconds", 20));
        int fieldDurationTicks = secondsToTicks(config.getInt("state-transfer.field-duration-seconds", 12));
        int fieldRadius = Math.max(1, config.getInt("state-transfer.field-radius", 4));
        int pulseIntervalTicks = Math.max(10, config.getInt("state-transfer.pulse-interval-ticks", 20));
        int igniteFireTicks = Math.max(20, config.getInt("state-transfer.ignite-fire-ticks", 100));
        double heatDamage = Math.max(0.0D, config.getDouble("state-transfer.heat-damage", 1.0D));
        double healAmount = Math.max(1.0D, config.getDouble("state-transfer.heal-amount", 4.0D));
        int bounceCount = Math.max(1, config.getInt("state-transfer.bounce-count", 3));

        return new StateTransferSettings(
            effectDurationTicks,
            fieldDurationTicks,
            fieldRadius,
            pulseIntervalTicks,
            igniteFireTicks,
            heatDamage,
            healAmount,
            bounceCount
        );
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }
}
