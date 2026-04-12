package com.graftingplugin;

import com.graftingplugin.active.ActiveGraftRegistry;
import com.graftingplugin.aspect.AspectCatalog;
import com.graftingplugin.cast.CastSessionManager;
import com.graftingplugin.cast.CastSelectionService;
import com.graftingplugin.command.GraftCommand;
import com.graftingplugin.concept.ConceptRegistry;
import com.graftingplugin.config.MessageService;
import com.graftingplugin.config.PluginSettings;
import com.graftingplugin.focus.FocusItemService;
import com.graftingplugin.focus.FocusInteractionListener;
import com.graftingplugin.gui.ConceptCatalogGui;
import com.graftingplugin.gui.InventorySlotPickerGui;
import com.graftingplugin.gui.InventoryTargetPickerGui;
import com.graftingplugin.relation.RelationGraftPlanner;
import com.graftingplugin.relation.RelationGraftService;
import com.graftingplugin.sequence.SequenceTamperPlanner;
import com.graftingplugin.sequence.SequenceTamperService;
import com.graftingplugin.state.StateTransferPlanner;
import com.graftingplugin.state.StateTransferService;
import com.graftingplugin.subject.SubjectResolver;
import com.graftingplugin.topology.TopologyGraftPlanner;
import com.graftingplugin.topology.TopologyGraftService;
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
    private ActiveGraftRegistry activeGraftRegistry;
    private StateTransferPlanner stateTransferPlanner;
    private StateTransferService stateTransferService;
    private RelationGraftPlanner relationGraftPlanner;
    private RelationGraftService relationGraftService;
    private TopologyGraftPlanner topologyGraftPlanner;
    private TopologyGraftService topologyGraftService;
    private SequenceTamperPlanner sequenceTamperPlanner;
    private SequenceTamperService sequenceTamperService;
    private FocusInteractionListener focusInteractionListener;
    private ConceptCatalogGui conceptCatalogGui;
    private InventorySlotPickerGui inventorySlotPickerGui;
    private InventoryTargetPickerGui inventoryTargetPickerGui;

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
        if (sequenceTamperService != null) {
            sequenceTamperService.shutdown();
        }
        if (topologyGraftService != null) {
            topologyGraftService.shutdown();
        }
        if (relationGraftService != null) {
            relationGraftService.shutdown();
        }
        if (stateTransferService != null) {
            stateTransferService.shutdown();
        }
        if (castSessionManager != null) {
            castSessionManager.clearAll();
        }
        if (activeGraftRegistry != null) {
            activeGraftRegistry.clear();
        }
    }

    public void reloadPluginState() {
        if (sequenceTamperService != null) {
            sequenceTamperService.shutdown();
        }
        if (topologyGraftService != null) {
            topologyGraftService.shutdown();
        }
        if (relationGraftService != null) {
            relationGraftService.shutdown();
        }
        if (stateTransferService != null) {
            stateTransferService.shutdown();
        }
        if (castSessionManager != null) {
            castSessionManager.clearAll();
        }
        if (activeGraftRegistry != null) {
            activeGraftRegistry.clear();
        }
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
        if (this.activeGraftRegistry == null) {
            this.activeGraftRegistry = new ActiveGraftRegistry();
        }
        if (this.stateTransferPlanner == null) {
            this.stateTransferPlanner = new StateTransferPlanner();
        }
        if (this.stateTransferService == null) {
            this.stateTransferService = new StateTransferService(this, stateTransferPlanner);
        }
        if (this.relationGraftPlanner == null) {
            this.relationGraftPlanner = new RelationGraftPlanner();
        }
        if (this.relationGraftService == null) {
            this.relationGraftService = new RelationGraftService(this, relationGraftPlanner);
        }
        if (this.topologyGraftPlanner == null) {
            this.topologyGraftPlanner = new TopologyGraftPlanner();
        }
        if (this.topologyGraftService == null) {
            this.topologyGraftService = new TopologyGraftService(this, topologyGraftPlanner);
        }
        if (this.sequenceTamperPlanner == null) {
            this.sequenceTamperPlanner = new SequenceTamperPlanner();
        }
        if (this.sequenceTamperService == null) {
            this.sequenceTamperService = new SequenceTamperService(this, sequenceTamperPlanner);
        }
        if (this.focusInteractionListener == null) {
            this.focusInteractionListener = new FocusInteractionListener(this);
        }
        if (this.conceptCatalogGui == null) {
            this.conceptCatalogGui = new ConceptCatalogGui(this);
        }
        if (this.inventorySlotPickerGui == null) {
            this.inventorySlotPickerGui = new InventorySlotPickerGui(this);
        }
        if (this.inventoryTargetPickerGui == null) {
            this.inventoryTargetPickerGui = new InventoryTargetPickerGui(this);
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
        getServer().getPluginManager().registerEvents(relationGraftService, this);
        getServer().getPluginManager().registerEvents(topologyGraftService, this);
        getServer().getPluginManager().registerEvents(sequenceTamperService, this);
        getServer().getPluginManager().registerEvents(focusInteractionListener, this);
        getServer().getPluginManager().registerEvents(conceptCatalogGui, this);
        getServer().getPluginManager().registerEvents(inventorySlotPickerGui, this);
        getServer().getPluginManager().registerEvents(inventoryTargetPickerGui, this);
        focusInteractionListener.logStartup();
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

    public ActiveGraftRegistry activeGraftRegistry() {
        return activeGraftRegistry;
    }

    public StateTransferPlanner stateTransferPlanner() {
        return stateTransferPlanner;
    }

    public StateTransferService stateTransferService() {
        return stateTransferService;
    }

    public RelationGraftService relationGraftService() {
        return relationGraftService;
    }

    public TopologyGraftService topologyGraftService() {
        return topologyGraftService;
    }

    public SequenceTamperService sequenceTamperService() {
        return sequenceTamperService;
    }

    public ConceptCatalogGui conceptCatalogGui() {
        return conceptCatalogGui;
    }

    public InventorySlotPickerGui inventorySlotPickerGui() {
        return inventorySlotPickerGui;
    }

    public InventoryTargetPickerGui inventoryTargetPickerGui() {
        return inventoryTargetPickerGui;
    }

    public FocusInteractionListener focusInteractionListener() {
        return focusInteractionListener;
    }
}
