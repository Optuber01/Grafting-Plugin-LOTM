package com.graftingplugin.tests;

import com.graftingplugin.aspect.AspectCatalog;
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
        assertContains(catalog.blockAspects(Material.CHEST), GraftAspect.OPEN, GraftAspect.ON_OPEN, GraftAspect.CONTAINER_LINK, GraftAspect.DESTINATION);
        assertContains(catalog.itemAspects(Material.SPLASH_POTION), GraftAspect.ON_HIT);
    }

    private static void assertContains(Set<GraftAspect> actual, GraftAspect... expected) {
        for (GraftAspect aspect : expected) {
            if (!actual.contains(aspect)) {
                throw new AssertionError("Expected aspect " + aspect + " in " + actual);
            }
        }
    }
}
