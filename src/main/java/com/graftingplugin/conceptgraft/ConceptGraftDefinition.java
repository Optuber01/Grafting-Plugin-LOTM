package com.graftingplugin.conceptgraft;

import org.bukkit.Material;

public record ConceptGraftDefinition(
    ConceptGraftType type,
    Material iconMaterial,
    String requiredConceptKey
) {

    public String displayName() {
        return type.displayName();
    }

    public String description() {
        return type.description();
    }

    public String key() {
        return type.key();
    }
}
