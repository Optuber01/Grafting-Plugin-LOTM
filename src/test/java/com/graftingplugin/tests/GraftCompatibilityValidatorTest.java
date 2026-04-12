package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import com.graftingplugin.validation.GraftCompatibilityValidator;

import java.util.List;
import java.util.Set;

public final class GraftCompatibilityValidatorTest {

    private GraftCompatibilityValidatorTest() {
    }

    public static void run() {
        GraftCompatibilityValidator validator = new GraftCompatibilityValidator();
        GraftSubject sun = new GraftSubject("concept:sun", "Sun", SubjectKind.CONCEPT, Set.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE));
        GraftSubject gravity = new GraftSubject("concept:gravity", "Gravity", SubjectKind.CONCEPT, Set.of(GraftAspect.HEAVY, GraftAspect.BOUNCE));
        GraftSubject beginning = new GraftSubject("concept:beginning", "Beginning", SubjectKind.CONCEPT, Set.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN));
        GraftSubject zombie = new GraftSubject("entity:zombie", "Zombie", SubjectKind.ENTITY, Set.of(GraftAspect.AGGRO, GraftAspect.TETHER));
        GraftSubject villager = new GraftSubject("entity:villager", "Villager", SubjectKind.ENTITY, Set.of());
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of());
        GraftSubject stone = new GraftSubject("block:stone", "Stone", SubjectKind.BLOCK, Set.of());
        GraftSubject doorway = new GraftSubject("block:oak_door", "Oak Door", SubjectKind.BLOCK, Set.of(GraftAspect.ENTRY, GraftAspect.EXIT));
        GraftSubject anchor = new GraftSubject("location:test", "Anchor", SubjectKind.LOCATION, Set.of(GraftAspect.ANCHOR));
        GraftSubject splashPotion = new GraftSubject("potion:splash_potion", "Splash Potion", SubjectKind.POTION, Set.of(GraftAspect.ON_HIT, GraftAspect.POISON, GraftAspect.HEAL));
        GraftSubject arrow = new GraftSubject("projectile:arrow", "Arrow", SubjectKind.PROJECTILE, Set.of(GraftAspect.ON_HIT, GraftAspect.TARGET));
        GraftSubject triggerChest = new GraftSubject("container:chest_trigger", "Trigger Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.ON_OPEN));
        GraftSubject badRetargetSource = new GraftSubject("entity:bad", "Bad Source", SubjectKind.ENTITY, Set.of(GraftAspect.TARGET));

        List<GraftAspect> stateAspects = validator.compatibleSourceAspects(GraftFamily.STATE, sun);
        if (!stateAspects.equals(List.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE))) {
            throw new AssertionError("Unexpected state aspect list: " + stateAspects);
        }
        List<GraftAspect> gravityAspects = validator.compatibleSourceAspects(GraftFamily.STATE, gravity);
        if (!gravityAspects.equals(List.of(GraftAspect.HEAVY, GraftAspect.BOUNCE))) {
            throw new AssertionError("Gravity should expose only supported state aspects: " + gravityAspects);
        }
        List<GraftAspect> relationAspects = validator.compatibleSourceAspects(GraftFamily.RELATION, zombie);
        if (!relationAspects.equals(List.of(GraftAspect.AGGRO, GraftAspect.TETHER))) {
            throw new AssertionError("Unexpected relation aspect list: " + relationAspects);
        }
        List<GraftAspect> topologyAspects = validator.compatibleSourceAspects(GraftFamily.TOPOLOGY, beginning);
        if (!topologyAspects.containsAll(List.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN))) {
            throw new AssertionError("Unexpected topology aspect list: " + topologyAspects);
        }
        List<GraftAspect> sequenceAspects = validator.compatibleSourceAspects(GraftFamily.SEQUENCE, splashPotion);
        if (!sequenceAspects.equals(List.of(GraftAspect.ON_HIT))) {
            throw new AssertionError("Unexpected sequence aspect list: " + sequenceAspects);
        }
        if (!validator.compatibleSourceAspects(GraftFamily.STATE, beginning).isEmpty()) {
            throw new AssertionError("Beginning should not expose state aspects");
        }

        if (!validator.supportedFamilyAspects(GraftFamily.RELATION).equals(List.of(
            GraftAspect.TARGET,
            GraftAspect.AGGRO,
            GraftAspect.TETHER,
            GraftAspect.DESTINATION,
            GraftAspect.RECEIVER,
            GraftAspect.CONTAINER_LINK
        ))) {
            throw new AssertionError("Relation supported aspect list drifted");
        }
        if (!validator.supportedFamilyAspects(GraftFamily.SEQUENCE).equals(List.of(GraftAspect.ON_HIT, GraftAspect.ON_OPEN))) {
            throw new AssertionError("Sequence supported aspect list drifted");
        }

        assertSuccess(validator.validateTarget(sun, GraftAspect.LIGHT, stone), "Sun light should be valid on a block target");
        assertSuccess(validator.validateTarget(beginning, GraftAspect.BEGIN, anchor), "Begin should validate against a location target");
        assertSuccess(validator.validateTarget(zombie, GraftAspect.AGGRO, villager), "Aggro should validate against an entity target");
        assertSuccess(validator.validateTarget(chest, GraftAspect.DESTINATION, barrel), "Destination should validate against a container target");
        assertSuccess(validator.validateTarget(doorway, GraftAspect.ENTRY, anchor), "Entry should validate against a location target");
        assertSuccess(validator.validateTarget(splashPotion, GraftAspect.ON_HIT, arrow), "On-hit should validate against a projectile target");
        assertSuccess(validator.validateTarget(triggerChest, GraftAspect.ON_OPEN, chest), "On-open should validate against a container target");

        assertFailure(validator.validateTarget(splashPotion, GraftAspect.HEAL, arrow), "Heal on projectile should fail fast");
        assertFailure(validator.validateTarget(beginning, GraftAspect.BEGIN, zombie), "Begin on entity should fail fast");
        assertFailure(validator.validateTarget(badRetargetSource, GraftAspect.TARGET, villager), "Entity-sourced target retargeting should fail fast");
        assertFailure(validator.validateAspectSelection(GraftFamily.STATE, sun, GraftAspect.ON_HIT), "Selecting a sequence aspect in state mode should fail");
    }

    private static void assertSuccess(com.graftingplugin.validation.GraftCompatibilityResult result, String message) {
        if (!result.success()) {
            throw new AssertionError(message + ": " + result.message());
        }
    }

    private static void assertFailure(com.graftingplugin.validation.GraftCompatibilityResult result, String message) {
        if (result.success()) {
            throw new AssertionError(message);
        }
    }
}
