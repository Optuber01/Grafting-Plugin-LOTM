package com.graftingplugin.conceptgraft;

import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConceptGraftCatalog {

    private final Map<ConceptGraftType, ConceptGraftDefinition> definitions;

    public ConceptGraftCatalog() {
        this.definitions = buildDefinitions();
    }

    public Optional<ConceptGraftDefinition> get(ConceptGraftType type) {
        return Optional.ofNullable(definitions.get(type));
    }

    public Collection<ConceptGraftDefinition> all() {
        return definitions.values();
    }

    public List<ConceptGraftDefinition> forConceptKey(String conceptKey) {
        return definitions.values().stream()
            .filter(def -> def.requiredConceptKey().equals(conceptKey))
            .toList();
    }

    private static Map<ConceptGraftType, ConceptGraftDefinition> buildDefinitions() {
        Map<ConceptGraftType, ConceptGraftDefinition> defs = new LinkedHashMap<>();
        defs.put(ConceptGraftType.SUN_TO_GROUND, new ConceptGraftDefinition(ConceptGraftType.SUN_TO_GROUND, Material.SUNFLOWER, "sun"));
        defs.put(ConceptGraftType.SKY_TO_GROUND, new ConceptGraftDefinition(ConceptGraftType.SKY_TO_GROUND, Material.FEATHER, "sky"));
        defs.put(ConceptGraftType.NETHER_ZONE, new ConceptGraftDefinition(ConceptGraftType.NETHER_ZONE, Material.NETHERRACK, "nether"));
        defs.put(ConceptGraftType.END_ZONE, new ConceptGraftDefinition(ConceptGraftType.END_ZONE, Material.END_STONE, "end-dimension"));
        defs.put(ConceptGraftType.OVERWORLD_ZONE, new ConceptGraftDefinition(ConceptGraftType.OVERWORLD_ZONE, Material.GRASS_BLOCK, "overworld"));
        defs.put(ConceptGraftType.CONCEALMENT_TO_RECOGNITION, new ConceptGraftDefinition(ConceptGraftType.CONCEALMENT_TO_RECOGNITION, Material.BLACK_CANDLE, "concealment"));
        defs.put(ConceptGraftType.BEGINNING_TO_END, new ConceptGraftDefinition(ConceptGraftType.BEGINNING_TO_END, Material.CLOCK, "beginning"));
        defs.put(ConceptGraftType.THRESHOLD_TO_ELSEWHERE, new ConceptGraftDefinition(ConceptGraftType.THRESHOLD_TO_ELSEWHERE, Material.ENDER_CHEST, "distance"));
        return Map.copyOf(defs);
    }
}
