package com.graftingplugin.conceptgraft;

public enum ConceptGraftType {

    SUN_TO_GROUND("sun-to-ground", "Sun \u2192 Ground", "Graft solar identity onto a zone: daylight, burning undead, melting ice, accelerated growth."),
    SKY_TO_GROUND("sky-to-ground", "Sky \u2192 Ground", "Graft sky rules onto a zone: levitation, no fall damage, upward drift."),
    NETHER_ZONE("nether-zone", "Nether \u2192 Zone", "Graft nether identity onto a zone: water evaporates, eternal fire, fire resistance."),
    END_ZONE("end-zone", "End \u2192 Zone", "Graft end identity onto a zone: random micro-teleports, void echoes, ender resonance."),
    OVERWORLD_ZONE("overworld-zone", "Overworld \u2192 Zone", "Graft overworld normality: cancel alien rules, restore natural law."),
    BEGINNING_TO_END("beginning-to-end", "Beginning \u2194 End", "Loop beginning and end together: create a spatial cycle between two anchors.");

    private final String key;
    private final String displayName;
    private final String description;

    ConceptGraftType(String key, String displayName, String description) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean requiresTwoAnchors() {
        return this == BEGINNING_TO_END;
    }
}
