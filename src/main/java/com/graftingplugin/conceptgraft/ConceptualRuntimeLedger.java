package com.graftingplugin.conceptgraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConceptualRuntimeLedger {

    private final Map<UUID, ActiveConceptRuntime> activeById = new HashMap<>();
    private final Map<UUID, List<UUID>> activeByOwner = new HashMap<>();
    private final Map<UUID, Long> cooldownsByOwner = new HashMap<>();

    public ActivationGate checkActivation(UUID ownerId, long nowMillis, ConceptGraftSettings settings) {
        Long cooldownUntil = cooldownsByOwner.get(ownerId);
        if (cooldownUntil != null && cooldownUntil > nowMillis) {
            long remainingMillis = cooldownUntil - nowMillis;
            long remainingSeconds = Math.max(1L, (long) Math.ceil(remainingMillis / 1000.0D));
            return ActivationGate.cooldown(remainingSeconds);
        }
        int activeCount = activeByOwner.getOrDefault(ownerId, List.of()).size();
        if (activeCount >= settings.maxActivePerPlayer()) {
            return ActivationGate.maxActive(activeCount);
        }
        return ActivationGate.allowed(activeCount);
    }

    public void recordActivation(UUID trackingId, UUID ownerId, ConceptRuntimeKind kind, ConceptGraftType type, String targetName, int durationTicks, int cooldownTicks, long nowMillis) {
        ActiveConceptRuntime runtime = new ActiveConceptRuntime(
            trackingId,
            ownerId,
            kind,
            type,
            targetName,
            nowMillis + Math.max(1L, durationTicks) * 50L
        );
        activeById.put(trackingId, runtime);
        activeByOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(trackingId);
        cooldownsByOwner.put(ownerId, nowMillis + Math.max(1L, cooldownTicks) * 50L);
    }

    public void release(UUID trackingId) {
        ActiveConceptRuntime removed = activeById.remove(trackingId);
        if (removed == null) {
            return;
        }
        List<UUID> ownerEntries = activeByOwner.get(removed.ownerId());
        if (ownerEntries == null) {
            return;
        }
        ownerEntries.removeIf(existingId -> existingId.equals(trackingId));
        if (ownerEntries.isEmpty()) {
            activeByOwner.remove(removed.ownerId());
        }
    }

    public List<ActiveConceptRuntime> activeFor(UUID ownerId) {
        List<UUID> ownerEntries = activeByOwner.get(ownerId);
        if (ownerEntries == null || ownerEntries.isEmpty()) {
            return List.of();
        }
        List<ActiveConceptRuntime> runtimes = new ArrayList<>(ownerEntries.size());
        for (UUID trackingId : ownerEntries) {
            ActiveConceptRuntime runtime = activeById.get(trackingId);
            if (runtime != null) {
                runtimes.add(runtime);
            }
        }
        return List.copyOf(runtimes);
    }

    public long cooldownRemainingSeconds(UUID ownerId, long nowMillis) {
        Long cooldownUntil = cooldownsByOwner.get(ownerId);
        if (cooldownUntil == null || cooldownUntil <= nowMillis) {
            return 0L;
        }
        return Math.max(1L, (long) Math.ceil((cooldownUntil - nowMillis) / 1000.0D));
    }

    public void clearAll() {
        activeById.clear();
        activeByOwner.clear();
        cooldownsByOwner.clear();
    }

    public record ActivationGate(boolean allowed, boolean cooldownBlocked, boolean maxActiveBlocked, long remainingSeconds, int activeCount) {

        private static ActivationGate allowed(int activeCount) {
            return new ActivationGate(true, false, false, 0L, activeCount);
        }

        private static ActivationGate cooldown(long remainingSeconds) {
            return new ActivationGate(false, true, false, remainingSeconds, 0);
        }

        private static ActivationGate maxActive(int activeCount) {
            return new ActivationGate(false, false, true, 0L, activeCount);
        }
    }

    public enum ConceptRuntimeKind {
        LAW_ZONE,
        IDENTITY_LOOP,
        RELATION_ZONE,
        RELATION_RELAY
    }

    public record ActiveConceptRuntime(
        UUID trackingId,
        UUID ownerId,
        ConceptRuntimeKind kind,
        ConceptGraftType type,
        String targetName,
        long expiresAtMillis
    ) {
    }
}
