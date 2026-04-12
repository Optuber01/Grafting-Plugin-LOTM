package com.graftingplugin.tests;

import com.graftingplugin.aspect.AspectCatalog;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.concept.ConceptRegistry;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import com.graftingplugin.subject.SubjectResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public final class FluidVoidSubjectTest {

    private FluidVoidSubjectTest() {
    }

    public static void run() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("concepts.sun.name", "Sun");
        config.set("concepts.sun.aspects", List.of("light", "heat", "ignite"));

        SubjectResolver resolver = new SubjectResolver(new AspectCatalog(), ConceptRegistry.fromConfig(config));


        GraftSubject water = resolver.resolveBlock(Material.WATER).orElseThrow();
        if (water.kind() != SubjectKind.FLUID) {
            throw new AssertionError("Water should resolve as FLUID, got " + water.kind());
        }
        if (!water.aspects().contains(GraftAspect.FREEZE)) {
            throw new AssertionError("Water should have FREEZE aspect: " + water.aspects());
        }
        if (!water.aspects().contains(GraftAspect.SLOW)) {
            throw new AssertionError("Water should have SLOW aspect: " + water.aspects());
        }


        GraftSubject lava = resolver.resolveBlock(Material.LAVA).orElseThrow();
        if (lava.kind() != SubjectKind.FLUID) {
            throw new AssertionError("Lava should resolve as FLUID, got " + lava.kind());
        }
        if (!lava.aspects().contains(GraftAspect.HEAT)) {
            throw new AssertionError("Lava should have HEAT aspect: " + lava.aspects());
        }
        if (lava.properties().get(DynamicProperty.THERMAL) <= 0) {
            throw new AssertionError("Lava should have positive thermal: " + lava.properties());
        }


        GraftSubject voidSubject = resolver.resolveVoid().orElseThrow();
        if (voidSubject.kind() != SubjectKind.VOID) {
            throw new AssertionError("Void should resolve as VOID, got " + voidSubject.kind());
        }
        if (!voidSubject.aspects().contains(GraftAspect.CONCEAL)) {
            throw new AssertionError("Void should have CONCEAL aspect: " + voidSubject.aspects());
        }


        if (resolver.resolveBlock(Material.AIR).isPresent()) {
            throw new AssertionError("AIR should not resolve as a block");
        }


        GraftSubject directFluid = resolver.resolveFluid(Material.WATER).orElseThrow();
        if (directFluid.kind() != SubjectKind.FLUID) {
            throw new AssertionError("Direct fluid resolve should produce FLUID kind");
        }


        if (resolver.resolveFluid(Material.STONE).isPresent()) {
            throw new AssertionError("Stone should not resolve as fluid");
        }


        AspectCatalog catalog = new AspectCatalog();
        assert catalog.isFluid(Material.WATER) : "WATER should be detected as fluid";
        assert catalog.isFluid(Material.LAVA) : "LAVA should be detected as fluid";
        assert !catalog.isFluid(Material.STONE) : "STONE should not be detected as fluid";
        assert !catalog.isFluid(Material.AIR) : "AIR should not be detected as fluid";
    }
}
