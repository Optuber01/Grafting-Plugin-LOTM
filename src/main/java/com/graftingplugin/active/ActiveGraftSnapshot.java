package com.graftingplugin.active;

import com.graftingplugin.cast.GraftFamily;

import java.util.UUID;

public record ActiveGraftSnapshot(
    UUID trackingId,
    GraftFamily family,
    String aspectName,
    String sourceName,
    String targetName,
    long createdOrder,
    long expiresAtMillis
) {

    public long remainingSeconds() {
        long remainingMillis = Math.max(0L, expiresAtMillis - System.currentTimeMillis());
        return Math.max(1L, (long) Math.ceil(remainingMillis / 1000.0D));
    }
}
