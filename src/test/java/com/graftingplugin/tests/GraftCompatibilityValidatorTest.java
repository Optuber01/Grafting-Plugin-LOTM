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
        GraftSubject beginning = new GraftSubject("concept:beginning", "Beginning", SubjectKind.CONCEPT, Set.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN));
        GraftSubject zombie = new GraftSubject("entity:zombie", "Zombie", SubjectKind.ENTITY, Set.of(GraftAspect.AGGRO, GraftAspect.TARGET, GraftAspect.TETHER));
        GraftSubject villager = new GraftSubject("entity:villager", "Villager", SubjectKind.ENTITY, Set.of());
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of());
        GraftSubject stone = new GraftSubject("block:stone", "Stone", SubjectKind.BLOCK, Set.of());
        GraftSubject doorway = new GraftSubject("block:oak_door", "Oak Door", SubjectKind.BLOCK, Set.of(GraftAspect.ENTRY, GraftAspect.EXIT));
        GraftSubject anchor = new GraftSubject("location:test", "Anchor", SubjectKind.LOCATION, Set.of(GraftAspect.ANCHOR));

        List<GraftAspect> stateAspects = validator.compatibleSourceAspects(GraftFamily.STATE, sun);
        if (!stateAspects.equals(List.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE))) {
            throw new AssertionError("Unexpected state aspect list: " + stateAspects);
        }
        if (!validator.compatibleSourceAspects(GraftFamily.TOPOLOGY, doorway).contains(GraftAspect.ENTRY)) {
            throw new AssertionError("Topology source aspects did not include entry");
        }
        if (!validator.compatibleSourceAspects(GraftFamily.STATE, beginning).isEmpty()) {
            throw new AssertionError("Beginning should not expose state aspects");
        }
        if (!validator.validateTarget(sun, GraftAspect.LIGHT, stone).success()) {
            throw new AssertionError("Sun light should be valid on a block target");
        }
        if (validator.validateTarget(beginning, GraftAspect.BEGIN, zombie).success()) {
            throw new AssertionError("Begin should not validate against an entity target");
        }
        if (!validator.validateTarget(zombie, GraftAspect.AGGRO, villager).success()) {
            throw new AssertionError("Aggro should validate against an entity target");
        }
        if (!validator.validateTarget(chest, GraftAspect.DESTINATION, barrel).success()) {
            throw new AssertionError("Destination should validate against a container target");
        }
        if (!validator.validateTarget(doorway, GraftAspect.ENTRY, anchor).success()) {
            throw new AssertionError("Entry should validate against a location target");
        }
    }
}
