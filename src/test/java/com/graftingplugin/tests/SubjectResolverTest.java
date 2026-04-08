package com.graftingplugin.tests;

import com.graftingplugin.aspect.AspectCatalog;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.concept.ConceptRegistry;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import com.graftingplugin.subject.SubjectResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public final class SubjectResolverTest {

    private SubjectResolverTest() {
    }

    public static void run() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("concepts.sun.name", "Sun");
        config.set("concepts.sun.aspects", List.of("light", "heat", "ignite"));

        SubjectResolver resolver = new SubjectResolver(new AspectCatalog(), ConceptRegistry.fromConfig(config));

        GraftSubject chest = resolver.resolveBlock(Material.CHEST).orElseThrow();
        if (chest.kind() != SubjectKind.CONTAINER) {
            throw new AssertionError("Expected chest to resolve as container, got " + chest.kind());
        }
        if (!chest.aspects().containsAll(List.of(GraftAspect.OPEN, GraftAspect.ON_OPEN, GraftAspect.CONTAINER_LINK, GraftAspect.DESTINATION))) {
            throw new AssertionError("Chest aspects were incomplete: " + chest.aspects());
        }

        GraftSubject sun = resolver.resolveConcept("sun").orElseThrow();
        if (sun.kind() != SubjectKind.CONCEPT || !sun.aspects().contains(GraftAspect.LIGHT)) {
            throw new AssertionError("Sun concept did not resolve correctly: " + sun);
        }

        GraftSubject splashPotion = resolver.resolveItem(Material.SPLASH_POTION).orElseThrow();
        if (splashPotion.kind() != SubjectKind.POTION || !splashPotion.aspects().contains(GraftAspect.ON_HIT)) {
            throw new AssertionError("Splash potion did not resolve correctly: " + splashPotion);
        }
    }
}
