package com.graftingplugin.command;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.active.ActiveGraftSnapshot;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSession;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.state.StatusTransferSupport;
import com.graftingplugin.subject.GraftSubject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GraftCommand implements CommandExecutor, TabCompleter {

    private final GraftingPlugin plugin;

    public GraftCommand(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasUseAccess(sender)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (isAdminSubcommand(subcommand) && !sender.hasPermission("grafting.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        switch (subcommand) {
            case "help" -> handleHelp(sender, args);
            case "mode" -> handleMode(sender, args);
            case "concept" -> handleConcept(sender, args);
            case "inventory", "inv" -> handleInventory(sender);
            case "target" -> handleTarget(sender);
            case "aspect" -> handleAspect(sender, args);
            case "next", "cycle" -> handleCycle(sender);
            case "self" -> handleSelf(sender);
            case "clear" -> handleClear(sender);
            case "inspect" -> handleInspect(sender);
            case "active" -> handleActive(sender);
            case "debug" -> handleDebug(sender);
            case "status" -> handleStatus(sender, args);
            case "clearactive" -> handleClearActive(sender, args);
            case "givefocus" -> handleGiveFocus(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleHelp(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        page = Math.max(1, Math.min(page, 3));

        sender.sendMessage(Component.text("=== Grafting Help (" + page + "/3) ===", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        switch (page) {
            case 1 -> {
                sender.sendMessage(section("Core Loop"));
                sender.sendMessage(text("1. Pick a mode, then pick a source, then cast.", NamedTextColor.GRAY));
                sender.sendMessage(modeButtons());
                sender.sendMessage(text("2. Right-Click a block/entity, or use the source shortcuts below.", NamedTextColor.GRAY));
                sender.sendMessage(sourceButtons());
                sender.sendMessage(text("3. Shift-Right-Click or /graft next cycles aspects.", NamedTextColor.GRAY));
                sender.sendMessage(text("4. Left-Click a target to cast. Your setup now stays armed until /graft clear.", NamedTextColor.GRAY));
                sender.sendMessage(Component.empty());
                sender.sendMessage(section("Reliable Source Shortcuts"));
                sender.sendMessage(text("• /graft self — select yourself as the source", NamedTextColor.GRAY));
                sender.sendMessage(text("• Sneak + Left-Click air — clear your current source/aspect", NamedTextColor.GRAY));
                sender.sendMessage(text("• Sneak + Right-Click with no source — select the water/lava you are looking at", NamedTextColor.GRAY));
                sender.sendMessage(text("• /graft inventory — pick an item from your inventory as source", NamedTextColor.GRAY));
                sender.sendMessage(text("• /graft concept list — open practical concept sources", NamedTextColor.GRAY));
            }
            case 2 -> {
                sender.sendMessage(section("Practical Workflows"));
                sender.sendMessage(text("• State Graft: move a state like heat, heal, speed, poison, freeze, glow.", NamedTextColor.GRAY));
                sender.sendMessage(text("• Link Graft: aggro redirect, tethers, projectile retargets, item/container routing.", NamedTextColor.GRAY));
                sender.sendMessage(text("• Location Graft: temporary anchor links and loops between places.", NamedTextColor.GRAY));
                sender.sendMessage(text("• Event Graft: On Hit payloads and On Open relays.", NamedTextColor.GRAY));
                sender.sendMessage(Component.empty());
                sender.sendMessage(section("Item Work"));
                sender.sendMessage(text("• Offhand item: cast in air while holding the item in your offhand.", NamedTextColor.GRAY));
                sender.sendMessage(commandLine("/graft target", "Pick a specific inventory slot as the target."));
                sender.sendMessage(commandLine("/graft inventory", "Pick an inventory item as the source."));
                sender.sendMessage(Component.empty());
                sender.sendMessage(section("Common Practical Examples"));
                sender.sendMessage(text("• Repair item: mode state → source with Heal → cast onto offhand/target slot.", NamedTextColor.GRAY));
                sender.sendMessage(text("• Chest routing: mode link → source chest → target chest.", NamedTextColor.GRAY));
                sender.sendMessage(text("• Aggro redirect: mode link → hostile mob source → player/mob target.", NamedTextColor.GRAY));
            }
            case 3 -> {
                sender.sendMessage(section("Conceptual Grafting"));
                sender.sendMessage(commandLine("/graft concept", "Open the rare conceptual graft menu."));
                sender.sendMessage(commandLine("/graft concept list", "Open practical concept sources for normal grafting."));
                sender.sendMessage(commandLine("/graft concept <name>", "Arm one practical concept source directly."));
                sender.sendMessage(text("Bare /graft concept is for rare conceptual grafts. /graft concept list is for practical concept sources.", NamedTextColor.GRAY));
                sender.sendMessage(text("Beginning ↔ End and Threshold → Elsewhere are anchor-based; the other conceptual grafts are zone laws.", NamedTextColor.GRAY));
                sender.sendMessage(Component.empty());
                sender.sendMessage(section("Runtime & Cleanup"));
                sender.sendMessage(commandLine("/graft inspect", "See your current practical setup and armed conceptual cast."));
                sender.sendMessage(commandLine("/graft active", "See your active practical and conceptual runtime grafts."));
                sender.sendMessage(commandLine("/graft clear", "Clear your selected source/aspect only."));
                sender.sendMessage(commandLine("/graft clearactive", "Admin: clear active runtime grafts."));
                sender.sendMessage(Component.empty());
                sender.sendMessage(section("Important Notes"));
                sender.sendMessage(text("• /graft clear does not cancel already-active runtime grafts; use /graft active or /graft clearactive for that.", NamedTextColor.GRAY));
                sender.sendMessage(text("• Practical Sky is a routine source (speed/light/bounce); Sky → Ground is the conceptual law version.", NamedTextColor.GRAY));
            }
        }

        sender.sendMessage(helpNav(page));
    }

    private void handleMode(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /graft mode <state|link|location|event>");
            return;
        }
        GraftFamily family = GraftFamily.fromInput(args[1]).orElse(null);
        if (family == null) {
            sender.sendMessage("Unknown graft family. Use state, link, location, or event.");
            return;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        session.clearSelection();
        session.setFamily(family);
        plugin.messages().send(player, "mode-set", "family", family.displayName());
        player.sendMessage("\u00a77" + family.description() + '.');
        if (family == GraftFamily.SEQUENCE) {
            player.sendMessage("\u00a78Event Graft currently supports On Hit and On Open.");
        }
    }

    private void handleConcept(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.conceptCatalogGui().openConceptualGraftMenu(player);
            plugin.messages().send(player, "conceptual-menu-opened");
            return;
        }
        if (args[1].equalsIgnoreCase("list")) {
            plugin.conceptCatalogGui().open(player);
            plugin.messages().send(player, "practical-concept-catalog-opened");
            return;
        }
        GraftSubject source = plugin.subjectResolver().resolveConcept(args[1]).orElse(null);
        if (source == null) {
            plugin.messages().send(player, "unknown-concept", "concept", args[1]);
            return;
        }
        plugin.castSelectionService().armSource(player, source);
    }

    private void handleInventory(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.inventorySlotPickerGui().open(player);
        player.sendMessage("\u00a77Pick an item from your inventory to use as the \u00a7eSource\u00a77.");
        player.sendMessage("\u00a78Link mode: Left-Click a chest to deposit it, or /graft target to pick a slot-swap target.");
        player.sendMessage("\u00a78State mode: this picks the state source. Use /graft target to pick the item target.");
    }

    private void handleTarget(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.inventoryTargetPickerGui().open(player);
        player.sendMessage("\u00a77Pick an inventory slot to use as the \u00a7bTarget\u00a77.");
        player.sendMessage("\u00a78State + Heal: repairs the item in that slot. Link + /graft inventory: swaps two of your own inventory slots.");
    }

    private void handleAspect(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /graft aspect <aspect>");
            return;
        }

        GraftAspect aspect = GraftAspect.fromInput(args[1]).orElse(null);
        if (aspect == null) {
            plugin.messages().send(player, "invalid-aspect", "aspect", args[1]);
            return;
        }
        plugin.castSelectionService().selectAspect(player, aspect);
    }

    private void handleCycle(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.focusInteractionListener().cycleAspectSelection(player);
    }

    private void handleSelf(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.focusInteractionListener().selectSelfSourceCommand(player);
    }

    private void handleClear(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        session.clearSelection();
        plugin.messages().send(player, "selection-cleared", "family", session.family().displayName());
    }

    private void handleDebug(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        boolean nowEnabled = !plugin.focusInteractionListener().isDebugEnabled(player);
        plugin.focusInteractionListener().setDebugEnabled(player, nowEnabled);
        player.sendMessage(nowEnabled ? "\u00a7aDebug logging ENABLED. All wand actions will be logged to console." : "\u00a7cDebug logging DISABLED.");
    }

    private void handleInspect(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        player.sendMessage("\u00a7dMode: \u00a7f" + session.family().displayName());
        if (session.source() == null) {
            player.sendMessage("\u00a7dSource: \u00a77none \u00a78(Right-Click something, or /graft inventory / /graft concept)");
        } else {
            boolean isConcept = session.source().kind() == com.graftingplugin.subject.SubjectKind.CONCEPT;
            String srcLabel = isConcept ? "Concept Source" : "Source";
            String slotSuffix = session.sourceReference().hasInventorySlot() ? " \u00a78[inv slot " + (session.sourceReference().inventorySlot() + 1) + "]" : "";
            player.sendMessage("\u00a7d" + srcLabel + ": \u00a76" + session.source().displayName() + slotSuffix);
            if (isConcept) {
                player.sendMessage("\u00a78Concept sources are named identities, not physical objects.");
            }
        }
        player.sendMessage("\u00a7dAspect: \u00a7f" + selectedAspectLabel(session));
        if (session.selectedTargetSlot() >= 0) {
            org.bukkit.inventory.ItemStack tgtItem = player.getInventory().getStorageContents()[session.selectedTargetSlot()];
            String tgtName = (tgtItem != null && !tgtItem.getType().isAir()) ? tgtItem.getType().name().toLowerCase(java.util.Locale.ROOT) : "empty";
            player.sendMessage("\u00a7dTarget Slot: \u00a7b" + (session.selectedTargetSlot() + 1) + " \u00a78(" + tgtName + ")");
        } else {
            player.sendMessage("\u00a7dTarget Slot: \u00a77not set \u00a78(/graft target to pick)");
        }
        List<GraftAspect> supported = plugin.compatibilityValidator().supportedFamilyAspects(session.family());
        player.sendMessage("\u00a7dAvailable aspects: " + formatAspectList(supported));
        if (session.source() != null) {
            List<GraftAspect> compat = plugin.compatibilityValidator().compatibleSourceAspects(session.family(), session.source());
            player.sendMessage("\u00a7dFrom this source: " + formatSourceAspectList(session, compat));
            if (compat.contains(GraftAspect.STATUS)) {
                player.sendMessage("\u00a7dActive source effects: \u00a77" + StatusTransferSupport.formatAvailableEffects(plugin, session.sourceReference()));
            }
            String props = formatProperties(session.source());
            if (!props.isEmpty()) {
                player.sendMessage("\u00a7dProperties: \u00a77" + props);
            }
        }
        com.graftingplugin.gui.ConceptCatalogGui.PendingConceptAction pendingConcept = plugin.conceptCatalogGui().getPendingAction(player);
        if (pendingConcept == null) {
            player.sendMessage("\u00a7dConceptual Cast: \u00a77none armed \u00a78(/graft concept)");
        } else if (!pendingConcept.type().requiresTwoAnchors()) {
            player.sendMessage("\u00a7dConceptual Cast: \u00a75" + pendingConcept.type().displayName() + " \u00a78(zone law armed)");
        } else if (pendingConcept.firstAnchor() == null) {
            player.sendMessage("\u00a7dConceptual Cast: \u00a75" + pendingConcept.type().displayName() + " \u00a78(waiting for first anchor)");
        } else {
            player.sendMessage("\u00a7dConceptual Cast: \u00a75" + pendingConcept.type().displayName() + " \u00a78(waiting for second anchor)");
        }
    }

    private void handleActive(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }

        List<ActiveGraftSnapshot> activeGrafts = plugin.activeGraftRegistry().activeFor(player.getUniqueId());
        if (activeGrafts.isEmpty()) {
            player.sendMessage("No active grafts.");
            return;
        }

        player.sendMessage("Active grafts:");
        sendActiveSection(player, activeGrafts, false, "Practical", "§7");
        sendActiveSection(player, activeGrafts, true, "Conceptual", "§5");
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("grafting.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Usage: /graft status <player>");
            return;
        }
        if (target == null) {
            sender.sendMessage("Target player not found.");
            return;
        }

        CastSession session = plugin.castSessionManager().session(target.getUniqueId());
        sender.sendMessage("Graft status for " + target.getName() + ":");
        sender.sendMessage("- Mode: " + session.family().displayName());
        sender.sendMessage("- Source: " + describeSource(session));
        sender.sendMessage("- Aspect: " + selectedAspectLabel(session));
        sender.sendMessage("- Supported aspects: " + formatAspectList(plugin.compatibilityValidator().supportedFamilyAspects(session.family())));
        if (session.source() != null) {
            List<GraftAspect> compatibleAspects = plugin.compatibilityValidator().compatibleSourceAspects(session.family(), session.source());
            sender.sendMessage("- Source-compatible aspects: " + formatSourceAspectList(session, compatibleAspects));
            if (compatibleAspects.contains(GraftAspect.STATUS)) {
                sender.sendMessage("- Active source effects: " + StatusTransferSupport.formatAvailableEffects(plugin, session.sourceReference()));
            }
            sender.sendMessage("- Source properties: " + formatProperties(session.source()));
        }

        List<ActiveGraftSnapshot> activeGrafts = plugin.activeGraftRegistry().activeFor(target.getUniqueId());
        sender.sendMessage("- Active graft count: " + activeGrafts.size());
        if (!activeGrafts.isEmpty()) {
            List<String> practical = describeActiveGrafts(activeGrafts, false);
            List<String> conceptual = describeActiveGrafts(activeGrafts, true);
            if (!practical.isEmpty()) {
                sender.sendMessage("- Practical active:");
                for (String line : practical) {
                    sender.sendMessage("  * " + line);
                }
            }
            if (!conceptual.isEmpty()) {
                sender.sendMessage("- Conceptual active:");
                for (String line : conceptual) {
                    sender.sendMessage("  * " + line);
                }
            }
        }
    }

    private void handleClearActive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("grafting.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Usage: /graft clearactive <player>");
            return;
        }
        if (target == null) {
            sender.sendMessage("Target player not found.");
            return;
        }

        List<Runnable> cleanupActions = plugin.activeGraftRegistry().clearOwner(target.getUniqueId());
        cleanupActions.forEach(Runnable::run);
        if (cleanupActions.isEmpty()) {
            plugin.messages().send(sender, "no-active-grafts-cleared", "player", target.getName());
        } else {
            plugin.messages().send(sender, "active-grafts-cleared", Map.of(
                "count", Integer.toString(cleanupActions.size()),
                "player", target.getName()
            ));
        }
        if (!target.equals(sender) && !cleanupActions.isEmpty()) {
            plugin.messages().send(target, "active-grafts-cleared-notify");
        }
    }

    private void handleGiveFocus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("grafting.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("Usage: /graft givefocus <player>");
            return;
        }
        if (target == null) {
            sender.sendMessage("Target player not found.");
            return;
        }
        target.getInventory().addItem(plugin.focusItemService().createFocusItem());
        plugin.messages().send(target, "focus-given");
        if (!target.equals(sender)) {
            sender.sendMessage("Gave a Mystic Focus to " + target.getName() + '.');
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("grafting.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return;
        }
        plugin.reloadPluginState();
        sender.sendMessage("GraftingPlugin reloaded.");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("\u00a7dGrafting Plugin \u00a77- Use \u00a7e/graft help \u00a77for a clickable guide.");
        sender.sendMessage("/graft help|mode|concept|inventory|inv|target|aspect|next|cycle|self|inspect|clear|active|debug");
        sender.sendMessage("/graft status|clearactive|givefocus|reload");
        sender.sendMessage("\u00a77Modes: \u00a7estate \u00a77| \u00a7elink \u00a77| \u00a7elocation \u00a77| \u00a7eevent");
        sender.sendMessage("\u00a77Concepts: \u00a7e/graft concept\u00a77 = conceptual graft menu, \u00a7e/graft concept list\u00a77 = practical concept sources, \u00a7e/graft concept <name>\u00a77 = arm one directly.");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("This command must be used by a player.");
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasUseAccess(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(List.of("help", "mode", "concept", "inventory", "inv", "target", "aspect", "next", "cycle", "self", "inspect", "clear", "active", "debug"));
            if (sender.hasPermission("grafting.admin")) {
                commands.addAll(List.of("status", "clearactive", "givefocus", "reload"));
            }
            return filter(commands, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filter(List.of("state", "link", "location", "event"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("concept")) {
            List<String> conceptOptions = new ArrayList<>(plugin.conceptRegistry().keys());
            conceptOptions.add(0, "list");
            return filter(conceptOptions, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("aspect") && sender instanceof Player player) {
            CastSession session = plugin.castSessionManager().session(player.getUniqueId());
            GraftSubject source = session.source();
            List<GraftAspect> aspects = source == null
                ? plugin.compatibilityValidator().supportedFamilyAspects(session.family())
                : plugin.compatibilityValidator().compatibleSourceAspects(session.family(), source);
            return filter(aspects.stream()
                .map(GraftAspect::key)
                .toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givefocus")) {
            if (!sender.hasPermission("grafting.admin")) {
                return List.of();
            }
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("clearactive"))) {
            if (!sender.hasPermission("grafting.admin")) {
                return List.of();
            }
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private boolean hasUseAccess(CommandSender sender) {
        return sender.hasPermission("grafting.use") || sender.hasPermission("grafting.admin");
    }

    private boolean isAdminSubcommand(String subcommand) {
        return switch (subcommand) {
            case "status", "clearactive", "givefocus", "reload" -> true;
            default -> false;
        };
    }

    private Component helpNav(int page) {
        Component previous = page <= 1
            ? text("[« Prev]", NamedTextColor.DARK_GRAY)
            : button("[« Prev]", "/graft help " + (page - 1), "Open help page " + (page - 1));
        Component next = page >= 3
            ? text("[Next »]", NamedTextColor.DARK_GRAY)
            : button("[Next »]", "/graft help " + (page + 1), "Open help page " + (page + 1));
        return Component.empty()
            .append(previous)
            .append(Component.text("  "))
            .append(next)
            .append(Component.text("  "))
            .append(button("[Inspect]", "/graft inspect", "Show your current setup"));
    }

    private Component modeButtons() {
        return Component.empty()
            .append(button("[State]", "/graft mode state", "Move a state onto a target"))
            .append(Component.text(" "))
            .append(button("[Link]", "/graft mode link", "Link a source to a target"))
            .append(Component.text(" "))
            .append(button("[Location]", "/graft mode location", "Bend space between anchors"))
            .append(Component.text(" "))
            .append(button("[Event]", "/graft mode event", "Load an On Hit / On Open trigger"));
    }

    private Component sourceButtons() {
        return Component.empty()
            .append(button("[Self]", "/graft self", "Select yourself as the source"))
            .append(Component.text(" "))
            .append(button("[Inventory]", "/graft inventory", "Pick an inventory item as the source"))
            .append(Component.text(" "))
            .append(button("[Concepts]", "/graft concept list", "Open practical concept sources"));
    }

    private Component commandLine(String command, String description) {
        return Component.empty()
            .append(button(command, command, description))
            .append(Component.text(" — " + description, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
    }

    private Component section(String title) {
        return Component.text(title, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false);
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component button(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.AQUA, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
    }

    private String selectedAspectLabel(CastSession session) {
        if (session.selectedAspect() == null) {
            return "§7none";
        }
        if (session.selectedAspect() != GraftAspect.STATUS) {
            return session.selectedAspect().displayName();
        }
        return StatusTransferSupport.selectedLabel(plugin, session.sourceReference(), session.selectedStatusEffectKey());
    }

    private String formatSourceAspectList(CastSession session, List<GraftAspect> aspects) {
        if (aspects.isEmpty()) {
            return "§7none";
        }
        List<String> labels = new ArrayList<>();
        for (GraftAspect aspect : aspects) {
            if (aspect == GraftAspect.STATUS) {
                List<org.bukkit.potion.PotionEffectType> available = StatusTransferSupport.availableEffects(plugin, session.sourceReference());
                if (available.isEmpty()) {
                    labels.add("status");
                } else {
                    for (org.bukkit.potion.PotionEffectType effectType : available) {
                        labels.add(StatusTransferSupport.displayName(effectType));
                    }
                }
                continue;
            }
            labels.add(aspect.key());
        }
        return String.join(", ", labels);
    }

    private String formatAspectList(List<GraftAspect> aspects) {
        return aspects.isEmpty() ? "none" : String.join(", ", aspects.stream().map(GraftAspect::key).toList());
    }

    private String describeSource(CastSession session) {
        if (session.source() == null) {
            return "none";
        }
        StringBuilder builder = new StringBuilder(session.source().displayName());
        if (session.sourceReference().hasEntity()) {
            Entity entity = Bukkit.getEntity(session.sourceReference().entityId());
            builder.append(entity == null ? " [entity:missing]" : " [entity:" + entity.getType().name().toLowerCase(Locale.ROOT) + "]");
        } else if (session.sourceReference().hasBlockLocation()) {
            builder.append(" [block]");
        } else {
            builder.append(" [virtual]");
        }
        return builder.toString();
    }

    private void sendActiveSection(Player player, List<ActiveGraftSnapshot> activeGrafts, boolean conceptual, String title, String color) {
        List<String> lines = describeActiveGrafts(activeGrafts, conceptual);
        if (lines.isEmpty()) {
            return;
        }
        player.sendMessage(color + title + ":");
        for (String line : lines) {
            player.sendMessage("- " + line);
        }
    }

    private List<String> describeActiveGrafts(List<ActiveGraftSnapshot> activeGrafts, boolean conceptual) {
        return activeGrafts.stream()
            .filter(snapshot -> snapshot.conceptual() == conceptual)
            .map(snapshot -> "[" + snapshot.familyLabel() + "] " + snapshot.aspectName() + " | " + snapshot.sourceName() + " -> " + snapshot.targetName() + " (" + snapshot.remainingSeconds() + "s)")
            .toList();
    }

    private String formatProperties(GraftSubject source) {
        List<String> pieces = new ArrayList<>();
        for (DynamicProperty property : DynamicProperty.values()) {
            double value = source.properties().get(property);
            if (value == 0.0D) {
                continue;
            }
            pieces.add(property.name().toLowerCase(Locale.ROOT) + '=' + String.format(Locale.ROOT, "%.2f", value));
        }
        return pieces.isEmpty() ? "none" : String.join(", ", pieces);
    }

    private List<String> filter(List<String> options, String token) {
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT))) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
