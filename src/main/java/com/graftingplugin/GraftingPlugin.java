package com.graftingplugin;

import com.graftingplugin.cast.CastSessionManager;
import com.graftingplugin.command.GraftCommand;
import com.graftingplugin.config.MessageService;
import com.graftingplugin.config.PluginSettings;
import com.graftingplugin.focus.FocusItemService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GraftingPlugin extends JavaPlugin {

    private PluginSettings settings;
    private MessageService messages;
    private FocusItemService focusItemService;
    private CastSessionManager castSessionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        reloadPluginState();
        registerCommands();
    }

    @Override
    public void onDisable() {
        if (castSessionManager != null) {
            castSessionManager.clearAll();
        }
    }

    public void reloadPluginState() {
        reloadConfig();
        this.settings = PluginSettings.fromConfig(getConfig());
        if (this.messages == null) {
            this.messages = new MessageService(this);
        }
        this.messages.reload();
        this.focusItemService = new FocusItemService(this, settings);
        if (this.castSessionManager == null) {
            this.castSessionManager = new CastSessionManager();
        }
    }

    private void registerCommands() {
        GraftCommand graftCommand = new GraftCommand(this);
        PluginCommand command = getCommand("graft");
        if (command == null) {
            throw new IllegalStateException("Command graft is not defined");
        }
        command.setExecutor(graftCommand);
        command.setTabCompleter(graftCommand);
    }

    public PluginSettings settings() {
        return settings;
    }

    public MessageService messages() {
        return messages;
    }

    public FocusItemService focusItemService() {
        return focusItemService;
    }

    public CastSessionManager castSessionManager() {
        return castSessionManager;
    }
}
