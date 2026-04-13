package com.graftingplugin.relation;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.AspectEffectConfig;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.config.RelationGraftSettings;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
        if ((plan.mode() == RelationGraftMode.INVENTORY_HANDOFF || plan.mode() == RelationGraftMode.CONTAINER_WITHDRAW)
            && !(targetEntity instanceof Player)) {
            caster.sendMessage("§cThat practical flow only works with a player inventory as the target.");
            return false;
        }

        boolean success = switch (plan.mode()) {
            case MOB_AGGRO -> applyAggroRedirect(caster, source, aspect, sourceReference, targetEntity, target.subject().displayName(), plan.modifier());
            case PROJECTILE_RETARGET_ENTITY -> applyProjectileRetargetToEntity(caster, source, aspect, sourceReference, targetEntity, target.subject().displayName(), plan.modifier());
            case TETHER_ENTITY -> applyTetherToEntity(caster, source, aspect, sourceReference, targetEntity, target.subject().displayName(), plan.modifier());
            case INVENTORY_HANDOFF -> applyInventoryHandoff(caster, source, sourceReference, targetEntity, target.subject().displayName());
            case CONTAINER_WITHDRAW -> applyContainerWithdraw(caster, source, sourceReference, targetEntity, target.subject().displayName());
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

        finishRelationCast(caster, aspect, target.subject(), plan);
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
            case PROJECTILE_RETARGET_LOCATION -> applyProjectileRetargetToLocation(caster, source, aspect, sourceReference, target.location(), target.subject().displayName(), plan.modifier());
            case CONTAINER_ROUTE -> applyContainerRoute(caster, source, aspect, sourceReference, targetBlock, target.subject().displayName(), plan.modifier());
            case TETHER_LOCATION -> applyTetherToLocation(caster, source, aspect, sourceReference, target.location(), target.subject().displayName(), plan.modifier());
            case INVENTORY_DEPOSIT -> applyInventoryDeposit(caster, source, sourceReference, targetBlock, target.subject().displayName());
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

        finishRelationCast(caster, aspect, target.subject(), plan);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        RoutedInventory routed = resolveRoutedInventory(event.getDestination());
        if (routed == null) {
            return;
        }

        Inventory targetInventory = routed.targetInventory();

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
        RoutedInventory routed = resolveRoutedInventory(event.getView().getTopInventory());
        if (routed == null) {
            return;
        }

        Inventory targetInventory = routed.targetInventory();

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < topSize) {
            ItemStack cursor = event.getCursor();
            if (isEmpty(cursor)) {
                return;
            }
            int requested = event.isRightClick() ? 1 : cursor.getAmount();
            ItemStack offered = cursor.clone();
            offered.setAmount(requested);
            int movedAmount = transferIntoTarget(targetInventory, offered);
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
        RoutedInventory routed = resolveRoutedInventory(event.getView().getTopInventory());
        if (routed == null) {
            return;
        }

        Inventory targetInventory = routed.targetInventory();

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

    private void finishRelationCast(Player caster, GraftAspect aspect, GraftSubject target, RelationGraftPlan plan) {
        plugin.messages().send(caster, "relation-cast", Map.of(
            "aspect", aspect.displayName(),
            "target", target.displayName()
        ));
        caster.sendMessage("\u00a77" + describeRelationOutcome(plan));
        caster.sendMessage("§8Your graft setup remains armed. Use §e/graft clear§8 when you want to reset it.");
        caster.playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.2f);
    }

    private RelationGraftPlan validateAndPlan(Player caster, GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateTarget(source, aspect, target);
        if (!compatibility.success()) {
            caster.sendMessage("\u00a7c" + compatibility.message());
            return null;
        }

        RelationGraftPlan plan = planner.plan(aspect, source, target, source.properties()).orElse(null);
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

    private boolean applyAggroRedirect(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Entity targetEntity, String targetName, PropertyModifier mod) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (!(sourceEntity instanceof Mob mob) || !(targetEntity instanceof LivingEntity livingTarget)) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        int aggroDuration = (int) (settings.aggroDurationTicks() * mod.durationMultiplier());
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
                clearAggroRedirect(sourceId, true, "source or target disappeared");
                return;
            }
            if (elapsed[0] >= aggroDuration) {
                clearAggroRedirect(sourceId, true);
                return;
            }

            currentMob.setTarget(currentLiving);
            elapsed[0] += settings.aggroRefreshTicks();
        }, 0L, settings.aggroRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeAggroRedirects.put(sourceId, new ActiveAggroRedirect(caster.getUniqueId(), aspect.displayName(), source.displayName(), targetName, previousTargetId, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, aggroDuration, () -> clearAggroRedirect(sourceId, true));
        mob.setTarget(livingTarget);
        return true;
    }

    private boolean applyProjectileRetargetToEntity(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Entity targetEntity, String targetName, PropertyModifier mod) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (!(sourceEntity instanceof Projectile projectile) || !targetEntity.isValid()) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        int projectileDuration = (int) (settings.projectileDurationTicks() * mod.durationMultiplier());
        double turnStrength = settings.projectileTurnStrength() * mod.intensity();
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
                clearProjectileRetarget(sourceId, "source or target disappeared");
                return;
            }
            if (elapsed[0] >= projectileDuration) {
                clearProjectileRetarget(sourceId);
                return;
            }

            steerProjectile(currentProjectile, currentTarget.getLocation().add(0.0D, currentTarget.getHeight() * 0.5D, 0.0D), turnStrength);
            currentProjectile.setGlowing(true);
            elapsed[0] += settings.projectileRefreshTicks();
        }, 0L, settings.projectileRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeProjectileRetargets.put(sourceId, new ActiveProjectileRetarget(caster.getUniqueId(), aspect.displayName(), source.displayName(), targetName, wasGlowing, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, projectileDuration, () -> clearProjectileRetarget(sourceId));
        projectile.setGlowing(true);
        return true;
    }

    private boolean applyProjectileRetargetToLocation(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Location location, String targetName, PropertyModifier mod) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (!(sourceEntity instanceof Projectile projectile) || location == null || location.getWorld() == null) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        int projectileDuration = (int) (settings.projectileDurationTicks() * mod.durationMultiplier());
        double turnStrength = settings.projectileTurnStrength() * mod.intensity();
        clearProjectileRetarget(projectile.getUniqueId());

        UUID sourceId = projectile.getUniqueId();
        Location anchor = location.clone();
        boolean wasGlowing = projectile.isGlowing();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            if (!(currentSource instanceof Projectile currentProjectile) || !currentProjectile.isValid() || currentProjectile.isDead() || anchor.getWorld() == null) {
                clearProjectileRetarget(sourceId, "source projectile disappeared");
                return;
            }
            if (elapsed[0] >= projectileDuration) {
                clearProjectileRetarget(sourceId);
                return;
            }

            steerProjectile(currentProjectile, anchor, turnStrength);
            currentProjectile.setGlowing(true);
            elapsed[0] += settings.projectileRefreshTicks();
        }, 0L, settings.projectileRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeProjectileRetargets.put(sourceId, new ActiveProjectileRetarget(caster.getUniqueId(), aspect.displayName(), source.displayName(), targetName, wasGlowing, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, projectileDuration, () -> clearProjectileRetarget(sourceId));
        projectile.setGlowing(true);
        return true;
    }

    private boolean applyContainerRoute(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Block targetBlock, String targetName, PropertyModifier mod) {
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
        int durationTicks = Math.max(1, (int) Math.round(plugin.settings().relationGraftSettings().containerDurationTicks() * mod.durationMultiplier()));
        BukkitTask cleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> clearContainerRoute(sourceKey), durationTicks);
        activeTasks.add(cleanupTask);
        activeContainerRoutes.put(sourceKey, new ActiveContainerRoute(sourceKey, caster.getUniqueId(), aspect.displayName(), source.displayName(), targetName, sourceLocation, targetLocation, cleanupTask));
        registerActive(caster.getUniqueId(), UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)), aspect.displayName(), source.displayName(), targetName, durationTicks, () -> clearContainerRoute(sourceKey));
        return true;
    }

    private boolean applyTetherToEntity(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Entity targetEntity, String targetName, PropertyModifier mod) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (sourceEntity == null || !sourceEntity.isValid() || !targetEntity.isValid()) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        int tetherDuration = (int) (settings.tetherDurationTicks() * mod.durationMultiplier());
        double tetherStrength = settings.tetherStrength() * mod.intensity();
        clearTether(sourceEntity.getUniqueId());

        UUID sourceId = sourceEntity.getUniqueId();
        UUID targetId = targetEntity.getUniqueId();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            Entity currentTarget = plugin.getServer().getEntity(targetId);
            if (currentSource == null || !currentSource.isValid() || currentTarget == null || !currentTarget.isValid()) {
                clearTether(sourceId, "source or target disappeared");
                return;
            }
            if (elapsed[0] >= tetherDuration) {
                clearTether(sourceId);
                return;
            }

            applyTetherPull(currentSource, currentTarget.getLocation().add(0.0D, currentTarget.getHeight() * 0.5D, 0.0D), settings, tetherStrength);
            elapsed[0] += settings.tetherRefreshTicks();
        }, 0L, settings.tetherRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeTethers.put(sourceId, new ActiveTether(caster.getUniqueId(), aspect.displayName(), source.displayName(), targetName, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, tetherDuration, () -> clearTether(sourceId));
        return true;
    }

    private boolean applyTetherToLocation(Player caster, GraftSubject source, GraftAspect aspect, CastSourceReference sourceReference, Location location, String targetName, PropertyModifier mod) {
        Entity sourceEntity = resolveSourceEntity(sourceReference);
        if (sourceEntity == null || !sourceEntity.isValid() || location == null || location.getWorld() == null) {
            return false;
        }

        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        int tetherDuration = (int) (settings.tetherDurationTicks() * mod.durationMultiplier());
        double tetherStrength = settings.tetherStrength() * mod.intensity();
        clearTether(sourceEntity.getUniqueId());

        UUID sourceId = sourceEntity.getUniqueId();
        Location anchor = location.clone();
        int[] elapsed = {0};
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Entity currentSource = plugin.getServer().getEntity(sourceId);
            if (currentSource == null || !currentSource.isValid() || anchor.getWorld() == null) {
                clearTether(sourceId, "source disappeared");
                return;
            }
            if (elapsed[0] >= tetherDuration) {
                clearTether(sourceId);
                return;
            }

            applyTetherPull(currentSource, anchor, settings, tetherStrength);
            elapsed[0] += settings.tetherRefreshTicks();
        }, 0L, settings.tetherRefreshTicks());
        activeTasks.add(taskHolder[0]);
        activeTethers.put(sourceId, new ActiveTether(caster.getUniqueId(), aspect.displayName(), source.displayName(), targetName, taskHolder[0]));
        registerActive(caster.getUniqueId(), sourceId, aspect.displayName(), source.displayName(), targetName, tetherDuration, () -> clearTether(sourceId));
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

    private String describeRelationOutcome(RelationGraftPlan plan) {
        RelationGraftSettings settings = plugin.settings().relationGraftSettings();
        PropertyModifier mod = plan.modifier();
        return switch (plan.mode()) {
            case MOB_AGGRO -> "Aggro redirected for " + formatSeconds((int) Math.round(settings.aggroDurationTicks() * mod.durationMultiplier())) + ".";
            case PROJECTILE_RETARGET_ENTITY, PROJECTILE_RETARGET_LOCATION -> "Projectile retarget active for " + formatSeconds((int) Math.round(settings.projectileDurationTicks() * mod.durationMultiplier())) + " with turn strength " + formatDecimal(settings.projectileTurnStrength() * mod.intensity()) + ".";
            case CONTAINER_ROUTE -> "Container route active for " + formatSeconds((int) Math.round(settings.containerDurationTicks() * mod.durationMultiplier())) + ".";
            case TETHER_ENTITY, TETHER_LOCATION -> "Tether active for " + formatSeconds((int) Math.round(settings.tetherDurationTicks() * mod.durationMultiplier())) + " with pull strength " + formatDecimal(settings.tetherStrength() * mod.intensity()) + ".";
            case INVENTORY_DEPOSIT -> "Item deposited into container.";
            case INVENTORY_HANDOFF -> "Item handed into the target player's inventory.";
            case CONTAINER_WITHDRAW -> "First available stack withdrawn from the container into the target player's inventory.";
            case SLOT_SWAP -> "Items swapped between slots.";
        };
    }

    private void notifyOwnerEnded(UUID ownerId, String aspectName, String sourceName, String targetName, String reason) {
        if (reason == null || ownerId == null) {
            return;
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        owner.sendMessage("\u00a77" + aspectName + " from " + sourceName + " to " + targetName + " ended: " + reason + ".");
    }

    private String formatSeconds(int ticks) {
        return formatDecimal(ticks / 20.0D) + "s";
    }

    private String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
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

    private void applyTetherPull(Entity source, Location anchor, RelationGraftSettings settings, double tetherStrength) {
        Vector delta = anchor.toVector().subtract(source.getLocation().toVector());
        double distance = delta.length();
        if (distance <= settings.tetherSlackDistance() || distance <= 0.0001D) {
            return;
        }

        double pullMagnitude = Math.min(1.2D, (distance - settings.tetherSlackDistance()) * tetherStrength);
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

    private RoutedInventory resolveRoutedInventory(Inventory inventory) {
        ActiveContainerRoute route = routeForInventory(inventory);
        if (route == null) {
            return null;
        }
        Inventory targetInventory = resolveContainerInventory(route.targetLocation());
        if (targetInventory == null) {
            clearContainerRoute(route.sourceKey(), "source or target container disappeared");
            return null;
        }
        return new RoutedInventory(route, targetInventory);
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

        if (aspect == GraftAspect.TETHER || aspect == GraftAspect.TARGET || aspect == GraftAspect.AGGRO) {
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

    public boolean applySlotToSlot(Player caster, GraftSubject source, int sourceSlot, GraftAspect aspect, int targetSlot) {
        ItemStack sourceItem = caster.getInventory().getStorageContents()[sourceSlot];
        ItemStack targetItem = caster.getInventory().getStorageContents()[targetSlot];
        if (sourceItem == null || sourceItem.getType().isAir()) {
            caster.sendMessage("\u00a7cSource slot is empty.");
            return false;
        }
        caster.getInventory().setItem(sourceSlot, targetItem);
        caster.getInventory().setItem(targetSlot, sourceItem);
        caster.updateInventory();
        plugin.messages().send(caster, "slot-swap-applied", java.util.Map.of(
            "aspect", aspect.displayName(),
            "source", source.displayName()
        ));
        caster.sendMessage("§8Your graft setup remains armed. Use §e/graft clear§8 when you want to reset it.");
        caster.playSound(caster.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        return true;
    }

    private boolean applyInventoryDeposit(Player caster, GraftSubject source, CastSourceReference sourceReference, Block targetBlock, String targetName) {
        if (!sourceReference.hasInventorySlot()) {
            return false;
        }
        if (!(targetBlock.getState() instanceof Container container)) {
            return false;
        }
        int slot = sourceReference.inventorySlot();
        ItemStack item = caster.getInventory().getStorageContents()[slot];
        if (item == null || item.getType().isAir()) {
            caster.sendMessage("\u00a7cThat inventory slot is now empty.");
            return false;
        }
        Inventory targetInventory = container.getInventory();
        int moved = transferIntoTarget(targetInventory, item);
        if (moved <= 0) {
            caster.sendMessage("\u00a7cThe target container has no room for " + source.displayName() + ".");
            return false;
        }
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - moved);
        if (remaining.getAmount() <= 0) {
            caster.getInventory().setItem(slot, null);
        } else {
            caster.getInventory().setItem(slot, remaining);
        }
        caster.updateInventory();
        plugin.messages().send(caster, "item-deposited", java.util.Map.of(
            "item", source.displayName(),
            "target", targetName
        ));
        caster.playSound(caster.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
        return true;
    }

    private boolean applyInventoryHandoff(Player caster, GraftSubject source, CastSourceReference sourceReference, Entity targetEntity, String targetName) {
        if (!sourceReference.hasInventorySlot() || !(targetEntity instanceof Player targetPlayer)) {
            return false;
        }
        int slot = sourceReference.inventorySlot();
        ItemStack item = caster.getInventory().getStorageContents()[slot];
        if (item == null || item.getType().isAir()) {
            caster.sendMessage("\u00a7cThat inventory slot is now empty.");
            return false;
        }
        int moved = transferIntoTarget(targetPlayer.getInventory(), item);
        if (moved <= 0) {
            caster.sendMessage("\u00a7c" + targetPlayer.getName() + " has no room for " + source.displayName() + ".");
            return false;
        }
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - moved);
        if (remaining.getAmount() <= 0) {
            caster.getInventory().setItem(slot, null);
        } else {
            caster.getInventory().setItem(slot, remaining);
        }
        caster.updateInventory();
        targetPlayer.updateInventory();
        plugin.messages().send(caster, "item-handed-off", java.util.Map.of(
            "item", source.displayName(),
            "target", targetPlayer.getName()
        ));
        targetPlayer.sendMessage("\u00a7b" + source.displayName() + " \u00a77was grafted into your inventory by \u00a76" + caster.getName() + "\u00a77.");
        caster.playSound(caster.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.15f);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.05f);
        return true;
    }

    private boolean applyContainerWithdraw(Player caster, GraftSubject source, CastSourceReference sourceReference, Entity targetEntity, String targetName) {
        if (!(targetEntity instanceof Player targetPlayer)) {
            return false;
        }
        Block sourceBlock = resolveSourceBlock(sourceReference);
        if (sourceBlock == null || !(sourceBlock.getState() instanceof Container container)) {
            return false;
        }
        Inventory containerInventory = container.getInventory();
        int firstFilledSlot = firstFilledSlot(containerInventory);
        if (firstFilledSlot < 0) {
            caster.sendMessage("\u00a7cThat container has nothing to withdraw.");
            return false;
        }
        ItemStack item = containerInventory.getItem(firstFilledSlot);
        if (item == null || item.getType().isAir()) {
            return false;
        }
        int moved = transferIntoTarget(targetPlayer.getInventory(), item);
        if (moved <= 0) {
            caster.sendMessage("\u00a7c" + targetPlayer.getName() + " has no room for the withdrawn stack.");
            return false;
        }
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - moved);
        containerInventory.setItem(firstFilledSlot, remaining.getAmount() <= 0 ? null : remaining);
        targetPlayer.updateInventory();
        plugin.messages().send(caster, "container-withdrawn", java.util.Map.of(
            "source", source.displayName(),
            "target", targetPlayer.getName()
        ));
        targetPlayer.sendMessage("\u00a7bA stack from \u00a76" + source.displayName() + " \u00a77was grafted into your inventory by \u00a76" + caster.getName() + "\u00a77.");
        caster.playSound(caster.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.95f);
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        return true;
    }

    private int firstFilledSlot(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isEmpty(item)) {
                return slot;
            }
        }
        return -1;
    }

    private void clearAggroRedirect(UUID sourceId, boolean restorePreviousTarget) {
        clearAggroRedirect(sourceId, restorePreviousTarget, null);
    }

    private void clearAggroRedirect(UUID sourceId, boolean restorePreviousTarget, String reason) {
        ActiveAggroRedirect active = activeAggroRedirects.remove(sourceId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(sourceId);
        cancelTrackedTask(active.task());
        notifyOwnerEnded(active.ownerId(), active.aspectName(), active.sourceName(), active.targetName(), reason);
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
        clearProjectileRetarget(sourceId, null);
    }

    private void clearProjectileRetarget(UUID sourceId, String reason) {
        ActiveProjectileRetarget active = activeProjectileRetargets.remove(sourceId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(sourceId);
        cancelTrackedTask(active.task());
        notifyOwnerEnded(active.ownerId(), active.aspectName(), active.sourceName(), active.targetName(), reason);
        Entity sourceEntity = plugin.getServer().getEntity(sourceId);
        if (sourceEntity instanceof Projectile projectile && projectile.isValid()) {
            projectile.setGlowing(active.wasGlowing());
        }
    }

    private void clearTether(UUID sourceId) {
        clearTether(sourceId, null);
    }

    private void clearTether(UUID sourceId, String reason) {
        ActiveTether active = activeTethers.remove(sourceId);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(sourceId);
        cancelTrackedTask(active.task());
        notifyOwnerEnded(active.ownerId(), active.aspectName(), active.sourceName(), active.targetName(), reason);
    }

    private void clearContainerRoute(String sourceKey) {
        clearContainerRoute(sourceKey, null);
    }

    private void clearContainerRoute(String sourceKey, String reason) {
        ActiveContainerRoute active = activeContainerRoutes.remove(sourceKey);
        if (active == null) {
            return;
        }
        plugin.activeGraftRegistry().unregister(UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)));
        cancelTrackedTask(active.cleanupTask());
        notifyOwnerEnded(active.ownerId(), active.aspectName(), active.sourceName(), active.targetName(), reason);
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

    private record RoutedInventory(ActiveContainerRoute route, Inventory targetInventory) {
    }

    private record ActiveAggroRedirect(UUID ownerId, String aspectName, String sourceName, String targetName, UUID previousTargetId, BukkitTask task) {
    }

    private record ActiveProjectileRetarget(UUID ownerId, String aspectName, String sourceName, String targetName, boolean wasGlowing, BukkitTask task) {
    }

    private record ActiveTether(UUID ownerId, String aspectName, String sourceName, String targetName, BukkitTask task) {
    }

    private record ActiveContainerRoute(String sourceKey, UUID ownerId, String aspectName, String sourceName, String targetName, Location sourceLocation, Location targetLocation, BukkitTask cleanupTask) {
    }
}
