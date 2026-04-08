package com.graftingplugin.relation;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.config.RelationGraftSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

public final class RelationGraftService implements Listener {

    private final GraftingPlugin plugin;
    private final RelationGraftPlanner planner;
    private final Map<UUID, ActiveAggroRedirect> activeAggroRedirects = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveProjectileRetarget> activeProjectileRetargets = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveTether> activeTethers = new ConcurrentHashMap<>();
    private final Map<String, ActiveContainerRoute> activeContainerRoutes = new ConcurrentHashMap<>();
    private final Set<BukkitTask> activeTasks = ConcurrentHashMap.newKeySet();

    public RelationGraftService(GraftingPlugin plugin, RelationGraftPlanner planner) {
        this.plugin = plugin;
        this.planner = planner;
    }

    public void shutdown() {
        for (UUID sourceId : Set.copyOf(activeAggroRedirects.keySet())) {
            clearAggroRedirect(sourceId, true);
        }
        for (UUID sourceId : Set.copyOf(activeProjectileRetargets.keySet())) {
            clearProjectileRetarget(sourceId);
        }
        for (UUID sourceId : Set.copyOf(activeTethers.keySet())) {
            clearTether(sourceId);
        }
        for (String sourceKey : Set.copyOf(activeContainerRoutes.keySet())) {
            clearContainerRoute(sourceKey);
        }
        for (BukkitTask task : Set.copyOf(activeTasks)) {
            task.cancel();
        }
        activeTasks.clear();
    }

    public boolean applyToEntity(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, Entity targetEntity) {
        ResolvedTarget target = resolveEntityTarget(targetEntity);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        RelationGraftPlan plan = validateAndPlan(caster, source, aspect, target.subject());
        if (plan == null) {
            return false;
        }

        boolean success = switch (plan.mode()) {
            case MOB_AGGRO -> applyAggroRedirect(caster, source, aspect, sourceReference, targetEntity, target.subject().displayName());
            case PROJECTILE_RETARGET_ENTITY -> applyProjectileRetargetToEntity(caster, source, aspect, sourceReference, targetEntity, target.subject().displayName());
            case TETHER_ENTITY -> applyTetherToEntity(caster, source, aspect, sourceReference, targetEntity, target.subject().displayName());
            default -> false;
        };
        if (!success) {
            plugin.messages().send(caster, "relation-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.subject().displayName()
            ));
            return false;
        }

        plugin.messages().send(caster, "relation-cast", Map.of(
            "aspect", aspect.displayName(),
            "target", target.subject().displayName()
        ));
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        return true;
    }

    public boolean applyToBlock(Player caster, GraftSubject source, CastSourceReference sourceReference, GraftAspect aspect, Block targetBlock) {
        ResolvedTarget target = resolveBlockTarget(aspect, targetBlock);
        if (target == null) {
            plugin.messages().send(caster, "no-target-found");
            return false;
        }

        RelationGraftPlan plan = validateAndPlan(caster, source, aspect, target.subject());
        if (plan == null) {
            return false;
        }

        boolean success = switch (plan.mode()) {
            case PROJECTILE_RETARGET_LOCATION -> applyProjectileRetargetToLocation(caster, source, aspect, sourceReference, target.location(), target.subject().displayName());
            case CONTAINER_ROUTE -> applyContainerRoute(caster, source, aspect, sourceReference, targetBlock, target.subject().displayName());
            case TETHER_LOCATION -> applyTetherToLocation(caster, source, aspect, sourceReference, target.location(), target.subject().displayName());
            default -> false;
        };
        if (!success) {
            plugin.messages().send(caster, "relation-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.subject().displayName()
            ));
            return false;
        }

        plugin.messages().send(caster, "relation-cast", Map.of(
            "aspect", aspect.displayName(),
            "target", target.subject().displayName()
        ));
        plugin.castSessionManager().session(caster.getUniqueId()).clearSelection();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ActiveContainerRoute route = routeForInventory(event.getDestination());
        if (route == null) {
            return;
        }

        Inventory targetInventory = resolveContainerInventory(route.targetLocation());
        if (targetInventory == null) {
            clearContainerRoute(route.sourceKey());
            return;
        }

        int movedAmount = transferIntoTarget(targetInventory, event.getItem());
        if (movedAmount <= 0) {
            return;
        }

        event.setCancelled(true);
        ItemStack removal = event.getItem().clone();
        removal.setAmount(movedAmount);
        event.getSource().removeItem(removal);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ActiveContainerRoute route = routeForInventory(event.getView().getTopInventory());
        if (route == null) {
            return;
        }

        Inventory targetInventory = resolveContainerInventory(route.targetLocation());
        if (targetInventory == null) {
            clearContainerRoute(route.sourceKey());
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < topSize) {
            ItemStack cursor = event.getCursor();
            if (isEmpty(cursor)) {
                return;
            }
            int requested = event.isRightClick() ? 1 : cursor.getAmount();
            ItemStack routed = cursor.clone();
            routed.setAmount(requested);
            int movedAmount = transferIntoTarget(targetInventory, routed);
            if (movedAmount <= 0) {
                return;
            }

            event.setCancelled(true);
            int remainingAmount = cursor.getAmount() - movedAmount;
            if (remainingAmount <= 0) {
                event.setCursor(new ItemStack(Material.AIR));
            } else {
                ItemStack remaining = cursor.clone();
                remaining.setAmount(remainingAmount);
                event.setCursor(remaining);
            }
            return;
        }

        if (event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (isEmpty(current)) {
            return;
        }

        int movedAmount = transferIntoTarget(targetInventory, current);
        if (movedAmount <= 0) {
            return;
        }

        event.setCancelled(true);
        if (movedAmount >= current.getAmount()) {
            event.setCurrentItem(null);
            return;
        }

        ItemStack remaining = current.clone();
        remaining.setAmount(current.getAmount() - movedAmount);
        event.setCurrentItem(remaining);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        ActiveContainerRoute route = routeForInventory(event.getView().getTopInventory());
        if (route == null) {
            return;
        }

        Inventory targetInventory = resolveContainerInventory(route.targetLocation());
        if (targetInventory == null) {
            clearContainerRoute(route.sourceKey());
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().noneMatch(rawSlot -> rawSlot < topSize)) {
            return;
        }

        int movedAmount = 0;
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            if (entry.getKey() >= topSize) {
                continue;
            }
            movedAmount += transferIntoTarget(targetInventory, entry.getValue());
        }
        if (movedAmount <= 0) {
            return;
        }

        event.setCancelled(true);
        ItemStack oldCursor = event.getOldCursor();
        if (isEmpty(oldCursor) || movedAmount >= oldCursor.getAmount()) {
            event.setCursor(new ItemStack(Material.AIR));
            return;
        }

        ItemStack remaining = oldCursor.clone();
        remaining.setAmount(oldCursor.getAmount() - movedAmount);
        event.setCursor(remaining);
    }

    private RelationGraftPlan validateAndPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateTarget(source, aspect, target);
        if (!compatibility.success()) {
            plugin.messages().send(caster, "target-incompatible", Map.of(
                "target", target.displayName(),
                "aspect", aspect.displayName()
            ));
            return null;
        }

        RelationGraftPlan plan = planner.plan(aspect, source, target).orElse(null);
        if (plan == null) {
            plugin.messages().send(caster, "relation-handler-missing", Map.of(
                "aspect", aspect.displayName(),
                "source", source.displayName(),
                "target", target.displayName()
            ));
            return null;
        }
        return plan;
    }

    private boolean applyAggroRedirect(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Entity targetEntity, String targetName) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (!(sourceEntity instanceof Mob mob) || !(targetEntity instanceof LivingEntity livingTarget)) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        LivingEntity previousTarget = mob.getTarget();
        clearAggroRedirect(mob.getUniqueId(), false);

        UUID sourceId = mob.getUniqueId();
        UUID targetId = livingTarget.getUniqueId();
        UUID previousTargetId = previousTarget == null ? null : previousTarget.getUniqueId();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            Entity currentTarget = plugin.getServer().getEntity(targetId);
            if (!(currentSource instanceof Mob currentMob) || !currentMob.isValid() || !(currentTarget instanceof LivingEntity currentLiving) || !currentLiving.isValid()) {
                clearAggroRedirect(sourceId, true);
                return;
            }
            if (elapsed[0] >= settings.aggroDurationTicks()) {
                clearAggroRedirect(sourceId, true);
                return;
            }

            currentMob.setTarget(currentLiving);
            elapsed[0] += settings.aggroRefreshTicks();
        }, 0L, settings.aggroRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeAggroRedirects.put(sourceId, new ActiveAggroRedirect(previousTargetId, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, settings.aggroDurationTicks(), () -> clearAggroRedirect(sourceId, true));
        mob.setTarget(livingTarget);
        return true;
    }

    private boolean applyProjectileRetargetToEntity(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Entity targetEntity, String targetName) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (!(sourceEntity instanceof Projectile projectile) || !targetEntity.isValid()) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        clearProjectileRetarget(projectile.getUniqueId());

        UUID sourceId = projectile.getUniqueId();
        UUID targetId = targetEntity.getUniqueId();
        boolean wasGlowing = projectile.isGlowing();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            Entity currentTarget = plugin.getServer().getEntity(targetId);
            if (!(currentSource instanceof Projectile currentProjectile) || !currentProjectile.isValid() || currentProjectile.isDead() || currentTarget == null || !currentTarget.isValid()) {
                clearProjectileRetarget(sourceId);
                return;
            }
            if (elapsed[0] >= settings.projectileDurationTicks()) {
                clearProjectileRetarget(sourceId);
                return;
            }

            steerProjectile(currentProjectile, currentTarget.getLocation().add(0.0D, currentTarget.getHeight() * 0.5D, 0.0D), settings.projectileTurnStrength());
            currentProjectile.setGlowing(true);
            elapsed[0] += settings.projectileRefreshTicks();
        }, 0L, settings.projectileRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeProjectileRetargets.put(sourceId, new ActiveProjectileRetarget(wasGlowing, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, settings.projectileDurationTicks(), () -> clearProjectileRetarget(sourceId));
        projectile.setGlowing(true);
        return true;
    }

    private boolean applyProjectileRetargetToLocation(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Location location, String targetName) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (!(sourceEntity instanceof Projectile projectile) || location == null || location.getWorld() == null) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        clearProjectileRetarget(projectile.getUniqueId());

        UUID sourceId = projectile.getUniqueId();
        Location anchor = location.clone();
        boolean wasGlowing = projectile.isGlowing();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            if (!(currentSource instanceof Projectile currentProjectile) || !currentProjectile.isValid() || currentProjectile.isDead() || anchor.getWorld() == null) {
                clearProjectileRetarget(sourceId);
                return;
            }
            if (elapsed[0] >= settings.projectileDurationTicks()) {
                clearProjectileRetarget(sourceId);
                return;
            }

            steerProjectile(currentProjectile, anchor, settings.projectileTurnStrength());
            currentProjectile.setGlowing(true);
            elapsed[0] += settings.projectileRefreshTicks();
        }, 0L, settings.projectileRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeProjectileRetargets.put(sourceId, new ActiveProjectileRetarget(wasGlowing, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, settings.projectileDurationTicks(), () -> clearProjectileRetarget(sourceId));
        projectile.setGlowing(true);
        return true;
    }

    private boolean applyContainerRoute(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Block targetBlock, String targetName) {
        Block sourceBlock = resolveSourceBlock(sourceReference);
        if (sourceBlock == null || !(sourceBlock.getState() instanceof Container)) {
            return false;
        }
        if (!(targetBlock.getState() instanceof Container)) {
            return false;
        }

        String sourceKey = locationKey(sourceBlock.getLocation());
        String targetKey = locationKey(targetBlock.getLocation());
        if (sourceKey.equals(targetKey)) {
            return false;
        }

        clearContainerRoute(sourceKey);
        Location sourceLocation = sourceBlock.getLocation().toBlockLocation();
        Location targetLocation = targetBlock.getLocation().toBlockLocation();
        int durationTicks = plugin.settings().relationGraftSettings().containerDurationTicks();
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearContainerRoute(sourceKey), durationTicks);
        activeTasks.add(cleanupTask);
        activeContainerRoutes.put(sourceKey, new ActiveContainerRoute(sourceKey, sourceLocation, targetLocation, cleanupTask));
        registerActive(caster.getUniqueId(), UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)), aspect.displayName(), source.displayName(), targetName, durationTicks, () -> clearContainerRoute(sourceKey));
        return true;
    }

    private boolean applyTetherToEntity(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Entity targetEntity, String targetName) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (sourceEntity == null || !sourceEntity.isValid() || !targetEntity.isValid()) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        clearTether(sourceEntity.getUniqueId());

        UUID sourceId = sourceEntity.getUniqueId();
        UUID targetId = targetEntity.getUniqueId();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            Entity currentTarget = plugin.getServer().getEntity(targetId);
            if (currentSource == null || !currentSource.isValid() || currentTarget == null || !currentTarget.isValid()) {
                clearTether(sourceId);
                return;
            }
            if (elapsed[0] >= settings.tetherDurationTicks()) {
                clearTether(sourceId);
                return;
            }

            applyTetherPull(currentSource, currentTarget.getLocation().add(0.0D, currentTarget.getHeight() * 0.5D, 0.0D), settings);
            elapsed[0] += settings.tetherRefreshTicks();
        }, 0L, settings.tetherRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeTethers.put(sourceId, new ActiveTether(taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, settings.tetherDurationTicks(), () -> clearTether(sourceId));
        return true;
    }

    private boolean applyTetherToLocation(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Location location, String targetName) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (sourceEntity == null || !sourceEntity.isValid() || location == null || location.getWorld() == null) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        clearTether(sourceEntity.getUniqueId());

        UUID sourceId = sourceEntity.getUniqueId();
        Location anchor = location.clone();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            if (currentSource == null || !currentSource.isValid() || anchor.getWorld() == null) {
                clearTether(sourceId);
                return;
            }
            if (elapsed[0] >= settings.tetherDurationTicks()) {
                clearTether(sourceId);
                return;
            }

            applyTetherPull(currentSource, anchor, settings);
            elapsed[0] += settings.tetherRefreshTicks();
        }, 0L, settings.tetherRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeTethers.put(sourceId, new ActiveTether(taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, settings.tetherDurationTicks(), () -> clearTether(sourceId));
        return true;
    }

    private void registerActive(UUID ownerId, UUID trackingId, String aspectName, String sourceName, String targetName, int durationTicks, Runnable cleanupAction) {
        for (Runnable cleanup : plugin.activeGraftRegistry().register(
            trackingId,
            ownerId,
            GraftFamily.RELATION,
            aspectName,
            sourceName,
            targetName,
            durationTicks,
            cleanupAction
        )) {
            cleanup.run();
        }
    }

    private void steerProjectile(Projectile projectile, Location targetLocation, double turnStrength) {
        Vector toTarget = targetLocation.toVector().subtract(projectile.getLocation().toVector());
        if (toTarget.lengthSquared() <= 0.0001D) {
            return;
        }

        Vector currentVelocity = projectile.getVelocity();
        double speed = Math.max(0.8D, currentVelocity.length());
        Vector desiredVelocity = toTarget.normalize().multiply(speed);
        Vector adjusted = currentVelocity.clone().multiply(1.0D - turnStrength).add(desiredVelocity.multiply(turnStrength));
        if (adjusted.lengthSquared() <= 0.0001D) {
            adjusted = desiredVelocity;
        }
        projectile.setVelocity(adjusted);
    }

    private void applyTetherPull(Entity source, Location anchor, RelationGraftSettings settings) {
        Vector delta = anchor.toVector().subtract(source.getLocation().toVector());
        double distance = delta.length();
        if (distance <= settings.tetherSlackDistance() || distance <= 0.0001D) {
            return;
        }

        double pullMagnitude = Math.min(1.2D, (distance - settings.tetherSlackDistance()) * settings.tetherStrength());
        Vector pull = delta.normalize().multiply(pullMagnitude);
        source.setVelocity(source.getVelocity().multiply(0.7D).add(pull));
    }

    private int transferIntoTarget(Inventory targetInventory, ItemStack itemStack) {
        if (isEmpty(itemStack)) {
            return 0;
        }

        ItemStack offered = itemStack.clone();
        Map<Integer, ItemStack> leftover = targetInventory.addItem(offered);
        int remaining = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
        return offered.getAmount() - remaining;
    }

    private ActiveContainerRoute routeForInventory(Inventory inventory) {
        Location inventoryLocation = inventory.getLocation();
        if (inventoryLocation == null || inventoryLocation.getWorld() == null) {
            return null;
        }
        return activeContainerRoutes.get(locationKey(inventoryLocation));
    }

    private Inventory resolveContainerInventory(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        Block block = location.getBlock();
        if (!(block.getState() instanceof Container container)) {
            return null;
        }
        return container.getInventory();
    }

    private ResolvedTarget resolveEntityTarget(Entity entity) {
        if (entity == null) {
            return null;
        }
        GraftSubject subject = entity instanceof Projectile projectile
            ? plugin.subjectResolver().resolveProjectile(projectile).orElse(null)
            : plugin.subjectResolver().resolveEntity(entity).orElse(null);
        return subject == null ? null : new ResolvedTarget(subject, entity.getLocation());
    }

    private ResolvedTarget resolveBlockTarget(GraftAspect aspect, Block block) {
        if (block == null) {
            return null;
        }

        if (aspect == GraftAspect.TETHER || aspect == GraftAspect.TARGET || aspect == GraftAspect.AGGRO || aspect == GraftAspect.RECEIVER) {
            Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
            GraftSubject locationTarget = plugin.subjectResolver().resolveLocation(center).orElse(null);
            return locationTarget == null ? null : new ResolvedTarget(locationTarget, center);
        }

        GraftSubject subject = plugin.subjectResolver().resolveBlock(block).orElse(null);
        if (subject == null) {
            return null;
        }
        return new ResolvedTarget(subject, block.getLocation().add(0.5D, 0.5D, 0.5D));
    }

    private Entity resolveSourceEntity(CastSourceReference sourceReference) {
        if (sourceReference == null || !sourceReference.hasEntity()) {
            return null;
        }
        return plugin.getServer().getEntity(sourceReference.entityId());
    }

    private Block resolveSourceBlock(CastSourceReference sourceReference) {
        if (sourceReference == null || !sourceReference.hasBlockLocation()) {
            return null;
        }
        Location sourceLocation = sourceReference.blockLocation();
        if (sourceLocation == null || sourceLocation.getWorld() == null) {
            return null;
        }
        return sourceLocation.getBlock();
    }

    private void clearAggroRedirect(UUID sourceId, boolean restorePreviousTarget) {
        ActiveAggroRedirect active = activeAggroRedirects.remove(sourceId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(sourceId);
        cancelTrackedTask(active.task());
        if (!restorePreviousTarget) {
            return;
        }

        Entity sourceEntity = plugin.getServer().getEntity(sourceId);
        if (!(sourceEntity instanceof Mob mob) || !mob.isValid()) {
            return;
        }
        if (active.previousTargetId() == null) {
            mob.setTarget(null);
            return;
        }
        Entity previous = plugin.getServer().getEntity(active.previousTargetId());
        mob.setTarget(previous instanceof LivingEntity living ? living : null);
    }

    private void clearProjectileRetarget(UUID sourceId) {
        ActiveProjectileRetarget active = activeProjectileRetargets.remove(sourceId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(sourceId);
        cancelTrackedTask(active.task());
        Entity sourceEntity = plugin.getServer().getEntity(sourceId);
        if (sourceEntity instanceof Projectile projectile && projectile.isValid()) {
            projectile.setGlowing(active.wasGlowing());
        }
    }

    private void clearTether(UUID sourceId) {
        ActiveTether active = activeTethers.remove(sourceId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(sourceId);
        cancelTrackedTask(active.task());
    }

    private void clearContainerRoute(String sourceKey) {
        ActiveContainerRoute active = activeContainerRoutes.remove(sourceKey);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)));
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

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0;
    }

    private record ResolvedTarget(GraftSubject subject, Location location) {
    }

    private record ActiveAggroRedirect(UUID previousTargetId, BukkitTask task) {
    }

    private record ActiveProjectileRetarget(boolean wasGlowing, BukkitTask task) {
    }

    private record ActiveTether(BukkitTask task) {
    }

    private record ActiveContainerRoute(String sourceKey, Location sourceLocation, Location targetLocation, BukkitTask cleanupTask) {
    }
}
