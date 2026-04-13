package com.graftingplugin.conceptgraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConceptPreviewFeedbackGate {

    private final Map<UUID, PreviewFeedbackState> states = new ConcurrentHashMap<>();
    public ConceptPreviewFeedbackGate(long resendWindowMillis) {
    }

    public boolean shouldSend(UUID playerId, String previewKey, String message, long nowMillis) {
        PreviewFeedbackState previous = states.get(playerId);
        if (previous != null
            && previous.previewKey().equals(previewKey)
            && previous.message().equals(message)) {
            return false;
        }
        states.put(playerId, new PreviewFeedbackState(previewKey, message, nowMillis));
        return true;
    }

    public void clear(UUID playerId) {
        states.remove(playerId);
    }

    public void clearAll() {
        states.clear();
    }

    private record PreviewFeedbackState(String previewKey, String message, long sentAtMillis) {
    }
}
