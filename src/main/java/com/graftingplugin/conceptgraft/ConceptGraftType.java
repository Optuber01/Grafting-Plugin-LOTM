package com.graftingplugin.conceptgraft;

public enum ConceptGraftType {

    SUN_TO_GROUND("sun-to-ground", "Sun \u2192 Ground", "Impose solar law on a zone: light cannot be blocked, cold cannot persist, undead cannot endure, and growth is forced."),
    SKY_TO_GROUND("sky-to-ground", "Sky \u2192 Ground", "Impose sky law on a zone: gravity is suspended, falling is forbidden, and unsupported weight cannot descend."),
    NETHER_ZONE("nether-zone", "Nether \u2192 Zone", "Impose nether law on a zone: water cannot exist, fire cannot be quenched, and heat protects instead of harms."),
    END_ZONE("end-zone", "End \u2192 Zone", "Impose end law on a zone: position becomes uncertain and space shifts without warning for all who remain."),
    OVERWORLD_ZONE("overworld-zone", "Overworld \u2192 Zone", "Reclaim overworld law over a zone: foreign rules are rejected, imposed identities are stripped, and natural order is restored."),
    BEGINNING_TO_END("beginning-to-end", "Beginning \u2194 End", "Identify two locations as the same place: crossing one brings you to the other, as if no distance exists.");

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
