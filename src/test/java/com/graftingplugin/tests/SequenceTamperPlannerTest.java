package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.sequence.SequenceTamperMode;
import com.graftingplugin.sequence.SequenceTamperPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Set;

public final class SequenceTamperPlannerTest {

    private SequenceTamperPlannerTest() {
    }

    public static void run() {
        SequenceTamperPlanner planner = new SequenceTamperPlanner();

        GraftSubject splashPotion = new GraftSubject("potion:splash_potion", "Splash Potion", SubjectKind.POTION, Set.of(GraftAspect.ON_HIT, GraftAspect.POISON));
        GraftSubject arrow = new GraftSubject("projectile:arrow", "Arrow", SubjectKind.PROJECTILE, Set.of(GraftAspect.ON_HIT, GraftAspect.TARGET));
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.ON_OPEN));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of(GraftAspect.ON_OPEN));
        GraftSubject beginning = new GraftSubject("concept:beginning", "Beginning", SubjectKind.CONCEPT, Set.of(GraftAspect.BEGIN));

        assertMode(planner, GraftAspect.ON_HIT, splashPotion, arrow, SequenceTamperMode.PROJECTILE_HIT_PAYLOAD);
        assertMode(planner, GraftAspect.ON_OPEN, chest, barrel, SequenceTamperMode.CONTAINER_OPEN_RELAY);

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
}
