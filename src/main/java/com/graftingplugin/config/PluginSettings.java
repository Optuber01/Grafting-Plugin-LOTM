package com.graftingplugin.config;

import com.graftingplugin.conceptgraft.ConceptGraftSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
    Material focusMaterial,
    String focusName,
    int interactionRange,
    boolean focusDebugEnabledByDefault,
    StateTransferSettings stateTransferSettings,
    RelationGraftSettings relationGraftSettings,
    TopologyGraftSettings topologyGraftSettings,
    SequenceTamperSettings sequenceTamperSettings,
    ConceptGraftSettings conceptGraftSettings
) {

    public static PluginSettings fromConfig(FileConfiguration config) {
        Material focusMaterial = Material.matchMaterial(config.getString("focus.material", "BLAZE_ROD"));
        if (focusMaterial == null) {
            focusMaterial = Material.BLAZE_ROD;
        }
        String focusName = config.getString("focus.name", "<light_purple>Mystic Focus</light_purple>");
        int interactionRange = Math.max(1, config.getInt("focus.interaction-range", 8));
        boolean focusDebugEnabledByDefault = config.getBoolean("focus.debug-enabled-by-default", false);
        return new PluginSettings(
            focusMaterial,
            focusName,
            interactionRange,
            focusDebugEnabledByDefault,
            StateTransferSettings.fromConfig(config),
            RelationGraftSettings.fromConfig(config),
            TopologyGraftSettings.fromConfig(config),
            SequenceTamperSettings.fromConfig(config),
            ConceptGraftSettings.fromConfig(config)
        );
    }
}
