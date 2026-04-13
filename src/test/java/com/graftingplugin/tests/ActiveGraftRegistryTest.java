package com.graftingplugin.tests;

import com.graftingplugin.active.ActiveGraftRegistry;
import com.graftingplugin.active.ActiveGraftSnapshot;
import com.graftingplugin.cast.GraftFamily;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActiveGraftRegistryTest {

    private ActiveGraftRegistryTest() {
    }

    public static void run() {
        testStateLimitReplacesOldest();
        testSingleSlotFamiliesReplaceOldest();
        testUnregisterRemovesActiveGraft();
        testClearOwnerRunsCleanupAndRemovesEntries();
        testConceptualEntriesKeepDistinctPresentationAndLimitScope();
    }

    private static void testStateLimitReplacesOldest() {
        ActiveGraftRegistry registry = new ActiveGraftRegistry();
        UUID ownerId = UUID.randomUUID();
        AtomicInteger cleanupCount = new AtomicInteger();

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        registry.register(first, ownerId, GraftFamily.STATE, "Heat", "Sun", "Zombie", 200, cleanupCount::incrementAndGet);
        registry.register(second, ownerId, GraftFamily.STATE, "Light", "Sun", "Stone", 200, cleanupCount::incrementAndGet);
        List<Runnable> evicted = registry.register(third, ownerId, GraftFamily.STATE, "Speed", "Potion", "Player", 200, cleanupCount::incrementAndGet);
        evicted.forEach(Runnable::run);

        List<ActiveGraftSnapshot> active = registry.activeFor(ownerId);
        if (active.size() != 2) {
            throw new AssertionError("Expected two active state grafts after replacement, got " + active.size());
        }
        if (active.stream().anyMatch(snapshot -> snapshot.trackingId().equals(first))) {
            throw new AssertionError("Oldest state graft should have been replaced");
        }
        if (cleanupCount.get() != 1) {
            throw new AssertionError("Expected one cleanup callback for state replacement, got " + cleanupCount.get());
        }
    }

    private static void testSingleSlotFamiliesReplaceOldest() {
        ActiveGraftRegistry registry = new ActiveGraftRegistry();
        UUID ownerId = UUID.randomUUID();
        AtomicInteger relationCleanup = new AtomicInteger();

        UUID firstRelation = UUID.randomUUID();
        UUID secondRelation = UUID.randomUUID();
        registry.register(firstRelation, ownerId, GraftFamily.RELATION, "Aggro", "Zombie", "Villager", 200, relationCleanup::incrementAndGet);
        List<Runnable> evicted = registry.register(secondRelation, ownerId, GraftFamily.RELATION, "Target", "Arrow", "Player", 200, relationCleanup::incrementAndGet);
        evicted.forEach(Runnable::run);

        List<ActiveGraftSnapshot> active = registry.activeFor(ownerId);
        if (active.size() != 1 || !active.getFirst().trackingId().equals(secondRelation)) {
            throw new AssertionError("Expected only the newest relation graft to remain active");
        }
        if (relationCleanup.get() != 1) {
            throw new AssertionError("Expected one cleanup callback for relation replacement, got " + relationCleanup.get());
        }
    }

    private static void testUnregisterRemovesActiveGraft() {
        ActiveGraftRegistry registry = new ActiveGraftRegistry();
        UUID ownerId = UUID.randomUUID();
        UUID trackingId = UUID.randomUUID();

        registry.register(trackingId, ownerId, GraftFamily.SEQUENCE, "On Hit", "Splash Potion", "Arrow", 200, () -> {
        });
        registry.unregister(trackingId);

        if (!registry.activeFor(ownerId).isEmpty()) {
            throw new AssertionError("Unregister should remove the active graft entry");
        }
    }

    private static void testClearOwnerRunsCleanupAndRemovesEntries() {
        ActiveGraftRegistry registry = new ActiveGraftRegistry();
        UUID ownerId = UUID.randomUUID();
        AtomicInteger cleanupCount = new AtomicInteger();

        registry.register(UUID.randomUUID(), ownerId, GraftFamily.STATE, "Heat", "Sun", "Zombie", 200, cleanupCount::incrementAndGet);
        registry.register(UUID.randomUUID(), ownerId, GraftFamily.TOPOLOGY, "Anchor", "Door", "Anchor", 200, cleanupCount::incrementAndGet);

        List<Runnable> cleanupActions = registry.clearOwner(ownerId);
        cleanupActions.forEach(Runnable::run);

        if (cleanupActions.size() != 2) {
            throw new AssertionError("Expected two cleanup callbacks from clearOwner, got " + cleanupActions.size());
        }
        if (cleanupCount.get() != 2) {
            throw new AssertionError("Expected two cleanup executions from clearOwner, got " + cleanupCount.get());
        }
        if (!registry.activeFor(ownerId).isEmpty()) {
            throw new AssertionError("clearOwner should remove all active graft entries for the owner");
        }
    }

    private static void testConceptualEntriesKeepDistinctPresentationAndLimitScope() {
        ActiveGraftRegistry registry = new ActiveGraftRegistry();
        UUID ownerId = UUID.randomUUID();

        UUID practicalTopology = UUID.randomUUID();
        UUID conceptualTopology = UUID.randomUUID();
        registry.register(practicalTopology, ownerId, GraftFamily.TOPOLOGY, "Anchor", "Door", "Hall", 200, () -> {
        });
        registry.register(conceptualTopology, ownerId, GraftFamily.TOPOLOGY, "Conceptual Law", true, "Sun → Ground", "solar law", "world @ 0, 64, 0", 200, () -> {
        });

        List<ActiveGraftSnapshot> active = registry.activeFor(ownerId);
        if (active.size() != 2) {
            throw new AssertionError("Conceptual entries should not evict practical topology entries");
        }
        ActiveGraftSnapshot conceptual = active.stream()
            .filter(ActiveGraftSnapshot::conceptual)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a conceptual active entry"));
        if (!"Conceptual Law".equals(conceptual.familyLabel())) {
            throw new AssertionError("Expected conceptual family label to be preserved");
        }
    }
}
