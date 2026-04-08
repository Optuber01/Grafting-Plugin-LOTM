package com.graftingplugin.topology;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.config.TopologyGraftSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TopologyGraftService implements Listener {

    private final GraftingPlugin plugin;
    private final TopologyGraftPlanner planner;
    private final Map<UUID, ActiveTopologyRoute> activeRoutes = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public TopologyGraftService(GraftingPlugin plugin, TopologyGraftPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
    }

    public void shutdown() {
        for (UUID routeId : Set.copyOf(activeRoutes.keySet())) {
            clearRoute(routeId);
        }
        for (BukkitTask task : Set.copyOf(activeTasks)) {
            task.cancel();
        }
        activeTasks.clear();
        playerCooldowns.clear();
    }

    public boolean applyToBlock(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, Block targetBlock) {
        AnchorReference sourceAnchor = resolveSourceAnchor(caster, source, sourceReference);
        if (sourceAnchor == null) {
            plugin.messages().send(caster, "stored-source-invalid");
            return false;
        }

        ResolvedTarget target = resolveTargetBlock(targetBlock);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        TopologyGraftPlan plan = validateAndPlan(caster, source, aspect, target.subject());
        if (plan == null) {
            return false;
        }

        if (!activateRoute(caster, sourceAnchor, target, aspect, plan)) {
            plugin.messages().send(caster, "topology-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.subject().displayName()
            ));
            return false;
        }

        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        return true;
    }

    public boolean applyToLocation(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, Location targetLocation) {
        AnchorReference sourceAnchor = resolveSourceAnchor(caster, source, sourceReference);
        if (sourceAnchor == null) {
            plugin.messages().send(caster, "stored-source-invalid");
            return false;
        }

        ResolvedTarget target = resolveTargetLocation(targetLocation);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        TopologyGraftPlan plan = validateAndPlan(caster, source, aspect, target.subject());
        if (plan == null) {
            return false;
        }

        if (!activateRoute(caster, sourceAnchor, target, aspect, plan)) {
            plugin.messages().send(caster, "topology-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.subject().displayName()
            ));
            return false;
        }

        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long cooldownUntil = playerCooldowns.get(event.getPlayer().getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now) {
            return;
        }

        for (ActiveTopologyRoute route : activeRoutes.values()) {
            if (!isRouteValid(route)) {
                clearRoute(route.id());
                continue;
            }
            if (!route.triggerAnchor().getWorld().equals(to.getWorld())) {
                continue;
            }
            if (to.distanceSquared(route.triggerAnchor()) > route.activationRadius() * route.activationRadius()) {
                continue;
            }

            teleportPlayer(event.getPlayer(), route, now);
            break;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (ActiveTopologyRoute route : Set.copyOf(activeRoutes.values())) {
            if (isInChunk(route.triggerAnchor(), chunk) || isInChunk(route.destinationAnchor(), chunk)) {
                clearRoute(route.id());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location broken = event.getBlock().getLocation().toBlockLocation();
        for (ActiveTopologyRoute route : Set.copyOf(activeRoutes.values())) {
            if ((route.sourceBlockAnchor() != null && route.sourceBlockAnchor().equals(broken))
                || (route.targetBlockAnchor() != null && route.targetBlockAnchor().equals(broken))) {
                clearRoute(route.id());
            }
        }
    }

    private TopologyGraftPlan validateAndPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateTarget(source, aspect, target);
        if (!compatibility.success()) {
            plugin.messages().send(caster, "target-incompatible", Map.of(
                "target", target.displayName(),
                "aspect", aspect.displayName()
            ));
            return null;
        }

        TopologyGraftPlan plan = planner.plan(aspect, source, target).orElse(null);
        if (plan == null) {
            plugin.messages().send(caster, "topology-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.displayName()
            ));
            return null;
        }
        return plan;
    }

    private boolean activateRoute(Player caster, AnchorReference sourceAnchor, ResolvedTarget target, GraftAspect aspect, TopologyGraftPlan plan) {
        if (sourceAnchor.anchor().getWorld() == null || target.anchor().getWorld() == null) {
            return false;
        }

        Location triggerAnchor = plan.mode() == TopologyGraftMode.ANCHOR_LINK ? sourceAnchor.anchor().clone() : target.anchor().clone();
        Location destinationAnchor = plan.mode() == TopologyGraftMode.ANCHOR_LINK ? target.anchor().clone() : sourceAnchor.destination().clone();
        if (triggerAnchor.getWorld() == null || destinationAnchor.getWorld() == null) {
            return false;
        }

        TopologyGraftSettings settings = plugin.settings().topologyGraftSettings();
        UUID routeId = UUID.randomUUID();
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearRoute(routeId), settings.durationTicks());
        activeTasks.add(cleanupTask);

        ActiveTopologyRoute route = new ActiveTopologyRoute(
            routeId,
            caster.getUniqueId(),
            aspect,
            plan.mode(),
            triggerAnchor,
            destinationAnchor,
            sourceAnchor.blockAnchor(),
            target.blockAnchor(),
            settings.activationRadius(),
            settings.activationCooldownTicks(),
            cleanupTask,
            sourceAnchor.displayName(),
            target.subject().displayName()
        );
        activeRoutes.put(routeId, route);

        sourceAnchor.anchor().getWorld().spawnParticle(Particle.PORTAL, sourceAnchor.anchor(), 20, 0.25D, 0.5D, 0.25D, 0.05D);
        target.anchor().getWorld().spawnParticle(Particle.PORTAL, target.anchor(), 20, 0.25D, 0.5D, 0.25D, 0.05D);

        String messageKey = plan.mode() == TopologyGraftMode.ANCHOR_LINK ? "topology-cast-link" : "topology-cast-loop";
        plugin.messages().send(caster, messageKey, Map.of(
            "aspect", aspect.displayName(),
            "source", sourceAnchor.displayName(),
            "target", target.subject().displayName()
        ));
        return true;
    }

    private void teleportPlayer(Player player, ActiveTopologyRoute route, long now) {
        Location destination = route.destinationAnchor().clone();
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        Vector velocity = player.getVelocity().clone();
        player.teleport(destination);
        player.setFallDistance(0.0F);
        player.setVelocity(velocity);
        playerCooldowns.put(player.getUniqueId(), now + route.activationCooldownTicks() * 50L);
        destination.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destination, 16, 0.2D, 0.35D, 0.2D, 0.03D);
    }

    private AnchorReference resolveSourceAnchor(Player caster, GraftSubject source, CastSourceReference sourceReference) {
        if (sourceReference != null && sourceReference.hasBlockLocation()) {
            Location blockAnchor = sourceReference.blockLocation();
            if (blockAnchor == null || blockAnchor.getWorld() == null) {
                return null;
            }
            return new AnchorReference(
                source.displayName(),
                centeredTriggerAnchor(blockAnchor),
                standingAnchor(blockAnchor),
                blockAnchor.toBlockLocation()
            );
        }
        if (source.kind() == com.graftingplugin.subject.SubjectKind.CONCEPT) {
            Location current = caster.getLocation().clone();
            return new AnchorReference(source.displayName(), current.clone(), current.clone(), null);
        }
        return null;
    }

    private ResolvedTarget resolveTargetBlock(Block block) {
        if (block == null) {
            return null;
        }
        GraftSubject target = plugin.subjectResolver().resolveBlock(block).orElse(null);
        if (target == null) {
            return null;
        }
        Location blockAnchor = block.getLocation().toBlockLocation();
        return new ResolvedTarget(target, standingAnchor(blockAnchor), blockAnchor);
    }

    private ResolvedTarget resolveTargetLocation(Location location) {
        if (location == null) {
            return null;
        }
        GraftSubject target = plugin.subjectResolver().resolveLocation(location).orElse(null);
        if (target == null) {
            return null;
        }
        return new ResolvedTarget(target, location.clone(), null);
    }

    private boolean isRouteValid(ActiveTopologyRoute route) {
        if (route.triggerAnchor().getWorld() == null || route.destinationAnchor().getWorld() == null) {
            return false;
        }
        if (!isChunkLoaded(route.triggerAnchor()) || !isChunkLoaded(route.destinationAnchor())) {
            return false;
        }
        if (route.sourceBlockAnchor() != null && route.sourceBlockAnchor().getBlock().getType().isAir()) {
            return false;
        }
        if (route.targetBlockAnchor() != null && route.targetBlockAnchor().getBlock().getType().isAir()) {
            return false;
        }
        return true;
    }

    private boolean isChunkLoaded(Location location) {
        return location.getWorld() != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private boolean isInChunk(Location location, Chunk chunk) {
        return location.getWorld() != null
            && location.getWorld().equals(chunk.getWorld())
            && (location.getBlockX() >> 4) == chunk.getX()
            && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    private Location centeredTriggerAnchor(Location blockLocation) {
        return blockLocation.clone().add(0.5D, 0.5D, 0.5D);
    }

    private Location standingAnchor(Location blockLocation) {
        return blockLocation.clone().add(0.5D, 1.0D, 0.5D);
    }

    private void clearRoute(UUID routeId) {
        ActiveTopologyRoute route = activeRoutes.remove(routeId);
        if (route == null) {
            return;
        }
        route.cleanupTask().cancel();
        activeTasks.remove(route.cleanupTask());
    }

    private record AnchorReference(String displayName, Location anchor, Location destination, Location blockAnchor) {
    }

    private record ResolvedTarget(GraftSubject subject, Location anchor, Location blockAnchor) {
    }

    private record ActiveTopologyRoute(
        UUID id,
        UUID ownerId,
        GraftAspect aspect,
        TopologyGraftMode mode,
        Location triggerAnchor,
        Location destinationAnchor,
        Location sourceBlockAnchor,
        Location targetBlockAnchor,
        double activationRadius,
        int activationCooldownTicks,
        BukkitTask cleanupTask,
        String sourceName,
        String targetName
    ) {
    }
}
