package com.graftingplugin.tests;

import com.graftingplugin.conceptgraft.ConceptGraftCatalog;
import com.graftingplugin.conceptgraft.ConceptGraftDefinition;
import com.graftingplugin.conceptgraft.ConceptGraftType;

import java.util.List;

public final class ConceptGraftCatalogTest {

    private ConceptGraftCatalogTest() {
    }

    public static void run() {
        ConceptGraftCatalog catalog = new ConceptGraftCatalog();

        for (ConceptGraftType type : ConceptGraftType.values()) {
            ConceptGraftDefinition def = catalog.get(type).orElseThrow(
                () -> new AssertionError("Missing definition for " + type));
            assert def.type() == type : "Type mismatch for " + type;
            assert def.requiredConceptKey() != null && !def.requiredConceptKey().isEmpty() : "No concept key for " + type;
            assert def.iconMaterial() != null : "No icon material for " + type;
        }

        assert catalog.all().size() == ConceptGraftType.values().length : "Catalog size mismatch";

        List<ConceptGraftDefinition> sunGrafts = catalog.forConceptKey("sun");
        assert sunGrafts.size() == 1 : "Expected 1 sun graft, got " + sunGrafts.size();
        assert sunGrafts.get(0).type() == ConceptGraftType.SUN_TO_GROUND : "Expected SUN_TO_GROUND";

        List<ConceptGraftDefinition> beginningGrafts = catalog.forConceptKey("beginning");
        assert beginningGrafts.size() == 1 : "Expected 1 beginning graft, got " + beginningGrafts.size();
        assert beginningGrafts.get(0).type() == ConceptGraftType.BEGINNING_TO_END : "Expected BEGINNING_TO_END";

        List<ConceptGraftDefinition> concealmentGrafts = catalog.forConceptKey("concealment");
        assert concealmentGrafts.size() == 1 : "Expected 1 concealment graft, got " + concealmentGrafts.size();
        assert concealmentGrafts.get(0).type() == ConceptGraftType.CONCEALMENT_TO_RECOGNITION : "Expected CONCEALMENT_TO_RECOGNITION";

        List<ConceptGraftDefinition> distanceGrafts = catalog.forConceptKey("distance");
        assert distanceGrafts.size() == 1 : "Expected 1 distance graft, got " + distanceGrafts.size();
        assert distanceGrafts.get(0).type() == ConceptGraftType.THRESHOLD_TO_ELSEWHERE : "Expected THRESHOLD_TO_ELSEWHERE";

        assert ConceptGraftType.BEGINNING_TO_END.requiresTwoAnchors() : "BEGINNING_TO_END should require two anchors";
        assert ConceptGraftType.THRESHOLD_TO_ELSEWHERE.requiresTwoAnchors() : "THRESHOLD_TO_ELSEWHERE should require two anchors";
        assert !ConceptGraftType.SUN_TO_GROUND.requiresTwoAnchors() : "SUN_TO_GROUND should not require two anchors";
        assert !ConceptGraftType.NETHER_ZONE.requiresTwoAnchors() : "NETHER_ZONE should not require two anchors";
    }
}
