package com.graftingplugin.tests;

import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.state.StateTransferMode;
import com.graftingplugin.state.StateTransferPlan;
import com.graftingplugin.state.StateTransferPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Map;
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

        // HEAVY is now a valid state aspect
        assertMode(planner, GraftAspect.HEAVY, entity, StateTransferMode.ENTITY_EFFECT);
        assertMode(planner, GraftAspect.HEAVY, block, StateTransferMode.FIELD);
        assertMode(planner, GraftAspect.HEAVY, projectile, StateTransferMode.PROJECTILE_TRAIT);

        // Amplifier modulation from source property profile
        DynamicPropertyProfile obsidianProfile = new DynamicPropertyProfile(Map.of(DynamicProperty.MASS, 50.0));
        DynamicPropertyProfile dirtProfile = new DynamicPropertyProfile(Map.of(DynamicProperty.MASS, 0.5));

        StateTransferPlan obsidianHeavy = planner.plan(GraftAspect.HEAVY, entity, obsidianProfile).orElseThrow();
        if (obsidianHeavy.amplifier() != 2) {
            throw new AssertionError("Obsidian-sourced HEAVY on entity should have amplifier 2, got " + obsidianHeavy.amplifier());
        }
        StateTransferPlan dirtHeavy = planner.plan(GraftAspect.HEAVY, entity, dirtProfile).orElseThrow();
        if (dirtHeavy.amplifier() != 0) {
            throw new AssertionError("Dirt-sourced HEAVY on entity should have amplifier 0, got " + dirtHeavy.amplifier());
        }

        // Default amplifier is 0 when no profile is given
        StateTransferPlan defaultPlan = planner.plan(GraftAspect.SPEED, entity).orElseThrow();
        if (defaultPlan.amplifier() != 0) {
            throw new AssertionError("Default amplifier should be 0, got " + defaultPlan.amplifier());
        }
    }

    private static void assertMode(StateTransferPlanner planner, GraftAspect aspect, GraftSubject target, StateTransferMode mode) {
        StateTransferMode actual = planner.plan(aspect, target).orElseThrow().mode();
        if (actual != mode) {
            throw new AssertionError("Expected " + mode + " for " + aspect + " -> " + target.kind() + " but got " + actual);
        }
    }
}
