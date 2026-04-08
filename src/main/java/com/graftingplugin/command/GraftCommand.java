package com.graftingplugin.command;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.CastSession;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.subject.GraftSubject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            case "active" -> sender.sendMessage("Active graft tracking comes online with the runtime families.");
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
        if (session.source() != null) {
            List<GraftAspect> compatibleAspects = plugin.compatibilityValidator().compatibleSourceAspects(session.family(), session.source());
            player.sendMessage("Available aspects: " + (compatibleAspects.isEmpty() ? "none" : formatAspectList(compatibleAspects)));
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
            return filter(List.of("mode", "concept", "aspect", "inspect", "clear", "active", "givefocus", "reload"), args[0]);
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
            if (source == null) {
                return List.of();
            }
            return filter(plugin.compatibilityValidator().compatibleSourceAspects(session.family(), source).stream()
                .map(GraftAspect::key)
                .toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givefocus")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private String formatAspectList(List<GraftAspect> aspects) {
        return String.join(", ", aspects.stream().map(GraftAspect::key).toList());
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
