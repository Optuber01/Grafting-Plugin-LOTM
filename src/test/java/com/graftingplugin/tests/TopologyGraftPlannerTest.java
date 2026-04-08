package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.topology.TopologyGraftMode;
import com.graftingplugin.topology.TopologyGraftPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Set;

public final class TopologyGraftPlannerTest {

    private TopologyGraftPlannerTest() {
    }

    public static void run() {
        TopologyGraftPlanner planner = new TopologyGraftPlanner();

        GraftSubject doorway = new GraftSubject("block:oak_door", "Oak Door", SubjectKind.BLOCK, Set.of(GraftAspect.ENTRY, GraftAspect.EXIT));
        GraftSubject anchor = new GraftSubject("location:test", "Anchor", SubjectKind.LOCATION, Set.of(GraftAspect.ANCHOR));
        GraftSubject area = new GraftSubject("area:test", "Area", SubjectKind.AREA, Set.of(GraftAspect.ANCHOR, GraftAspect.VOLUME));
        GraftSubject beginning = new GraftSubject("concept:beginning", "Beginning", SubjectKind.CONCEPT, Set.of(GraftAspect.PATH_START, GraftAspect.BEGIN));

        assertMode(planner, GraftAspect.ENTRY, doorway, anchor, TopologyGraftMode.ANCHOR_LINK);
        assertMode(planner, GraftAspect.ANCHOR, area, anchor, TopologyGraftMode.ANCHOR_LINK);
        assertMode(planner, GraftAspect.PATH_START, beginning, anchor, TopologyGraftMode.PATH_LOOP);

        if (planner.plan(GraftAspect.SURFACE, doorway, anchor).isPresent()) {
            throw new AssertionError("Surface should not plan for topology runtime");
        }
    }

    private static void assertMode(TopologyGraftPlanner planner, GraftAspect aspect, GraftSubject source, GraftSubject target, TopologyGraftMode mode) {
        TopologyGraftMode actual = planner.plan(aspect, source, target).orElseThrow().mode();
        if (actual != mode) {
            throw new AssertionError("Expected " + mode + " for " + aspect + " from " + source.kind() + " to " + target.kind() + " but got " + actual);
        }
    }
}
