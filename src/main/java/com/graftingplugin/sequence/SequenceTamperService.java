package com.graftingplugin.sequence;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.AspectEffectConfig;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.config.SequenceTamperSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

public final class SequenceTamperService implements Listener {

    private final GraftingPlugin plugin;
    private final SequenceTamperPlanner planner;
    private final Map<UUID, ActiveHitPayload> activeHitPayloads = new ConcurrentHashMap<>();
    private final Map<String, ActiveOpenRelay> activeOpenRelays = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    public SequenceTamperService(GraftingPlugin plugin, SequenceTamperPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
    }

    public void shutdown() {
        for (UUID projectileId : Set.copyOf(activeHitPayloads.keySet())) {
            clearHitPayload(projectileId);
        }
        for (String targetKey : Set.copyOf(activeOpenRelays.keySet())) {
            clearOpenRelay(targetKey);
        }
        for (BukkitTask task : Set.copyOf(activeTasks)) {
            task.cancel();
        }
        activeTasks.clear();
    }

    public boolean applyToProjectile(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, Projectile targetProjectile) {
        GraftSubject target = plugin.subjectResolver().resolveProjectile(targetProjectile).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        SequenceTamperPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null) {
            return false;
        }

        boolean success = switch (plan.mode()) {
            case PROJECTILE_HIT_PAYLOAD -> applyProjectileHitPayload(caster, source, aspect, target, targetProjectile, plan.modifier());
            default -> false;
        };
        if (!success) {
            plugin.messages().send(caster, "sequence-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.displayName()
            ));
            return false;
        }

        finishProjectileCast(caster, source, aspect, target, plan);
        return true;
    }

    public boolean applyToBlock(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, Block targetBlock) {
        GraftSubject target = plugin.subjectResolver().resolveBlock(targetBlock).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        SequenceTamperPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null) {
            return false;
        }

        boolean success = switch (plan.mode()) {
            case INTERACT_RELAY -> applyInteractRelay(caster, source, sourceReference, aspect, target, targetBlock, plan.modifier());
            default -> false;
        };
        if (!success) {
            plugin.messages().send(caster, "sequence-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.displayName()
            ));
            return false;
        }

        finishRelayCast(caster, aspect, target, plan);
        return true;
    }

    private void finishProjectileCast(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, SequenceTamperPlan plan) {
        plugin.messages().send(caster, "sequence-cast-hit", Map.of(
            "aspect", aspect.displayName(),
            "target", target.displayName()
        ));
        caster.sendMessage("§7Payload armed for " + formatSeconds(Math.max(1, (int) Math.round(plugin.settings().sequenceTamperSettings().payloadDurationTicks() * plan.modifier().durationMultiplier()))) + ": " + formatAspects(transferablePayloadAspects(source)) + ".");
        caster.sendMessage("§8Your graft setup remains armed. Use §e/graft clear§8 when you want to reset it.");
        caster.playSound(caster.getLocation(), Sound.BLOCK_DISPENSER_LAUNCH, 0.7f, 1.0f);
    }

    private void finishRelayCast(Player caster, GraftAspect aspect, GraftSubject target, SequenceTamperPlan plan) {
        plugin.messages().send(caster, "sequence-cast-open", Map.of(
            "aspect", aspect.displayName(),
            "target", target.displayName()
        ));
        caster.sendMessage("§7Open relay active for " + formatSeconds(Math.max(1, (int) Math.round(plugin.settings().sequenceTamperSettings().openRelayDurationTicks() * plan.modifier().durationMultiplier()))) + ".");
        caster.sendMessage("§8Your graft setup remains armed. Use §e/graft clear§8 when you want to reset it.");
        caster.playSound(caster.getLocation(), Sound.BLOCK_DISPENSER_LAUNCH, 0.7f, 1.0f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        ActiveHitPayload active = activeHitPayloads.remove(projectileId);
        if (active == null) {
            return;
        }

        plugin.activeGraftRegistry().unregister(projectileId);
        cancelTrackedTask(active.cleanupTask());
        if (event.getEntity().isValid()) {
            event.getEntity().setGlowing(active.wasGlowing());
        }
        applyHitPayload(event, active);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Location location = event.getInventory().getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        triggerRelayAt(location.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        triggerRelayAt(event.getClickedBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location broken = event.getBlock().getLocation().toBlockLocation();
        for (ActiveOpenRelay relay : Set.copyOf(activeOpenRelays.values())) {
            if (relay.targetLocation().equals(broken)
                || (relay.sourceBlockLocation() != null && relay.sourceBlockLocation().equals(broken))) {
                clearOpenRelay(relay.targetKey());
            }
        }
    }

    private SequenceTamperPlan validateAndPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateTarget(source, aspect, target);
        if (!compatibility.success()) {
            caster.sendMessage("§c" + compatibility.message());
            return null;
        }

        SequenceTamperPlan plan = planner.plan(aspect, source, target, source.properties()).orElse(null);
        if (plan == null) {
            plugin.messages().send(caster, "sequence-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.displayName()
            ));
            return null;
        }
        return plan;
    }

    private boolean applyProjectileHitPayload(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Projectile targetProjectile, PropertyModifier modifier) {
        Set<GraftAspect> payloadAspects = transferablePayloadAspects(source);
        if (payloadAspects.isEmpty()) {
            return false;
        }

        clearHitPayload(targetProjectile.getUniqueId());
        SequenceTamperSettings settings = plugin.settings().sequenceTamperSettings();
        UUID projectileId = targetProjectile.getUniqueId();
        boolean wasGlowing = targetProjectile.isGlowing();
        int payloadDurationTicks = Math.max(1, (int) Math.round(settings.payloadDurationTicks() * modifier.durationMultiplier()));
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearHitPayload(projectileId), payloadDurationTicks);
        activeTasks.add(cleanupTask);
        activeHitPayloads.put(projectileId, new ActiveHitPayload(payloadAspects, wasGlowing, cleanupTask));
        registerActive(projectileId, caster.getUniqueId(), aspect.displayName(), source.displayName(), target.displayName(), payloadDurationTicks, () -> clearHitPayload(projectileId));
        targetProjectile.setGlowing(true);
        return true;
    }

    private boolean applyInteractRelay(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, GraftSubject target, Block targetBlock, PropertyModifier modifier) {
        Block normalizedTarget = normalizeRelayBlock(targetBlock);
        if (!canRelayThrough(normalizedTarget)) {
            return false;
        }

        RelaySource relaySource = resolveRelaySource(caster, source, sourceReference);
        if (relaySource == null) {
            plugin.messages().send(caster, "stored-source-invalid");
            return false;
        }

        Location targetLocation = normalizedTarget.getLocation().toBlockLocation();
        if (relaySource.blockLocation() != null && relaySource.blockLocation().equals(targetLocation)) {
            return false;
        }

        String targetKey = locationKey(targetLocation);
        UUID trackingId = UUID.nameUUIDFromBytes(targetKey.getBytes(StandardCharsets.UTF_8));
        clearOpenRelay(targetKey);

        int durationTicks = Math.max(1, (int) Math.round(plugin.settings().sequenceTamperSettings().openRelayDurationTicks() * modifier.durationMultiplier()));
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearOpenRelay(targetKey), durationTicks);
        activeTasks.add(cleanupTask);
        activeOpenRelays.put(targetKey, new ActiveOpenRelay(targetKey, relaySource.anchor(), relaySource.blockLocation(), targetLocation, cleanupTask));
        registerActive(trackingId, caster.getUniqueId(), aspect.displayName(), source.displayName(), target.displayName(), durationTicks, () -> clearOpenRelay(targetKey));
        return true;
    }

    private RelaySource resolveRelaySource(Player caster, GraftSubject source, CastSourceReference sourceReference) {
        if (sourceReference != null && sourceReference.hasBlockLocation()) {
            Location sourceLocation = sourceReference.blockLocation();
            if (sourceLocation == null || sourceLocation.getWorld() == null) {
                return null;
            }
            Block normalizedSource = normalizeRelayBlock(sourceLocation.getBlock());
            return new RelaySource(centeredAnchor(normalizedSource.getLocation()), normalizedSource.getLocation().toBlockLocation());
        }
        if (source.kind() == SubjectKind.CONCEPT || source.kind() == SubjectKind.LOCATION || source.kind() == SubjectKind.AREA) {
            return new RelaySource(caster.getLocation().clone(), null);
        }
        return null;
    }

    private Set<GraftAspect> transferablePayloadAspects(GraftSubject source) {
        EnumSet<GraftAspect> payloadAspects = EnumSet.noneOf(GraftAspect.class);
        for (GraftAspect aspect : source.aspectsFor(GraftFamily.STATE)) {
            if (AspectEffectConfig.isPayloadAspect(aspect)) {
                payloadAspects.add(aspect);
            }
        }
        return Set.copyOf(payloadAspects);
    }

    private void applyHitPayload(ProjectileHitEvent event, ActiveHitPayload active) {
        Projectile projectile = event.getEntity();
        Entity hitEntity = event.getHitEntity();
        if (hitEntity != null) {
            applyPayloadToEntity(hitEntity, active.payloadAspects(), projectile);
        }

        Location impact = hitEntity != null
            ? hitEntity.getLocation().add(0.0D, hitEntity.getHeight() * 0.5D, 0.0D)
            : event.getHitBlock() != null
                ? centeredAnchor(event.getHitBlock().getLocation())
                : projectile.getLocation();
        if (impact.getWorld() != null) {
            impact.getWorld().spawnParticle(payloadParticle(active.payloadAspects()), impact, 18, 0.2D, 0.2D, 0.2D, 0.03D);
        }
    }

    private void applyPayloadToEntity(Entity targetEntity, Set<GraftAspect> payloadAspects, Projectile projectile) {

        boolean hasFire = payloadAspects.stream().anyMatch(a -> {
            AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(a).orElse(null);
            return spec != null && spec.causesFire();
        });
        if (hasFire) {
            targetEntity.setFireTicks(Math.max(targetEntity.getFireTicks(), plugin.settings().stateTransferSettings().igniteFireTicks()));
        }

        if (!(targetEntity instanceof LivingEntity livingEntity)) {
            return;
        }

        int duration = plugin.settings().stateTransferSettings().effectDurationTicks();
        for (GraftAspect aspect : payloadAspects) {
            AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(aspect).orElse(null);
            if (spec == null) {
                continue;
            }


            if (spec.causesFire()) {
                if (aspect == GraftAspect.HEAT) {
                    Entity damageSource = projectile.getShooter() instanceof Entity shooter ? shooter : projectile;
                    livingEntity.damage(plugin.settings().stateTransferSettings().heatDamage(), damageSource);
                }
                continue;
            }


            if (aspect == GraftAspect.HEAL) {
                livingEntity.setHealth(Math.min(livingEntity.getMaxHealth(), livingEntity.getHealth() + plugin.settings().stateTransferSettings().healAmount()));
            }


            if (spec.primaryEffect() != null) {
                livingEntity.addPotionEffect(new PotionEffect(spec.primaryEffect(), duration, 0, true, true, true));
            }
        }
    }

    private Particle payloadParticle(Set<GraftAspect> payloadAspects) {


        if (payloadAspects.contains(GraftAspect.HEAT) || payloadAspects.contains(GraftAspect.IGNITE)) {
            return Particle.FLAME;
        }
        if (payloadAspects.contains(GraftAspect.POISON)) {
            return Particle.SQUID_INK;
        }
        if (payloadAspects.contains(GraftAspect.HEAL)) {
            return Particle.HEART;
        }
        if (payloadAspects.contains(GraftAspect.SLOW) || payloadAspects.contains(GraftAspect.STICKY) || payloadAspects.contains(GraftAspect.FREEZE)) {
            return Particle.SNOWFLAKE;
        }
        if (payloadAspects.contains(GraftAspect.SPEED)) {
            return Particle.CLOUD;
        }

        for (GraftAspect aspect : payloadAspects) {
            Particle p = AspectEffectConfig.particleFor(aspect);
            if (p != Particle.ENCHANT) {
                return p;
            }
        }
        return Particle.ENCHANT;
    }

    private void registerActive(UUID trackingId, UUID ownerId, String aspectName, String sourceName, String targetName, int durationTicks, Runnable cleanupAction) {
        for (Runnable cleanup : plugin.activeGraftRegistry().register(
            trackingId,
            ownerId,
            GraftFamily.SEQUENCE,
            aspectName,
            sourceName,
            targetName,
            durationTicks,
            cleanupAction
        )) {
            cleanup.run();
        }
    }

    private void triggerRelayAt(Block triggerBlock) {
        Block normalizedTrigger = normalizeRelayBlock(triggerBlock);
        if (normalizedTrigger == null) {
            return;
        }
        ActiveOpenRelay relay = activeOpenRelays.get(locationKey(normalizedTrigger.getLocation()));
        if (relay == null) {
            return;
        }
        if (!isRelayValid(relay)) {
            clearOpenRelay(relay.targetKey());
            return;
        }
        triggerOpenRelay(relay);
    }

    private String formatAspects(Set<GraftAspect> aspects) {
        return aspects.stream().map(GraftAspect::displayName).sorted().reduce((left, right) -> left + ", " + right).orElse("none");
    }

    private String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1fs", ticks / 20.0D);
    }

    private ActiveOpenRelay relayForInventory(Inventory inventory) {
        Location inventoryLocation = inventory.getLocation();
        if (inventoryLocation == null || inventoryLocation.getWorld() == null) {
            return null;
        }
        return activeOpenRelays.get(locationKey(inventoryLocation));
    }

    private boolean isRelayValid(ActiveOpenRelay relay) {
        if (relay.sourceAnchor().getWorld() == null || relay.targetLocation().getWorld() == null) {
            return false;
        }
        if (!canRelayThrough(relay.targetLocation().getBlock())) {
            return false;
        }
        return relay.sourceBlockLocation() == null || !relay.sourceBlockLocation().getBlock().getType().isAir();
    }

    private void triggerOpenRelay(ActiveOpenRelay relay) {
        Location anchor = relay.sourceAnchor().clone();
        if (anchor.getWorld() == null) {
            clearOpenRelay(relay.targetKey());
            return;
        }

        anchor.getWorld().spawnParticle(Particle.PORTAL, anchor, 18, 0.25D, 0.25D, 0.25D, 0.05D);
        anchor.getWorld().spawnParticle(Particle.ENCHANT, anchor, 24, 0.35D, 0.35D, 0.35D, 0.05D);
        anchor.getWorld().playSound(anchor, Sound.BLOCK_CHEST_OPEN, 0.8F, 1.15F);
        if (relay.sourceBlockLocation() != null) {
            activateRelaySource(relay.sourceBlockLocation(), plugin.settings().sequenceTamperSettings().relayOpenTicks());
        }
    }

    private boolean canRelayThrough(Block block) {
        if (block == null || block.getType().isAir()) {
            return false;
        }
        BlockState state = block.getState();
        if (state instanceof Container || state instanceof Lidded) {
            return true;
        }
        BlockData data = block.getBlockData();
        return data instanceof Openable || data instanceof Powerable;
    }

    private Block normalizeRelayBlock(Block block) {
        if (block == null) {
            return null;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    private void activateRelaySource(Location sourceBlockLocation, int delayTicks) {
        if (sourceBlockLocation.getWorld() == null) {
            return;
        }
        Block sourceBlock = sourceBlockLocation.getBlock();
        BlockState state = sourceBlock.getState();
        if (state instanceof Lidded lidded) {
            lidded.open();
            scheduleRelayClose(sourceBlockLocation, delayTicks);
            return;
        }

        BlockData original = sourceBlock.getBlockData().clone();
        BlockData updated = original.clone();
        boolean changed = false;
        if (updated instanceof Openable openable) {
            openable.setOpen(!openable.isOpen());
            changed = true;
        }
        if (updated instanceof Powerable powerable) {
            powerable.setPowered(!powerable.isPowered());
            changed = true;
        }
        if (!changed) {
            return;
        }
        sourceBlock.setBlockData(updated, true);
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (sourceBlockLocation.getWorld() == null) {
                    return;
                }
                sourceBlockLocation.getBlock().setBlockData(original, true);
            } finally {
                activeTasks.remove(taskHolder[0]);
            }
        }, delayTicks);
        activeTasks.add(taskHolder[0]);
    }

    private void scheduleRelayClose(Location sourceBlockLocation, int delayTicks) {
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                if (sourceBlockLocation.getWorld() == null) {
                    return;
                }
                Block sourceBlock = sourceBlockLocation.getBlock();
                if (sourceBlock.getState() instanceof Lidded lidded) {
                    lidded.close();
                }
            } finally {
                activeTasks.remove(taskHolder[0]);
            }
        }, delayTicks);
        activeTasks.add(taskHolder[0]);
    }

    private void clearHitPayload(UUID projectileId) {
        ActiveHitPayload active = activeHitPayloads.remove(projectileId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(projectileId);
        cancelTrackedTask(active.cleanupTask());
        Entity sourceEntity = plugin.getServer().getEntity(projectileId);
        if (sourceEntity instanceof Projectile projectile && projectile.isValid()) {
            projectile.setGlowing(active.wasGlowing());
        }
    }

    private void clearOpenRelay(String targetKey) {
        ActiveOpenRelay active = activeOpenRelays.remove(targetKey);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(UUID.nameUUIDFromBytes(targetKey.getBytes(StandardCharsets.UTF_8)));
        cancelTrackedTask(active.cleanupTask());
    }

    private void cancelTrackedTask(BukkitTask task) {
        if (task == null) {
            return;
        }
        task.cancel();
        activeTasks.remove(task);
    }

    private String locationKey(Location location) {
        Location normalized = location.toBlockLocation();
        String worldName = normalized.getWorld() == null ? "world" : normalized.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
        return worldName + ':' + normalized.getBlockX() + ':' + normalized.getBlockY() + ':' + normalized.getBlockZ();
    }

    private Location centeredAnchor(Location location) {
        return location.clone().add(0.5D, 0.5D, 0.5D);
    }

    private record RelaySource(Location anchor, Location blockLocation) {
    }

    private record ActiveHitPayload(Set<GraftAspect> payloadAspects, boolean wasGlowing, BukkitTask cleanupTask) {

        private ActiveHitPayload {
            payloadAspects = Set.copyOf(payloadAspects);
        }
    }

    private record ActiveOpenRelay(String targetKey, Location sourceAnchor, Location sourceBlockLocation, Location targetLocation, BukkitTask cleanupTask) {
    }
}
