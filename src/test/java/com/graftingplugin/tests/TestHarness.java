package com.graftingplugin.tests;

public final class TestHarness {

    public static void main(String[] args) {
        run("ActiveGraftRegistryTest", ActiveGraftRegistryTest::run);
        run("AspectCatalogTest", AspectCatalogTest::run);
        run("ConceptRegistryTest", ConceptRegistryTest::run);
        run("PluginSettingsTest", PluginSettingsTest::run);
        run("SubjectResolverTest", SubjectResolverTest::run);
        run("GraftCompatibilityValidatorTest", GraftCompatibilityValidatorTest::run);
        run("StateTransferPlannerTest", StateTransferPlannerTest::run);
        run("RelationGraftPlannerTest", RelationGraftPlannerTest::run);
        run("TopologyGraftPlannerTest", TopologyGraftPlannerTest::run);
        run("SequenceTamperPlannerTest", SequenceTamperPlannerTest::run);
        run("GraftFamilyAliasTest", GraftFamilyAliasTest::run);
        run("FluidVoidSubjectTest", FluidVoidSubjectTest::run);
        run("PracticalGraftWorkflowTest", PracticalGraftWorkflowTest::run);
        run("ConceptGraftCatalogTest", ConceptGraftCatalogTest::run);
        run("ConceptGraftSettingsTest", ConceptGraftSettingsTest::run);
        run("ConceptGraftPresentationTest", ConceptGraftPresentationTest::run);
        run("ConceptPreviewFeedbackGateTest", ConceptPreviewFeedbackGateTest::run);
        run("ConceptualRuntimeLedgerTest", ConceptualRuntimeLedgerTest::run);
        System.out.println("All phase-2 and conceptual-graft tests passed.");
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
            System.out.println("PASS " + name);
        } catch (Throwable throwable) {
            System.err.println("FAIL " + name + ": " + throwable.getMessage());
            throwable.printStackTrace(System.err);
            throw new RuntimeException(throwable);
        }
    }
}
