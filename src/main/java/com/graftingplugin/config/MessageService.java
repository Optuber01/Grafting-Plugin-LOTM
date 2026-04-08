package com.graftingplugin.config;

import com.graftingplugin.GraftingPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public final class MessageService {

    private final GraftingPlugin plugin;
    private YamlConfiguration messages;

    public MessageService(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, String placeholder, String value) {
        send(sender, key, Map.of(placeholder, value));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = messages.getString(key, "<red>Missing message: " + key + "</red>");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        sender.sendMessage(stripMiniMessage(message));
    }

    private String stripMiniMessage(String input) {
        return input.replaceAll("<[^>]+>", "");
    }
}
