package com.graftingplugin.conceptgraft;

import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger.ConceptRuntimeKind;

public final class ConceptGraftPresentation {

    public String activeLabelFor(ConceptGraftType type) {
        return switch (type) {
            case BEGINNING_TO_END -> "Conceptual Identity";
            case THRESHOLD_TO_ELSEWHERE, CONCEALMENT_TO_RECOGNITION -> "Conceptual Rewrite";
            default -> "Conceptual Law";
        };
    }

    public String conceptualSourceName(ConceptGraftType type) {
        return switch (type) {
            case SUN_TO_GROUND -> "solar law";
            case SKY_TO_GROUND -> "sky law";
            case NETHER_ZONE -> "nether law";
            case END_ZONE -> "end law";
            case OVERWORLD_ZONE -> "overworld law";
            case CONCEALMENT_TO_RECOGNITION -> "recognition rewrite";
            case BEGINNING_TO_END -> "shared place-identity";
            case THRESHOLD_TO_ELSEWHERE -> "threshold rewrite";
        };
    }

    public ConceptRuntimeKind runtimeKindFor(ConceptGraftType type) {
        return switch (type) {
            case BEGINNING_TO_END -> ConceptRuntimeKind.IDENTITY_LOOP;
            case THRESHOLD_TO_ELSEWHERE -> ConceptRuntimeKind.RELATION_RELAY;
            case CONCEALMENT_TO_RECOGNITION -> ConceptRuntimeKind.RELATION_ZONE;
            default -> ConceptRuntimeKind.LAW_ZONE;
        };
    }

    public String entryActionBarFor(ConceptGraftType type) {
        return switch (type) {
            case SUN_TO_GROUND -> "§6Solar law dominates this ground";
            case SKY_TO_GROUND -> "§bSky law denies falling here";
            case NETHER_ZONE -> "§cNether law rejects water here";
            case END_ZONE -> "§5End law loosens position here";
            case OVERWORLD_ZONE -> "§aOverworld law restores natural order";
            case CONCEALMENT_TO_RECOGNITION -> "§8Recognition slips away here";
            case BEGINNING_TO_END -> "§5Beginning and end are one path here";
            case THRESHOLD_TO_ELSEWHERE -> "§5This threshold opens elsewhere";
        };
    }

    public String exitActionBarFor(ConceptGraftType type) {
        return switch (type) {
            case BEGINNING_TO_END -> "§7You leave the shared path identity";
            case THRESHOLD_TO_ELSEWHERE -> "§7You leave the rewritten threshold";
            case CONCEALMENT_TO_RECOGNITION -> "§7Recognition settles back into place";
            default -> "§7You leave imposed conceptual law";
        };
    }

    public String triggerActionBarFor(ConceptGraftType type) {
        return switch (type) {
            case BEGINNING_TO_END -> "§5Crossing here resolves elsewhere";
            case THRESHOLD_TO_ELSEWHERE -> "§5Opening here reveals elsewhere";
            case CONCEALMENT_TO_RECOGNITION -> "§8Hostile recognition slips past you";
            case END_ZONE -> "§5End law displaces you";
            default -> entryActionBarFor(type);
        };
    }
}
