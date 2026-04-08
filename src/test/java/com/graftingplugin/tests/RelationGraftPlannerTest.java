package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.relation.RelationGraftMode;
import com.graftingplugin.relation.RelationGraftPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Set;

public final class RelationGraftPlannerTest {

    private RelationGraftPlannerTest() {
    }

    public static void run() {
        RelationGraftPlanner planner = new RelationGraftPlanner();

        GraftSubject zombie = new GraftSubject("entity:zombie", "Zombie", SubjectKind.ENTITY, Set.of(GraftAspect.AGGRO, GraftAspect.TARGET, GraftAspect.TETHER));
        GraftSubject villager = new GraftSubject("entity:villager", "Villager", SubjectKind.ENTITY, Set.of());
        GraftSubject arrow = new GraftSubject("projectile:arrow", "Arrow", SubjectKind.PROJECTILE, Set.of(GraftAspect.TARGET, GraftAspect.RECEIVER, GraftAspect.TETHER));
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of());
        GraftSubject anchor = new GraftSubject("location:test", "Anchor", SubjectKind.LOCATION, Set.of(GraftAspect.ANCHOR));

        assertMode(planner, GraftAspect.AGGRO, zombie, villager, RelationGraftMode.MOB_AGGRO);
        assertMode(planner, GraftAspect.TARGET, arrow, villager, RelationGraftMode.PROJECTILE_RETARGET_ENTITY);
        assertMode(planner, GraftAspect.DESTINATION, chest, barrel, RelationGraftMode.CONTAINER_ROUTE);
        assertMode(planner, GraftAspect.TETHER, zombie, anchor, RelationGraftMode.TETHER_LOCATION);

        if (planner.plan(GraftAspect.OWNER, zombie, villager).isPresent()) {
            throw new AssertionError("Owner should not plan for milestone-4 relation runtime");
        }
    }

    private static void assertMode(RelationGraftPlanner planner, GraftAspect aspect, GraftSubject source, GraftSubject target, RelationGraftMode mode) {
        RelationGraftMode actual = planner.plan(aspect, source, target).orElseThrow().mode();
        if (actual != mode) {
            throw new AssertionError("Expected " + mode + " for " + aspect + " from " + source.kind() + " to " + target.kind() + " but got " + actual);
        }
    }
}
