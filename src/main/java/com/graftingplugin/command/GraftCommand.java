package com.graftingplugin.command;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.active.ActiveGraftSnapshot;
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
            case "mode" -> handleMode(sender, args);
            case "concept" -> handleConcept(sender, args);
            case "aspect" -> handleAspect(sender, args);
            case "clear" -> handleClear(sender);
            case "inspect" -> handleInspect(sender);
            case "active" -> handleActive(sender);
            case "status" -> handleStatus(sender, args);
            case "clearactive" -> handleClearActive(sender, args);
            case "givefocus" -> handleGiveFocus(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleMode(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /graft mode <state|relation|topology|sequence>");
            return;
        }
        GraftFamily family = GraftFamily.fromInput(args[1]).orElse(null);
        if (family == null) {
            sender.sendMessage("Unknown graft family.");
            return;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        session.clearSelection();
        session.setFamily(family);
        plugin.messages().send(player, "mode-set", "family", family.displayName());
    }

    private void handleConcept(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /graft concept <concept>");
            return;
        }
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        GraftSubject source = plugin.subjectResolver().resolveConcept(args[1]).orElse(null);
        if (source == null) {
            plugin.messages().send(player, "unknown-concept", "concept", args[1]);
            return;
        }
        if (!plugin.castSelectionService().armSource(player, source)) {
            plugin.messages().send(player, "concept-incompatible", java.util.Map.of(
                "concept", source.displayName(),
                "family", session.family().displayName()
            ));
        }
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

    private void handleClear(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        plugin.castSessionManager().clear(player.getUniqueId());
        plugin.messages().send(player, "cast-cleared");
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
            player.sendMessage("- [" + snapshot.family().key() + "] "
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
        }

        List<ActiveGraftSnapshot> activeGrafts = plugin.activeGraftRegistry().activeFor(target.getUniqueId());
        sender.sendMessage("- Active graft count: " + activeGrafts.size());
        if (!activeGrafts.isEmpty()) {
            for (ActiveGraftSnapshot snapshot : activeGrafts) {
                sender.sendMessage("  * [" + snapshot.family().key() + "] " + snapshot.aspectName() + " -> " + snapshot.targetName() + " (" + snapshot.remainingSeconds() + "s)");
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
        sender.sendMessage("/graft mode <state|relation|topology|sequence>");
        sender.sendMessage("/graft concept <concept>");
        sender.sendMessage("/graft aspect <aspect>");
        sender.sendMessage("/graft inspect");
        sender.sendMessage("/graft clear");
        sender.sendMessage("/graft active");
        sender.sendMessage("/graft status [player]");
        sender.sendMessage("/graft clearactive [player]");
        sender.sendMessage("/graft givefocus [player]");
        sender.sendMessage("/graft reload");
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
            return filter(List.of("mode", "concept", "aspect", "inspect", "clear", "active", "status", "clearactive", "givefocus", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return filter(List.of("state", "relation", "topology", "sequence"), args[1]);
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
