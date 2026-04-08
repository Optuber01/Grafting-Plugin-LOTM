package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.state.StateTransferMode;
import com.graftingplugin.state.StateTransferPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Set;

public final class StateTransferPlannerTest {

    private StateTransferPlannerTest() {
    }

    public static void run() {
        StateTransferPlanner planner = new StateTransferPlanner();

        GraftSubject block = new GraftSubject("block:stone", "Stone", SubjectKind.BLOCK, Set.of());
        GraftSubject area = new GraftSubject("area:test", "Area", SubjectKind.AREA, Set.of());
        GraftSubject entity = new GraftSubject("entity:zombie", "Zombie", SubjectKind.ENTITY, Set.of());
        GraftSubject projectile = new GraftSubject("projectile:arrow", "Arrow", SubjectKind.PROJECTILE, Set.of());

        assertMode(planner, GraftAspect.LIGHT, block, StateTransferMode.FIELD);
        assertMode(planner, GraftAspect.HEAT, area, StateTransferMode.FIELD);
        assertMode(planner, GraftAspect.IGNITE, entity, StateTransferMode.ENTITY_FIRE);
        assertMode(planner, GraftAspect.BOUNCE, projectile, StateTransferMode.PROJECTILE_BOUNCE);
        assertMode(planner, GraftAspect.SPEED, entity, StateTransferMode.ENTITY_EFFECT);
        if (planner.plan(GraftAspect.HEAL, projectile).isPresent()) {
            throw new AssertionError("Heal should not plan onto a projectile target");
        }
    }

    private static void assertMode(StateTransferPlanner planner, GraftAspect aspect, GraftSubject target, StateTransferMode mode) {
        StateTransferMode actual = planner.plan(aspect, target).orElseThrow().mode();
        if (actual != mode) {
            throw new AssertionError("Expected " + mode + " for " + aspect + " -> " + target.kind() + " but got " + actual);
        }
    }
}
