package com.graftingplugin.topology;

import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Optional;

public final class TopologyGraftPlanner {

    public Optional<TopologyGraftPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target) {
        return plan(aspect, source, target, source.properties());
    }

    public Optional<TopologyGraftPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target, DynamicPropertyProfile sourceProfile) {
        PropertyModifier modifier = PropertyModifier.fromProfile(aspect, sourceProfile);
        return switch (aspect) {
            case ANCHOR, ENTRY, EXIT, NEAR, FAR -> planLink(aspect, source.kind(), target.kind(), modifier);
            case PATH_START, PATH_END, BEGIN, END -> planLoop(aspect, source.kind(), target.kind(), modifier);
            default -> Optional.empty();
        };
    }

    private Optional<TopologyGraftPlan> planLink(GraftAspect aspect, SubjectKind sourceKind, SubjectKind targetKind, PropertyModifier modifier) {
        if (!isSupportedSource(sourceKind) || !isSupportedTarget(targetKind)) {
            return Optional.empty();
        }
        return Optional.of(new TopologyGraftPlan(aspect, sourceKind, targetKind, TopologyGraftMode.ANCHOR_LINK, "Create a temporary topology route from one anchor to another.", modifier));
    }

    private Optional<TopologyGraftPlan> planLoop(GraftAspect aspect, SubjectKind sourceKind, SubjectKind targetKind, PropertyModifier modifier) {
        if (!isSupportedSource(sourceKind) || !isSupportedTarget(targetKind)) {
            return Optional.empty();
        }
        return Optional.of(new TopologyGraftPlan(aspect, sourceKind, targetKind, TopologyGraftMode.PATH_LOOP, "Loop a path endpoint back to its origin anchor for a duration.", modifier));
    }

    private boolean isSupportedSource(SubjectKind kind) {
        return kind == SubjectKind.BLOCK || kind == SubjectKind.LOCATION || kind == SubjectKind.AREA || kind == SubjectKind.CONCEPT;
    }

    private boolean isSupportedTarget(SubjectKind kind) {
        return kind == SubjectKind.BLOCK || kind == SubjectKind.LOCATION || kind == SubjectKind.AREA;
    }
}
