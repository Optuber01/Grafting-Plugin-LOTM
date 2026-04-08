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
        GraftSubject gravity = new GraftSubject("concept:gravity", "Gravity", SubjectKind.CONCEPT, Set.of(GraftAspect.HEAVY, GraftAspect.PULL, GraftAspect.BOUNCE));
        GraftSubject beginning = new GraftSubject("concept:beginning", "Beginning", SubjectKind.CONCEPT, Set.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN, GraftAspect.VOLUME));
        GraftSubject zombie = new GraftSubject("entity:zombie", "Zombie", SubjectKind.ENTITY, Set.of(GraftAspect.AGGRO, GraftAspect.TARGET, GraftAspect.TETHER, GraftAspect.OWNER));
        GraftSubject villager = new GraftSubject("entity:villager", "Villager", SubjectKind.ENTITY, Set.of());
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of());
        GraftSubject stone = new GraftSubject("block:stone", "Stone", SubjectKind.BLOCK, Set.of());
        GraftSubject doorway = new GraftSubject("block:oak_door", "Oak Door", SubjectKind.BLOCK, Set.of(GraftAspect.ENTRY, GraftAspect.EXIT, GraftAspect.SURFACE));
        GraftSubject anchor = new GraftSubject("location:test", "Anchor", SubjectKind.LOCATION, Set.of(GraftAspect.ANCHOR));
        GraftSubject splashPotion = new GraftSubject("potion:splash_potion", "Splash Potion", SubjectKind.POTION, Set.of(GraftAspect.ON_HIT, GraftAspect.ON_ENTER, GraftAspect.POISON, GraftAspect.HEAL));
        GraftSubject arrow = new GraftSubject("projectile:arrow", "Arrow", SubjectKind.PROJECTILE, Set.of(GraftAspect.ON_HIT, GraftAspect.TARGET));
        GraftSubject triggerChest = new GraftSubject("container:chest_trigger", "Trigger Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.ON_OPEN));

        List<GraftAspect> stateAspects = validator.compatibleSourceAspects(GraftFamily.STATE, sun);
        if (!stateAspects.equals(List.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE))) {
            // HEAVY is now a supported state aspect, but sun doesn't expose it so this list is unchanged.
            throw new AssertionError("Unexpected state aspect list: " + stateAspects);
        }
        List<GraftAspect> gravityAspects = validator.compatibleSourceAspects(GraftFamily.STATE, gravity);
        if (!gravityAspects.equals(List.of(GraftAspect.HEAVY, GraftAspect.BOUNCE))) {
            throw new AssertionError("Unsupported state aspects leaked through gravity concept: " + gravityAspects);
        }
        List<GraftAspect> relationAspects = validator.compatibleSourceAspects(GraftFamily.RELATION, zombie);
        if (!relationAspects.equals(List.of(GraftAspect.AGGRO, GraftAspect.TETHER))) {
            throw new AssertionError("Unexpected relation aspect list: " + relationAspects);
        }
        List<GraftAspect> topologyAspects = validator.compatibleSourceAspects(GraftFamily.TOPOLOGY, beginning);
        if (!topologyAspects.equals(List.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN))) {
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

        if (!validator.validateTarget(sun, GraftAspect.LIGHT, stone).success()) {
            throw new AssertionError("Sun light should be valid on a block target");
        }
        if (validator.validateTarget(splashPotion, GraftAspect.HEAL, arrow).success()) {
            throw new AssertionError("Heal should not validate onto a projectile target");
        }
        if (validator.validateTarget(beginning, GraftAspect.BEGIN, zombie).success()) {
            throw new AssertionError("Begin should not validate against an entity target");
        }
        if (!validator.validateTarget(beginning, GraftAspect.BEGIN, anchor).success()) {
            throw new AssertionError("Begin should validate against a location target");
        }
        if (!validator.validateTarget(zombie, GraftAspect.AGGRO, villager).success()) {
            throw new AssertionError("Aggro should validate against an entity target");
        }
        if (validator.validateTarget(zombie, GraftAspect.TARGET, villager).success()) {
            throw new AssertionError("Entity-sourced target retargeting should not validate");
        }
        if (validator.validateTarget(zombie, GraftAspect.OWNER, villager).success()) {
            throw new AssertionError("Owner should not validate for current relation runtime");
        }
        if (!validator.validateTarget(chest, GraftAspect.DESTINATION, barrel).success()) {
            throw new AssertionError("Destination should validate against a container target");
        }
        if (!validator.validateTarget(doorway, GraftAspect.ENTRY, anchor).success()) {
            throw new AssertionError("Entry should validate against a location target");
        }
        if (validator.validateTarget(doorway, GraftAspect.SURFACE, anchor).success()) {
            throw new AssertionError("Surface should not validate for current topology runtime");
        }
        if (!validator.validateTarget(splashPotion, GraftAspect.ON_HIT, arrow).success()) {
            throw new AssertionError("On-hit should validate against a projectile target");
        }
        if (validator.validateTarget(splashPotion, GraftAspect.ON_ENTER, arrow).success()) {
            throw new AssertionError("On-enter should not validate for current sequence runtime");
        }
        if (!validator.validateTarget(triggerChest, GraftAspect.ON_OPEN, chest).success()) {
            throw new AssertionError("On-open should validate against a container target");
        }
    }
}
