package com.graftingplugin.tests;

import com.graftingplugin.conceptgraft.ConceptPreviewFeedbackGate;

import java.util.UUID;

public final class ConceptPreviewFeedbackGateTest {

    private ConceptPreviewFeedbackGateTest() {
    }

    public static void run() {
        ConceptPreviewFeedbackGate gate = new ConceptPreviewFeedbackGate(1500L);
        UUID playerId = UUID.randomUUID();

        if (!gate.shouldSend(playerId, "sun:1", "preview", 1000L)) {
            throw new AssertionError("First preview feedback should send");
        }
        if (gate.shouldSend(playerId, "sun:1", "preview", 1200L)) {
            throw new AssertionError("Identical preview feedback should not resend while the preview state is unchanged");
        }
        if (!gate.shouldSend(playerId, "sun:2", "preview", 1200L)) {
            throw new AssertionError("Changed preview key should send immediately");
        }
        if (!gate.shouldSend(playerId, "sun:2", "preview updated", 1300L)) {
            throw new AssertionError("Changed preview message should send immediately");
        }
        if (gate.shouldSend(playerId, "sun:2", "preview updated", 3000L)) {
            throw new AssertionError("Same preview should stay quiet until the preview state changes or clears");
        }

        gate.clear(playerId);
        if (!gate.shouldSend(playerId, "sun:2", "preview updated", 3100L)) {
            throw new AssertionError("Clearing preview state should allow immediate resend");
        }

        gate.clearAll();
        if (!gate.shouldSend(playerId, "sun:2", "preview updated", 3200L)) {
            throw new AssertionError("clearAll should reset all preview state");
        }
    }
}
