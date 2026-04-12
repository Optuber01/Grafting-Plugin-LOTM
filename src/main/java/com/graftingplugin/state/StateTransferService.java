package com.graftingplugin.state;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.AspectEffectConfig;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.config.StateTransferSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StateTransferService implements Listener {

    private final GraftingPlugin plugin;
    private final StateTransferPlanner planner;
    private final Map<UUID, ActiveProjectilePayload> projectilePayloads = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveProjectileBounce> projectileBounces = new ConcurrentHashMap<>();
    private final Set<UUID> bouncingEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ActiveBounceEffect> activeBounceEffects = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> bounceProtectionTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveBlockManifest> activeBlockManifests = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveField> activeFields = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    public StateTransferService(GraftingPlugin plugin, StateTransferPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
    }

    public void shutdown() {
        for (ActiveProjectilePayload payload : Set.copyOf(projectilePayloads.values())) {
            clearProjectilePayload(payload.trackingId());
        }
        for (ActiveProjectileBounce bounce : Set.copyOf(projectileBounces.values())) {
            clearProjectileBounce(bounce.trackingId());
        }
        for (UUID trackingId : Set.copyOf(activeBounceEffects.keySet())) {
            clearBounceEffect(trackingId);
        }
        for (BukkitTask task : Set.copyOf(bounceProtectionTasks.values())) {
            cancelTrackedTask(task);
        }
        for (UUID trackingId : Set.copyOf(activeBlockManifests.keySet())) {
            clearBlockManifest(trackingId);
        }
        for (UUID trackingId : Set.copyOf(activeFields.keySet())) {
            clearField(trackingId);
        }
        for (BukkitTask task : Set.copyOf(activeTasks)) {
            task.cancel();
        }
        activeTasks.clear();
        projectilePayloads.clear();
        projectileBounces.clear();
        bouncingEntities.clear();
        activeBounceEffects.clear();
        bounceProtectionTasks.clear();
        activeBlockManifests.clear();
        activeFields.clear();
    }

    public boolean applyToEntity(Player caster, GraftSubject source, GraftAspect aspect, Entity targetEntity) {
        GraftSubject target = plugin.subjectResolver().resolveEntity(targetEntity).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null) {
            return false;
        }
        if (!applyEntityPlan(caster, source, aspect, target, targetEntity, plan)) {
            return false;
        }

        finishEntityCast(caster, aspect, target.displayName(), targetEntity, plan);
        return true;
    }

    public boolean applyToProjectile(Player caster, GraftSubject source, GraftAspect aspect, Projectile projectile) {
        GraftSubject target = plugin.subjectResolver().resolveProjectile(projectile).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null) {
            return false;
        }
        if (!applyProjectilePlan(caster, source, aspect, target, projectile, plan)) {
            return false;
        }

        finishProjectileCast(caster, aspect, target.displayName(), plan);
        return true;
    }

    public boolean applyToOffhandItem(Player caster, GraftSubject source, GraftAspect aspect) {
        ItemStack offhand = caster.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType().isAir() || plugin.focusItemService().isFocus(offhand)) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        GraftSubject target = plugin.subjectResolver().resolveItem(offhand).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null) {
            return false;
        }

        ItemStack updated = applyItemPlan(offhand, plan);
        if (updated == null) {
            caster.sendMessage("§cThat carried item cannot express " + aspect.displayName() + ".");
            return false;
        }

        caster.getInventory().setItemInOffHand(updated);
        caster.updateInventory();
        finishItemCast(caster, aspect, target.displayName(), updated, plan);
        return true;
    }

    public boolean applyToBlock(Player caster, GraftSubject source, GraftAspect aspect, Block block) {
        GraftSubject target = plugin.subjectResolver().resolveBlock(block).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null) {
            return false;
        }
        if (!applyBlockPlan(caster, source, target.displayName(), block, plan)) {
            return false;
        }
        clearCastSelection(caster);
        return true;
    }

    public boolean applyToFluid(Player caster, GraftSubject source, GraftAspect aspect, Block fluidBlock) {
        GraftSubject target = plugin.subjectResolver().resolveFluid(fluidBlock.getType()).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null || plan.mode() != StateTransferMode.BLOCK_MANIFEST) {
            return false;
        }
        if (!applyBlockPlan(caster, source, target.displayName(), fluidBlock, plan)) {
            return false;
        }
        clearCastSelection(caster);
        return true;
    }

    public boolean applyToArea(Player caster, GraftSubject source, GraftAspect aspect, Location center) {
        GraftSubject target = plugin.subjectResolver().resolveArea(center, plugin.settings().stateTransferSettings().fieldRadius()).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null || plan.mode() != StateTransferMode.FIELD) {
            return false;
        }

        startField(caster, source, plan, center, target.displayName());
        clearCastSelection(caster);
        return true;
    }

    private boolean applyEntityPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Entity targetEntity, StateTransferPlan plan) {
        return switch (plan.mode()) {
            case ENTITY_EFFECT -> applyEntityEffect(caster, plan, targetEntity);
            case ENTITY_FIRE -> applyEntityFire(caster, aspect, targetEntity, plan.modifier());
            case ENTITY_BOUNCE -> applyEntityBounce(caster, source, aspect, target, targetEntity, plan.modifier());
            default -> false;
        };
    }

    private boolean applyProjectilePlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Projectile projectile, StateTransferPlan plan) {
        return switch (plan.mode()) {
            case PROJECTILE_TRAIT -> applyProjectileTrait(aspect, projectile, plan.modifier());
            case PROJECTILE_PAYLOAD -> applyProjectilePayload(caster, source, aspect, target, projectile, plan.modifier());
            case PROJECTILE_BOUNCE -> applyProjectileBounce(caster, source, aspect, target, projectile, plan.modifier());
            default -> false;
        };
    }

    private ItemStack applyItemPlan(ItemStack targetItem, StateTransferPlan plan) {
        return switch (plan.mode()) {
            case ITEM_REPAIR -> applyItemRepair(targetItem, plan.modifier());
            case ITEM_DAMAGE -> applyItemDamage(targetItem, plan.modifier());
            default -> null;
        };
    }

    private boolean applyBlockPlan(Player caster, GraftSubject source, String targetName, Block block, StateTransferPlan plan) {
        return switch (plan.mode()) {
            case BLOCK_MANIFEST -> startBlockManifest(caster, source, plan, block, targetName);
            case FIELD -> {
                Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
                startField(caster, source, plan, center, targetName);
                yield true;
            }
            default -> false;
        };
    }

    private void finishEntityCast(Player caster, GraftAspect aspect, String targetName, Entity targetEntity, StateTransferPlan plan) {
        plugin.messages().send(caster, "state-cast-entity", Map.of(
            "aspect", aspect.displayName(),
            "target", targetName
        ));
        caster.sendMessage("§7" + describeEntityOutcome(plan));
        if (targetEntity instanceof LivingEntity living) {
            living.getWorld().spawnParticle(Particle.ENCHANT, living.getLocation().add(0, living.getHeight() / 2, 0), 25, 0.4, 0.4, 0.4, 0.8);
        } else {
            targetEntity.getWorld().spawnParticle(Particle.ENCHANT, targetEntity.getLocation(), 25, 0.4, 0.4, 0.4, 0.8);
        }
        clearCastSelection(caster);
    }

    private void finishProjectileCast(Player caster, GraftAspect aspect, String targetName, StateTransferPlan plan) {
        plugin.messages().send(caster, "state-cast-projectile", Map.of(
            "aspect", aspect.displayName(),
            "target", targetName
        ));
        caster.sendMessage("§7" + describeProjectileOutcome(plan));
        caster.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.0f);
        clearCastSelection(caster);
    }

    private void finishItemCast(Player caster, GraftAspect aspect, String targetName, ItemStack updated, StateTransferPlan plan) {
        caster.sendMessage("§b" + aspect.displayName() + " §7applied to offhand item §6" + targetName + "§7.");
        caster.sendMessage("§7" + describeItemOutcome(plan, updated));
        caster.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.0f);
        clearCastSelection(caster);
    }

    private void clearCastSelection(Player caster) {
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!bouncingEntities.contains(event.getEntity().getUniqueId()) && !hasActiveBounceSurface(event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
        Entity entity = event.getEntity();
        entity.setFallDistance(0.0F);
        Vector velocity = entity.getVelocity();
        entity.setVelocity(new Vector(velocity.getX(), Math.max(0.9D, Math.abs(velocity.getY()) + 0.9D), velocity.getZ()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        ActiveProjectilePayload payload = projectilePayloads.remove(projectile.getUniqueId());
        if (payload == null || !(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        plugin.activeGraftRegistry().unregister(payload.trackingId());
        cancelTrackedTask(payload.cleanupTask());
        applyPayloadToLivingEntity(livingEntity, payload.aspect(), payload.modifier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectilePayloads.containsKey(projectile.getUniqueId()) && !projectileBounces.containsKey(projectile.getUniqueId())) {
            return;
        }

        if (event.getHitEntity() == null) {
            clearProjectilePayloadForProjectile(projectile.getUniqueId());
        }

        ActiveProjectileBounce bounce = projectileBounces.get(projectile.getUniqueId());
        if (bounce == null) {
            return;
        }
        int remainingBounces = bounce.remainingBounces();
        if (remainingBounces <= 0) {
            clearProjectileBounce(bounce.trackingId());
            return;
        }

        Vector current = projectile.getVelocity();
        Vector reflected = current.clone();
        if (event.getHitBlockFace() != null) {
            switch (event.getHitBlockFace()) {
                case EAST, WEST -> reflected.setX(-reflected.getX());
                case NORTH, SOUTH -> reflected.setZ(-reflected.getZ());
                case UP, DOWN -> reflected.setY(Math.abs(reflected.getY()) + 0.5D);
                default -> {
                }
            }
        } else {
            reflected.multiply(-1.0D);
            reflected.setY(Math.abs(reflected.getY()) + 0.4D);
        }
        reflected.multiply(0.8D);

        if (projectile instanceof AbstractArrow arrow) {
            UUID previousProjectileId = projectile.getUniqueId();
            Location spawnLocation = projectile.getLocation().add(reflected.clone().normalize().multiply(0.2D));
            AbstractArrow bouncedArrow = projectile.getWorld().spawnArrow(spawnLocation, reflected, (float) reflected.length(), 0.0F);
            bouncedArrow.setShooter(arrow.getShooter());
            bouncedArrow.setCritical(arrow.isCritical());
            bouncedArrow.setPickupStatus(arrow.getPickupStatus());
            bouncedArrow.setGlowing(arrow.isGlowing());
            ActiveProjectilePayload payload = projectilePayloads.remove(previousProjectileId);
            if (payload != null) {
                projectilePayloads.put(bouncedArrow.getUniqueId(), payload);
            }
            projectileBounces.remove(previousProjectileId);
            projectileBounces.put(bouncedArrow.getUniqueId(), bounce.withRemainingBounces(remainingBounces - 1));
            projectile.remove();
        } else if (projectile.isValid()) {
            projectile.teleport(projectile.getLocation().add(reflected.clone().normalize().multiply(0.3D)));
            projectile.setVelocity(reflected);
            projectileBounces.put(projectile.getUniqueId(), bounce.withRemainingBounces(remainingBounces - 1));
        } else {
            clearProjectileBounce(bounce.trackingId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location broken = event.getBlock().getLocation().toBlockLocation();
        for (Map.Entry<UUID, ActiveBlockManifest> entry : Set.copyOf(activeBlockManifests.entrySet())) {
            ActiveBlockManifest manifest = entry.getValue();
            if (manifest.targetLocation().equals(broken) || (manifest.ambientLocation() != null && manifest.ambientLocation().equals(broken))) {
                clearBlockManifest(entry.getKey());
            }
        }
    }

    private StateTransferPlan validateAndPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateTarget(source, aspect, target);
        if (!compatibility.success()) {
            caster.sendMessage("§c" + compatibility.message());
            return null;
        }

        StateTransferPlan plan = planner.plan(aspect, target, source.properties()).orElse(null);
        if (plan == null) {
            plugin.messages().send(caster, "state-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "target", target.displayName()
            ));
            return null;
        }
        return plan;
    }

    private boolean applyEntityEffect(Player caster, StateTransferPlan plan, Entity targetEntity) {
        if (!(targetEntity instanceof LivingEntity livingEntity)) {
            return false;
        }

        StateTransferSettings settings = plugin.settings().stateTransferSettings();
        GraftAspect aspect = plan.aspect();
        PropertyModifier mod = plan.modifier();


        int duration = (int) (settings.effectDurationTicks() * mod.durationMultiplier());
        int amp = mod.amplifier();


        AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(aspect).orElse(null);
        if (spec == null || spec.primaryEffect() == null) {
            return false;
        }


        livingEntity.addPotionEffect(new PotionEffect(spec.primaryEffect(), duration, amp, true, true, true));


        if (spec.secondaryEffect() != null) {
            livingEntity.addPotionEffect(new PotionEffect(spec.secondaryEffect(), duration, amp, true, true, true));
        }


        if (aspect == GraftAspect.HEAL) {
            livingEntity.setHealth(Math.min(livingEntity.getMaxHealth(), livingEntity.getHealth() + settings.healAmount() * mod.intensity()));
            livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, Math.max(0, mod.amplifier()), true, true, true));
        }

        return true;
    }

    private boolean applyEntityFire(Player caster, GraftAspect aspect, Entity targetEntity, PropertyModifier mod) {
        if (aspect == GraftAspect.IGNITE) {
            int fireTicks = (int) (plugin.settings().stateTransferSettings().igniteFireTicks() * mod.durationMultiplier());
            targetEntity.setFireTicks(Math.max(targetEntity.getFireTicks(), fireTicks));
        }
        if (aspect == GraftAspect.HEAT && targetEntity instanceof LivingEntity livingEntity) {
            livingEntity.damage(plugin.settings().stateTransferSettings().heatDamage() * mod.intensity(), caster);
        }
        return true;
    }

    private boolean applyEntityBounce(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Entity targetEntity, PropertyModifier mod) {
        targetEntity.setFallDistance(0.0F);
        Vector velocity = targetEntity.getVelocity();
        double bounceForce = Math.max(1.0D, velocity.getY() + 1.0D * mod.intensity());
        targetEntity.setVelocity(new Vector(velocity.getX(), bounceForce, velocity.getZ()));
        UUID trackingId = targetEntity.getUniqueId();
        clearBounceEffect(trackingId);
        bouncingEntities.add(trackingId);
        int durationTicks = Math.max(1, (int) Math.round(plugin.settings().stateTransferSettings().effectDurationTicks() * mod.durationMultiplier()));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearBounceEffect(trackingId), durationTicks);
        activeTasks.add(task);
        activeBounceEffects.put(trackingId, new ActiveBounceEffect(task));
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), target.displayName(), durationTicks, () -> clearBounceEffect(trackingId));
        return true;
    }

    private boolean applyProjectileTrait(GraftAspect aspect, Projectile projectile, PropertyModifier mod) {
        AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(aspect).orElse(null);


        if (aspect == GraftAspect.LIGHT || aspect == GraftAspect.GLOW) {
            projectile.setGlowing(true);
            return true;
        }


        if (spec != null && spec.velocityScale() != 1.0) {
            double scale = spec.velocityScale() * mod.intensity();
            projectile.setVelocity(projectile.getVelocity().multiply(scale));
            return true;
        }


        if (aspect == GraftAspect.HEAVY) {
            projectile.setGravity(true);
            projectile.setVelocity(projectile.getVelocity().multiply(0.75D * mod.intensity()));
            return true;
        }

        return false;
    }

    private boolean applyProjectilePayload(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Projectile projectile, PropertyModifier mod) {
        clearProjectilePayloadForProjectile(projectile.getUniqueId());
        int durationTicks = Math.max(1, (int) Math.round(plugin.settings().stateTransferSettings().effectDurationTicks() * mod.durationMultiplier()));
        UUID trackingId = UUID.randomUUID();
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearProjectilePayload(trackingId), durationTicks);
        activeTasks.add(cleanupTask);
        projectilePayloads.put(projectile.getUniqueId(), new ActiveProjectilePayload(trackingId, aspect, mod, cleanupTask));
        if (aspect == GraftAspect.IGNITE) {
            projectile.setFireTicks(plugin.settings().stateTransferSettings().igniteFireTicks());
        }
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), target.displayName(), durationTicks, () -> clearProjectilePayload(trackingId));
        return true;
    }

    private ItemStack applyItemRepair(ItemStack targetItem, PropertyModifier mod) {
        if (!(targetItem.getItemMeta() instanceof Damageable damageable) || targetItem.getType().getMaxDurability() <= 0) {
            return null;
        }

        ItemStack updated = targetItem.clone();
        Damageable updatedMeta = (Damageable) updated.getItemMeta();
        int repairAmount = Math.max(1, (int) Math.round(targetItem.getType().getMaxDurability() * 0.12D * mod.intensity()));
        updatedMeta.setDamage(Math.max(0, updatedMeta.getDamage() - repairAmount));
        updated.setItemMeta(updatedMeta);
        return updated;
    }

    private ItemStack applyItemDamage(ItemStack targetItem, PropertyModifier mod) {
        if (!(targetItem.getItemMeta() instanceof Damageable damageable) || targetItem.getType().getMaxDurability() <= 0) {
            return null;
        }

        ItemStack updated = targetItem.clone();
        Damageable updatedMeta = (Damageable) updated.getItemMeta();
        int damageAmount = Math.max(1, (int) Math.round(targetItem.getType().getMaxDurability() * 0.08D * mod.intensity()));
        int maxDamage = targetItem.getType().getMaxDurability();
        updatedMeta.setDamage(Math.min(maxDamage - 1, updatedMeta.getDamage() + damageAmount));
        updated.setItemMeta(updatedMeta);
        return updated;
    }

    private boolean applyProjectileBounce(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Projectile projectile, PropertyModifier mod) {
        clearProjectileBounceForProjectile(projectile.getUniqueId());
        int durationTicks = Math.max(1, (int) Math.round(plugin.settings().stateTransferSettings().effectDurationTicks() * mod.durationMultiplier()));
        UUID trackingId = UUID.randomUUID();
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearProjectileBounce(trackingId), durationTicks);
        activeTasks.add(cleanupTask);
        projectileBounces.put(projectile.getUniqueId(), new ActiveProjectileBounce(trackingId, plugin.settings().stateTransferSettings().bounceCount(), projectile.isGlowing(), cleanupTask));
        projectile.setGlowing(true);
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), target.displayName(), durationTicks, () -> clearProjectileBounce(trackingId));
        return true;
    }

    private void applyPayloadToLivingEntity(LivingEntity target, GraftAspect aspect, PropertyModifier mod) {
        StateTransferSettings settings = plugin.settings().stateTransferSettings();
        AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(aspect).orElse(null);

        if (aspect == GraftAspect.IGNITE) {
            target.setFireTicks(Math.max(target.getFireTicks(), (int) Math.round(settings.igniteFireTicks() * mod.durationMultiplier())));
            return;
        }
        if (aspect == GraftAspect.HEAT) {
            target.damage(settings.heatDamage() * mod.intensity());
            return;
        }

        if (spec != null && spec.primaryEffect() != null) {
            target.addPotionEffect(new PotionEffect(spec.primaryEffect(), Math.max(1, (int) Math.round(settings.effectDurationTicks() * mod.durationMultiplier())), mod.amplifier(), true, true, true));
            if (spec.secondaryEffect() != null) {
                target.addPotionEffect(new PotionEffect(spec.secondaryEffect(), Math.max(1, (int) Math.round(settings.effectDurationTicks() * mod.durationMultiplier())), mod.amplifier(), true, true, true));
            }
        }
        if (aspect == GraftAspect.HEAL) {
            target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + settings.healAmount() * mod.intensity()));
        }
    }

    private void startField(Player caster, GraftSubject source, StateTransferPlan plan, Location center, String targetName) {
        StateTransferSettings settings = plugin.settings().stateTransferSettings();
        GraftAspect aspect = plan.aspect();
        PropertyModifier mod = plan.modifier();
        UUID trackingId = UUID.randomUUID();
        int fieldDuration = Math.max(1, (int) Math.round(settings.fieldDurationTicks() * mod.durationMultiplier()));
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (center.getWorld() == null || elapsed[0] >= fieldDuration) {
                clearField(trackingId);
                return;
            }
            pulseField(caster, aspect, center, settings, mod);
            elapsed[0] += settings.pulseIntervalTicks();
        }, 0L, settings.pulseIntervalTicks());
        activeTasks.add(taskHolder[0]);
        activeFields.put(trackingId, new ActiveField(aspect, center.clone(), settings.fieldRadius() * mod.radiusMultiplier(), taskHolder[0]));
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), targetName, fieldDuration, () -> clearField(trackingId));
        plugin.messages().send(caster, "state-cast-field", Map.of(
            "aspect", aspect.displayName(),
            "target", targetName
        ));
        caster.sendMessage("§7" + describeFieldOutcome(plan, settings, targetName));
    }

    private void pulseField(Player caster, GraftAspect aspect, Location center, StateTransferSettings settings, PropertyModifier mod) {
        if (center.getWorld() == null) {
            return;
        }

        double effectiveRadius = settings.fieldRadius() * mod.radiusMultiplier();
        Particle particle = AspectEffectConfig.particleFor(aspect);

        try {
            center.getWorld().spawnParticle(particle, center, 12, effectiveRadius * 0.25D, 0.5D, effectiveRadius * 0.25D, 0.02D);
        } catch (IllegalArgumentException e) {

            center.getWorld().spawnParticle(Particle.CLOUD, center, 12, effectiveRadius * 0.25D, 0.5D, effectiveRadius * 0.25D, 0.02D);
        }

        int pulseDuration = settings.pulseIntervalTicks() + 10;
        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, effectiveRadius)) {
            applyStateToLivingEntity(caster, aspect, entity, settings, mod, pulseDuration);
        }
    }

    private boolean startBlockManifest(Player caster, GraftSubject source, StateTransferPlan plan, Block targetBlock, String targetName) {
        StateTransferSettings settings = plugin.settings().stateTransferSettings();
        GraftAspect aspect = plan.aspect();
        PropertyModifier mod = plan.modifier();
        UUID trackingId = UUID.randomUUID();
        Location targetLocation = targetBlock.getLocation().toBlockLocation();
        Location center = targetLocation.clone().add(0.5D, 0.5D, 0.5D);
        int manifestDuration = Math.max(1, (int) Math.round(settings.fieldDurationTicks() * mod.durationMultiplier()));
        if (applyImmediateBlockReaction(caster, targetBlock, aspect, mod)) {
            plugin.messages().send(caster, "state-cast-field", Map.of(
                "aspect", aspect.displayName(),
                "target", targetName
            ));
            caster.sendMessage("§7" + targetName + " reacted immediately to " + aspect.displayName() + ".");
            return true;
        }
        double contactRadius = Math.max(1.1D, 0.9D * mod.radiusMultiplier());
        AmbientManifest ambient = establishAmbientManifest(targetBlock, aspect, mod);
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (targetLocation.getWorld() == null || elapsed[0] >= manifestDuration || targetLocation.getBlock().getType().isAir()) {
                clearBlockManifest(trackingId);
                return;
            }
            pulseBlockManifest(caster, aspect, center, settings, mod, contactRadius);
            elapsed[0] += 1;
        }, 0L, 1L);
        activeTasks.add(taskHolder[0]);
        activeBlockManifests.put(trackingId, new ActiveBlockManifest(aspect, targetLocation, ambient == null ? null : ambient.location(), ambient == null ? null : ambient.material(), contactRadius, taskHolder[0]));
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), targetName, manifestDuration, () -> clearBlockManifest(trackingId));
        plugin.messages().send(caster, "state-cast-field", Map.of(
            "aspect", aspect.displayName(),
            "target", targetName
        ));
        caster.sendMessage("§7" + describeBlockOutcome(plan, targetName, contactRadius, manifestDuration, ambient != null));
        return true;
    }

    private void pulseBlockManifest(Player caster, GraftAspect aspect, Location center, StateTransferSettings settings, PropertyModifier mod, double contactRadius) {
        if (center.getWorld() == null) {
            return;
        }

        Particle particle = AspectEffectConfig.particleFor(aspect);
        try {
            center.getWorld().spawnParticle(particle, center, 10, 0.18D, 0.18D, 0.18D, 0.02D);
        } catch (IllegalArgumentException e) {
            center.getWorld().spawnParticle(Particle.CLOUD, center, 10, 0.18D, 0.18D, 0.18D, 0.02D);
        }

        int pulseDuration = settings.pulseIntervalTicks() + 10;
        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, contactRadius, 1.25D, contactRadius)) {
            applyStateToLivingEntity(caster, aspect, entity, settings, mod, pulseDuration);
        }
    }

    private void applyStateToLivingEntity(Player caster, GraftAspect aspect, LivingEntity entity, StateTransferSettings settings, PropertyModifier mod, int durationTicks) {
        AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(aspect).orElse(null);
        if (spec == null) {
            return;
        }

        if (spec.causesFire()) {
            if (aspect == GraftAspect.HEAT) {
                entity.damage(settings.heatDamage() * mod.intensity(), caster);
            } else {
                entity.setFireTicks(Math.max(entity.getFireTicks(), (int) Math.round(settings.igniteFireTicks() * mod.durationMultiplier())));
            }
            return;
        }

        if (spec.causesBounce()) {
            applySurfaceBounce(entity, mod.intensity(), 120);
            return;
        }

        if (aspect == GraftAspect.FREEZE) {
            try {
                entity.setFreezeTicks(Math.max(entity.getFreezeTicks(), (int) Math.round(120 * mod.durationMultiplier())));
            } catch (Throwable ignored) {

            }
            entity.setFireTicks(0);
        }

        if (aspect == GraftAspect.HEAL) {
            entity.setHealth(Math.min(entity.getMaxHealth(), entity.getHealth() + settings.healAmount() * mod.intensity()));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, Math.max(0, mod.amplifier()), true, true, true));
        }

        if (spec.primaryEffect() != null) {
            entity.addPotionEffect(new PotionEffect(spec.primaryEffect(), durationTicks, mod.amplifier(), true, true, true));
        }
        if (spec.secondaryEffect() != null) {
            entity.addPotionEffect(new PotionEffect(spec.secondaryEffect(), durationTicks, mod.amplifier(), true, true, true));
        }
    }

    private AmbientManifest establishAmbientManifest(Block targetBlock, GraftAspect aspect, PropertyModifier mod) {
        Block ambientBlock = targetBlock.getRelative(0, 1, 0);
        if (!ambientBlock.getType().isAir()) {
            return null;
        }

        if (AspectEffectConfig.emitsLight(aspect)) {
            ambientBlock.setType(Material.LIGHT, false);
            int lightLevel = Math.max(8, Math.min(14, 8 + mod.amplifier() + (int) Math.round((mod.radiusMultiplier() - 1.0D) * 4.0D)));
            try {
                BlockData data = Bukkit.createBlockData("minecraft:light[level=" + lightLevel + "]");
                ambientBlock.setBlockData(data, false);
            } catch (IllegalArgumentException ignored) {

            }
            return new AmbientManifest(ambientBlock.getLocation().toBlockLocation(), Material.LIGHT);
        }
        if (aspect == GraftAspect.IGNITE
            && !plugin.subjectResolver().resolveFluid(targetBlock.getType()).isPresent()) {
            ambientBlock.setType(Material.FIRE, false);
            return new AmbientManifest(ambientBlock.getLocation().toBlockLocation(), Material.FIRE);
        }
        return null;
    }

    private boolean applyImmediateBlockReaction(Player caster, Block targetBlock, GraftAspect aspect, PropertyModifier mod) {
        if (aspect != GraftAspect.IGNITE) {
            return false;
        }
        if (targetBlock.getType() != Material.TNT || targetBlock.getWorld() == null) {
            return false;
        }

        Location primedLocation = targetBlock.getLocation().add(0.5D, 0.0D, 0.5D);
        targetBlock.setType(Material.AIR, false);
        TNTPrimed primed = targetBlock.getWorld().spawn(primedLocation, TNTPrimed.class);
        primed.setFuseTicks(Math.max(20, (int) Math.round(primed.getFuseTicks() / Math.max(1.0D, mod.intensity()))));
        primed.setSource(caster);
        return true;
    }

    private String describeEntityOutcome(StateTransferPlan plan) {
        return "Effect: " + describeAspectBehavior(plan.aspect()) + " for " + formatSeconds(scaledEffectDuration(plan.modifier())) + ".";
    }

    private String describeProjectileOutcome(StateTransferPlan plan) {
        return switch (plan.mode()) {
            case PROJECTILE_TRAIT -> "Projectile trait applied immediately: " + describeAspectBehavior(plan.aspect()) + ".";
            case PROJECTILE_PAYLOAD -> "Projectile carries " + describeAspectBehavior(plan.aspect()) + " for " + formatSeconds(scaledEffectDuration(plan.modifier())) + ".";
            case PROJECTILE_BOUNCE -> "Projectile carries a bounce trait for " + formatSeconds(scaledEffectDuration(plan.modifier())) + ".";
            default -> "Projectile updated.";
        };
    }

    private String describeBlockOutcome(StateTransferPlan plan, String targetName, double contactRadius, int durationTicks, boolean ambientPlaced) {
        List<String> details = new ArrayList<>();
        if (ambientPlaced && AspectEffectConfig.emitsLight(plan.aspect())) {
            details.add("emits light");
        }
        if (ambientPlaced && AspectEffectConfig.getSpec(plan.aspect()).map(AspectEffectConfig.EffectSpec::causesFire).orElse(false)) {
            details.add("burns above the block");
        }
        details.add(describeAspectBehavior(plan.aspect()) + " on contact within " + formatDouble(contactRadius) + "m");
        return targetName + " now manifests " + plan.aspect().displayName() + " for " + formatSeconds(durationTicks) + ": " + String.join(", ", details) + ".";
    }

    private String describeFieldOutcome(StateTransferPlan plan, StateTransferSettings settings, String targetName) {
        double effectiveRadius = settings.fieldRadius() * plan.modifier().radiusMultiplier();
        int durationTicks = Math.max(1, (int) Math.round(settings.fieldDurationTicks() * plan.modifier().durationMultiplier()));
        return targetName + " now hosts a " + plan.aspect().displayName() + " field for " + formatSeconds(durationTicks) + " in a " + formatDouble(effectiveRadius) + "m radius: " + describeAspectBehavior(plan.aspect()) + ".";
    }

    private String describeItemOutcome(StateTransferPlan plan, ItemStack updatedItem) {
        if (!(updatedItem.getItemMeta() instanceof Damageable damageable) || updatedItem.getType().getMaxDurability() <= 0) {
            return "Item state updated.";
        }
        int remaining = Math.max(0, updatedItem.getType().getMaxDurability() - damageable.getDamage());
        return switch (plan.mode()) {
            case ITEM_REPAIR -> "Item integrity restored. Remaining durability: " + remaining + "/" + updatedItem.getType().getMaxDurability() + ".";
            case ITEM_DAMAGE -> "Item integrity scorched. Remaining durability: " + remaining + "/" + updatedItem.getType().getMaxDurability() + ".";
            default -> "Item state updated.";
        };
    }

    private String describeAspectBehavior(GraftAspect aspect) {
        AspectEffectConfig.EffectSpec spec = AspectEffectConfig.getSpec(aspect).orElse(null);
        List<String> parts = new ArrayList<>();
        if (spec != null && spec.emitsLight()) {
            parts.add("emits light");
        }
        if (spec != null && spec.causesFire()) {
            parts.add(aspect == GraftAspect.HEAT ? "deals heat damage" : "ignites targets");
        }
        if (spec != null && spec.causesBounce()) {
            parts.add("launches targets upward");
        }
        if (aspect == GraftAspect.HEAL) {
            parts.add("restores health");
        }
        if (spec != null && spec.primaryEffect() != null) {
            parts.add("applies " + effectName(spec.primaryEffect()) + (spec.secondaryEffect() != null ? " + " + effectName(spec.secondaryEffect()) : ""));
        }
        return parts.isEmpty() ? aspect.displayName().toLowerCase(Locale.ROOT) : String.join(", ", parts);
    }

    private int scaledEffectDuration(PropertyModifier mod) {
        return Math.max(1, (int) Math.round(plugin.settings().stateTransferSettings().effectDurationTicks() * mod.durationMultiplier()));
    }

    private String effectName(PotionEffectType effectType) {
        String raw = effectType.getKey().getKey().replace('_', ' ');
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    private String formatSeconds(int ticks) {
        return formatDouble(ticks / 20.0D) + "s";
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private boolean hasActiveBounceSurface(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Location feet = location.clone();
        for (ActiveBlockManifest active : activeBlockManifests.values()) {
            if (active.aspect() != GraftAspect.BOUNCE || active.targetLocation().getWorld() == null || !active.targetLocation().getWorld().equals(feet.getWorld())) {
                continue;
            }
            Location center = active.targetLocation().clone().add(0.5D, 0.5D, 0.5D);
            if (center.distanceSquared(feet) <= Math.pow(active.contactRadius() + 0.75D, 2)) {
                return true;
            }
        }
        for (ActiveField active : activeFields.values()) {
            if (active.aspect() != GraftAspect.BOUNCE || active.center().getWorld() == null || !active.center().getWorld().equals(feet.getWorld())) {
                continue;
            }
            if (active.center().distanceSquared(feet) <= Math.pow(active.radius() + 0.75D, 2)) {
                return true;
            }
        }
        return false;
    }

    private void registerActive(UUID ownerId, UUID trackingId, String aspectName, String sourceName, String targetName, int durationTicks, Runnable cleanupAction) {
        for (Runnable cleanup : plugin.activeGraftRegistry().register(
            trackingId,
            ownerId,
            GraftFamily.STATE,
            aspectName,
            sourceName,
            targetName,
            durationTicks,
            cleanupAction
        )) {
            cleanup.run();
        }
    }

    private void clearProjectilePayloadForProjectile(UUID projectileId) {
        ActiveProjectilePayload active = projectilePayloads.get(projectileId);
        if (active == null) {
            return;
        }
        clearProjectilePayload(active.trackingId());
    }

    private void clearProjectilePayload(UUID trackingId) {
        UUID projectileId = null;
        ActiveProjectilePayload active = null;
        for (Map.Entry<UUID, ActiveProjectilePayload> entry : projectilePayloads.entrySet()) {
            if (entry.getValue().trackingId().equals(trackingId)) {
                projectileId = entry.getKey();
                active = entry.getValue();
                break;
            }
        }
        if (projectileId != null) {
            projectilePayloads.remove(projectileId);
        }
        plugin.activeGraftRegistry().unregister(trackingId);
        if (active != null) {
            cancelTrackedTask(active.cleanupTask());
        }
    }

    private void clearProjectileBounceForProjectile(UUID projectileId) {
        ActiveProjectileBounce active = projectileBounces.get(projectileId);
        if (active == null) {
            return;
        }
        clearProjectileBounce(active.trackingId());
    }

    private void clearProjectileBounce(UUID trackingId) {
        UUID projectileId = null;
        ActiveProjectileBounce active = null;
        for (Map.Entry<UUID, ActiveProjectileBounce> entry : projectileBounces.entrySet()) {
            if (entry.getValue().trackingId().equals(trackingId)) {
                projectileId = entry.getKey();
                active = entry.getValue();
                break;
            }
        }
        if (projectileId != null) {
            projectileBounces.remove(projectileId);
        }
        plugin.activeGraftRegistry().unregister(trackingId);
        if (active == null) {
            return;
        }
        cancelTrackedTask(active.cleanupTask());
        if (projectileId == null) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(projectileId);
        if (entity instanceof Projectile projectile && projectile.isValid()) {
            projectile.setGlowing(active.wasGlowing());
        }
    }

    private void cancelTrackedTask(BukkitTask task) {
        if (task == null) {
            return;
        }
        task.cancel();
        activeTasks.remove(task);
    }

    private void protectBounceLanding(UUID entityId, int durationTicks) {
        bouncingEntities.add(entityId);
        BukkitTask previous = bounceProtectionTasks.remove(entityId);
        cancelTrackedTask(previous);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            bouncingEntities.remove(entityId);
            bounceProtectionTasks.remove(entityId);
        }, durationTicks);
        activeTasks.add(task);
        bounceProtectionTasks.put(entityId, task);
    }

    private void applySurfaceBounce(LivingEntity entity, double intensity, int protectionTicks) {
        if (entity.getVelocity().getY() >= -0.08D && entity.getFallDistance() <= 0.5F) {
            return;
        }
        Vector velocity = entity.getVelocity();
        double bounceForce = Math.max(0.9D, Math.abs(velocity.getY()) * 0.9D * intensity);
        entity.setFallDistance(0.0F);
        entity.setVelocity(new Vector(velocity.getX(), bounceForce, velocity.getZ()));
        protectBounceLanding(entity.getUniqueId(), protectionTicks);
    }

    private void clearBounceEffect(UUID trackingId) {
        ActiveBounceEffect active = activeBounceEffects.remove(trackingId);
        bouncingEntities.remove(trackingId);
        plugin.activeGraftRegistry().unregister(trackingId);
        if (active != null) {
            active.task().cancel();
            activeTasks.remove(active.task());
        }
    }

    private void clearBlockManifest(UUID trackingId) {
        ActiveBlockManifest active = activeBlockManifests.remove(trackingId);
        plugin.activeGraftRegistry().unregister(trackingId);
        if (active == null) {
            return;
        }
        active.task().cancel();
        activeTasks.remove(active.task());
        if (active.ambientLocation() != null && active.ambientMaterial() != null && active.ambientLocation().getWorld() != null) {
            Block ambientBlock = active.ambientLocation().getBlock();
            if (ambientBlock.getType() == active.ambientMaterial()) {
                ambientBlock.setType(Material.AIR, false);
            }
        }
    }

    private void clearField(UUID trackingId) {
        ActiveField active = activeFields.remove(trackingId);
        plugin.activeGraftRegistry().unregister(trackingId);
        if (active != null) {
            active.task().cancel();
            activeTasks.remove(active.task());
        }
    }

    private record ActiveProjectilePayload(UUID trackingId, GraftAspect aspect, PropertyModifier modifier, BukkitTask cleanupTask) {
    }

    private record ActiveProjectileBounce(UUID trackingId, int remainingBounces, boolean wasGlowing, BukkitTask cleanupTask) {

        private ActiveProjectileBounce withRemainingBounces(int updatedRemainingBounces) {
            return new ActiveProjectileBounce(trackingId, updatedRemainingBounces, wasGlowing, cleanupTask);
        }
    }

    private record ActiveBounceEffect(BukkitTask task) {
    }

    private record ActiveBlockManifest(GraftAspect aspect, Location targetLocation, Location ambientLocation, Material ambientMaterial, double contactRadius, BukkitTask task) {
    }

    private record ActiveField(GraftAspect aspect, Location center, double radius, BukkitTask task) {
    }

    private record AmbientManifest(Location location, Material material) {
    }

}
