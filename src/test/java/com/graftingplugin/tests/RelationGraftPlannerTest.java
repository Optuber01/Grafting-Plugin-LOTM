package com.graftingplugin.tests;

import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.relation.RelationGraftMode;
import com.graftingplugin.relation.RelationGraftPlan;
import com.graftingplugin.relation.RelationGraftPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Map;
import java.util.Set;

public final class RelationGraftPlannerTest {

    private RelationGraftPlannerTest() {
    }

    public static void run() {
        RelationGraftPlanner planner = new RelationGraftPlanner();

        GraftSubject zombie = new GraftSubject(
            "entity:zombie",
            "Zombie",
            SubjectKind.ENTITY,
            Set.of(GraftAspect.AGGRO, GraftAspect.TETHER),
            new DynamicPropertyProfile(Map.of(DynamicProperty.MASS, 10.0))
        );
        GraftSubject villager = new GraftSubject("entity:villager", "Villager", SubjectKind.ENTITY, Set.of());
        GraftSubject arrow = new GraftSubject(
            "projectile:arrow",
            "Arrow",
            SubjectKind.PROJECTILE,
            Set.of(GraftAspect.TARGET, GraftAspect.RECEIVER, GraftAspect.TETHER),
            new DynamicPropertyProfile(Map.of(DynamicProperty.MOTILITY, 0.5))
        );
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of());
        GraftSubject anchor = new GraftSubject("location:test", "Anchor", SubjectKind.LOCATION, Set.of(GraftAspect.ANCHOR));

        assertMode(planner, GraftAspect.AGGRO, zombie, villager, RelationGraftMode.MOB_AGGRO);
        assertMode(planner, GraftAspect.TARGET, arrow, villager, RelationGraftMode.PROJECTILE_RETARGET_ENTITY);
        assertMode(planner, GraftAspect.DESTINATION, chest, barrel, RelationGraftMode.CONTAINER_ROUTE);
        assertMode(planner, GraftAspect.TETHER, zombie, anchor, RelationGraftMode.TETHER_LOCATION);

        RelationGraftPlan targetPlan = planner.plan(GraftAspect.TARGET, arrow, villager).orElseThrow();
        assertClose(targetPlan.modifier().durationMultiplier(), 1.25, "Projectile retarget should scale duration from motility");
        assertClose(targetPlan.modifier().intensity(), 2.0, "Projectile retarget should scale turn strength from motility");

        RelationGraftPlan tetherPlan = planner.plan(GraftAspect.TETHER, zombie, anchor).orElseThrow();
        assertClose(tetherPlan.modifier().durationMultiplier(), 1.5, "Tethers should scale duration from mass");
        assertClose(tetherPlan.modifier().intensity(), 2.0, "Tethers should scale pull strength from mass");

        if (planner.plan(GraftAspect.ANCHOR, zombie, villager).isPresent()) {
            throw new AssertionError("Anchor should not plan for relation runtime");
        }
    }

    private static void assertMode(RelationGraftPlanner planner, GraftAspect aspect, GraftSubject source, GraftSubject target, RelationGraftMode mode) {
        RelationGraftMode actual = planner.plan(aspect, source, target).orElseThrow().mode();
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
