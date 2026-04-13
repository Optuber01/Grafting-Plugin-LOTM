package com.graftingplugin.conceptgraft;

public enum ConceptGraftType {

    SUN_TO_GROUND("sun-to-ground", "Sun \u2192 Ground", "Impose solar law on a zone: light cannot be blocked, cold cannot persist, undead cannot endure, and growth is forced.", ConceptPlacementStyle.ZONE),
    SKY_TO_GROUND("sky-to-ground", "Sky \u2192 Ground", "Impose sky law on a zone: gravity is suspended, falling is forbidden, and unsupported weight cannot descend.", ConceptPlacementStyle.ZONE),
    NETHER_ZONE("nether-zone", "Nether \u2192 Zone", "Impose nether law on a zone: water cannot exist, fire cannot be quenched, and heat protects instead of harms.", ConceptPlacementStyle.ZONE),
    END_ZONE("end-zone", "End \u2192 Zone", "Impose end law on a zone: position becomes uncertain and space shifts without warning for all who remain.", ConceptPlacementStyle.ZONE),
    OVERWORLD_ZONE("overworld-zone", "Overworld \u2192 Zone", "Reclaim overworld law over a zone: foreign rules are rejected, imposed identities are stripped, and natural order is restored.", ConceptPlacementStyle.ZONE),
    CONCEALMENT_TO_RECOGNITION("concealment-to-recognition", "Concealment \u2192 Recognition", "Rewrite hostile recognition inside a zone: pursuit loses its target, and intruders are treated as unknown to local hostility.", ConceptPlacementStyle.ZONE),
    BEGINNING_TO_END("beginning-to-end", "Beginning \u2194 End", "Identify two locations as the same place: crossing one brings you to the other, as if no distance exists.", ConceptPlacementStyle.DUAL_ANCHOR_CASTER_FIRST),
    THRESHOLD_TO_ELSEWHERE("threshold-to-elsewhere", "Threshold \u2192 Elsewhere", "Identify one container threshold with another interior: opening here reveals elsewhere.", ConceptPlacementStyle.DUAL_ANCHOR_BLOCKS);

    private final String key;
    private final String displayName;
    private final String description;
    private final ConceptPlacementStyle placementStyle;

    ConceptGraftType(String key, String displayName, String description, ConceptPlacementStyle placementStyle) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.placementStyle = placementStyle;
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

    public ConceptPlacementStyle placementStyle() {
        return placementStyle;
    }

    public boolean requiresTwoAnchors() {
        return placementStyle != ConceptPlacementStyle.ZONE;
    }

    public boolean firstAnchorComesFromCaster() {
        return placementStyle == ConceptPlacementStyle.DUAL_ANCHOR_CASTER_FIRST;
    }
}
