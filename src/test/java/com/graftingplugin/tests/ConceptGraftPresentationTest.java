package com.graftingplugin.tests;

import com.graftingplugin.conceptgraft.ConceptGraftPresentation;
import com.graftingplugin.conceptgraft.ConceptGraftType;
import com.graftingplugin.conceptgraft.ConceptualRuntimeLedger.ConceptRuntimeKind;

public final class ConceptGraftPresentationTest {

    private ConceptGraftPresentationTest() {
    }

    public static void run() {
        ConceptGraftPresentation presentation = new ConceptGraftPresentation();

        if (!"Conceptual Law".equals(presentation.activeLabelFor(ConceptGraftType.SUN_TO_GROUND))) {
            throw new AssertionError("Sun law should present as Conceptual Law");
        }
        if (!"Conceptual Rewrite".equals(presentation.activeLabelFor(ConceptGraftType.CONCEALMENT_TO_RECOGNITION))) {
            throw new AssertionError("Recognition rewrite should present as Conceptual Rewrite");
        }
        if (!"Conceptual Identity".equals(presentation.activeLabelFor(ConceptGraftType.BEGINNING_TO_END))) {
            throw new AssertionError("Beginning ↔ End should present as Conceptual Identity");
        }
        if (presentation.runtimeKindFor(ConceptGraftType.THRESHOLD_TO_ELSEWHERE) != ConceptRuntimeKind.RELATION_RELAY) {
            throw new AssertionError("Threshold rewrite should be a relation relay runtime");
        }
        if (!presentation.entryActionBarFor(ConceptGraftType.SKY_TO_GROUND).contains("Sky law")) {
            throw new AssertionError("Sky entry feedback should mention sky law");
        }
        if (!presentation.exitActionBarFor(ConceptGraftType.CONCEALMENT_TO_RECOGNITION).contains("Recognition")) {
            throw new AssertionError("Recognition exit feedback should mention recognition");
        }
        if (!presentation.triggerActionBarFor(ConceptGraftType.BEGINNING_TO_END).contains("elsewhere")) {
            throw new AssertionError("Loop trigger feedback should explain elsewhere resolution");
        }
    }
}
