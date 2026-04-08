package com.graftingplugin.cast;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CastSessionManager {

    private final Map<UUID, CastSession> sessions = new ConcurrentHashMap<>();

    public CastSession session(UUID playerId) {
        return sessions.computeIfAbsent(playerId, ignored -> new CastSession());
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clearAll() {
        sessions.clear();
    }
}
