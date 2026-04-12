package com.graftingplugin.tests;

import com.graftingplugin.cast.GraftFamily;

public final class GraftFamilyAliasTest {

    private GraftFamilyAliasTest() {
    }

    public static void run() {
        assertFamily("state", GraftFamily.STATE);
        assertFamily("link", GraftFamily.RELATION);
        assertFamily("location", GraftFamily.TOPOLOGY);
        assertFamily("event", GraftFamily.SEQUENCE);

        assertFamily("attributes", GraftFamily.STATE);
        assertFamily("connection", GraftFamily.RELATION);
        assertFamily("spatial", GraftFamily.TOPOLOGY);
        assertFamily("trigger", GraftFamily.SEQUENCE);

        assertFamily("relation", GraftFamily.RELATION);
        assertFamily("topology", GraftFamily.TOPOLOGY);
        assertFamily("sequence", GraftFamily.SEQUENCE);

        assertKey(GraftFamily.STATE, "state");
        assertKey(GraftFamily.RELATION, "link");
        assertKey(GraftFamily.TOPOLOGY, "location");
        assertKey(GraftFamily.SEQUENCE, "event");
    }

    private static void assertFamily(String input, GraftFamily expected) {
        GraftFamily actual = GraftFamily.fromInput(input).orElseThrow(() -> new AssertionError("Expected family for input: " + input));
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " for input " + input + " but got " + actual);
        }
    }

    private static void assertKey(GraftFamily family, String expected) {
        if (!family.key().equals(expected)) {
            throw new AssertionError("Expected key " + expected + " for " + family + " but got " + family.key());
        }
    }
}
