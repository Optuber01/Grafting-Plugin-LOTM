package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.concept.ConceptRegistry;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public final class ConceptRegistryTest {

    private ConceptRegistryTest() {
    }

    public static void run() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("concepts.sun.name", "Sun");
        config.set("concepts.sun.aspects", List.of("light", "heat", "ignite"));
        config.set("concepts.distance.name", "Distance");
        config.set("concepts.distance.aspects", List.of("near", "far"));

        ConceptRegistry registry = ConceptRegistry.fromConfig(config);
        GraftSubject sun = registry.resolve("Sun").orElseThrow();

        if (sun.kind() != SubjectKind.CONCEPT) {
            throw new AssertionError("Expected concept subject kind but got " + sun.kind());
        }
        if (!sun.aspects().containsAll(List.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE))) {
            throw new AssertionError("Sun concept aspects were not loaded correctly: " + sun.aspects());
        }
        if (registry.resolve("distance").isEmpty()) {
            throw new AssertionError("Expected distance concept to resolve");
        }
    }
}
