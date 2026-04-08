package com.graftingplugin.state;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.config.StateTransferSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StateTransferService implements Listener {

    private final GraftingPlugin plugin;
    private final StateTransferPlanner planner;
    private final Map<UUID, ProjectilePayload> projectilePayloads = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> projectileBounces = new ConcurrentHashMap<>();
    private final Set<UUID> bouncingEntities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ActiveBounceEffect> activeBounceEffects = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveField> activeFields = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    public StateTransferService(GraftingPlugin plugin, StateTransferPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
    }

    public void shutdown() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
        projectilePayloads.clear();
        projectileBounces.clear();
        bouncingEntities.clear();
        activeBounceEffects.clear();
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

        boolean success = switch (plan.mode()) {
            case ENTITY_EFFECT -> applyEntityEffect(caster, aspect, targetEntity);
            case ENTITY_FIRE -> applyEntityFire(caster, aspect, targetEntity);
            case ENTITY_BOUNCE -> applyEntityBounce(caster, source, aspect, target, targetEntity);
            default -> false;
        };
        if (success) {
            plugin.messages().send(caster, "state-cast-entity", Map.of(
                "aspect", aspect.displayName(),
                "target", target.displayName()
            ));
            plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        }
        return success;
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

        boolean success = switch (plan.mode()) {
            case PROJECTILE_TRAIT -> applyProjectileTrait(aspect, projectile);
            case PROJECTILE_PAYLOAD -> applyProjectilePayload(aspect, projectile);
            case PROJECTILE_BOUNCE -> applyProjectileBounce(projectile);
            default -> false;
        };
        if (success) {
            plugin.messages().send(caster, "state-cast-projectile", Map.of(
                "aspect", aspect.displayName(),
                "target", target.displayName()
            ));
            plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        }
        return success;
    }

    public boolean applyToBlock(Player caster, GraftSubject source, GraftAspect aspect, Block block) {
        GraftSubject target = plugin.subjectResolver().resolveBlock(block).orElse(null);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        StateTransferPlan plan = validateAndPlan(caster, source, aspect, target);
        if (plan == null || plan.mode() != StateTransferMode.FIELD) {
            return false;
        }

        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        startField(caster, source, aspect, center, target.displayName());
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
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

        startField(caster, source, aspect, center, target.displayName());
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!bouncingEntities.contains(event.getEntity().getUniqueId())) {
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
        ProjectilePayload payload = projectilePayloads.remove(projectile.getUniqueId());
        if (payload == null || !(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        applyProjectilePayload(livingEntity, payload);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!projectilePayloads.containsKey(projectile.getUniqueId()) && !projectileBounces.containsKey(projectile.getUniqueId())) {
            return;
        }

        if (event.getHitEntity() == null) {
            projectilePayloads.remove(projectile.getUniqueId());
        }

        Integer remainingBounces = projectileBounces.get(projectile.getUniqueId());
        if (remainingBounces == null) {
            return;
        }
        if (remainingBounces <= 0) {
            projectileBounces.remove(projectile.getUniqueId());
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
            Location spawnLocation = projectile.getLocation().add(reflected.clone().normalize().multiply(0.2D));
            AbstractArrow bouncedArrow = projectile.getWorld().spawnArrow(spawnLocation, reflected, (float) reflected.length(), 0.0F);
            bouncedArrow.setShooter(arrow.getShooter());
            bouncedArrow.setCritical(arrow.isCritical());
            bouncedArrow.setPickupStatus(arrow.getPickupStatus());
            bouncedArrow.setGlowing(arrow.isGlowing());
            ProjectilePayload payload = projectilePayloads.remove(projectile.getUniqueId());
            if (payload != null) {
                projectilePayloads.put(bouncedArrow.getUniqueId(), payload);
            }
            projectileBounces.put(bouncedArrow.getUniqueId(), remainingBounces - 1);
            projectile.remove();
        } else if (projectile.isValid()) {
            projectile.teleport(projectile.getLocation().add(reflected.clone().normalize().multiply(0.3D)));
            projectile.setVelocity(reflected);
            projectileBounces.put(projectile.getUniqueId(), remainingBounces - 1);
        } else {
            projectileBounces.remove(projectile.getUniqueId());
        }
    }

    private StateTransferPlan validateAndPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateTarget(source, aspect, target);
        if (!compatibility.success()) {
            plugin.messages().send(caster, "target-incompatible", Map.of(
                "target", target.displayName(),
                "aspect", aspect.displayName()
            ));
            return null;
        }

        StateTransferPlan plan = planner.plan(aspect, target).orElse(null);
        if (plan == null) {
            plugin.messages().send(caster, "state-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "target", target.displayName()
            ));
            return null;
        }
        return plan;
    }

    private boolean applyEntityEffect(Player caster, GraftAspect aspect, Entity targetEntity) {
        if (!(targetEntity instanceof LivingEntity livingEntity)) {
            return false;
        }

        StateTransferSettings settings = plugin.settings().stateTransferSettings();
        int duration = settings.effectDurationTicks();
        switch (aspect) {
            case LIGHT, GLOW -> {
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, true, true, true));
                return true;
            }
            case SPEED -> {
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, true, true, true));
                return true;
            }
            case SLOW, STICKY, FREEZE -> {
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, true));
                return true;
            }
            case POISON -> {
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0, true, true, true));
                return true;
            }
            case HEAL -> {
                livingEntity.setHealth(Math.min(livingEntity.getMaxHealth(), livingEntity.getHealth() + settings.healAmount()));
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, true, true, true));
                return true;
            }
            case CONCEAL -> {
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, true, true, true));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean applyEntityFire(Player caster, GraftAspect aspect, Entity targetEntity) {
        targetEntity.setFireTicks(Math.max(targetEntity.getFireTicks(), plugin.settings().stateTransferSettings().igniteFireTicks()));
        if (aspect == GraftAspect.HEAT && targetEntity instanceof LivingEntity livingEntity) {
            livingEntity.damage(plugin.settings().stateTransferSettings().heatDamage(), caster);
        }
        return true;
    }

    private boolean applyEntityBounce(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target, Entity targetEntity) {
        targetEntity.setFallDistance(0.0F);
        Vector velocity = targetEntity.getVelocity();
        targetEntity.setVelocity(new Vector(velocity.getX(), Math.max(1.0D, velocity.getY() + 1.0D), velocity.getZ()));
        UUID trackingId = targetEntity.getUniqueId();
        clearBounceEffect(trackingId);
        bouncingEntities.add(trackingId);
        int durationTicks = plugin.settings().stateTransferSettings().effectDurationTicks();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearBounceEffect(trackingId), durationTicks);
        activeTasks.add(task);
        activeBounceEffects.put(trackingId, new ActiveBounceEffect(task));
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), target.displayName(), durationTicks, () -> clearBounceEffect(trackingId));
        return true;
    }

    private boolean applyProjectileTrait(GraftAspect aspect, Projectile projectile) {
        return switch (aspect) {
            case SPEED -> {
                projectile.setVelocity(projectile.getVelocity().multiply(1.5D));
                yield true;
            }
            case SLOW -> {
                projectile.setVelocity(projectile.getVelocity().multiply(0.5D));
                yield true;
            }
            case LIGHT, GLOW -> {
                projectile.setGlowing(true);
                yield true;
            }
            default -> false;
        };
    }

    private boolean applyProjectilePayload(GraftAspect aspect, Projectile projectile) {
        projectilePayloads.put(projectile.getUniqueId(), new ProjectilePayload(aspect));
        if (aspect == GraftAspect.IGNITE || aspect == GraftAspect.HEAT) {
            projectile.setFireTicks(plugin.settings().stateTransferSettings().igniteFireTicks());
        }
        return true;
    }

    private boolean applyProjectileBounce(Projectile projectile) {
        projectileBounces.put(projectile.getUniqueId(), plugin.settings().stateTransferSettings().bounceCount());
        projectile.setGlowing(true);
        return true;
    }

    private void applyProjectilePayload(LivingEntity target, ProjectilePayload payload) {
        switch (payload.aspect()) {
            case POISON -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, plugin.settings().stateTransferSettings().effectDurationTicks(), 0, true, true, true));
            case IGNITE -> target.setFireTicks(Math.max(target.getFireTicks(), plugin.settings().stateTransferSettings().igniteFireTicks()));
            case HEAT -> {
                target.setFireTicks(Math.max(target.getFireTicks(), plugin.settings().stateTransferSettings().igniteFireTicks()));
                target.damage(plugin.settings().stateTransferSettings().heatDamage());
            }
            default -> {
            }
        }
    }

    private void startField(Player caster, GraftSubject source, GraftAspect aspect, Location center, String targetName) {
        StateTransferSettings settings = plugin.settings().stateTransferSettings();
        UUID trackingId = UUID.randomUUID();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (center.getWorld() == null || elapsed[0] >= settings.fieldDurationTicks()) {
                clearField(trackingId);
                return;
            }
            pulseField(caster, aspect, center, settings);
            elapsed[0] += settings.pulseIntervalTicks();
        }, 0L, settings.pulseIntervalTicks());
        activeTasks.add(taskHolder[0]);
        activeFields.put(trackingId, new ActiveField(taskHolder[0]));
        registerActive(caster.getUniqueId(), trackingId, aspect.displayName(), source.displayName(), targetName, settings.fieldDurationTicks(), () -> clearField(trackingId));
        plugin.messages().send(caster, "state-cast-field", Map.of(
            "aspect", aspect.displayName(),
            "target", targetName
        ));
    }

    private void pulseField(Player caster, GraftAspect aspect, Location center, StateTransferSettings settings) {
        if (center.getWorld() == null) {
            return;
        }

        center.getWorld().spawnParticle(fieldParticle(aspect), center, 12, settings.fieldRadius() * 0.25D, 0.5D, settings.fieldRadius() * 0.25D, 0.02D);
        for (LivingEntity entity : center.getWorld().getNearbyLivingEntities(center, settings.fieldRadius())) {
            switch (aspect) {
                case LIGHT, GLOW -> entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, settings.pulseIntervalTicks() + 10, 0, true, true, true));
                case HEAT -> entity.damage(settings.heatDamage(), caster);
                case IGNITE -> entity.setFireTicks(Math.max(entity.getFireTicks(), settings.igniteFireTicks()));
                case BOUNCE -> {
                    if (entity.isOnGround()) {
                        Vector velocity = entity.getVelocity();
                        entity.setVelocity(new Vector(velocity.getX(), Math.max(0.9D, velocity.getY() + 0.9D), velocity.getZ()));
                    }
                }
                case SPEED -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, settings.pulseIntervalTicks() + 10, 0, true, true, true));
                case SLOW, STICKY, FREEZE -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, settings.pulseIntervalTicks() + 10, 0, true, true, true));
                case POISON -> entity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, settings.pulseIntervalTicks() + 20, 0, true, true, true));
                case HEAL -> entity.setHealth(Math.min(entity.getMaxHealth(), entity.getHealth() + settings.healAmount()));
                case CONCEAL -> entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, settings.pulseIntervalTicks() + 10, 0, true, true, true));
                default -> {
                }
            }
        }
    }

    private Particle fieldParticle(GraftAspect aspect) {
        return switch (aspect) {
            case LIGHT, GLOW -> Particle.END_ROD;
            case HEAT, IGNITE -> Particle.FLAME;
            case BOUNCE -> Particle.CLOUD;
            case SPEED -> Particle.CLOUD;
            case SLOW, STICKY, FREEZE -> Particle.SNOWFLAKE;
            case POISON -> Particle.ENTITY_EFFECT;
            case HEAL -> Particle.HEART;
            case CONCEAL -> Particle.SMOKE;
            default -> Particle.ENCHANT;
        };
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

    private void clearBounceEffect(UUID trackingId) {
        ActiveBounceEffect active = activeBounceEffects.remove(trackingId);
        bouncingEntities.remove(trackingId);
        plugin.activeGraftRegistry().unregister(trackingId);
        if (active != null) {
            active.task().cancel();
            activeTasks.remove(active.task());
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

    private record ProjectilePayload(GraftAspect aspect) {
    }

    private record ActiveBounceEffect(BukkitTask task) {
    }

    private record ActiveField(BukkitTask task) {
    }
}
