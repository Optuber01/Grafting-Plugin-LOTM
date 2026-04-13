package com.graftingplugin.tests;

import com.graftingplugin.conceptgraft.ConceptGraftSettings;
import com.graftingplugin.conceptgraft.ConceptGraftType;
import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger;
import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger.ActivationGate;
import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger.ActiveConceptRuntime;
import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger.ConceptRuntimeKind;

import java.util.List;
import java.util.UUID;

public final class ConceptualRuntimeLedgerTest {

    private ConceptualRuntimeLedgerTest() {
    }

    public static void run() {
        testCooldownAndMaxActiveProgression();
        testReleaseKeepsCooldownButDropsActiveEntry();
        testClearAllResetsRuntimeState();
        testRuntimeKindIsRecorded();
    }

    private static void testCooldownAndMaxActiveProgression() {
        ConceptualRuntimeLedger ledger = new ConceptualRuntimeLedger();
        UUID ownerId = UUID.randomUUID();
        ConceptGraftSettings settings = new ConceptGraftSettings(200, 8.0D, 20, 200, 300, 1.5D, 20, 1);
        long start = 1_000L;

        ActivationGate initial = ledger.checkActivation(ownerId, start, settings);
        if (!initial.allowed()) {
            throw new AssertionError("Fresh owner should be allowed to activate conceptual grafts");
        }

        ledger.recordActivation(UUID.randomUUID(), ownerId, ConceptRuntimeKind.LAW_ZONE, ConceptGraftType.SUN_TO_GROUND, "world @ 0, 64, 0", 200, 200, start);

        ActivationGate cooldownBlocked = ledger.checkActivation(ownerId, start + 1_000L, settings);
        if (!cooldownBlocked.cooldownBlocked()) {
            throw new AssertionError("Owner should be on cooldown immediately after activation");
        }

        ActivationGate maxActiveBlocked = ledger.checkActivation(ownerId, start + 11_000L, settings);
        if (!maxActiveBlocked.maxActiveBlocked()) {
            throw new AssertionError("Owner should hit max-active after cooldown expires while graft remains active");
        }
    }

    private static void testReleaseKeepsCooldownButDropsActiveEntry() {
        ConceptualRuntimeLedger ledger = new ConceptualRuntimeLedger();
        UUID ownerId = UUID.randomUUID();
        UUID trackingId = UUID.randomUUID();
        ConceptGraftSettings settings = new ConceptGraftSettings(200, 8.0D, 20, 200, 300, 1.5D, 20, 1);
        long start = 5_000L;

        ledger.recordActivation(trackingId, ownerId, ConceptRuntimeKind.IDENTITY_LOOP, ConceptGraftType.BEGINNING_TO_END, "anchors", 200, 200, start);
        ledger.release(trackingId);

        List<ActiveConceptRuntime> active = ledger.activeFor(ownerId);
        if (!active.isEmpty()) {
            throw new AssertionError("Released conceptual graft should no longer be active");
        }
        if (ledger.cooldownRemainingSeconds(ownerId, start + 2_000L) <= 0L) {
            throw new AssertionError("Cooldown should remain after the active graft is released early");
        }
        ActivationGate blocked = ledger.checkActivation(ownerId, start + 2_000L, settings);
        if (!blocked.cooldownBlocked()) {
            throw new AssertionError("Cooldown should still block activation after early release");
        }
    }

    private static void testClearAllResetsRuntimeState() {
        ConceptualRuntimeLedger ledger = new ConceptualRuntimeLedger();
        UUID ownerId = UUID.randomUUID();
        ConceptGraftSettings settings = new ConceptGraftSettings(200, 8.0D, 20, 200, 300, 1.5D, 20, 1);
        long start = 10_000L;

        ledger.recordActivation(UUID.randomUUID(), ownerId, ConceptRuntimeKind.RELATION_RELAY, ConceptGraftType.THRESHOLD_TO_ELSEWHERE, "relay", 300, 200, start);
        ledger.clearAll();

        if (!ledger.activeFor(ownerId).isEmpty()) {
            throw new AssertionError("clearAll should remove all active conceptual entries");
        }
        if (ledger.cooldownRemainingSeconds(ownerId, start + 1_000L) != 0L) {
            throw new AssertionError("clearAll should also clear conceptual cooldowns");
        }
        if (!ledger.checkActivation(ownerId, start + 1_000L, settings).allowed()) {
            throw new AssertionError("Owner should be able to activate again after clearAll");
        }
    }

    private static void testRuntimeKindIsRecorded() {
        ConceptualRuntimeLedger ledger = new ConceptualRuntimeLedger();
        UUID ownerId = UUID.randomUUID();
        UUID trackingId = UUID.randomUUID();

        ledger.recordActivation(trackingId, ownerId, ConceptRuntimeKind.RELATION_ZONE, ConceptGraftType.CONCEALMENT_TO_RECOGNITION, "recognition zone", 200, 200, 20_000L);

        ActiveConceptRuntime runtime = ledger.activeFor(ownerId).stream().findFirst().orElseThrow(
            () -> new AssertionError("Expected conceptual runtime entry to be recorded")
        );
        if (runtime.kind() != ConceptRuntimeKind.RELATION_ZONE) {
            throw new AssertionError("Expected RELATION_ZONE, got " + runtime.kind());
        }
        if (runtime.type() != ConceptGraftType.CONCEALMENT_TO_RECOGNITION) {
            throw new AssertionError("Expected CONCEALMENT_TO_RECOGNITION, got " + runtime.type());
        }
    }
}
