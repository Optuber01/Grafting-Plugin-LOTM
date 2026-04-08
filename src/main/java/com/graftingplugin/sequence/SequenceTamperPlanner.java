package com.graftingplugin.sequence;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Optional;

public final class SequenceTamperPlanner {

    public Optional<SequenceTamperPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target) {
        return switch (aspect) {
            case ON_HIT -> planOnHit(source.kind(), target.kind(), aspect);
            case ON_OPEN -> planOnOpen(source.kind(), target.kind(), aspect);
            default -> Optional.empty();
        };
    }

    private Optional<SequenceTamperPlan> planOnHit(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect) {
        if ((sourceKind == SubjectKind.PROJECTILE || sourceKind == SubjectKind.POTION || sourceKind == SubjectKind.ITEM || sourceKind == SubjectKind.CONCEPT)
            && targetKind == SubjectKind.PROJECTILE) {
            return Optional.of(new SequenceTamperPlan(aspect, sourceKind, targetKind, SequenceTamperMode.PROJECTILE_HIT_PAYLOAD, "Arm a projectile with a transferred on-hit payload."));
        }
        return Optional.empty();
    }

    private Optional<SequenceTamperPlan> planOnOpen(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect) {
        if ((sourceKind == SubjectKind.CONTAINER || sourceKind == SubjectKind.LOCATION || sourceKind == SubjectKind.AREA || sourceKind == SubjectKind.CONCEPT)
            && targetKind == SubjectKind.CONTAINER) {
            return Optional.of(new SequenceTamperPlan(aspect, sourceKind, targetKind, SequenceTamperMode.CONTAINER_OPEN_RELAY, "Relay a container open trigger toward a stored anchor."));
        }
        return Optional.empty();
    }
}
