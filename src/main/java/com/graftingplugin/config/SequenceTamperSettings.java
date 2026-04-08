package com.graftingplugin.config;

import org.bukkit.configuration.file.FileConfiguration;

public record SequenceTamperSettings(
    int payloadDurationTicks,
    int openRelayDurationTicks,
    int relayOpenTicks
) {

    public static SequenceTamperSettings fromConfig(FileConfiguration config) {
        int payloadDurationTicks = secondsToTicks(config.getInt("sequence-tamper.payload-duration-seconds", 20));
        int openRelayDurationTicks = secondsToTicks(config.getInt("sequence-tamper.open-relay-duration-seconds", 30));
        int relayOpenTicks = Math.max(5, config.getInt("sequence-tamper.relay-open-ticks", 25));
        return new SequenceTamperSettings(payloadDurationTicks, openRelayDurationTicks, relayOpenTicks);
    }

    private static int secondsToTicks(int seconds) {
        return Math.max(1, seconds) * 20;
    }
}
