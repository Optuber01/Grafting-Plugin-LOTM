package com.graftingplugin.command;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.active.ActiveGraftSnapshot;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSession;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.subject.GraftSubject;
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
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "help" -> handleHelp(sender, args);
            case "mode" -> handleMode(sender, args);
            case "concept" -> handleConcept(sender, args);
            case "inventory", "inv" -> handleInventory(sender);
            case "aspect" -> handleAspect(sender, args);
            case "next", "cycle" -> handleCycle(sender);
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
        page = Math.max(1, Math.min(page, 2));

        sender.sendMessage("§5§l═══ Grafting Help (" + page + "/2) §5§l═══");
        switch (page) {
            case 1 -> {
                sender.sendMessage("§d§lCore Loop:");
                sender.sendMessage("§7  1. §f/graft mode <state|link|location|event>");
                sender.sendMessage("§7  2. §fRight-Click a source with the Mystic Focus");
                sender.sendMessage("§7  3. §fShift+Right-Click or /graft aspect to pick an aspect");
                sender.sendMessage("§7  4. §fLeft-Click a target to cast");
                sender.sendMessage("");
                sender.sendMessage("§d§lFocus Controls:");
                sender.sendMessage("§7  Right-Click §f→ Pick a source");
                sender.sendMessage("§7  Shift+Right-Click §f→ Cycle aspect, or pick a fluid if no source is set");
                sender.sendMessage("§7  Left-Click §f→ Cast on the target");
                sender.sendMessage("§7  Shift+Left-Click in air §f→ Clear source and aspect, or pick Void if none is set");
                sender.sendMessage("§7  Double Right-Click in air §f→ Pick yourself as the source");
                sender.sendMessage("");
                sender.sendMessage("§d§lQuick Commands:");
                sender.sendMessage("§e  /graft concept [name] §f→ Open concept sources or pick one directly");
                sender.sendMessage("§e  /graft inventory §f→ Pick an inventory item as the source");
                sender.sendMessage("§e  /graft next §f→ Cycle to the next aspect");
                sender.sendMessage("§e  /graft inspect §f→ Show your current setup");
            }
            case 2 -> {
                sender.sendMessage("§d§lModes:");
                sender.sendMessage("§9◆ State Graft §f→ Move heat, light, speed, healing, poison, and similar states");
                sender.sendMessage("§c❤ Link Graft §f→ Redirect aggro, tether targets, retarget projectiles, or route containers");
                sender.sendMessage("§a✦ Location Graft §f→ Create anchor links and path loops");
                sender.sendMessage("§6⚡ Event Graft §f→ Use On Hit and On Open triggers");
                sender.sendMessage("");
                sender.sendMessage("§d§lUseful Notes:");
                sender.sendMessage("§7  /graft concept picks a source. It is not a separate mode.");
                sender.sendMessage("§7  /graft inventory only picks a source item.");
                sender.sendMessage("§7  To graft onto an item, hold the target item in your offhand and cast.");
                sender.sendMessage("§7  /graft clear keeps your current mode.");
                sender.sendMessage("");
                sender.sendMessage("§d§lOther Commands:");
                sender.sendMessage("§e  /graft active §f→ Show your active grafts");
                sender.sendMessage("§e  /graft debug §f→ Toggle focus debug logging");
                sender.sendMessage("§e  /graft clearactive [player] §f→ Clear active grafts (admin)");
                sender.sendMessage("§e  /graft givefocus [player] §f→ Give a Mystic Focus (admin)");
                sender.sendMessage("§e  /graft reload §f→ Reload config and clear runtime state (admin)");
            }
        }
        if (page < 2) {
            sender.sendMessage("§7Use §e/graft help " + (page + 1) + " §7for the next page.");
        }
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
        player.sendMessage("§7" + family.description() + '.');
        if (family == GraftFamily.SEQUENCE) {
            player.sendMessage("§8Event Graft currently supports On Hit and On Open.");
        }
    }

    private void handleConcept(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.conceptCatalogGui().open(player);
            player.sendMessage("§7Pick a concept source or use §e/graft concept <name>§7 directly.");
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
        player.sendMessage("§7Pick an inventory item to use as the graft source.");
        player.sendMessage("§8To graft onto an item, hold the target item in your offhand and cast.");
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
        player.sendMessage(nowEnabled ? "§aDebug logging ENABLED. All wand actions will be logged to console." : "§cDebug logging DISABLED.");
    }

    private void handleInspect(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        player.sendMessage("Mode: " + session.family().displayName());
        player.sendMessage("Source: " + (session.source() == null ? "none" : session.source().displayName()));
        player.sendMessage("Aspect: " + (session.selectedAspect() == null ? "none" : session.selectedAspect().displayName()));
        player.sendMessage("Supported aspects for mode: " + formatAspectList(plugin.compatibilityValidator().supportedFamilyAspects(session.family())));
        if (session.source() != null) {
            List<GraftAspect> compatibleAspects = plugin.compatibilityValidator().compatibleSourceAspects(session.family(), session.source());
            player.sendMessage("Available aspects: " + (compatibleAspects.isEmpty() ? "none" : formatAspectList(compatibleAspects)));
            player.sendMessage("Properties: " + formatProperties(session.source()));
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
        for (ActiveGraftSnapshot snapshot : activeGrafts) {
            player.sendMessage("- [" + snapshot.family().displayName() + "] "
                + snapshot.aspectName()
                + ": " + snapshot.sourceName()
                + " -> " + snapshot.targetName()
                + " (" + snapshot.remainingSeconds() + "s)");
        }
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
        sender.sendMessage("- Aspect: " + (session.selectedAspect() == null ? "none" : session.selectedAspect().displayName()));
        sender.sendMessage("- Supported aspects: " + formatAspectList(plugin.compatibilityValidator().supportedFamilyAspects(session.family())));
        if (session.source() != null) {
            List<GraftAspect> compatibleAspects = plugin.compatibilityValidator().compatibleSourceAspects(session.family(), session.source());
            sender.sendMessage("- Source-compatible aspects: " + (compatibleAspects.isEmpty() ? "none" : formatAspectList(compatibleAspects)));
            sender.sendMessage("- Source properties: " + formatProperties(session.source()));
        }

        List<ActiveGraftSnapshot> activeGrafts = plugin.activeGraftRegistry().activeFor(target.getUniqueId());
        sender.sendMessage("- Active graft count: " + activeGrafts.size());
        if (!activeGrafts.isEmpty()) {
            for (ActiveGraftSnapshot snapshot : activeGrafts) {
                sender.sendMessage("  * [" + snapshot.family().displayName() + "] " + snapshot.aspectName() + " -> " + snapshot.targetName() + " (" + snapshot.remainingSeconds() + "s)");
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
        sender.sendMessage("§dGrafting Plugin §7- Use §e/graft help §7for a quick guide.");
        sender.sendMessage("/graft help|mode|concept|inventory|inv|aspect|next|cycle|inspect|clear|active|debug");
        sender.sendMessage("/graft status|clearactive|givefocus|reload");
        sender.sendMessage("§7Modes: §estate §7| §elink §7| §elocation §7| §eevent");
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
        if (args.length == 1) {
            return filter(List.of("help", "mode", "concept", "inventory", "inv", "aspect", "next", "cycle", "inspect", "clear", "active", "debug", "status", "clearactive", "givefocus", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filter(List.of("state", "link", "location", "event"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("concept")) {
            return filter(plugin.conceptRegistry().keys(), args[1]);
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
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("clearactive"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
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
