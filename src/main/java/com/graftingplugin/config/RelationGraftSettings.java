package com.graftingplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

public record RelationGraftSettings(
    int aggroDurationTicks,
    int aggroRefreshTicks,
    int projectileDurationTicks,
    int projectileRefreshTicks,
    double projectileTurnStrength,
    int containerDurationTicks,
    int tetherDurationTicks,
    int tetherRefreshTicks,
    double tetherStrength,
    double tetherSlackDistance
) {

    public static RelationGraftSettings fromConfig(FileConfiguration config) {
        int aggroDurationTicks = secondsToTicks(config.getInt("relation-graft.aggro-duration-seconds", 15));
        int aggroRefreshTicks = Math.max(5, config.getInt("relation-graft.aggro-refresh-ticks", 10));
        int projectileDurationTicks = secondsToTicks(config.getInt("relation-graft.projectile-duration-seconds", 10));
        int projectileRefreshTicks = Math.max(1, config.getInt("relation-graft.projectile-refresh-ticks", 2));
        double projectileTurnStrength = clamp(config.getDouble("relation-graft.projectile-turn-strength", 0.35D), 0.05D, 1.0D);
        int containerDurationTicks = secondsToTicks(config.getInt("relation-graft.container-duration-seconds", 20));
        int tetherDurationTicks = secondsToTicks(config.getInt("relation-graft.tether-duration-seconds", 12));
        int tetherRefreshTicks = Math.max(1, config.getInt("relation-graft.tether-refresh-ticks", 2));
        double tetherStrength = Math.max(0.05D, config.getDouble("relation-graft.tether-strength", 0.18D));
        double tetherSlackDistance = Math.max(0.5D, config.getDouble("relation-graft.tether-slack-distance", 2.0D));

        return new RelationGraftSettings(
            aggroDurationTicks,
            aggroRefreshTicks,
            projectileDurationTicks,
            projectileRefreshTicks,
            projectileTurnStrength,
            containerDurationTicks,
            tetherDurationTicks,
            tetherRefreshTicks,
            tetherStrength,
            tetherSlackDistance
        );
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
