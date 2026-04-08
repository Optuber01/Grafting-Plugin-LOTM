package com.graftingplugin.tests;

public final class TestHarness {

    public static void main(String[] args) {
        run("AspectCatalogTest", AspectCatalogTest::run);
        run("ConceptRegistryTest", ConceptRegistryTest::run);
        run("PluginSettingsTest", PluginSettingsTest::run);
        run("SubjectResolverTest", SubjectResolverTest::run);
        run("GraftCompatibilityValidatorTest", GraftCompatibilityValidatorTest::run);
        run("StateTransferPlannerTest", StateTransferPlannerTest::run);
        System.out.println("All milestone-3 tests passed.");
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
