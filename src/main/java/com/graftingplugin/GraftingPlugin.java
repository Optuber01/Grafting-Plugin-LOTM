package com.graftingplugin;

import com.graftingplugin.aspect.AspectCatalog;
import com.graftingplugin.cast.CastSessionManager;
import com.graftingplugin.cast.CastSelectionService;
import com.graftingplugin.command.GraftCommand;
import com.graftingplugin.concept.ConceptRegistry;
import com.graftingplugin.config.MessageService;
import com.graftingplugin.config.PluginSettings;
import com.graftingplugin.focus.FocusItemService;
import com.graftingplugin.focus.FocusInteractionListener;
import com.graftingplugin.state.StateTransferPlanner;
import com.graftingplugin.state.StateTransferService;
import com.graftingplugin.subject.SubjectResolver;
import com.graftingplugin.validation.GraftCompatibilityValidator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GraftingPlugin extends JavaPlugin {

    private PluginSettings settings;
    private MessageService messages;
    private FocusItemService focusItemService;
    private CastSessionManager castSessionManager;
    private AspectCatalog aspectCatalog;
    private ConceptRegistry conceptRegistry;
    private SubjectResolver subjectResolver;
    private GraftCompatibilityValidator compatibilityValidator;
    private CastSelectionService castSelectionService;
    private StateTransferPlanner stateTransferPlanner;
    private StateTransferService stateTransferService;
    private FocusInteractionListener focusInteractionListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        reloadPluginState();
        registerCommands();
        registerListeners();
    }

    @Override
    public void onDisable() {
        if (stateTransferService != null) {
            stateTransferService.shutdown();
        }
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
        this.aspectCatalog = new AspectCatalog();
        this.conceptRegistry = ConceptRegistry.fromConfig(getConfig());
        this.subjectResolver = new SubjectResolver(aspectCatalog, conceptRegistry);
        this.compatibilityValidator = new GraftCompatibilityValidator();
        if (this.castSelectionService == null) {
            this.castSelectionService = new CastSelectionService(this);
        }
        if (this.stateTransferPlanner == null) {
            this.stateTransferPlanner = new StateTransferPlanner();
        }
        if (this.stateTransferService == null) {
            this.stateTransferService = new StateTransferService(this, stateTransferPlanner);
        }
        if (this.focusInteractionListener == null) {
            this.focusInteractionListener = new FocusInteractionListener(this);
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

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(stateTransferService, this);
        getServer().getPluginManager().registerEvents(focusInteractionListener, this);
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

    public AspectCatalog aspectCatalog() {
        return aspectCatalog;
    }

    public ConceptRegistry conceptRegistry() {
        return conceptRegistry;
    }

    public SubjectResolver subjectResolver() {
        return subjectResolver;
    }

    public GraftCompatibilityValidator compatibilityValidator() {
        return compatibilityValidator;
    }

    public CastSelectionService castSelectionService() {
        return castSelectionService;
    }

    public StateTransferPlanner stateTransferPlanner() {
        return stateTransferPlanner;
    }

    public StateTransferService stateTransferService() {
        return stateTransferService;
    }
}
