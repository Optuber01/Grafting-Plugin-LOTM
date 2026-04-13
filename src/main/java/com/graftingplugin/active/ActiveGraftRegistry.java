package com.graftingplugin.active;

import com.graftingplugin.cast.GraftFamily;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class ActiveGraftRegistry {

    private final AtomicLong nextCreatedOrder = new AtomicLong();
    private final Map<UUID, RegisteredGraft> byTrackingId = new HashMap<>();
    private final Map<UUID, List<RegisteredGraft>> byOwner = new HashMap<>();

    public synchronized List<Runnable> register(
        UUID trackingId,
        UUID ownerId,
        GraftFamily family,
        String aspectName,
        String sourceName,
        String targetName,
        int durationTicks,
        Runnable cleanupAction
    ) {
        return register(trackingId, ownerId, family, family.name(), family.displayName(), false, aspectName, sourceName, targetName, durationTicks, cleanupAction);
    }

    public synchronized List<Runnable> register(
        UUID trackingId,
        UUID ownerId,
        GraftFamily family,
        String familyLabel,
        boolean conceptual,
        String aspectName,
        String sourceName,
        String targetName,
        int durationTicks,
        Runnable cleanupAction
    ) {
        return register(trackingId, ownerId, family, conceptual ? "CONCEPTUAL" : family.name(), familyLabel, conceptual, aspectName, sourceName, targetName, durationTicks, cleanupAction);
    }

    public synchronized List<Runnable> register(
        UUID trackingId,
        UUID ownerId,
        GraftFamily family,
        String limitScope,
        String familyLabel,
        boolean conceptual,
        String aspectName,
        String sourceName,
        String targetName,
        int durationTicks,
        Runnable cleanupAction
    ) {
        Objects.requireNonNull(trackingId, "trackingId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(limitScope, "limitScope");
        Objects.requireNonNull(familyLabel, "familyLabel");
        Objects.requireNonNull(aspectName, "aspectName");
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(cleanupAction, "cleanupAction");

        RegisteredGraft graft = new RegisteredGraft(
            trackingId,
            ownerId,
            family,
            limitScope,
            familyLabel,
            conceptual,
            aspectName,
            sourceName,
            targetName,
            nextCreatedOrder.incrementAndGet(),
            System.currentTimeMillis() + Math.max(1L, durationTicks) * 50L,
            cleanupAction
        );
        byTrackingId.put(trackingId, graft);
        byOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(graft);
        return evictOverflow(ownerId, limitScope, family);
    }

    public synchronized void unregister(UUID trackingId) {
        RegisteredGraft removed = byTrackingId.remove(trackingId);
        if (removed == null) {
            return;
        }

        List<RegisteredGraft> ownerEntries = byOwner.get(removed.ownerId());
        if (ownerEntries == null) {
            return;
        }
        ownerEntries.removeIf(entry -> entry.trackingId().equals(trackingId));
        if (ownerEntries.isEmpty()) {
            byOwner.remove(removed.ownerId());
        }
    }

    public synchronized List<ActiveGraftSnapshot> activeFor(UUID ownerId) {
        List<RegisteredGraft> ownerEntries = byOwner.get(ownerId);
        if (ownerEntries == null || ownerEntries.isEmpty()) {
            return List.of();
        }

        return ownerEntries.stream()
            .sorted(Comparator.comparingLong(RegisteredGraft::createdOrder))
            .map(RegisteredGraft::snapshot)
            .toList();
    }

    public synchronized List<Runnable> clearOwner(UUID ownerId) {
        List<RegisteredGraft> ownerEntries = byOwner.remove(ownerId);
        if (ownerEntries == null || ownerEntries.isEmpty()) {
            return List.of();
        }

        List<Runnable> cleanupActions = new ArrayList<>(ownerEntries.size());
        for (RegisteredGraft entry : ownerEntries) {
            byTrackingId.remove(entry.trackingId());
            cleanupActions.add(entry.cleanupAction());
        }
        return List.copyOf(cleanupActions);
    }

    public synchronized void clear() {
        byTrackingId.clear();
        byOwner.clear();
    }

    private List<Runnable> evictOverflow(UUID ownerId, String limitScope, GraftFamily family) {
        List<RegisteredGraft> ownerEntries = byOwner.get(ownerId);
        if (ownerEntries == null || ownerEntries.isEmpty()) {
            return List.of();
        }

        List<RegisteredGraft> familyEntries = ownerEntries.stream()
            .filter(entry -> entry.limitScope().equals(limitScope))
            .sorted(Comparator.comparingLong(RegisteredGraft::createdOrder))
            .toList();
        int overflow = familyEntries.size() - limitFor(limitScope, family);
        if (overflow <= 0) {
            return List.of();
        }

        List<Runnable> cleanupActions = new ArrayList<>(overflow);
        for (int index = 0; index < overflow; index++) {
            RegisteredGraft removed = familyEntries.get(index);
            byTrackingId.remove(removed.trackingId());
            ownerEntries.removeIf(entry -> entry.trackingId().equals(removed.trackingId()));
            cleanupActions.add(removed.cleanupAction());
        }
        if (ownerEntries.isEmpty()) {
            byOwner.remove(ownerId);
        }
        return List.copyOf(cleanupActions);
    }

    private int limitFor(String limitScope, GraftFamily family) {
        if ("CONCEPTUAL".equals(limitScope)) {
            return Integer.MAX_VALUE;
        }
        return switch (family) {
            case STATE -> 2;
            case RELATION, TOPOLOGY, SEQUENCE -> 1;
        };
    }

    private record RegisteredGraft(
        UUID trackingId,
        UUID ownerId,
        GraftFamily family,
        String limitScope,
        String familyLabel,
        boolean conceptual,
        String aspectName,
        String sourceName,
        String targetName,
        long createdOrder,
        long expiresAtMillis,
        Runnable cleanupAction
    ) {

        private ActiveGraftSnapshot snapshot() {
            return new ActiveGraftSnapshot(trackingId, family, familyLabel, conceptual, aspectName, sourceName, targetName, createdOrder, expiresAtMillis);
        }
    }
}
