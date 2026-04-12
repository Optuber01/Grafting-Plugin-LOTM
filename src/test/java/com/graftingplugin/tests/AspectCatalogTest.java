package com.graftingplugin.tests;

import com.graftingplugin.aspect.AspectCatalog;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import org.bukkit.Material;

import java.util.Set;

public final class AspectCatalogTest {

    private AspectCatalogTest() {
    }

    public static void run() {
        AspectCatalog catalog = new AspectCatalog();

        assertContains(catalog.blockAspects(Material.LAVA), GraftAspect.HEAT, GraftAspect.IGNITE);
        assertContains(catalog.blockAspects(Material.SLIME_BLOCK), GraftAspect.BOUNCE);
        assertContains(catalog.blockAspects(Material.CHEST), GraftAspect.ON_OPEN, GraftAspect.CONTAINER_LINK, GraftAspect.DESTINATION);
        assertContains(catalog.blockAspects(Material.SPRUCE_DOOR), GraftAspect.ON_OPEN, GraftAspect.ENTRY, GraftAspect.EXIT);
        assertContains(catalog.blockAspects(Material.LEVER), GraftAspect.ON_OPEN);
        assertContains(catalog.itemAspects(Material.SPLASH_POTION), GraftAspect.ON_HIT);

        assertContains(catalog.blockAspects(Material.STONE), GraftAspect.ANCHOR);
        assertContains(catalog.blockAspects(Material.DIRT), GraftAspect.ANCHOR);
        assertContains(catalog.blockAspects(Material.OBSIDIAN), GraftAspect.HEAVY, GraftAspect.ANCHOR);

        DynamicPropertyProfile stoneProfile = catalog.blockProperties(Material.STONE);
        assert stoneProfile.get(DynamicProperty.MASS) > 0 : "Stone should have positive mass";
        DynamicPropertyProfile lavaProfile = catalog.blockProperties(Material.LAVA);
        assert lavaProfile.get(DynamicProperty.THERMAL) > 0 : "Lava should have positive thermal";
        DynamicPropertyProfile iceProfile = catalog.blockProperties(Material.BLUE_ICE);
        assert iceProfile.get(DynamicProperty.THERMAL) < 0 : "Blue ice should have negative thermal";

        assert catalog.blockAspects(Material.AIR).isEmpty() : "Air should have no aspects";
        assert catalog.blockProperties(Material.AIR) == DynamicPropertyProfile.EMPTY : "Air should have empty profile";
    }

    private static void assertContains(Set<GraftAspect> actual, GraftAspect... expected) {
        for (GraftAspect aspect : expected) {
            if (!actual.contains(aspect)) {
                throw new AssertionError("Expected aspect " + aspect + " in " + actual);
            }
        }
    }
}
