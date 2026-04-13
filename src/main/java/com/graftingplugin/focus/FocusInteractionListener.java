package com.graftingplugin.focus;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.gui.ConceptCatalogGui;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.cast.CastSession;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.state.StatusTransferSupport;
import com.graftingplugin.subject.GraftSubject;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class FocusInteractionListener implements Listener {

    private static final long DOUBLE_CLICK_THRESHOLD_MS = 400L;

    private final GraftingPlugin plugin;
    private final Map<UUID, Long> lastRightClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> debugEnabled = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> castReentryGuard = ConcurrentHashMap.newKeySet();

    public FocusInteractionListener(GraftingPlugin plugin) {
        this.plugin = plugin;
    }


    public void logStartup() {
        if (!plugin.settings().focusDebugEnabledByDefault()) {
            plugin.getLogger().info("GraftingPlugin focus listener active. Debug logging is disabled by default.");
            return;
        }
        plugin.getLogger().info("[GraftDebug] ============================================");
        plugin.getLogger().info("[GraftDebug] FocusInteractionListener REGISTERED and ACTIVE");
        plugin.getLogger().info("[GraftDebug] Control scheme:");
        plugin.getLogger().info("[GraftDebug]   Right-Click = Select source");
        plugin.getLogger().info("[GraftDebug]   Left-Click = Cast onto target");
        plugin.getLogger().info("[GraftDebug]   Shift+Right-Click = Cycle aspect / Select fluid");
        plugin.getLogger().info("[GraftDebug]   Shift+Left-Click air = Clear selection");
        plugin.getLogger().info("[GraftDebug]   Double Right-Click air = Self as source");
        plugin.getLogger().info("[GraftDebug]   Config default debug = true");
        plugin.getLogger().info("[GraftDebug] ============================================");
    }

    public boolean isDebugEnabled(Player player) {
        return debugEnabled.getOrDefault(player.getUniqueId(), false);
    }

    public void setDebugEnabled(Player player, boolean enabled) {
        if (enabled) {
            debugEnabled.put(player.getUniqueId(), true);
        } else {
            debugEnabled.remove(player.getUniqueId());
        }
    }

    public void cycleAspectSelection(Player player) {
        cycleAspect(player);
    }

    public void selectSelfSourceCommand(Player player) {
        selectSelfSource(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.settings().focusDebugEnabledByDefault()) {
            return;
        }
        Player player = event.getPlayer();
        setDebugEnabled(player, true);
        player.sendMessage("§aGraft debug logging is enabled by default on this server. Use §e/graft debug§a to toggle it.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastRightClickTime.remove(playerId);
        debugEnabled.remove(playerId);
        castReentryGuard.remove(playerId);
        plugin.castSessionManager().clear(playerId);
        plugin.conceptCatalogGui().clearPendingAction(event.getPlayer());
    }

    private void handleLeftClick(Player player, Action action, Block targetBlock, CastSession session) {
        ConceptCatalogGui.PendingConceptAction pending = plugin.conceptCatalogGui().getPendingAction(player);
        if (pending != null) {
            if (player.isSneaking() && action == Action.LEFT_CLICK_AIR) {
                plugin.conceptCatalogGui().clearPendingAction(player);
                plugin.messages().send(player, "conceptual-cast-cancelled");
                sendActionBar(player, "\u00a78Conceptual casting cancelled");
                return;
            }
            Location targetLoc = pending.type().placementStyle() == com.graftingplugin.conceptgraft.ConceptPlacementStyle.DUAL_ANCHOR_BLOCKS
                ? (targetBlock != null ? targetBlock.getLocation().add(0.5, 0.5, 0.5) : player.getLocation())
                : (targetBlock != null ? targetBlock.getLocation().add(0.5, 1.0, 0.5) : player.getLocation());
            boolean activated;
            if (!pending.type().requiresTwoAnchors()) {
                activated = plugin.conceptGraftService().activateZone(player, pending.type(), targetLoc);
                if (activated) {
                    plugin.conceptCatalogGui().clearPendingAction(player);
                }
                return;
            }
            if (pending.type().firstAnchorComesFromCaster()) {
                activated = plugin.conceptGraftService().activateAnchoredGraft(player, pending.type(), pending.firstAnchor(), targetLoc);
                if (activated) {
                    plugin.conceptCatalogGui().clearPendingAction(player);
                }
                return;
            }
            if (pending.firstAnchor() == null) {
                if (targetBlock != null && targetBlock.getType() == org.bukkit.Material.ENDER_CHEST) {
                    plugin.messages().send(player, "conceptual-threshold-no-ender-chest");
                    sendActionBar(player, "§cChoose a normal container, not an ender chest");
                    return;
                }
                if (!(targetBlock != null && targetBlock.getState() instanceof Container)) {
                    plugin.messages().send(player, "conceptual-threshold-needs-container");
                    sendActionBar(player, "\u00a7cChoose a container as the source threshold");
                    return;
                }
                plugin.conceptCatalogGui().setPendingAction(player, pending.withFirstAnchor(targetLoc));
                plugin.messages().send(player, "conceptual-threshold-source-fixed");
                plugin.messages().send(player, "conceptual-threshold-source-fixed-hint");
                sendActionBar(player, "\u00a75Source threshold fixed \u00a78| \u00a7dchoose destination");
                return;
            }
            activated = plugin.conceptGraftService().activateAnchoredGraft(player, pending.type(), pending.firstAnchor(), targetLoc);
            if (activated) {
                plugin.conceptCatalogGui().clearPendingAction(player);
            }
            return;
        }

        if (player.isSneaking() && action == Action.LEFT_CLICK_AIR) {
            debugLog(player, "Shift+Left-Click: CLEARING selection (source was %s)", session.source() != null ? session.source().displayName() : "none");
            clearSelection(player);
            return;
        }

        debugLog(player, "Left-Click: casting onto target (block=%s)", targetBlock);
        if (readyForCast(player)) {
            applyCast(player, targetBlock, null);
            return;
        }
        sendNotReadyFeedback(player, session);
    }

    private void handleRightClick(Player player, Block targetBlock, CastSession session) {
        if (player.isSneaking()) {
            if (session.source() != null) {
                debugLog(player, "Shift+Right-Click: cycling aspect (source=%s)", session.source().displayName());
                cycleAspect(player);
            } else {
                debugLog(player, "Shift+Right-Click: selecting fluid source (no source active)");
                selectFluidSource(player);
            }
            return;
        }

        long now = System.currentTimeMillis();
        Long lastClick = lastRightClickTime.put(player.getUniqueId(), now);
        boolean aimedAtAir = targetBlock == null && player.getTargetEntity(plugin.settings().interactionRange()) == null;
        if (aimedAtAir && lastClick != null && (now - lastClick) < DOUBLE_CLICK_THRESHOLD_MS) {
            lastRightClickTime.remove(player.getUniqueId());
            debugLog(player, "Double Right-Click: selecting self as source");
            selectSelfSource(player);
            return;
        }

        debugLog(player, "Right-Click: selecting source (block=%s)", targetBlock);
        selectSource(player, targetBlock, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!isHoldingFocus(player)) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }

        event.setCancelled(true);
        boolean isShift = player.isSneaking();
        Block targetBlock = event.getClickedBlock();
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());

        debugLog(player, "=== INTERACT === action=%s shift=%s block=%s source=%s aspect=%s mode=%s",
            action, isShift, targetBlock,
            session.source() != null ? session.source().displayName() : "null",
            session.selectedAspect() != null ? session.selectedAspect().displayName() : "null",
            session.effectiveFamily().displayName());

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handleLeftClick(player, action, targetBlock, session);
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(player, targetBlock, session);
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!isHoldingFocus(player)) {
            return;
        }

        event.setCancelled(true);
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (player.isSneaking() && session.source() != null) {
            debugLog(player, "Shift+Right-Click Entity: cycling aspect (source=%s)", session.source().displayName());
            cycleAspect(player);
            return;
        }

        lastRightClickTime.put(player.getUniqueId(), System.currentTimeMillis());
        debugLog(player, "Right-Click Entity: selecting source (entity=%s)", event.getRightClicked().getType());
        selectSource(player, null, event.getRightClicked());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!isHoldingFocus(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isHoldingFocus(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isHoldingFocus(player)) {
            return;
        }
        if (event.getEntity().equals(player) || !castReentryGuard.add(player.getUniqueId())) {
            return;
        }

        try {
            event.setCancelled(true);
            CastSession session = plugin.castSessionManager().session(player.getUniqueId());

            debugLog(player, "Left-Click Entity: casting onto entity target=%s", event.getEntity().getType());
            if (readyForCast(player)) {
                applyCast(player, null, event.getEntity());
                return;
            }

            sendNotReadyFeedback(player, session);
        } finally {
            castReentryGuard.remove(player.getUniqueId());
        }
    }


    private void clearSelection(Player player) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        String prevSource = session.source() != null ? session.source().displayName() : "none";
        String prevAspect = session.selectedAspect() != null ? session.selectedAspect().displayName() : "none";
        String prevMode = session.effectiveFamily().displayName();
        session.clearSelection();
        debugLog(player, "CLEAR: was source=%s, aspect=%s, mode=%s", prevSource, prevAspect, prevMode);
        plugin.messages().send(player, "selection-cleared", "family", session.family().displayName());
        sendActionBar(player, "\u00a77Selection cleared \u00a78(was " + prevMode + ": " + prevAspect + " from " + prevSource + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.5f);
    }


    private void cycleAspect(Player player) {
        clearPendingConceptualForPracticalSwitch(player);
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.source() == null) {
            sendActionBar(player, "\u00a7cNo source selected \u2014 Right-Click something first");
            return;
        }

        GraftFamily family = session.effectiveFamily();
        List<GraftAspect> aspects = plugin.compatibilityValidator().compatibleSourceAspects(family, session.source());
        if (aspects.isEmpty()) {
            sendActionBar(player, "\u00a7cNo compatible aspects for " + family.displayName() + " from " + session.source().displayName());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        GraftAspect current = session.selectedAspect();
        if (current == GraftAspect.STATUS) {
            List<org.bukkit.potion.PotionEffectType> availableEffects = StatusTransferSupport.availableEffects(plugin, session.sourceReference());
            if (!availableEffects.isEmpty()) {
                org.bukkit.potion.PotionEffectType currentEffect = StatusTransferSupport.resolveEffect(plugin, session.sourceReference(), session.selectedStatusEffectKey());
                int currentEffectIndex = availableEffects.indexOf(currentEffect);
                if (currentEffectIndex >= 0 && currentEffectIndex < availableEffects.size() - 1) {
                    org.bukkit.potion.PotionEffectType nextEffect = availableEffects.get(currentEffectIndex + 1);
                    session.setSelectedStatusEffectKey(StatusTransferSupport.effectKey(nextEffect));
                    String label = StatusTransferSupport.displayName(nextEffect);
                    debugLog(player, "cycleAspect: selected status %s (mode=%s, source=%s, total=%d)", label, family.displayName(), session.source().displayName(), availableEffects.size());
                    sendActionBar(player, "\u00a7e\u00a7l" + family.icon() + " " + family.displayName() + " \u00a78| \u00a7b" + label + " \u00a78(" + (currentEffectIndex + 2) + "/" + availableEffects.size() + ")");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.4f, 0.8f + ((currentEffectIndex + 1) * 0.1f));
                    return;
                }
            }
        }

        GraftAspect next;
        if (current == null) {
            next = aspects.get(0);
        } else {
            int idx = aspects.indexOf(current);
            next = aspects.get((idx + 1) % aspects.size());
        }

        session.setSelectedAspect(next);
        if (next == GraftAspect.STATUS) {
            session.setSelectedStatusEffectKey(StatusTransferSupport.effectKey(
                StatusTransferSupport.resolveEffect(plugin, session.sourceReference(), null)
            ));
        }
        String label = next == GraftAspect.STATUS
            ? StatusTransferSupport.selectedLabel(plugin, session.sourceReference(), session.selectedStatusEffectKey())
            : next.displayName();
        int totalCount = next == GraftAspect.STATUS
            ? Math.max(1, StatusTransferSupport.availableEffects(plugin, session.sourceReference()).size())
            : aspects.size();
        int position = next == GraftAspect.STATUS
            ? Math.max(1, StatusTransferSupport.availableEffects(plugin, session.sourceReference()).indexOf(
                StatusTransferSupport.resolveEffect(plugin, session.sourceReference(), session.selectedStatusEffectKey())
            ) + 1)
            : aspects.indexOf(next) + 1;
        debugLog(player, "cycleAspect: selected %s (mode=%s, source=%s, total=%d)", label, family.displayName(), session.source().displayName(), totalCount);
        sendActionBar(player, "\u00a7e\u00a7l" + family.icon() + " " + family.displayName() + " \u00a78| \u00a7b" + label + " \u00a78(" + position + "/" + totalCount + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.4f, 0.8f + (position * 0.1f));
    }

    private void selectSource(Player player, Block clickedBlock, Entity clickedEntity) {
        clearPendingConceptualForPracticalSwitch(player);
        ResolvedSource source = resolveConcreteSource(player, clickedBlock, clickedEntity);
        source = chooseBetterSourceForFamily(player, source, clickedBlock);
        if (source == null) {
            debugLog(player, "selectSource: no valid source found (block=%s, entity=%s)", clickedBlock, clickedEntity != null ? clickedEntity.getType() : "null");
            plugin.messages().send(player, "no-source-found");
            sendActionBar(player, "\u00a7cNo valid source found \u2014 try looking at something else");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }
        debugLog(player, "selectSource: resolved source=%s (kind=%s)", source.subject().displayName(), source.subject().kind());
        armSource(player, source.subject(), source.reference());
    }

    private void selectFluidSource(Player player) {
        clearPendingConceptualForPracticalSwitch(player);
        Block fluidBlock = player.getTargetBlockExact(plugin.settings().interactionRange(), FluidCollisionMode.ALWAYS);
        debugLog(player, "selectFluidSource: targetBlock=%s, type=%s", fluidBlock, fluidBlock != null ? fluidBlock.getType() : "null");

        if (fluidBlock != null && plugin.subjectResolver().resolveFluid(fluidBlock.getType()).isPresent()) {
            GraftSubject subject = plugin.subjectResolver().resolveFluid(fluidBlock.getType()).get();
            debugLog(player, "selectFluidSource: resolved fluid=%s", subject.displayName());
            armSource(player, subject, CastSourceReference.ofBlock(fluidBlock));
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 0.7f, 1.2f);
            Location eyeLoc = player.getEyeLocation();
            player.getWorld().spawnParticle(Particle.DRIPPING_WATER, eyeLoc.add(eyeLoc.getDirection()), 15, 0.3, 0.3, 0.3, 0.02);
            return;
        }

        debugLog(player, "selectFluidSource: no fluid or block found");
        plugin.messages().send(player, "no-source-found");
        sendActionBar(player, "\u00a7cNo fluid found \u2014 look at water or lava and Shift+Right-Click");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
    }

    private void selectVoidSource(Player player) {
        GraftSubject voidSubject = plugin.subjectResolver().resolveVoid().orElse(null);
        if (voidSubject == null) {
            debugLog(player, "selectVoidSource: void resolution failed");
            plugin.messages().send(player, "no-source-found");
            return;
        }
        debugLog(player, "selectVoidSource: selected Void as source");
        armSource(player, voidSubject, CastSourceReference.none());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);
        Location eyeLoc = player.getEyeLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, eyeLoc, 30, 0.3, 0.3, 0.3, 0.5);
        player.getWorld().spawnParticle(Particle.SQUID_INK, eyeLoc, 15, 0.2, 0.2, 0.2, 0.01);
    }

    private void selectSelfSource(Player player) {
        clearPendingConceptualForPracticalSwitch(player);
        GraftSubject selfSubject = plugin.subjectResolver().resolveEntity(player).orElse(null);
        if (selfSubject == null) {
            debugLog(player, "selectSelfSource: self resolution failed");
            plugin.messages().send(player, "no-source-found");
            return;
        }
        debugLog(player, "selectSelfSource: selected self as source");
        armSource(player, selfSubject, CastSourceReference.ofEntity(player));
        plugin.messages().send(player, "self-source-selected");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.0f);
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ENCHANT, loc, 30, 0.5, 0.5, 0.5, 1.0);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.02);
    }

    private void clearPendingConceptualForPracticalSwitch(Player player) {
        if (plugin.conceptCatalogGui().getPendingAction(player) == null) {
            return;
        }
        plugin.conceptCatalogGui().clearPendingAction(player);
        plugin.messages().send(player, "conceptual-cleared-for-practical");
        sendActionBar(player, "§8Armed conceptual cast cleared");
    }

    private void armSource(Player player, GraftSubject source, CastSourceReference reference) {
        if (!plugin.castSelectionService().armSource(player, source, reference)) {
            return;
        }

        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        GraftFamily family = session.family();
        List<GraftAspect> aspects = plugin.compatibilityValidator().compatibleSourceAspects(family, source);
        GraftAspect selectedAspect = session.selectedAspect();
        boolean isConcept = source.kind() == com.graftingplugin.subject.SubjectKind.CONCEPT;
        boolean isSlot = reference.hasInventorySlot();
        String srcPrefix = isConcept ? "§3[Concept] §6" : (isSlot ? "§a[Inv] §6" : "§6");
        String slotHint = isSlot ? " §8- Left-Click chest or /graft target" : "";
        if (selectedAspect != null) {
            String cycleHint = aspects.size() > 1 || (selectedAspect == GraftAspect.STATUS && !StatusTransferSupport.availableEffects(plugin, reference).isEmpty()) ? " §8(shift+RC cycle)" : "";
            String selectedLabel = selectedAspect == GraftAspect.STATUS
                ? StatusTransferSupport.selectedLabel(plugin, reference, session.selectedStatusEffectKey())
                : selectedAspect.displayName();
            sendActionBar(player, "§eSource: " + srcPrefix + source.displayName() + " §8| " + family.icon() + " §b" + selectedLabel + cycleHint + slotHint);
        } else {
            sendActionBar(player, "§eSource: " + srcPrefix + source.displayName() + " §8| " + family.icon() + " " + family.displayName() + slotHint);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.5f);
    }

    private void applyCast(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.source() == null) {
            plugin.messages().send(player, "no-source-selected");
            sendActionBar(player, "\u00a7cNo source selected \u2014 Right-Click something first");
            return;
        }
        if (session.selectedAspect() == null) {
            plugin.messages().send(player, "no-aspect-selected");
            sendActionBar(player, "\u00a7cNo aspect selected \u2014 Shift+Right-Click to cycle aspects");
            return;
        }

        GraftFamily family = session.effectiveFamily();
        debugLog(player, ">>> CAST: family=%s, aspect=%s, source=%s, targetBlock=%s, targetEntity=%s",
            family.displayName(), session.selectedAspect().displayName(), session.source().displayName(),
            clickedBlock, clickedEntity != null ? clickedEntity.getType() : "null");

        boolean success = switch (family) {
            case STATE -> applyStateTransfer(player, clickedBlock, clickedEntity);
            case RELATION -> applyRelationGraft(player, clickedBlock, clickedEntity);
            case TOPOLOGY -> applyTopologyGraft(player, clickedBlock, clickedEntity);
            case SEQUENCE -> applySequenceTamper(player, clickedBlock, clickedEntity);
        };
        if (!success) {
            return;
        }

        Location castOrigin = player.getEyeLocation();
        try {
            player.getWorld().spawnParticle(Particle.FLASH, castOrigin, 1, 0, 0, 0, 0);
        } catch (IllegalArgumentException e) {
            player.getWorld().spawnParticle(Particle.END_ROD, castOrigin, 3, 0.2, 0.2, 0.2, 0.01);
        }

        Sound castSound = switch (family) {
            case STATE -> Sound.ENTITY_EVOKER_CAST_SPELL;
            case RELATION -> Sound.ENTITY_BLAZE_SHOOT;
            case TOPOLOGY -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case SEQUENCE -> Sound.BLOCK_DISPENSER_LAUNCH;
        };
        player.playSound(player.getLocation(), castSound, 0.8f, 1.0f);

        Location castLoc = clickedEntity != null ? clickedEntity.getLocation() :
            (clickedBlock != null ? clickedBlock.getLocation().add(0.5, 0.5, 0.5) : player.getEyeLocation());
        spawnFamilyParticles(player, family, castLoc);
    }

    private void spawnFamilyParticles(Player player, GraftFamily family, Location loc) {
        switch (family) {
            case STATE -> {
                player.getWorld().spawnParticle(Particle.ENCHANT, loc, 50, 0.5, 0.5, 0.5, 1.0);
                player.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.3, 0.3, 0.3, 0.5);
            }
            case RELATION -> {
                player.getWorld().spawnParticle(Particle.HEART, loc, 15, 0.3, 0.3, 0.3, 0.01);
                player.getWorld().spawnParticle(Particle.ENCHANT, loc, 30, 0.5, 0.5, 0.5, 1.0);
            }
            case TOPOLOGY -> {
                player.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.5);
                player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 20, 0.3, 0.3, 0.3, 0.3);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 10, 0.2, 0.5, 0.2, 0.01);
            }
            case SEQUENCE -> {
                player.getWorld().spawnParticle(Particle.FLAME, loc, 25, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.3, 0.3, 0.3, 0.3);
            }
        }
    }

    private boolean applyStateTransfer(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.selectedTargetSlot() >= 0) {
            return plugin.stateTransferService().applyToInventorySlot(player, session.source(), session.sourceReference(), session.selectedAspect(), session.selectedTargetSlot());
        }
        FocusTarget target = resolveStateTarget(player, clickedBlock, clickedEntity);
        if (target != null) {
            return applyStateTransferToTarget(player, session, target);
        }
        if (canApplyToOffhandItem(player, session.selectedAspect())) {
            boolean success = plugin.stateTransferService().applyToOffhandItem(player, session.source(), session.sourceReference(), session.selectedAspect());
            if (success) {
                sendActionBar(player, "\u00a78Tip: use \u00a7e/graft target\u00a78 to pick a specific slot instead of offhand.");
            }
            return success;
        }
        return plugin.stateTransferService().applyToArea(player, session.source(), session.selectedAspect(), player.getLocation());
    }

    private boolean applyStateTransferToTarget(Player player, CastSession session, FocusTarget target) {
        if (rejectSamePhysicalSourceAndTarget(player, session, target.block(), target.entity())) {
            return false;
        }
        if (target.entity() instanceof Projectile projectile) {
            return plugin.stateTransferService().applyToProjectile(player, session.source(), session.selectedAspect(), projectile);
        }
        if (target.entity() != null) {
            return plugin.stateTransferService().applyToEntity(player, session.source(), session.sourceReference(), session.selectedAspect(), session.selectedStatusEffectKey(), target.entity());
        }
        if (plugin.subjectResolver().resolveFluid(target.block().getType()).isPresent()) {
            return plugin.stateTransferService().applyToFluid(player, session.source(), session.selectedAspect(), target.block());
        }
        return plugin.stateTransferService().applyToBlock(player, session.source(), session.selectedAspect(), target.block());
    }

    private boolean applyRelationGraft(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.sourceReference().hasInventorySlot() && session.selectedTargetSlot() >= 0) {
            return plugin.relationGraftService().applySlotToSlot(player, session.source(), session.sourceReference().inventorySlot(), session.selectedAspect(), session.selectedTargetSlot());
        }
        FocusTarget target = resolveExplicitOrLookTarget(player, clickedBlock, clickedEntity);
        if (target == null) {
            if (session.sourceReference().hasInventorySlot()) {
                sendActionBar(player, "\u00a7cNo container target found. Point at a chest or use \u00a7e/graft target\u00a7c to pick a slot.");
            } else {
                plugin.messages().send(player, "no-target-found");
            }
            return false;
        }
        if (rejectSamePhysicalSourceAndTarget(player, session, target.block(), target.entity())) {
            return false;
        }
        if (target.entity() != null) {
            return plugin.relationGraftService().applyToEntity(player, session.source(), session.sourceReference(), session.selectedAspect(), target.entity());
        }
        return plugin.relationGraftService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), target.block());
    }

    private boolean applyTopologyGraft(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        FocusTarget target = resolveExplicitOrLookTarget(player, clickedBlock, clickedEntity);
        if (target == null) {
            plugin.messages().send(player, "no-target-found");
            return false;
        }
        if (rejectSamePhysicalSourceAndTarget(player, session, target.block(), target.entity())) {
            return false;
        }
        if (target.entity() != null) {
            return plugin.topologyGraftService().applyToLocation(player, session.source(), session.sourceReference(), session.selectedAspect(), target.entity().getLocation().add(0.0D, target.entity().getHeight() * 0.5D, 0.0D));
        }
        return plugin.topologyGraftService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), target.block());
    }

    private boolean applySequenceTamper(Player player, Block clickedBlock, Entity clickedEntity) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (session.selectedAspect() == GraftAspect.ON_HIT) {
            Projectile projectileTarget = resolveProjectileCastTarget(player, clickedBlock, clickedEntity);
            if (projectileTarget == null) {
                plugin.messages().send(player, "no-target-found");
                return false;
            }
            if (rejectSamePhysicalSourceAndTarget(player, session, null, projectileTarget)) {
                return false;
            }
            return plugin.sequenceTamperService().applyToProjectile(player, session.source(), session.sourceReference(), session.selectedAspect(), projectileTarget);
        }

        Block blockTarget = resolveSequenceBlockTarget(player, clickedBlock);
        if (blockTarget == null) {
            plugin.messages().send(player, "no-target-found");
            return false;
        }
        if (rejectSamePhysicalSourceAndTarget(player, session, blockTarget, null)) {
            return false;
        }
        return plugin.sequenceTamperService().applyToBlock(player, session.source(), session.sourceReference(), session.selectedAspect(), blockTarget);
    }


    private Projectile resolveProjectileCastTarget(Player player, Block clickedBlock, Entity clickedEntity) {
        if (clickedEntity instanceof Projectile projectile) {
            return projectile;
        }
        FocusTarget target = resolveExplicitOrLookTarget(player, clickedBlock, clickedEntity);
        return target != null && target.entity() instanceof Projectile projectile ? projectile : null;
    }

    private Block resolveSequenceBlockTarget(Player player, Block clickedBlock) {
        if (clickedBlock != null) {
            return clickedBlock;
        }
        FocusTarget target = resolveLookTarget(player);
        return target == null ? null : target.block();
    }

    private FocusTarget resolveStateTarget(Player player, Block clickedBlock, Entity clickedEntity) {
        if (clickedEntity != null) {
            return focusTargetForEntity(clickedEntity);
        }
        if (clickedBlock != null) {
            return focusTargetForBlock(resolvePreferredFluidBlock(player, clickedBlock));
        }
        return resolveLookTarget(player);
    }

    private FocusTarget resolveExplicitOrLookTarget(Player player, Block clickedBlock, Entity clickedEntity) {
        if (clickedEntity != null) {
            return focusTargetForEntity(clickedEntity);
        }
        if (clickedBlock != null) {
            return focusTargetForBlock(clickedBlock);
        }
        return resolveLookTarget(player);
    }

    private FocusTarget focusTargetForEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        GraftSubject subject = entity instanceof Projectile projectile
            ? plugin.subjectResolver().resolveProjectile(projectile).orElse(null)
            : plugin.subjectResolver().resolveEntity(entity).orElse(null);
        return subject == null ? null : new FocusTarget(subject, entity, null);
    }

    private FocusTarget focusTargetForBlock(Block block) {
        if (block == null || block.getType().isAir()) {
            return null;
        }
        if (plugin.subjectResolver().resolveFluid(block.getType()).isPresent()) {
            GraftSubject subject = plugin.subjectResolver().resolveFluid(block.getType()).get();
            return new FocusTarget(subject, null, block);
        }
        GraftSubject subject = plugin.subjectResolver().resolveBlock(block).orElse(null);
        return subject == null ? null : new FocusTarget(subject, null, block);
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
            if (subject != null) {
                return new ResolvedSource(subject, CastSourceReference.ofBlock(clickedBlock));
            }
        }

        Block preferredFluid = resolvePreferredFluidBlock(player, null);
        if (preferredFluid != null && plugin.subjectResolver().resolveFluid(preferredFluid.getType()).isPresent()) {
            GraftSubject subject = plugin.subjectResolver().resolveFluid(preferredFluid.getType()).get();
            return new ResolvedSource(subject, CastSourceReference.ofBlock(preferredFluid));
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

    private boolean readyForCast(Player player) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        return session.source() != null && session.selectedAspect() != null;
    }

    private void sendNotReadyFeedback(Player player, CastSession session) {
        if (session.source() == null) {
            plugin.messages().send(player, "no-source-selected");
            sendActionBar(player, "\u00a7cNo source selected \u2014 Right-Click something first");
            return;
        }

        plugin.messages().send(player, "no-aspect-selected");
        sendActionBar(player, "\u00a7cNo aspect selected \u2014 Shift+Right-Click or /graft next");
    }

    private boolean isHoldingFocus(Player player) {
        return plugin.focusItemService().isFocus(player.getInventory().getItemInMainHand());
    }

    private boolean hasOffhandItemTarget(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && !offhand.getType().isAir() && !plugin.focusItemService().isFocus(offhand);
    }

    private boolean canApplyToOffhandItem(Player player, GraftAspect aspect) {
        if (aspect == null || !hasOffhandItemTarget(player)) {
            return false;
        }
        GraftSubject offhandTarget = plugin.subjectResolver().resolveItem(player.getInventory().getItemInOffHand()).orElse(null);
        return offhandTarget != null && plugin.stateTransferPlanner().plan(aspect, offhandTarget).isPresent();
    }

    private ResolvedSource chooseBetterSourceForFamily(Player player, ResolvedSource resolved, Block clickedBlock) {
        if (resolved == null) {
            return null;
        }
        if (clickedBlock != null) {
            return resolved;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (!plugin.compatibilityValidator().compatibleSourceAspects(session.effectiveFamily(), resolved.subject()).isEmpty()) {
            return resolved;
        }

        Block fluidBlock = resolvePreferredFluidBlock(player, null);
        if (fluidBlock == null || !plugin.subjectResolver().resolveFluid(fluidBlock.getType()).isPresent()) {
            return resolved;
        }

        GraftSubject fluidSubject = plugin.subjectResolver().resolveFluid(fluidBlock.getType()).orElse(null);
        if (fluidSubject == null) {
            return resolved;
        }
        if (plugin.compatibilityValidator().compatibleSourceAspects(session.effectiveFamily(), fluidSubject).isEmpty()) {
            return resolved;
        }
        return new ResolvedSource(fluidSubject, CastSourceReference.ofBlock(fluidBlock));
    }

    private Block resolvePreferredFluidBlock(Player player, Block clickedBlock) {
        Block rayTarget = player.getTargetBlockExact(plugin.settings().interactionRange(), FluidCollisionMode.ALWAYS);
        if (rayTarget != null && plugin.subjectResolver().resolveFluid(rayTarget.getType()).isPresent()) {
            return rayTarget;
        }
        return clickedBlock;
    }

    private FocusTarget resolveLookTarget(Player player) {
        Entity targetEntity = player.getTargetEntity(plugin.settings().interactionRange());
        if (targetEntity == null && player.getWorld() != null) {
            RayTraceResult rayTrace = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                plugin.settings().interactionRange(),
                0.25D,
                entity -> !entity.equals(player)
            );
            if (rayTrace != null && rayTrace.getHitEntity() != null) {
                targetEntity = rayTrace.getHitEntity();
            }
        }
        if (targetEntity != null && !targetEntity.equals(player)) {
            FocusTarget entityTarget = focusTargetForEntity(targetEntity);
            if (entityTarget != null) {
                return entityTarget;
            }
        }

        Block targetBlock = player.getTargetBlockExact(plugin.settings().interactionRange(), FluidCollisionMode.ALWAYS);
        return focusTargetForBlock(targetBlock);
    }

    private boolean rejectSamePhysicalSourceAndTarget(Player player, CastSession session, Block targetBlock, Entity targetEntity) {
        CastSourceReference sourceReference = session.sourceReference();
        if (sourceReference == null) {
            return false;
        }
        if (sourceReference.hasEntity() && targetEntity != null && sourceReference.entityId().equals(targetEntity.getUniqueId())) {
            player.sendMessage("§cSource and target cannot be the same entity.");
            sendActionBar(player, "§cPick something else as the target");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return true;
        }
        if (sourceReference.hasBlockLocation() && targetBlock != null) {
            Location sourceBlock = sourceReference.blockLocation();
            if (sourceBlock != null && sameBlock(sourceBlock, targetBlock.getLocation())) {
                player.sendMessage("§cSource and target cannot be the same block.");
                sendActionBar(player, "§cPick a different block as the target");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return true;
            }
        }
        return false;
    }

    private boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return false;
        }
        return first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private void sendActionBar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private void debugLog(Player player, String format, Object... args) {
        if (!isDebugEnabled(player)) {
            return;
        }
        plugin.getLogger().info("[GraftDebug] " + player.getName() + ": " + String.format(format, args));
    }

    private record FocusTarget(GraftSubject subject, Entity entity, Block block) {
    }

    private record ResolvedSource(GraftSubject subject, CastSourceReference reference) {
    }
}
