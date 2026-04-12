package com.graftingplugin.tests;

import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.sequence.SequenceTamperMode;
import com.graftingplugin.sequence.SequenceTamperPlan;
import com.graftingplugin.sequence.SequenceTamperPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Map;
import java.util.Set;

public final class SequenceTamperPlannerTest {

    private SequenceTamperPlannerTest() {
    }

    public static void run() {
        SequenceTamperPlanner planner = new SequenceTamperPlanner();

        GraftSubject splashPotion = new GraftSubject(
            "potion:splash_potion",
            "Splash Potion",
            SubjectKind.POTION,
            Set.of(GraftAspect.ON_HIT, GraftAspect.HEAT),
            new DynamicPropertyProfile(Map.of(DynamicProperty.THERMAL, 3.0))
        );
        GraftSubject arrow = new GraftSubject("projectile:arrow", "Arrow", SubjectKind.PROJECTILE, Set.of(GraftAspect.ON_HIT, GraftAspect.TARGET));
        GraftSubject chest = new GraftSubject(
            "container:chest",
            "Chest",
            SubjectKind.CONTAINER,
            Set.of(GraftAspect.ON_OPEN),
            new DynamicPropertyProfile(Map.of(DynamicProperty.LUMINANCE, 2.0))
        );
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of(GraftAspect.ON_OPEN));
        GraftSubject door = new GraftSubject("block:spruce_door", "Spruce Door", SubjectKind.BLOCK, Set.of(GraftAspect.ON_OPEN));
        GraftSubject beginning = new GraftSubject("concept:beginning", "Beginning", SubjectKind.CONCEPT, Set.of(GraftAspect.BEGIN));

        assertMode(planner, GraftAspect.ON_HIT, splashPotion, arrow, SequenceTamperMode.PROJECTILE_HIT_PAYLOAD);
        assertMode(planner, GraftAspect.ON_OPEN, chest, barrel, SequenceTamperMode.INTERACT_RELAY);
        assertMode(planner, GraftAspect.ON_OPEN, door, barrel, SequenceTamperMode.INTERACT_RELAY);
        assertMode(planner, GraftAspect.ON_OPEN, chest, door, SequenceTamperMode.INTERACT_RELAY);

        SequenceTamperPlan hitPlan = planner.plan(GraftAspect.ON_HIT, splashPotion, arrow).orElseThrow();
        assertClose(hitPlan.modifier().durationMultiplier(), 2.5, "Heated payload should lengthen projectile payload duration");
        assertClose(hitPlan.modifier().intensity(), 2.5, "Heated payload should increase projectile payload intensity");

        SequenceTamperPlan openPlan = planner.plan(GraftAspect.ON_OPEN, chest, barrel).orElseThrow();
        assertClose(openPlan.modifier().durationMultiplier(), 1.2, "Open relay should scale from the source's strongest property");

        if (planner.plan(GraftAspect.BEGIN, beginning, barrel).isPresent()) {
            throw new AssertionError("Begin should not plan for milestone-6 sequence runtime");
        }
    }

    private static void assertMode(SequenceTamperPlanner planner, GraftAspect aspect, GraftSubject source, GraftSubject target, SequenceTamperMode mode) {
        SequenceTamperMode actual = planner.plan(aspect, source, target).orElseThrow().mode();
        if (actual != mode) {
            throw new AssertionError("Expected " + mode + " for " + aspect + " from " + source.kind() + " to " + target.kind() + " but got " + actual);
        }
    }

    private static void assertClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 0.0001D) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
}
