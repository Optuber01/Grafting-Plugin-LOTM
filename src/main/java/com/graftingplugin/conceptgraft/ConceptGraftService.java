package com.graftingplugin.conceptgraft;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.cast.GraftFamily;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConceptGraftService implements Listener {

    private final GraftingPlugin plugin;
    private final Map<UUID, ActiveConceptZone> activeZones = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveConceptLoop> activeLoops = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loopTeleportCooldowns = new ConcurrentHashMap<>();

    public ConceptGraftService(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        for (UUID zoneId : Set.copyOf(activeZones.keySet())) {
            clearZone(zoneId);
        }
        for (UUID loopId : Set.copyOf(activeLoops.keySet())) {
            clearLoop(loopId);
        }
        for (BukkitTask task : Set.copyOf(activeTasks)) {
            task.cancel();
        }
        activeTasks.clear();
        playerCooldowns.clear();
        loopTeleportCooldowns.clear();
    }

    public boolean activateZone(Player caster, ConceptGraftType type, Location center) {
        if (isOnCooldown(caster)) {
            long remaining = cooldownRemainingSeconds(caster);
            plugin.messages().send(caster, "conceptual-cooldown", Map.of("seconds", Long.toString(remaining)));
            return false;
        }

        int activeCount = countActiveForPlayer(caster.getUniqueId());
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        if (activeCount >= settings.maxActivePerPlayer()) {
            plugin.messages().send(caster, "conceptual-max-active");
            return false;
        }

        UUID zoneId = UUID.randomUUID();
        Location zoneCenter = center.clone();
        int durationTicks = settings.zoneDurationTicks();
        double radius = settings.zoneRadius();

        BukkitTask pulseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ActiveConceptZone zone = activeZones.get(zoneId);
            if (zone == null) {
                return;
            }
            pulseZone(zone);
        }, 0L, settings.pulseIntervalTicks());
        activeTasks.add(pulseTask);

        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearZone(zoneId), durationTicks);
        activeTasks.add(cleanupTask);

        ActiveConceptZone zone = new ActiveConceptZone(zoneId, caster.getUniqueId(), type, zoneCenter, radius, pulseTask, cleanupTask);
        activeZones.put(zoneId, zone);

        plugin.activeGraftRegistry().register(
            zoneId, caster.getUniqueId(), GraftFamily.TOPOLOGY,
            type.displayName(), type.key(), describeLocation(zoneCenter),
            durationTicks, () -> clearZone(zoneId)
        );

        applyCooldown(caster);
        spawnActivationEffects(zoneCenter, type);

        plugin.messages().send(caster, "conceptual-zone-activated", Map.of(
            "graft", type.displayName(),
            "seconds", Integer.toString(durationTicks / 20)
        ));
        return true;
    }

    public boolean activateLoop(Player caster, Location anchorA, Location anchorB) {
        if (isOnCooldown(caster)) {
            long remaining = cooldownRemainingSeconds(caster);
            plugin.messages().send(caster, "conceptual-cooldown", Map.of("seconds", Long.toString(remaining)));
            return false;
        }

        int activeCount = countActiveForPlayer(caster.getUniqueId());
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        if (activeCount >= settings.maxActivePerPlayer()) {
            plugin.messages().send(caster, "conceptual-max-active");
            return false;
        }

        UUID loopId = UUID.randomUUID();
        int durationTicks = settings.loopDurationTicks();

        BukkitTask pulseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ActiveConceptLoop loop = activeLoops.get(loopId);
            if (loop == null) {
                return;
            }
            pulseLoop(loop);
        }, 20L, 30L);
        activeTasks.add(pulseTask);

        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearLoop(loopId), durationTicks);
        activeTasks.add(cleanupTask);

        ActiveConceptLoop loop = new ActiveConceptLoop(loopId, caster.getUniqueId(), anchorA.clone(), anchorB.clone(),
            settings.loopActivationRadius(), pulseTask, cleanupTask);
        activeLoops.put(loopId, loop);

        plugin.activeGraftRegistry().register(
            loopId, caster.getUniqueId(), GraftFamily.TOPOLOGY,
            ConceptGraftType.BEGINNING_TO_END.displayName(), "beginning-to-end",
            describeLocation(anchorA) + " \u2194 " + describeLocation(anchorB),
            durationTicks, () -> clearLoop(loopId)
        );

        applyCooldown(caster);
        spawnActivationEffects(anchorA, ConceptGraftType.BEGINNING_TO_END);
        spawnActivationEffects(anchorB, ConceptGraftType.BEGINNING_TO_END);

        plugin.messages().send(caster, "conceptual-loop-activated", Map.of(
            "seconds", Integer.toString(durationTicks / 20)
        ));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        for (ActiveConceptLoop loop : activeLoops.values()) {
            long now = System.currentTimeMillis();
            Long cooldownUntil = loopTeleportCooldowns.get(event.getPlayer().getUniqueId());
            if (cooldownUntil != null && cooldownUntil > now) {
                continue;
            }

            if (isNear(to, loop.anchorA(), loop.activationRadius())) {
                teleportToLoop(event.getPlayer(), loop.anchorB(), loop);
                break;
            }
            if (isNear(to, loop.anchorB(), loop.activationRadius())) {
                teleportToLoop(event.getPlayer(), loop.anchorA(), loop);
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
                event.getPlayer().sendMessage("\u00a7cThe water evaporates instantly. Nether rules are in effect here.");
                return;
            }
        }
    }

    private void pulseZone(ActiveConceptZone zone) {
        if (zone.center().getWorld() == null) {
            clearZone(zone.id());
            return;
        }
        switch (zone.type()) {
            case SUN_TO_GROUND -> pulseSunZone(zone);
            case SKY_TO_GROUND -> pulseSkyZone(zone);
            case NETHER_ZONE -> pulseNetherZone(zone);
            case END_ZONE -> pulseEndZone(zone);
            case OVERWORLD_ZONE -> pulseOverworldZone(zone);
            default -> {}
        }
    }

    private void pulseSunZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double r = zone.radius();

        world.spawnParticle(Particle.END_ROD, zone.center().clone().add(0, 3, 0), 8, r * 0.3, 1.5, r * 0.3, 0.01);
        world.spawnParticle(Particle.FLAME, zone.center(), 4, r * 0.2, 0.5, r * 0.2, 0.005);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), r)) {
            if (entity instanceof Player player) {
                player.setPlayerTime(6000L, false);
            }
            if (isUndead(entity)) {
                entity.setFireTicks(Math.max(entity.getFireTicks(), 60));
            }
        }

        int ir = (int) r;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                Location probe = zone.center().clone().add(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = probe.clone().add(0, dy, 0).getBlock();
                    Material mat = block.getType();
                    if (mat == Material.ICE) {
                        block.setType(Material.WATER);
                    } else if (mat == Material.SNOW || mat == Material.SNOW_BLOCK) {
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
        double r = zone.radius();

        world.spawnParticle(Particle.CLOUD, zone.center().clone().add(0, 2, 0), 6, r * 0.3, 1.0, r * 0.3, 0.01);
        world.spawnParticle(Particle.END_ROD, zone.center().clone().add(0, 1, 0), 4, r * 0.2, 2.0, r * 0.2, 0.02);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), r)) {
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
        double r = zone.radius();

        world.spawnParticle(Particle.SOUL_FIRE_FLAME, zone.center(), 6, r * 0.3, 0.5, r * 0.3, 0.01);
        world.spawnParticle(Particle.LAVA, zone.center(), 3, r * 0.2, 0.3, r * 0.2, 0.0);
        world.spawnParticle(Particle.ASH, zone.center(), 10, r * 0.4, 1.0, r * 0.4, 0.0);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), r)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, true));
        }

        int ir = (int) r;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                Location probe = zone.center().clone().add(dx, 0, dz);
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = probe.clone().add(0, dy, 0).getBlock();
                    Material mat = block.getType();
                    if (mat == Material.WATER) {
                        block.setType(Material.AIR);
                        world.spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.02);
                    } else if (mat == Material.ICE || mat == Material.BLUE_ICE || mat == Material.PACKED_ICE) {
                        block.setType(Material.AIR);
                        world.spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
        }
    }

    private void pulseEndZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double r = zone.radius();

        world.spawnParticle(Particle.DRAGON_BREATH, zone.center(), 6, r * 0.3, 0.5, r * 0.3, 0.005);
        world.spawnParticle(Particle.PORTAL, zone.center(), 12, r * 0.3, 1.0, r * 0.3, 0.3);
        world.spawnParticle(Particle.REVERSE_PORTAL, zone.center().clone().add(0, 1, 0), 6, r * 0.2, 0.5, r * 0.2, 0.1);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), r)) {
            if (!(entity instanceof Player)) {
                continue;
            }
            if (Math.random() > 0.08) {
                continue;
            }
            double offsetX = (Math.random() - 0.5) * r * 0.6;
            double offsetZ = (Math.random() - 0.5) * r * 0.6;
            Location destination = entity.getLocation().clone().add(offsetX, 0, offsetZ);
            Block destBlock = destination.getBlock();
            Block below = destBlock.getRelative(BlockFace.DOWN);
            if (destBlock.getType().isAir() && !below.getType().isAir()) {
                world.spawnParticle(Particle.REVERSE_PORTAL, entity.getLocation(), 12, 0.3, 0.5, 0.3, 0.1);
                entity.teleport(destination);
                world.spawnParticle(Particle.REVERSE_PORTAL, destination, 12, 0.3, 0.5, 0.3, 0.1);
                world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.5f);
            }
        }
    }

    private void pulseOverworldZone(ActiveConceptZone zone) {
        World world = zone.center().getWorld();
        double r = zone.radius();

        world.spawnParticle(Particle.HAPPY_VILLAGER, zone.center(), 5, r * 0.3, 0.5, r * 0.3, 0.01);

        for (LivingEntity entity : world.getNearbyLivingEntities(zone.center(), r)) {
            entity.removePotionEffect(PotionEffectType.LEVITATION);
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

    private void pulseLoop(ActiveConceptLoop loop) {
        if (loop.anchorA().getWorld() == null || loop.anchorB().getWorld() == null) {
            clearLoop(loop.id());
            return;
        }
        World worldA = loop.anchorA().getWorld();
        World worldB = loop.anchorB().getWorld();
        worldA.spawnParticle(Particle.PORTAL, loop.anchorA(), 8, 0.3, 0.5, 0.3, 0.3);
        worldA.spawnParticle(Particle.END_ROD, loop.anchorA(), 3, 0.2, 0.3, 0.2, 0.01);
        worldB.spawnParticle(Particle.PORTAL, loop.anchorB(), 8, 0.3, 0.5, 0.3, 0.3);
        worldB.spawnParticle(Particle.END_ROD, loop.anchorB(), 3, 0.2, 0.3, 0.2, 0.01);
    }

    private void teleportToLoop(Player player, Location destination, ActiveConceptLoop loop) {
        Location dest = destination.clone();
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());
        Vector velocity = player.getVelocity().clone();
        player.teleport(dest);
        player.setFallDistance(0.0F);
        player.setVelocity(velocity);

        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        loopTeleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + settings.loopCooldownTicks() * 50L);

        dest.getWorld().spawnParticle(Particle.REVERSE_PORTAL, dest, 20, 0.3, 0.5, 0.3, 0.1);
        dest.getWorld().spawnParticle(Particle.END_ROD, dest, 8, 0.2, 0.3, 0.2, 0.01);
        dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
    }

    private void clearZone(UUID zoneId) {
        ActiveConceptZone zone = activeZones.remove(zoneId);
        if (zone == null) {
            return;
        }
        zone.pulseTask().cancel();
        activeTasks.remove(zone.pulseTask());
        zone.cleanupTask().cancel();
        activeTasks.remove(zone.cleanupTask());
        plugin.activeGraftRegistry().unregister(zoneId);

        if (zone.type() == ConceptGraftType.SUN_TO_GROUND || zone.type() == ConceptGraftType.SKY_TO_GROUND) {
            if (zone.center().getWorld() != null) {
                for (Player player : zone.center().getWorld().getNearbyEntitiesByType(Player.class, zone.center(), zone.radius())) {
                    player.resetPlayerTime();
                    player.resetPlayerWeather();
                }
            }
        }

        if (zone.center().getWorld() != null) {
            zone.center().getWorld().spawnParticle(Particle.SMOKE, zone.center(), 20, zone.radius() * 0.2, 0.5, zone.radius() * 0.2, 0.02);
            zone.center().getWorld().playSound(zone.center(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
        }
    }

    private void clearLoop(UUID loopId) {
        ActiveConceptLoop loop = activeLoops.remove(loopId);
        if (loop == null) {
            return;
        }
        loop.pulseTask().cancel();
        activeTasks.remove(loop.pulseTask());
        loop.cleanupTask().cancel();
        activeTasks.remove(loop.cleanupTask());
        plugin.activeGraftRegistry().unregister(loopId);

        if (loop.anchorA().getWorld() != null) {
            loop.anchorA().getWorld().spawnParticle(Particle.SMOKE, loop.anchorA(), 10, 0.3, 0.5, 0.3, 0.02);
        }
        if (loop.anchorB().getWorld() != null) {
            loop.anchorB().getWorld().spawnParticle(Particle.SMOKE, loop.anchorB(), 10, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private boolean isOnCooldown(Player player) {
        Long cooldownUntil = playerCooldowns.get(player.getUniqueId());
        return cooldownUntil != null && cooldownUntil > System.currentTimeMillis();
    }

    private long cooldownRemainingSeconds(Player player) {
        Long cooldownUntil = playerCooldowns.get(player.getUniqueId());
        if (cooldownUntil == null) {
            return 0;
        }
        return Math.max(1L, (long) Math.ceil((cooldownUntil - System.currentTimeMillis()) / 1000.0));
    }

    private void applyCooldown(Player player) {
        ConceptGraftSettings settings = plugin.settings().conceptGraftSettings();
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + settings.cooldownTicks() * 50L);
    }

    private int countActiveForPlayer(UUID playerId) {
        int count = 0;
        for (ActiveConceptZone zone : activeZones.values()) {
            if (zone.ownerId().equals(playerId)) {
                count++;
            }
        }
        for (ActiveConceptLoop loop : activeLoops.values()) {
            if (loop.ownerId().equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    private boolean isInZone(Location loc, ActiveConceptZone zone) {
        if (loc.getWorld() == null || !loc.getWorld().equals(zone.center().getWorld())) {
            return false;
        }
        return loc.distanceSquared(zone.center()) <= zone.radius() * zone.radius();
    }

    private boolean isNear(Location loc, Location anchor, double radius) {
        if (loc.getWorld() == null || !loc.getWorld().equals(anchor.getWorld())) {
            return false;
        }
        return loc.distanceSquared(anchor) <= radius * radius;
    }

    private boolean isUndead(LivingEntity entity) {
        return entity instanceof Monster && (
            entity.getType().name().contains("ZOMBIE") ||
            entity.getType().name().contains("SKELETON") ||
            entity.getType().name().contains("PHANTOM") ||
            entity.getType().name().contains("DROWNED") ||
            entity.getType().name().contains("WITHER") ||
            entity.getType().name().contains("STRAY") ||
            entity.getType().name().contains("HUSK")
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
                world.spawnParticle(Particle.DRAGON_BREATH, center, 40, 2.0, 1.0, 2.0, 0.01);
                world.spawnParticle(Particle.PORTAL, center, 60, 2.0, 2.0, 2.0, 0.5);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.4f);
                world.playSound(center, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.3f, 1.5f);
            }
            case OVERWORLD_ZONE -> {
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 40, 2.0, 1.5, 2.0, 0.02);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
            }
            case BEGINNING_TO_END -> {
                world.spawnParticle(Particle.PORTAL, center, 50, 1.0, 1.5, 1.0, 0.5);
                world.spawnParticle(Particle.REVERSE_PORTAL, center, 30, 0.5, 1.0, 0.5, 0.3);
                world.spawnParticle(Particle.END_ROD, center, 15, 0.3, 0.5, 0.3, 0.02);
                world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
                world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.0f);
            }
        }
    }

    private String describeLocation(Location loc) {
        String world = loc.getWorld() == null ? "world" : loc.getWorld().getName();
        return world + " @ " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private record ActiveConceptZone(
        UUID id,
        UUID ownerId,
        ConceptGraftType type,
        Location center,
        double radius,
        BukkitTask pulseTask,
        BukkitTask cleanupTask
    ) {}

    private record ActiveConceptLoop(
        UUID id,
        UUID ownerId,
        Location anchorA,
        Location anchorB,
        double activationRadius,
        BukkitTask pulseTask,
        BukkitTask cleanupTask
    ) {}
}
