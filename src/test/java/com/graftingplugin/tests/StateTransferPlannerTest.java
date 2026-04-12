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
        GraftSubject item = new GraftSubject("item:diamond_sword", "Diamond Sword", SubjectKind.ITEM, Set.of());

        assertMode(planner, GraftAspect.LIGHT, block, StateTransferMode.BLOCK_MANIFEST);
        assertMode(planner, GraftAspect.HEAT, area, StateTransferMode.FIELD);
        assertMode(planner, GraftAspect.IGNITE, entity, StateTransferMode.ENTITY_FIRE);
        assertMode(planner, GraftAspect.BOUNCE, projectile, StateTransferMode.PROJECTILE_BOUNCE);
        assertMode(planner, GraftAspect.SPEED, entity, StateTransferMode.ENTITY_EFFECT);
        assertMode(planner, GraftAspect.HEAL, item, StateTransferMode.ITEM_REPAIR);
        assertMode(planner, GraftAspect.IGNITE, item, StateTransferMode.ITEM_DAMAGE);
        if (planner.plan(GraftAspect.HEAL, projectile).isPresent()) {
            throw new AssertionError("Heal should not plan onto a projectile target");
        }


        assertMode(planner, GraftAspect.HEAVY, entity, StateTransferMode.ENTITY_EFFECT);
        assertMode(planner, GraftAspect.HEAVY, block, StateTransferMode.BLOCK_MANIFEST);
        assertMode(planner, GraftAspect.HEAVY, projectile, StateTransferMode.PROJECTILE_TRAIT);


        DynamicPropertyProfile obsidianProfile = new DynamicPropertyProfile(Map.of(DynamicProperty.MASS, 50.0));
        DynamicPropertyProfile dirtProfile = new DynamicPropertyProfile(Map.of(DynamicProperty.MASS, 0.5));
        DynamicPropertyProfile vitalityProfile = new DynamicPropertyProfile(Map.of(DynamicProperty.VITALITY, 4.0));

        StateTransferPlan obsidianHeavy = planner.plan(GraftAspect.HEAVY, entity, obsidianProfile).orElseThrow();
        if (obsidianHeavy.modifier().amplifier() != 2) {
            throw new AssertionError("Obsidian-sourced HEAVY on entity should have amplifier 2, got " + obsidianHeavy.modifier().amplifier());
        }
        StateTransferPlan dirtHeavy = planner.plan(GraftAspect.HEAVY, entity, dirtProfile).orElseThrow();
        if (dirtHeavy.modifier().amplifier() != 0) {
            throw new AssertionError("Dirt-sourced HEAVY on entity should have amplifier 0, got " + dirtHeavy.modifier().amplifier());
        }

        StateTransferPlan vitalityHeal = planner.plan(GraftAspect.HEAL, entity, vitalityProfile).orElseThrow();
        if (vitalityHeal.modifier().amplifier() != 2) {
            throw new AssertionError("Vital source HEAL should have amplifier 2, got " + vitalityHeal.modifier().amplifier());
        }
        if (vitalityHeal.modifier().intensity() <= 1.0D) {
            throw new AssertionError("Vital source HEAL should scale intensity, got " + vitalityHeal.modifier().intensity());
        }


        StateTransferPlan defaultPlan = planner.plan(GraftAspect.SPEED, entity).orElseThrow();
        if (defaultPlan.modifier().amplifier() != 0) {
            throw new AssertionError("Default amplifier should be 0, got " + defaultPlan.modifier().amplifier());
        }
    }

    private static void assertMode(StateTransferPlanner planner, GraftAspect aspect, GraftSubject target, StateTransferMode mode) {
        StateTransferMode actual = planner.plan(aspect, target).orElseThrow().mode();
        if (actual != mode) {
            throw new AssertionError("Expected " + mode + " for " + aspect + " -> " + target.kind() + " but got " + actual);
        }
    }
}
