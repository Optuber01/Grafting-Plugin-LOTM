package com.graftingplugin.conceptgraft;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger.ActivationGate;
import com.graftingplugin.gui.ConceptCatalogGui;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConceptGraftService implements Listener {

    private final GraftingPlugin plugin;
    private final ConceptualRuntimeLedger runtimeLedger = new ConceptualRuntimeLedger();
    private final ConceptGraftPresentation presentation = new ConceptGraftPresentation();
    private final Map<UUID, ActiveConceptZone> activeZones = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveConceptLoop> activeLoops = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveThresholdRelay> activeThresholds = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> entityTeleportCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> feedbackCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerZonePresence = new ConcurrentHashMap<>();
    private final ConceptPreviewFeedbackGate previewFeedbackGate = new ConceptPreviewFeedbackGate();
    private BukkitTask previewTask;
    private BukkitTask presenceTask;

    public ConceptGraftService(GraftingPlugin plugin) {
        this.plugin = plugin;
        restartBackgroundTasks();
    }

    public void shutdown() {
        for (UUID zoneId : Set.copyOf(activeZones.keySet())) {
            clearZone(zoneId);
        }
        for (UUID loopId : Set.copyOf(activeLoops.keySet())) {
            clearLoop(loopId);
        }
        for (UUID relayId : Set.copyOf(activeThresholds.keySet())) {
            clearThresholdRelay(relayId);
        }
        stopBackgroundTasks();
        for (BukkitTask task : Set.copyOf(activeTasks)) {
            task.cancel();
        }
        activeTasks.clear();
        entityTeleportCooldowns.clear();
        feedbackCooldowns.clear();
        playerZonePresence.clear();
        previewFeedbackGate.clearAll();
        runtimeLedger.clearAll();
    }

    public void restartBackgroundTasks() {
        stopBackgroundTasks();
        previewFeedbackGate.clearAll();
        playerZonePresence.clear();
        previewTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulsePlacementPreviews, 1L, 8L);
        activeTasks.add(previewTask);
        presenceTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulseZonePresenceFeedback, 5L, 10L);
        activeTasks.add(presenceTask);
    }

    private void stopBackgroundTasks() {
        cancelTrackedTask(previewTask);
        cancelTrackedTask(presenceTask);
        previewTask = null;
        presenceTask = null;
    }

    public boolean activateZone(Player caster, ConceptGraftType type, Location center) {
        if (type.placementStyle() != ConceptPlacementStyle.ZONE) {
            return false;
        }
        if (!canActivate(caster)) {
            return false;
        }

        long nowMillis = System.currentTimeMillis();
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        UUID zoneId = UUID.randomUUID();
        Location zoneCenter = center.clone();
        int durationTicks = settings.zoneDurationTicks();
        double radius = settings.zoneRadius();

        BukkitTask pulseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ActiveConceptZone zone = activeZones.get(zoneId);
            if (zone != null) {
                pulseZone(zone);
            }
        }, 0L, settings.pulseIntervalTicks());
        activeTasks.add(pulseTask);

        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearZone(zoneId), durationTicks);
        activeTasks.add(cleanupTask);

        BukkitTask warningTask = scheduleExpiryWarning(caster.getUniqueId(), type.displayName(), durationTicks);

        ActiveConceptZone zone = new ActiveConceptZone(zoneId, caster.getUniqueId(), type, zoneCenter, radius, pulseTask, cleanupTask, warningTask);
        activeZones.put(zoneId, zone);
        runtimeLedger.recordActivation(zoneId, caster.getUniqueId(), presentation.runtimeKindFor(type), type, describeLocation(zoneCenter), durationTicks, settings.cooldownTicks(), nowMillis);
        registerConceptualActive(zoneId, caster.getUniqueId(), type, presentation.conceptualSourceName(type), describeLocation(zoneCenter), durationTicks, () -> clearZone(zoneId));

        spawnActivationEffects(zoneCenter, type);
        plugin.messages().send(caster, "conceptual-zone-activated", Map.of(
            "graft", type.displayName(),
            "seconds", Integer.toString(durationTicks / 20)
        ));
        sendConceptualSummary(caster, type, durationTicks / 20);
        sendActionBar(caster, "§5" + type.displayName() + " §8| §dlaw imposed over " + formatRadius(radius) + "m");
        return true;
    }

    public boolean activateAnchoredGraft(Player caster, ConceptGraftType type, Location anchorA, Location anchorB) {
        return switch (type) {
            case BEGINNING_TO_END -> activateLoop(caster, type, anchorA, anchorB);
            case THRESHOLD_TO_ELSEWHERE -> activateThresholdRelay(caster, type, anchorA, anchorB);
            default -> false;
        };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerZonePresence.remove(playerId);
        previewFeedbackGate.clear(playerId);
        entityTeleportCooldowns.remove(playerId);
        feedbackCooldowns.keySet().removeIf(key -> key.startsWith(playerId + ":"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        for (ActiveConceptLoop loop : activeLoops.values()) {
            if (isOnTeleportCooldown(event.getPlayer().getUniqueId())) {
                continue;
            }
            if (isNear(to, loop.anchorA(), loop.activationRadius())) {
                teleportThroughLoop(event.getPlayer(), loop.anchorB(), loop);
                break;
            }
            if (isNear(to, loop.anchorB(), loop.activationRadius())) {
                teleportThroughLoop(event.getPlayer(), loop.anchorA(), loop);
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        Location loc = event.getEntity().getLocation();
        for (ActiveConceptZone zone : activeZones.values()) {
            if (zone.type() == ConceptGraftType.SKY_TO_GROUND && isInZone(loc, zone)) {
                event.setCancelled(true);
                event.getEntity().setFallDistance(0.0F);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        if (placed.getType() != Material.WATER && placed.getType() != Material.ICE
            && placed.getType() != Material.BLUE_ICE && placed.getType() != Material.PACKED_ICE) {
            return;
        }
        Location loc = placed.getLocation();
        for (ActiveConceptZone zone : activeZones.values()) {
            if (zone.type() == ConceptGraftType.NETHER_ZONE && isInZone(loc, zone)) {
                event.setCancelled(true);
                placed.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
                placed.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
                plugin.messages().send(event.getPlayer(), "conceptual-nether-water-rejected");
                sendActionBar(event.getPlayer(), "§cNether law rejects water here");
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        Block placed = event.getBlock().getRelative(event.getBlockFace());
        Location loc = placed.getLocation();
        for (ActiveConceptZone zone : activeZones.values()) {
            if (zone.type() == ConceptGraftType.NETHER_ZONE && isInZone(loc, zone)) {
                event.setCancelled(true);
                placed.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
                placed.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
                plugin.messages().send(event.getPlayer(), "conceptual-nether-water-rejected");
                sendActionBar(event.getPlayer(), "§cNether law rejects water here");
                return;
            }
            if (zone.type() == ConceptGraftType.OVERWORLD_ZONE && isInZone(loc, zone)) {
                event.setCancelled(true);
                allowOverworldWater(event.getPlayer(), event.getHand(), placed);
                plugin.messages().send(event.getPlayer(), "conceptual-overworld-water-allowed");
                sendActionBar(event.getPlayer(), "§aOverworld law allows water here");
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) {
            return;
        }
        Location loc = event.getBlock().getLocation();
        for (ActiveConceptZone zone : activeZones.values()) {
            if (zone.type() == ConceptGraftType.SKY_TO_GROUND && isInZone(loc, zone)) {
                event.setCancelled(true);
                event.getBlock().getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.02);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        Location sourceLocation = mob.getLocation();
        Location targetLocation = player.getLocation();
        for (ActiveConceptZone zone : activeZones.values()) {
            if (zone.type() != ConceptGraftType.CONCEALMENT_TO_RECOGNITION) {
                continue;
            }
            if (!isInZone(sourceLocation, zone) || !isInZone(targetLocation, zone)) {
                continue;
            }
            event.setCancelled(true);
            mob.setTarget(null);
            sendThrottledActionBar(player, "recognition", presentation.triggerActionBarFor(ConceptGraftType.CONCEALMENT_TO_RECOGNITION), 1200L);
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Location openedLocation = event.getInventory().getLocation();
        if (openedLocation == null || openedLocation.getWorld() == null || !(event.getPlayer() instanceof Player player)) {
            return;
        }

        for (ActiveThresholdRelay relay : activeThresholds.values()) {
            if (!sameBlock(openedLocation, relay.sourceAnchor())) {
                continue;
            }
            Inventory targetInventory = resolveContainerInventory(relay.destinationAnchor());
            if (targetInventory == null) {
                clearThresholdRelay(relay.id());
                return;
            }
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.openInventory(targetInventory));
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.1f);
            sendThrottledActionBar(player, "threshold", presentation.triggerActionBarFor(ConceptGraftType.THRESHOLD_TO_ELSEWHERE), 800L);
            return;
        }
    }

    private boolean activateLoop(Player caster, ConceptGraftType type, Location anchorA, Location anchorB) {
        if (!canActivate(caster)) {
            return false;
        }

        long nowMillis = System.currentTimeMillis();
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        UUID loopId = UUID.randomUUID();
        int durationTicks = settings.loopDurationTicks();

        BukkitTask pulseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ActiveConceptLoop loop = activeLoops.get(loopId);
            if (loop != null) {
                pulseLoop(loop);
            }
        }, 1L, 10L);
        activeTasks.add(pulseTask);

        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearLoop(loopId), durationTicks);
        activeTasks.add(cleanupTask);

        BukkitTask warningTask = scheduleExpiryWarning(caster.getUniqueId(), type.displayName(), durationTicks);

        ActiveConceptLoop loop = new ActiveConceptLoop(loopId, caster.getUniqueId(), type, anchorA.clone(), anchorB.clone(), settings.loopActivationRadius(), pulseTask, cleanupTask, warningTask);
        activeLoops.put(loopId, loop);
        runtimeLedger.recordActivation(loopId, caster.getUniqueId(), presentation.runtimeKindFor(type), type, describeAnchoredTarget(anchorA, anchorB), durationTicks, settings.cooldownTicks(), nowMillis);
        registerConceptualActive(loopId, caster.getUniqueId(), type, presentation.conceptualSourceName(type), describeAnchoredTarget(anchorA, anchorB), durationTicks, () -> clearLoop(loopId));

        spawnActivationEffects(anchorA, type);
        spawnActivationEffects(anchorB, type);
        plugin.messages().send(caster, "conceptual-loop-activated", Map.of(
            "seconds", Integer.toString(durationTicks / 20)
        ));
        sendConceptualSummary(caster, type, durationTicks / 20);
        sendActionBar(caster, "§5Beginning and end identified as one path");
        return true;
    }

    private boolean activateThresholdRelay(Player caster, ConceptGraftType type, Location sourceAnchor, Location destinationAnchor) {
        Block sourceBlock = sourceAnchor.getBlock();
        Block destinationBlock = destinationAnchor.getBlock();
        if (sourceBlock.getType() == Material.ENDER_CHEST || destinationBlock.getType() == Material.ENDER_CHEST) {
            caster.sendMessage("§cThreshold → Elsewhere does not support ender chests yet.");
            return false;
        }
        if (!(sourceBlock.getState() instanceof Container sourceContainer) || !(destinationBlock.getState() instanceof Container destinationContainer)) {
            caster.sendMessage("§cThreshold → Elsewhere requires two container anchors.");
            return false;
        }
        if (sameBlock(sourceAnchor, destinationAnchor)) {
            caster.sendMessage("§cChoose two different container anchors.");
            return false;
        }
        if (sourceContainer.getInventory().equals(destinationContainer.getInventory())) {
            caster.sendMessage("§cChoose two different inventories, not two faces of the same storage.");
            return false;
        }
        if (!canActivate(caster)) {
            return false;
        }

        long nowMillis = System.currentTimeMillis();
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        UUID relayId = UUID.randomUUID();
        int durationTicks = settings.loopDurationTicks();

        BukkitTask pulseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ActiveThresholdRelay relay = activeThresholds.get(relayId);
            if (relay != null) {
                pulseThresholdRelay(relay);
            }
        }, 1L, 10L);
        activeTasks.add(pulseTask);

        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearThresholdRelay(relayId), durationTicks);
        activeTasks.add(cleanupTask);

        BukkitTask warningTask = scheduleExpiryWarning(caster.getUniqueId(), type.displayName(), durationTicks);

        ActiveThresholdRelay relay = new ActiveThresholdRelay(relayId, caster.getUniqueId(), type,
            sourceBlock.getLocation().toBlockLocation(), destinationBlock.getLocation().toBlockLocation(), pulseTask, cleanupTask, warningTask);
        activeThresholds.put(relayId, relay);
        runtimeLedger.recordActivation(relayId, caster.getUniqueId(), presentation.runtimeKindFor(type), type, describeAnchoredTarget(sourceAnchor, destinationAnchor), durationTicks, settings.cooldownTicks(), nowMillis);
        registerConceptualActive(relayId, caster.getUniqueId(), type, presentation.conceptualSourceName(type), describeAnchoredTarget(sourceAnchor, destinationAnchor), durationTicks, () -> clearThresholdRelay(relayId));

        spawnActivationEffects(sourceBlock.getLocation().add(0.5D, 0.5D, 0.5D), type);
        spawnActivationEffects(destinationBlock.getLocation().add(0.5D, 0.5D, 0.5D), type);
        plugin.messages().send(caster, "conceptual-threshold-activated", Map.of(
            "seconds", Integer.toString(durationTicks / 20)
        ));
        sendConceptualSummary(caster, type, durationTicks / 20);
        caster.sendMessage("§8Items you place during the rewrite remain in the destination inventory when it expires.");
        sendActionBar(caster, "§5This threshold now opens elsewhere");
        return true;
    }

    private boolean canActivate(Player caster) {
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        ActivationGate gate = runtimeLedger.checkActivation(caster.getUniqueId(), System.currentTimeMillis(), settings);
        if (gate.allowed()) {
            return true;
        }
        if (gate.cooldownBlocked()) {
            plugin.messages().send(caster, "conceptual-cooldown", Map.of("seconds", Long.toString(gate.remainingSeconds())));
            return false;
        }
        plugin.messages().send(caster, "conceptual-max-active");
        return false;
    }

    private void pulseZone(ActiveConceptZone zone) {
        if (zone.center().getWorld() == null) {
            clearZone(zone.id());
            return;
        }
        renderZoneBoundary(zone.center().getWorld(), zone.center(), zone.radius(), previewParticleFor(zone.type()), 10);
        switch (zone.type()) {
            case SUN_TO_GROUND -> pulseSunZone(zone);
            case SKY_TO_GROUND -> pulseSkyZone(zone);
            case NETHER_ZONE -> pulseNetherZone(zone);
            case END_ZONE -> pulseEndZone(zone);
            case OVERWORLD_ZONE -> pulseOverworldZone(zone);
            case CONCEALMENT_TO_RECOGNITION -> pulseConcealmentZone(zone);
            default -> {
            }
        }
    }

    private void pulseSunZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double radius = zone.radius();

        world.spawnParticle(Particle.END_ROD, zone.center().clone().add(0, 3, 0), 8, radius * 0.3, 1.5, radius * 0.3, 0.01);
        world.spawnParticle(Particle.FLAME, zone.center(), 4, radius * 0.2, 0.5, radius * 0.2, 0.005);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), radius)) {
            if (entity instanceof Player player) {
                player.setPlayerTime(6000L, false);
            }
            if (isUndead(entity)) {
                entity.setFireTicks(Math.max(entity.getFireTicks(), 60));
            }
        }

        int limit = (int) radius;
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dz = -limit; dz <= limit; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                Location probe = zone.center().clone().add(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = probe.clone().add(0, dy, 0).getBlock();
                    Material material = block.getType();
                    if (material == Material.ICE) {
                        block.setType(Material.WATER);
                    } else if (material == Material.SNOW || material == Material.SNOW_BLOCK) {
                        block.setType(Material.AIR);
                    } else if (block.getBlockData() instanceof Ageable ageable) {
                        if (ageable.getAge() < ageable.getMaximumAge()
                            && world.getBlockAt(block.getLocation().add(0, -1, 0)).getType() == Material.FARMLAND) {
                            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
                            block.setBlockData(ageable);
                        }
                    }
                }
            }
        }
    }

    private void pulseSkyZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double radius = zone.radius();

        world.spawnParticle(Particle.CLOUD, zone.center().clone().add(0, 2, 0), 6, radius * 0.3, 1.0, radius * 0.3, 0.01);
        world.spawnParticle(Particle.END_ROD, zone.center().clone().add(0, 1, 0), 4, radius * 0.2, 2.0, radius * 0.2, 0.02);

        for (FallingBlock fallingBlock : world.getNearbyEntitiesByType(FallingBlock.class, zone.center(), radius)) {
            fallingBlock.remove();
            world.spawnParticle(Particle.CLOUD, fallingBlock.getLocation(), 8, 0.2, 0.2, 0.2, 0.02);
        }

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), radius)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0, true, false, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false, true));
            entity.setFallDistance(0.0F);
            if (entity instanceof Player player) {
                player.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
            }
        }
    }

    private void pulseNetherZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double radius = zone.radius();

        world.spawnParticle(Particle.SOUL_FIRE_FLAME, zone.center(), 6, radius * 0.3, 0.5, radius * 0.3, 0.01);
        world.spawnParticle(Particle.LAVA, zone.center(), 3, radius * 0.2, 0.3, radius * 0.2, 0.0);
        world.spawnParticle(Particle.ASH, zone.center(), 10, radius * 0.4, 1.0, radius * 0.4, 0.0);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), radius)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, true));
        }

        int limit = (int) radius;
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dz = -limit; dz <= limit; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                Location probe = zone.center().clone().add(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = probe.clone().add(0, dy, 0).getBlock();
                    Material material = block.getType();
                    if (material == Material.WATER || material == Material.ICE || material == Material.BLUE_ICE || material == Material.PACKED_ICE) {
                        block.setType(Material.AIR);
                        world.spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
        }
    }

    private void pulseEndZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double radius = zone.radius();

        world.spawnParticle(Particle.REVERSE_PORTAL, zone.center(), 6, radius * 0.3, 0.5, radius * 0.3, 0.005);
        world.spawnParticle(Particle.PORTAL, zone.center(), 12, radius * 0.3, 1.0, radius * 0.3, 0.3);
        world.spawnParticle(Particle.REVERSE_PORTAL, zone.center().clone().add(0, 1, 0), 6, radius * 0.2, 0.5, radius * 0.2, 0.1);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), radius)) {
            if (Math.random() > 0.08D) {
                continue;
            }
            double offsetX = (Math.random() - 0.5D) * radius * 0.6D;
            double offsetZ = (Math.random() - 0.5D) * radius * 0.6D;
            Location destination = entity.getLocation().clone().add(offsetX, 0, offsetZ);
            Block destinationBlock = destination.getBlock();
            Block below = destinationBlock.getRelative(BlockFace.DOWN);
            if (destinationBlock.getType().isAir() && !below.getType().isAir()) {
                world.spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation(), 12, 0.3, 0.5, 0.3, 0.1);
                entity.teleport(destination);
                world.spawnParticle(Particle.REVERSE_PORTAL, destination, 12, 0.3, 0.5, 0.3, 0.1);
                world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.5f);
                if (entity instanceof Player player) {
                    sendThrottledActionBar(player, "end-shift", presentation.triggerActionBarFor(ConceptGraftType.END_ZONE), 1000L);
                }
            }
        }
    }

    private void allowOverworldWater(Player player, EquipmentSlot hand, Block placed) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || placed.getWorld() == null) {
                return;
            }
            if (!placed.getType().isAir() && placed.getType() != Material.FIRE) {
                return;
            }
            placed.setType(Material.WATER, false);
            placed.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, placed.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0.01);
            placed.getWorld().playSound(placed.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.7f, 1.2f);
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack emptyBucket = new ItemStack(Material.BUCKET);
                if (hand == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(emptyBucket);
                } else {
                    player.getInventory().setItemInMainHand(emptyBucket);
                }
                player.updateInventory();
            }
        });
    }

    private void pulseOverworldZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double radius = zone.radius();

        world.spawnParticle(Particle.HAPPY_VILLAGER, zone.center(), 5, radius * 0.3, 0.5, radius * 0.3, 0.01);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), radius)) {
            entity.removePotionEffect(PotionEffectType.LEVITATION);
            entity.removePotionEffect(PotionEffectType.SLOW_FALLING);
            entity.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            if (entity.getFireTicks() > 0) {
                entity.setFireTicks(0);
            }
            if (entity instanceof Player player) {
                player.resetPlayerTime();
                player.resetPlayerWeather();
            }
        }
    }

    private void pulseConcealmentZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double radius = zone.radius();

        world.spawnParticle(Particle.SMOKE, zone.center(), 6, radius * 0.25, 0.8, radius * 0.25, 0.01);
        world.spawnParticle(Particle.WITCH, zone.center().clone().add(0, 1, 0), 4, radius * 0.2, 0.5, radius * 0.2, 0.0);

        for (Mob mob : world.getNearbyEntitiesByType(Mob.class, zone.center(), radius)) {
            if (!(mob.getTarget() instanceof Player player)) {
                continue;
            }
            if (isInZone(player.getLocation(), zone)) {
                mob.setTarget(null);
            }
        }
    }

    private void pulseLoop(ActiveConceptLoop loop) {
        if (loop.anchorA().getWorld() == null || loop.anchorB().getWorld() == null) {
            clearLoop(loop.id());
            return;
        }
        World worldA = loop.anchorA().getWorld();
        World worldB = loop.anchorB().getWorld();
        worldA.spawnParticle(Particle.PORTAL, loop.anchorA(), 10, 0.35, 0.5, 0.35, 0.35);
        worldA.spawnParticle(Particle.END_ROD, loop.anchorA(), 4, 0.2, 0.35, 0.2, 0.01);
        spawnAnchorColumn(worldA, loop.anchorA(), Particle.END_ROD, 4);
        worldB.spawnParticle(Particle.PORTAL, loop.anchorB(), 10, 0.35, 0.5, 0.35, 0.35);
        worldB.spawnParticle(Particle.END_ROD, loop.anchorB(), 4, 0.2, 0.35, 0.2, 0.01);
        spawnAnchorColumn(worldB, loop.anchorB(), Particle.END_ROD, 4);
        renderLink(loop.anchorA(), loop.anchorB(), Particle.REVERSE_PORTAL, 16);
        transferLoopEntities(loop, loop.anchorA(), loop.anchorB());
        transferLoopEntities(loop, loop.anchorB(), loop.anchorA());
    }

    private void transferLoopEntities(ActiveConceptLoop loop, Location source, Location destination) {
        World world = source.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(source, loop.activationRadius(), 1.75D, loop.activationRadius())) {
            if (!entity.isValid() || isOnTeleportCooldown(entity.getUniqueId())) {
                continue;
            }
            teleportThroughLoop(entity, destination, loop);
        }
    }

    private void pulseThresholdRelay(ActiveThresholdRelay relay) {
        if (relay.sourceAnchor().getWorld() == null || relay.destinationAnchor().getWorld() == null) {
            clearThresholdRelay(relay.id());
            return;
        }
        if (!(relay.sourceAnchor().getBlock().getState() instanceof Container) || !(relay.destinationAnchor().getBlock().getState() instanceof Container)) {
            clearThresholdRelay(relay.id());
            return;
        }
        Location source = relay.sourceAnchor().clone().add(0.5D, 0.5D, 0.5D);
        Location destination = relay.destinationAnchor().clone().add(0.5D, 0.5D, 0.5D);
        source.getWorld().spawnParticle(Particle.REVERSE_PORTAL, source, 8, 0.25D, 0.25D, 0.25D, 0.2D);
        source.getWorld().spawnParticle(Particle.END_ROD, source, 3, 0.15D, 0.25D, 0.15D, 0.01D);
        spawnAnchorColumn(source.getWorld(), source, Particle.REVERSE_PORTAL, 3);
        destination.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destination, 8, 0.25D, 0.25D, 0.25D, 0.2D);
        destination.getWorld().spawnParticle(Particle.END_ROD, destination, 3, 0.15D, 0.25D, 0.15D, 0.01D);
        spawnAnchorColumn(destination.getWorld(), destination, Particle.END_ROD, 3);
        renderLink(source, destination, Particle.PORTAL, 12);
    }

    private void teleportThroughLoop(Entity entity, Location destination, ActiveConceptLoop loop) {
        Location target = destination.clone();
        Vector velocity = entity.getVelocity().clone();
        if (entity instanceof Player player) {
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());
        }
        entity.teleport(target);
        entity.setFallDistance(0.0F);
        entity.setVelocity(velocity);

        int cooldownTicks = plugin.settings().conceptGraftSettings().loopTeleportCooldownTicks();
        entityTeleportCooldowns.put(entity.getUniqueId(), System.currentTimeMillis() + cooldownTicks * 50L);

        World world = target.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.REVERSE_PORTAL, target, 16, 0.25D, 0.4D, 0.25D, 0.08D);
            world.spawnParticle(Particle.END_ROD, target, 6, 0.15D, 0.25D, 0.15D, 0.01D);
            world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
        }
        if (entity instanceof Player player) {
            sendThrottledActionBar(player, "loop", presentation.triggerActionBarFor(loop.type()), 600L);
        }
    }

    private void pulsePlacementPreviews() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ConceptCatalogGui.PendingConceptAction pending = plugin.conceptCatalogGui().getPendingAction(player);
            if (pending == null) {
                previewFeedbackGate.clear(player.getUniqueId());
                continue;
            }
            previewPendingAction(player, pending);
        }
    }

    private void previewPendingAction(Player player, ConceptCatalogGui.PendingConceptAction pending) {
        if (!plugin.focusItemService().isFocus(player.getInventory().getItemInMainHand())) {
            previewFeedbackGate.clear(player.getUniqueId());
            return;
        }
        if (pending.type().placementStyle() == ConceptPlacementStyle.ZONE) {
            Location previewCenter = resolvePreviewLocation(player, false);
            previewZone(player, pending.type(), previewCenter, plugin.settings().conceptGraftSettings().zoneRadius());
            sendPreviewActionBar(player,
                pending.type().key() + ":zone:" + previewCenter.getBlockX() + ':' + previewCenter.getBlockY() + ':' + previewCenter.getBlockZ(),
                "§5" + pending.type().displayName() + " §8| §dpreview " + formatRadius(plugin.settings().conceptGraftSettings().zoneRadius()) + "m law zone");
            return;
        }

        if (pending.type().firstAnchorComesFromCaster()) {
            Location secondAnchor = resolvePreviewLocation(player, false);
            previewAnchors(player, pending.type(), pending.firstAnchor(), true, secondAnchor, true);
            sendPreviewActionBar(player,
                pending.type().key() + ":second-anchor:" + secondAnchor.getBlockX() + ':' + secondAnchor.getBlockY() + ':' + secondAnchor.getBlockZ(),
                "§5" + pending.type().displayName() + " §8| §dchoose the second anchor");
            return;
        }

        Location previewAnchor = resolvePreviewLocation(player, true);
        boolean validContainer = previewAnchor.getBlock().getState() instanceof Container;
        if (pending.firstAnchor() == null) {
            previewSingleAnchor(player, pending.type(), previewAnchor, validContainer);
            sendPreviewActionBar(player,
                pending.type().key() + ":source:" + validContainer + ':' + previewAnchor.getBlockX() + ':' + previewAnchor.getBlockY() + ':' + previewAnchor.getBlockZ(),
                validContainer
                    ? "§5" + pending.type().displayName() + " §8| §dchoose a source container"
                    : "§c" + pending.type().displayName() + " §8| §7target a container as the source");
            return;
        }
        previewAnchors(player, pending.type(), pending.firstAnchor(), true, previewAnchor, validContainer);
        sendPreviewActionBar(player,
            pending.type().key() + ":destination:" + validContainer + ':' + previewAnchor.getBlockX() + ':' + previewAnchor.getBlockY() + ':' + previewAnchor.getBlockZ(),
            validContainer
                ? "§5" + pending.type().displayName() + " §8| §dchoose the destination container"
                : "§c" + pending.type().displayName() + " §8| §7target a container as the destination");
    }

    private void previewZone(Player player, ConceptGraftType type, Location center, double radius) {
        Particle particle = previewParticleFor(type);
        player.spawnParticle(particle, center, 8, 0.2D, 0.4D, 0.2D, 0.01D);
        spawnRing(player, center, radius, particle, 14);
        spawnRing(player, center.clone().add(0.0D, 1.2D, 0.0D), radius, particle, 14);
        spawnCardinalColumns(player, center, radius, particle, 3);
    }

    private void previewSingleAnchor(Player player, ConceptGraftType type, Location center, boolean valid) {
        Particle particle = valid ? previewParticleFor(type) : Particle.SMOKE;
        player.spawnParticle(particle, center, 10, 0.2D, 0.3D, 0.2D, 0.01D);
        spawnRing(player, center, 0.8D, particle, 10);
        spawnAnchorColumn(player, center, particle, 3);
    }

    private void previewAnchors(Player player, ConceptGraftType type, Location first, boolean firstValid, Location second, boolean secondValid) {
        Particle positive = previewParticleFor(type);
        Particle firstParticle = firstValid ? positive : Particle.SMOKE;
        Particle secondParticle = secondValid ? positive : Particle.SMOKE;
        if (first != null) {
            player.spawnParticle(firstParticle, first, 10, 0.2D, 0.3D, 0.2D, 0.01D);
            spawnRing(player, first, 0.8D, firstParticle, 10);
            spawnAnchorColumn(player, first, firstParticle, 3);
        }
        player.spawnParticle(secondParticle, second, 10, 0.2D, 0.3D, 0.2D, 0.01D);
        spawnRing(player, second, 0.8D, secondParticle, 10);
        spawnAnchorColumn(player, second, secondParticle, 3);
        spawnLink(player, first, second, secondValid ? positive : Particle.SMOKE, 12);
    }

    private Location resolvePreviewLocation(Player player, boolean blockAnchor) {
        Block targetBlock = player.getTargetBlockExact(plugin.settings().interactionRange(), FluidCollisionMode.ALWAYS);
        if (targetBlock != null) {
            return blockAnchor
                ? targetBlock.getLocation().add(0.5D, 0.5D, 0.5D)
                : targetBlock.getLocation().add(0.5D, 1.0D, 0.5D);
        }
        return player.getLocation().clone();
    }

    private void registerConceptualActive(UUID trackingId, UUID ownerId, ConceptGraftType type, String sourceName, String targetName, int durationTicks, Runnable cleanupAction) {
        plugin.activeGraftRegistry().register(
            trackingId,
            ownerId,
            GraftFamily.TOPOLOGY,
            presentation.activeLabelFor(type),
            true,
            type.displayName(),
            sourceName,
            targetName,
            durationTicks,
            cleanupAction
        );
    }

    private BukkitTask scheduleExpiryWarning(UUID ownerId, String graftName, int durationTicks) {
        int warningTicks = Math.min(200, Math.max(40, durationTicks / 4));
        if (durationTicks <= warningTicks) {
            return null;
        }
        BukkitTask warningTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner == null || !owner.isOnline()) {
                return;
            }
            int remainingSeconds = warningTicks / 20;
            plugin.messages().send(owner, "conceptual-expiring", Map.of(
                "graft", graftName,
                "seconds", Integer.toString(remainingSeconds)
            ));
            sendActionBar(owner, "§5" + graftName + " §8| §d" + remainingSeconds + "s remaining");
        }, durationTicks - warningTicks);
        activeTasks.add(warningTask);
        return warningTask;
    }

    private void clearZone(UUID zoneId) {
        ActiveConceptZone zone = activeZones.remove(zoneId);
        if (zone == null) {
            return;
        }
        cancelTrackedTask(zone.pulseTask());
        cancelTrackedTask(zone.cleanupTask());
        cancelTrackedTask(zone.warningTask());
        plugin.activeGraftRegistry().unregister(zoneId);
        runtimeLedger.release(zoneId);

        if (zone.type() == ConceptGraftType.SUN_TO_GROUND || zone.type() == ConceptGraftType.SKY_TO_GROUND) {
            if (zone.center().getWorld() != null) {
                for (Player player : zone.center().getWorld().getNearbyEntitiesByType(Player.class, zone.center(), zone.radius())) {
                    player.resetPlayerTime();
                    player.resetPlayerWeather();
                }
            }
        }

        if (zone.center().getWorld() != null) {
            zone.center().getWorld().spawnParticle(Particle.SMOKE, zone.center(), 18, zone.radius() * 0.2, 0.5, zone.radius() * 0.2, 0.02);
            zone.center().getWorld().playSound(zone.center(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
        }
    }

    private void clearLoop(UUID loopId) {
        ActiveConceptLoop loop = activeLoops.remove(loopId);
        if (loop == null) {
            return;
        }
        cancelTrackedTask(loop.pulseTask());
        cancelTrackedTask(loop.cleanupTask());
        cancelTrackedTask(loop.warningTask());
        plugin.activeGraftRegistry().unregister(loopId);
        runtimeLedger.release(loopId);

        if (loop.anchorA().getWorld() != null) {
            loop.anchorA().getWorld().spawnParticle(Particle.SMOKE, loop.anchorA(), 10, 0.3, 0.5, 0.3, 0.02);
        }
        if (loop.anchorB().getWorld() != null) {
            loop.anchorB().getWorld().spawnParticle(Particle.SMOKE, loop.anchorB(), 10, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private void clearThresholdRelay(UUID relayId) {
        ActiveThresholdRelay relay = activeThresholds.remove(relayId);
        if (relay == null) {
            return;
        }
        cancelTrackedTask(relay.pulseTask());
        cancelTrackedTask(relay.cleanupTask());
        cancelTrackedTask(relay.warningTask());
        plugin.activeGraftRegistry().unregister(relayId);
        runtimeLedger.release(relayId);

        if (relay.sourceAnchor().getWorld() != null) {
            relay.sourceAnchor().getWorld().spawnParticle(Particle.SMOKE, relay.sourceAnchor().clone().add(0.5D, 0.5D, 0.5D), 10, 0.2D, 0.2D, 0.2D, 0.02D);
        }
        if (relay.destinationAnchor().getWorld() != null) {
            relay.destinationAnchor().getWorld().spawnParticle(Particle.SMOKE, relay.destinationAnchor().clone().add(0.5D, 0.5D, 0.5D), 10, 0.2D, 0.2D, 0.2D, 0.02D);
        }
    }

    private void cancelTrackedTask(BukkitTask task) {
        if (task == null) {
            return;
        }
        task.cancel();
        activeTasks.remove(task);
    }

    private Inventory resolveContainerInventory(Location location) {
        if (location == null || location.getWorld() == null || !(location.getBlock().getState() instanceof Container container)) {
            return null;
        }
        return container.getInventory();
    }

    private boolean isInZone(Location location, ActiveConceptZone zone) {
        if (location.getWorld() == null || !location.getWorld().equals(zone.center().getWorld())) {
            return false;
        }
        return location.distanceSquared(zone.center()) <= zone.radius() * zone.radius();
    }

    private boolean isNear(Location location, Location anchor, double radius) {
        if (location.getWorld() == null || !location.getWorld().equals(anchor.getWorld())) {
            return false;
        }
        return location.distanceSquared(anchor) <= radius * radius;
    }

    private boolean isOnTeleportCooldown(UUID entityId) {
        Long cooldownUntil = entityTeleportCooldowns.get(entityId);
        return cooldownUntil != null && cooldownUntil > System.currentTimeMillis();
    }

    private boolean isUndead(LivingEntity entity) {
        return entity instanceof Monster && (
            entity.getType().name().contains("ZOMBIE")
                || entity.getType().name().contains("SKELETON")
                || entity.getType().name().contains("PHANTOM")
                || entity.getType().name().contains("DROWNED")
                || entity.getType().name().contains("WITHER")
                || entity.getType().name().contains("STRAY")
                || entity.getType().name().contains("HUSK")
        );
    }

    private void spawnActivationEffects(Location center, ConceptGraftType type) {
        if (center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        switch (type) {
            case SUN_TO_GROUND -> {
                world.spawnParticle(Particle.END_ROD, center.clone().add(0, 2, 0), 50, 2.0, 2.0, 2.0, 0.05);
                world.spawnParticle(Particle.FLAME, center, 30, 1.5, 1.0, 1.5, 0.02);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
                world.playSound(center, Sound.ENTITY_BLAZE_AMBIENT, 0.5f, 1.8f);
            }
            case SKY_TO_GROUND -> {
                world.spawnParticle(Particle.CLOUD, center.clone().add(0, 3, 0), 50, 3.0, 1.0, 3.0, 0.02);
                world.spawnParticle(Particle.END_ROD, center, 30, 2.0, 3.0, 2.0, 0.05);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                world.playSound(center, Sound.ENTITY_PHANTOM_AMBIENT, 0.4f, 1.5f);
            }
            case NETHER_ZONE -> {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 40, 2.0, 1.0, 2.0, 0.02);
                world.spawnParticle(Particle.LAVA, center, 20, 2.0, 0.5, 2.0, 0.0);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.6f);
                world.playSound(center, Sound.AMBIENT_NETHER_WASTES_MOOD, 0.7f, 1.0f);
            }
            case END_ZONE -> {
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 40, 2.0, 1.0, 2.0, 0.01);
                world.spawnParticle(Particle.PORTAL, center, 60, 2.0, 2.0, 2.0, 0.5);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.4f);
                world.playSound(center, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.3f, 1.5f);
            }
            case OVERWORLD_ZONE -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 40, 2.0, 1.5, 2.0, 0.02);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
            }
            case CONCEALMENT_TO_RECOGNITION -> {
                world.spawnParticle(Particle.SMOKE, center, 35, 2.0, 1.2, 2.0, 0.01);
                world.spawnParticle(Particle.WITCH, center.clone().add(0, 1.2D, 0), 20, 1.5D, 0.6D, 1.5D, 0.0D);
                world.playSound(center, Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.7f, 1.2f);
            }
            case BEGINNING_TO_END -> {
                world.spawnParticle(Particle.PORTAL, center, 50, 1.0, 1.5, 1.0, 0.5);
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 30, 0.5, 1.0, 0.5, 0.3);
                world.spawnParticle(Particle.END_ROD, center, 15, 0.3, 0.5, 0.3, 0.02);
                world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.0f);
            }
            case THRESHOLD_TO_ELSEWHERE -> {
                world.spawnParticle(Particle.PORTAL, center, 36, 0.6D, 0.6D, 0.6D, 0.2D);
                world.spawnParticle(Particle.END_ROD, center, 12, 0.25D, 0.4D, 0.25D, 0.01D);
                world.playSound(center, Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.0f);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.2f);
            }
        }
    }

    private Particle previewParticleFor(ConceptGraftType type) {
        return switch (type) {
            case SUN_TO_GROUND -> Particle.END_ROD;
            case SKY_TO_GROUND -> Particle.CLOUD;
            case NETHER_ZONE -> Particle.SOUL_FIRE_FLAME;
            case END_ZONE -> Particle.REVERSE_PORTAL;
            case OVERWORLD_ZONE -> Particle.HAPPY_VILLAGER;
            case CONCEALMENT_TO_RECOGNITION -> Particle.WITCH;
            case BEGINNING_TO_END -> Particle.PORTAL;
            case THRESHOLD_TO_ELSEWHERE -> Particle.REVERSE_PORTAL;
        };
    }

    private void pulseZonePresenceFeedback() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Set<UUID> currentZones = new HashSet<>();
            for (ActiveConceptZone zone : activeZones.values()) {
                if (!isInZone(player.getLocation(), zone)) {
                    continue;
                }
                currentZones.add(zone.id());
            }
            Set<UUID> previousZones = new HashSet<>(playerZonePresence.getOrDefault(player.getUniqueId(), Set.of()));
            for (UUID zoneId : currentZones) {
                if (previousZones.remove(zoneId)) {
                    continue;
                }
                ActiveConceptZone enteredZone = activeZones.get(zoneId);
                if (enteredZone == null) {
                    continue;
                }
                sendThrottledActionBar(player, "zone-enter-" + zoneId, presentation.entryActionBarFor(enteredZone.type()), 800L);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.1f);
            }
            for (UUID zoneId : previousZones) {
                ActiveConceptZone exitedZone = activeZones.get(zoneId);
                if (exitedZone == null) {
                    continue;
                }
                handleZoneExitVisualReset(player, exitedZone, currentZones);
                sendThrottledActionBar(player, "zone-exit-" + zoneId, presentation.exitActionBarFor(exitedZone.type()), 800L);
            }
            if (currentZones.isEmpty()) {
                playerZonePresence.remove(player.getUniqueId());
            } else {
                playerZonePresence.put(player.getUniqueId(), currentZones);
            }
        }
    }

    private void sendConceptualSummary(Player player, ConceptGraftType type, int seconds) {
        player.sendMessage("§8" + switch (type) {
            case SUN_TO_GROUND -> "Solar law holds for " + seconds + "s: daylight dominates, frost fails, undead burn, crops surge.";
            case SKY_TO_GROUND -> "Sky law holds for " + seconds + "s: falling is denied and unsupported descent is suppressed.";
            case NETHER_ZONE -> "Nether law holds for " + seconds + "s: water is rejected and heat shelters instead of harms.";
            case END_ZONE -> "End law holds for " + seconds + "s: positions become unstable and may shift without warning.";
            case OVERWORLD_ZONE -> "Overworld law holds for " + seconds + "s: foreign laws are stripped and natural order is restored.";
            case CONCEALMENT_TO_RECOGNITION -> "Recognition is rewritten for " + seconds + "s: hostile attention loses players inside the zone.";
            case BEGINNING_TO_END -> "For " + seconds + "s, the two anchors are treated as one route: what enters one may finish at the other.";
            case THRESHOLD_TO_ELSEWHERE -> "For " + seconds + "s, opening the source threshold reveals the destination inventory elsewhere.";
        });
    }

    private void handleZoneExitVisualReset(Player player, ActiveConceptZone exitedZone, Set<UUID> currentZones) {
        if (!affectsPlayerSky(exitedZone.type())) {
            return;
        }
        boolean stillInsideSkyAffectingZone = currentZones.stream()
            .map(activeZones::get)
            .anyMatch(zone -> zone != null && affectsPlayerSky(zone.type()));
        if (stillInsideSkyAffectingZone) {
            return;
        }
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    private boolean affectsPlayerSky(ConceptGraftType type) {
        return type == ConceptGraftType.SUN_TO_GROUND || type == ConceptGraftType.SKY_TO_GROUND;
    }

    private void renderZoneBoundary(World world, Location center, double radius, Particle particle, int points) {
        spawnRing(world, center, radius, particle, points);
        spawnRing(world, center.clone().add(0.0D, 1.2D, 0.0D), radius, particle, points);
        spawnRing(world, center.clone().add(0.0D, 2.4D, 0.0D), radius, particle, points);
        spawnCardinalColumns(world, center, radius, particle, 3);
    }

    private void renderLink(Location start, Location end, Particle particle, int points) {
        if (start == null || end == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
            return;
        }
        spawnLink(start.getWorld(), start, end, particle, points);
    }

    private void spawnRing(Player player, Location center, double radius, Particle particle, int points) {
        if (center.getWorld() == null) {
            return;
        }
        for (int index = 0; index < points; index++) {
            double angle = (Math.PI * 2.0D * index) / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.1D, Math.sin(angle) * radius);
            player.spawnParticle(particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnRing(World world, Location center, double radius, Particle particle, int points) {
        if (world == null) {
            return;
        }
        for (int index = 0; index < points; index++) {
            double angle = (Math.PI * 2.0D * index) / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.1D, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnCardinalColumns(World world, Location center, double radius, Particle particle, int height) {
        spawnAnchorColumn(world, center.clone().add(radius, 0.0D, 0.0D), particle, height);
        spawnAnchorColumn(world, center.clone().add(-radius, 0.0D, 0.0D), particle, height);
        spawnAnchorColumn(world, center.clone().add(0.0D, 0.0D, radius), particle, height);
        spawnAnchorColumn(world, center.clone().add(0.0D, 0.0D, -radius), particle, height);
    }

    private void spawnCardinalColumns(Player player, Location center, double radius, Particle particle, int height) {
        spawnAnchorColumn(player, center.clone().add(radius, 0.0D, 0.0D), particle, height);
        spawnAnchorColumn(player, center.clone().add(-radius, 0.0D, 0.0D), particle, height);
        spawnAnchorColumn(player, center.clone().add(0.0D, 0.0D, radius), particle, height);
        spawnAnchorColumn(player, center.clone().add(0.0D, 0.0D, -radius), particle, height);
    }

    private void spawnAnchorColumn(World world, Location base, Particle particle, int height) {
        for (int step = 0; step < height; step++) {
            world.spawnParticle(particle, base.clone().add(0.0D, step * 0.85D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnAnchorColumn(Player player, Location base, Particle particle, int height) {
        for (int step = 0; step < height; step++) {
            player.spawnParticle(particle, base.clone().add(0.0D, step * 0.85D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnLink(Player player, Location start, Location end, Particle particle, int points) {
        if (start == null || end == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld())) {
            return;
        }
        Vector delta = end.toVector().subtract(start.toVector());
        for (int index = 1; index < points; index++) {
            Location point = start.clone().add(delta.clone().multiply(index / (double) points));
            player.spawnParticle(particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnLink(World world, Location start, Location end, Particle particle, int points) {
        if (world == null || start == null || end == null) {
            return;
        }
        Vector delta = end.toVector().subtract(start.toVector());
        for (int index = 1; index < points; index++) {
            Location point = start.clone().add(delta.clone().multiply(index / (double) points));
            world.spawnParticle(particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void sendPreviewActionBar(Player player, String previewKey, String message) {
        if (!previewFeedbackGate.shouldSend(player.getUniqueId(), previewKey, message)) {
            return;
        }
        sendActionBar(player, message);
    }

    private void sendThrottledActionBar(Player player, String key, String message, long cooldownMillis) {
        String fullKey = player.getUniqueId() + ":" + key;
        long now = System.currentTimeMillis();
        Long nextAllowed = feedbackCooldowns.get(fullKey);
        if (nextAllowed != null && nextAllowed > now) {
            return;
        }
        feedbackCooldowns.put(fullKey, now + cooldownMillis);
        sendActionBar(player, message);
    }

    private void sendActionBar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private boolean sameBlock(Location first, Location second) {
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return false;
        }
        return first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private String describeLocation(Location location) {
        String world = location.getWorld() == null ? "world" : location.getWorld().getName();
        return world + " @ " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String describeAnchoredTarget(Location anchorA, Location anchorB) {
        return describeLocation(anchorA) + " ↔ " + describeLocation(anchorB);
    }

    private String formatRadius(double radius) {
        return String.format(java.util.Locale.ROOT, "%.1f", radius);
    }

    private record ActiveConceptZone(
        UUID id,
        UUID ownerId,
        ConceptGraftType type,
        Location center,
        double radius,
        BukkitTask pulseTask,
        BukkitTask cleanupTask,
        BukkitTask warningTask
    ) {
    }

    private record ActiveConceptLoop(
        UUID id,
        UUID ownerId,
        ConceptGraftType type,
        Location anchorA,
        Location anchorB,
        double activationRadius,
        BukkitTask pulseTask,
        BukkitTask cleanupTask,
        BukkitTask warningTask
    ) {
    }

    private record ActiveThresholdRelay(
        UUID id,
        UUID ownerId,
        ConceptGraftType type,
        Location sourceAnchor,
        Location destinationAnchor,
        BukkitTask pulseTask,
        BukkitTask cleanupTask,
        BukkitTask warningTask
    ) {
    }

}
