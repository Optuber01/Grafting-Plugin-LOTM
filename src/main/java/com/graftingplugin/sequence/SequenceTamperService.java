package com.graftingplugin.sequence;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.config.SequenceTamperSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Lidded;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
            case PROJECTILE_HIT_PAYLOAD -> applyProjectileHitPayload(source, targetProjectile);
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

        plugin.messages().send(caster, "sequence-cast-hit", Map.of(
            "aspect", aspect.displayName(),
            "target", target.displayName()
        ));
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
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
            case CONTAINER_OPEN_RELAY -> applyContainerOpenRelay(caster, source, sourceReference, targetBlock);
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

        plugin.messages().send(caster, "sequence-cast-open", Map.of(
            "aspect", aspect.displayName(),
            "target", target.displayName()
        ));
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        ActiveHitPayload active = activeHitPayloads.remove(projectileId);
        if (active == null) {
            return;
        }

        cancelTrackedTask(active.cleanupTask());
        if (event.getEntity().isValid()) {
            event.getEntity().setGlowing(active.wasGlowing());
        }
        applyHitPayload(event, active);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        ActiveOpenRelay relay = relayForInventory(event.getInventory());
        if (relay == null) {
            return;
        }
        if (!isRelayValid(relay)) {
            clearOpenRelay(relay.targetKey());
            return;
        }

        triggerOpenRelay(relay);
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
            plugin.messages().send(caster, "target-incompatible", Map.of(
                "target", target.displayName(),
                "aspect", aspect.displayName()
            ));
            return null;
        }

        SequenceTamperPlan plan = planner.plan(aspect, source, target).orElse(null);
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

    private boolean applyProjectileHitPayload(GraftSubject source, Projectile targetProjectile) {
        Set<GraftAspect> payloadAspects = transferablePayloadAspects(source);
        if (payloadAspects.isEmpty()) {
            return false;
        }

        clearHitPayload(targetProjectile.getUniqueId());
        SequenceTamperSettings settings = plugin.settings().sequenceTamperSettings();
        UUID projectileId = targetProjectile.getUniqueId();
        boolean wasGlowing = targetProjectile.isGlowing();
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearHitPayload(projectileId), settings.payloadDurationTicks());
        activeTasks.add(cleanupTask);
        activeHitPayloads.put(projectileId, new ActiveHitPayload(payloadAspects, wasGlowing, cleanupTask));
        targetProjectile.setGlowing(true);
        return true;
    }

    private boolean applyContainerOpenRelay(Player caster, GraftSubject source, CastSourceReference sourceReference, Block targetBlock) {
        if (!(targetBlock.getState() instanceof Container)) {
            return false;
        }

        RelaySource relaySource = resolveRelaySource(caster, source, sourceReference);
        if (relaySource == null) {
            plugin.messages().send(caster, "stored-source-invalid");
            return false;
        }

        Location targetLocation = targetBlock.getLocation().toBlockLocation();
        if (relaySource.blockLocation() != null && relaySource.blockLocation().equals(targetLocation)) {
            return false;
        }

        String targetKey = locationKey(targetLocation);
        clearOpenRelay(targetKey);

        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearOpenRelay(targetKey), plugin.settings().sequenceTamperSettings().openRelayDurationTicks());
        activeTasks.add(cleanupTask);
        activeOpenRelays.put(targetKey, new ActiveOpenRelay(targetKey, relaySource.anchor(), relaySource.blockLocation(), targetLocation, cleanupTask));
        return true;
    }

    private RelaySource resolveRelaySource(Player caster, GraftSubject source, CastSourceReference sourceReference) {
        if (sourceReference != null && sourceReference.hasBlockLocation()) {
            Location sourceLocation = sourceReference.blockLocation();
            if (sourceLocation == null || sourceLocation.getWorld() == null) {
                return null;
            }
            return new RelaySource(centeredAnchor(sourceLocation), sourceLocation.toBlockLocation());
        }
        if (source.kind() == SubjectKind.CONCEPT || source.kind() == SubjectKind.LOCATION || source.kind() == SubjectKind.AREA) {
            return new RelaySource(caster.getLocation().clone(), null);
        }
        return null;
    }

    private Set<GraftAspect> transferablePayloadAspects(GraftSubject source) {
        EnumSet<GraftAspect> payloadAspects = EnumSet.noneOf(GraftAspect.class);
        for (GraftAspect aspect : source.aspectsFor(GraftFamily.STATE)) {
            switch (aspect) {
                case LIGHT, GLOW, HEAT, IGNITE, FREEZE, STICKY, POISON, HEAL, SPEED, SLOW, CONCEAL -> payloadAspects.add(aspect);
                default -> {
                }
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
        if (payloadAspects.contains(GraftAspect.IGNITE) || payloadAspects.contains(GraftAspect.HEAT)) {
            targetEntity.setFireTicks(Math.max(targetEntity.getFireTicks(), plugin.settings().stateTransferSettings().igniteFireTicks()));
        }

        if (!(targetEntity instanceof LivingEntity livingEntity)) {
            return;
        }

        int duration = plugin.settings().stateTransferSettings().effectDurationTicks();
        if (payloadAspects.contains(GraftAspect.LIGHT) || payloadAspects.contains(GraftAspect.GLOW)) {
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, true, true, true));
        }
        if (payloadAspects.contains(GraftAspect.SPEED)) {
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, true, true, true));
        }
        if (payloadAspects.contains(GraftAspect.SLOW) || payloadAspects.contains(GraftAspect.STICKY) || payloadAspects.contains(GraftAspect.FREEZE)) {
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, true));
        }
        if (payloadAspects.contains(GraftAspect.POISON)) {
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0, true, true, true));
        }
        if (payloadAspects.contains(GraftAspect.HEAL)) {
            livingEntity.setHealth(Math.min(livingEntity.getMaxHealth(), livingEntity.getHealth() + plugin.settings().stateTransferSettings().healAmount()));
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, true, true, true));
        }
        if (payloadAspects.contains(GraftAspect.CONCEAL)) {
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, true, true));
        }
        if (payloadAspects.contains(GraftAspect.HEAT)) {
            Entity damageSource = projectile.getShooter() instanceof Entity shooter ? shooter : projectile;
            livingEntity.damage(plugin.settings().stateTransferSettings().heatDamage(), damageSource);
        }
    }

    private Particle payloadParticle(Set<GraftAspect> payloadAspects) {
        if (payloadAspects.contains(GraftAspect.HEAT) || payloadAspects.contains(GraftAspect.IGNITE)) {
            return Particle.FLAME;
        }
        if (payloadAspects.contains(GraftAspect.POISON)) {
            return Particle.ENTITY_EFFECT;
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
        return Particle.ENCHANT;
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
        if (!(relay.targetLocation().getBlock().getState() instanceof Container)) {
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
            Block sourceBlock = relay.sourceBlockLocation().getBlock();
            if (sourceBlock.getState() instanceof Lidded lidded) {
                lidded.open();
                scheduleRelayClose(relay.sourceBlockLocation(), plugin.settings().sequenceTamperSettings().relayOpenTicks());
            }
        }
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
