package com.graftingplugin.focus;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.cast.CastSession;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.subject.GraftSubject;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class FocusInteractionListener implements Listener {

    private final GraftingPlugin plugin;

    public FocusInteractionListener(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.focusItemService().isFocus(player.getInventory().getItemInMainHand())) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            selectSource(player, event.getClickedBlock(), null);
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (readyForCast(player)) {
                applyCast(player, event.getClickedBlock(), null);
            } else {
                selectSource(player, event.getClickedBlock(), null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.focusItemService().isFocus(player.getInventory().getItemInMainHand())) {
            return;
        }

        event.setCancelled(true);
        if (readyForCast(player)) {
            applyCast(player, null, event.getRightClicked());
        } else {
            selectSource(player, null, event.getRightClicked());
        }
    }

    private void selectSource(Player player, Block clickedBlock, Entity clickedEntity) {
        ResolvedSource source = resolveConcreteSource(player, clickedBlock, clickedEntity);
        if (source == null) {
            plugin.messages().send(player, "no-source-found");
            return;
        }
        plugin.castSelectionService().armSource(player, source.subject(), source.reference());
    }

    private void applyCast(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.source() == null) {
            plugin.messages().send(player, "no-source-selected");
            return;
        }
        if (session.selectedAspect() == null) {
            plugin.messages().send(player, "no-aspect-selected");
            return;
        }

        switch (session.family()) {
            case STATE -> applyStateTransfer(player, clickedBlock, clickedEntity);
            case RELATION -> applyRelationGraft(player, clickedBlock, clickedEntity);
            case TOPOLOGY -> applyTopologyGraft(player, clickedBlock, clickedEntity);
            case SEQUENCE -> applySequenceTamper(player, clickedBlock, clickedEntity);
            default -> plugin.messages().send(player, "family-runtime-pending", "family", session.family().displayName());
        }
    }

    private void applyStateTransfer(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (player.isSneaking()) {
            Location center = resolveAreaCenter(player, clickedBlock, clickedEntity);
            if (center == null) {
                plugin.messages().send(player, "no-target-found");
                return;
            }
            plugin.stateTransferService().applyToArea(player, session.source(), session.selectedAspect(), center);
            return;
        }

        if (clickedEntity != null) {
            if (clickedEntity instanceof Projectile projectile) {
                plugin.stateTransferService().applyToProjectile(player, session.source(), session.selectedAspect(), projectile);
                return;
            }
            plugin.stateTransferService().applyToEntity(player, session.source(), session.selectedAspect(), clickedEntity);
            return;
        }
        if (clickedBlock != null) {
            plugin.stateTransferService().applyToBlock(player, session.source(), session.selectedAspect(), clickedBlock);
            return;
        }

        FocusTarget focusTarget = resolveLookTarget(player);
        if (focusTarget == null) {
            plugin.messages().send(player, "no-target-found");
            return;
        }
        if (focusTarget.entity() instanceof Projectile projectile) {
            plugin.stateTransferService().applyToProjectile(player, session.source(), session.selectedAspect(), projectile);
            return;
        }
        if (focusTarget.entity() != null) {
            plugin.stateTransferService().applyToEntity(player, session.source(), session.selectedAspect(), focusTarget.entity());
            return;
        }
        plugin.stateTransferService().applyToBlock(player, session.source(), session.selectedAspect(), focusTarget.block());
    }

    private void applyRelationGraft(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (clickedEntity != null) {
            plugin.relationGraftService().applyToEntity(player, session.source(), session.sourceReference(), session.selectedAspect(), clickedEntity);
            return;
        }
        if (clickedBlock != null) {
            plugin.relationGraftService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), clickedBlock);
            return;
        }

        FocusTarget focusTarget = resolveLookTarget(player);
        if (focusTarget == null) {
            plugin.messages().send(player, "no-target-found");
            return;
        }
        if (focusTarget.entity() != null) {
            plugin.relationGraftService().applyToEntity(player, session.source(), session.sourceReference(), session.selectedAspect(), focusTarget.entity());
            return;
        }
        plugin.relationGraftService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), focusTarget.block());
    }

    private void applyTopologyGraft(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (clickedBlock != null) {
            plugin.topologyGraftService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), clickedBlock);
            return;
        }
        if (clickedEntity != null) {
            plugin.topologyGraftService().applyToLocation(player, session.source(), session.sourceReference(), session.selectedAspect(), clickedEntity.getLocation().add(0.0D, clickedEntity.getHeight() * 0.5D, 0.0D));
            return;
        }

        FocusTarget focusTarget = resolveLookTarget(player);
        if (focusTarget == null) {
            plugin.messages().send(player, "no-target-found");
            return;
        }
        if (focusTarget.entity() != null) {
            plugin.topologyGraftService().applyToLocation(player, session.source(), session.sourceReference(), session.selectedAspect(), focusTarget.entity().getLocation().add(0.0D, focusTarget.entity().getHeight() * 0.5D, 0.0D));
            return;
        }
        plugin.topologyGraftService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), focusTarget.block());
    }

    private void applySequenceTamper(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.selectedAspect() == GraftAspect.ON_HIT) {
            if (clickedEntity instanceof Projectile projectile) {
                plugin.sequenceTamperService().applyToProjectile(player, session.source(), session.sourceReference(), session.selectedAspect(), projectile);
                return;
            }

            FocusTarget focusTarget = resolveLookTarget(player);
            if (focusTarget != null && focusTarget.entity() instanceof Projectile projectile) {
                plugin.sequenceTamperService().applyToProjectile(player, session.source(), session.sourceReference(), session.selectedAspect(), projectile);
                return;
            }
            plugin.messages().send(player, "no-target-found");
            return;
        }

        if (clickedBlock != null) {
            plugin.sequenceTamperService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), clickedBlock);
            return;
        }

        FocusTarget focusTarget = resolveLookTarget(player);
        if (focusTarget == null) {
            plugin.messages().send(player, "no-target-found");
            return;
        }
        if (focusTarget.block() != null) {
            plugin.sequenceTamperService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), focusTarget.block());
            return;
        }
        if (focusTarget.entity() instanceof Projectile projectile) {
            plugin.sequenceTamperService().applyToProjectile(player, session.source(), session.sourceReference(), session.selectedAspect(), projectile);
            return;
        }
        plugin.messages().send(player, "no-target-found");
    }

    private ResolvedSource resolveConcreteSource(Player player, Block clickedBlock, Entity clickedEntity) {
        if (clickedEntity instanceof Projectile projectile) {
            GraftSubject subject = plugin.subjectResolver().resolveProjectile(projectile).orElse(null);
            return subject == null ? null : new ResolvedSource(subject, CastSourceReference.ofEntity(projectile));
        }
        if (clickedEntity != null) {
            GraftSubject subject = plugin.subjectResolver().resolveEntity(clickedEntity).orElse(null);
            return subject == null ? null : new ResolvedSource(subject, CastSourceReference.ofEntity(clickedEntity));
        }
        if (clickedBlock != null) {
            GraftSubject subject = plugin.subjectResolver().resolveBlock(clickedBlock).orElse(null);
            return subject == null ? null : new ResolvedSource(subject, CastSourceReference.ofBlock(clickedBlock));
        }

        FocusTarget lookTarget = resolveLookTarget(player);
        if (lookTarget != null) {
            if (lookTarget.entity() != null) {
                return new ResolvedSource(lookTarget.subject(), CastSourceReference.ofEntity(lookTarget.entity()));
            }
            return new ResolvedSource(lookTarget.subject(), CastSourceReference.ofBlock(lookTarget.block()));
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !plugin.focusItemService().isFocus(offhand)) {
            GraftSubject subject = plugin.subjectResolver().resolveItem(offhand).orElse(null);
            return subject == null ? null : new ResolvedSource(subject, CastSourceReference.none());
        }
        return null;
    }

    private Location resolveAreaCenter(Player player, Block clickedBlock, Entity clickedEntity) {
        if (clickedEntity != null) {
            return clickedEntity.getLocation();
        }
        if (clickedBlock != null) {
            return clickedBlock.getLocation().add(0.5D, 0.5D, 0.5D);
        }

        FocusTarget lookTarget = resolveLookTarget(player);
        if (lookTarget == null) {
            return null;
        }
        if (lookTarget.entity() != null) {
            return lookTarget.entity().getLocation();
        }
        return lookTarget.block().getLocation().add(0.5D, 0.5D, 0.5D);
    }

    private boolean readyForCast(Player player) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        return session.source() != null && session.selectedAspect() != null;
    }

    private FocusTarget resolveLookTarget(Player player) {
        Entity targetEntity = player.getTargetEntity(plugin.settings().interactionRange());
        if (targetEntity != null && !targetEntity.equals(player)) {
            GraftSubject subject = targetEntity instanceof Projectile projectile
                ? plugin.subjectResolver().resolveProjectile(projectile).orElse(null)
                : plugin.subjectResolver().resolveEntity(targetEntity).orElse(null);
            if (subject != null) {
                return new FocusTarget(subject, targetEntity, null);
            }
        }

        Block targetBlock = player.getTargetBlockExact(plugin.settings().interactionRange());
        if (targetBlock == null) {
            return null;
        }
        GraftSubject subject = plugin.subjectResolver().resolveBlock(targetBlock).orElse(null);
        if (subject == null) {
            return null;
        }
        return new FocusTarget(subject, null, targetBlock);
    }

    private record FocusTarget(GraftSubject subject, Entity entity, Block block) {
    }

    private record ResolvedSource(GraftSubject subject, CastSourceReference reference) {
    }
}
