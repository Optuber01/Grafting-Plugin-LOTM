package com.graftingplugin.sequence;

import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Optional;

public final class SequenceTamperPlanner {

    public Optional<SequenceTamperPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target) {
        return plan(aspect, source, target, source.properties());
    }

    public Optional<SequenceTamperPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target, DynamicPropertyProfile sourceProfile) {
        PropertyModifier modifier = modifierFor(aspect, source, sourceProfile);
        return switch (aspect) {
            case ON_HIT -> planOnHit(source.kind(), target.kind(), aspect, modifier);
            case ON_OPEN -> planOnOpen(source.kind(), target.kind(), aspect, modifier);
            default -> Optional.empty();
        };
    }

    private Optional<SequenceTamperPlan> planOnHit(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if ((sourceKind == SubjectKind.PROJECTILE || sourceKind == SubjectKind.POTION || sourceKind == SubjectKind.ITEM || sourceKind == SubjectKind.CONCEPT)
            && targetKind == SubjectKind.PROJECTILE) {
            return Optional.of(new SequenceTamperPlan(aspect, sourceKind, targetKind, SequenceTamperMode.PROJECTILE_HIT_PAYLOAD, "Arm a projectile with a transferred on-hit payload.", modifier));
        }
        return Optional.empty();
    }

    private Optional<SequenceTamperPlan> planOnOpen(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if ((sourceKind == SubjectKind.BLOCK || sourceKind == SubjectKind.CONTAINER || sourceKind == SubjectKind.LOCATION || sourceKind == SubjectKind.AREA || sourceKind == SubjectKind.CONCEPT)
            && (targetKind == SubjectKind.BLOCK || targetKind == SubjectKind.CONTAINER)) {
            return Optional.of(new SequenceTamperPlan(aspect, sourceKind, targetKind, SequenceTamperMode.INTERACT_RELAY, "Relay an interaction trigger toward a stored anchor.", modifier));
        }
        return Optional.empty();
    }

    private PropertyModifier modifierFor(GraftAspect aspect, GraftSubject source, DynamicPropertyProfile sourceProfile) {
        if (sourceProfile == null || sourceProfile == DynamicPropertyProfile.EMPTY) {
            return PropertyModifier.BASE;
        }
        if (aspect != GraftAspect.ON_HIT) {
            return PropertyModifier.fromProfile(aspect, sourceProfile);
        }

        PropertyModifier strongest = PropertyModifier.BASE;
        for (GraftAspect payloadAspect : source.aspectsFor(GraftFamily.STATE)) {
            if (!isPayloadAspect(payloadAspect)) {
                continue;
            }

            strongest = mergeStrongest(strongest, PropertyModifier.fromProfile(payloadAspect, sourceProfile));
        }
        return strongest;
    }

    private PropertyModifier mergeStrongest(PropertyModifier current, PropertyModifier candidate) {
        return new PropertyModifier(
            Math.max(current.durationMultiplier(), candidate.durationMultiplier()),
            Math.max(current.amplifier(), candidate.amplifier()),
            Math.max(current.radiusMultiplier(), candidate.radiusMultiplier()),
            Math.max(current.intensity(), candidate.intensity())
        );
    }

    private boolean isPayloadAspect(GraftAspect aspect) {
        return aspect.family() == GraftFamily.STATE && aspect != GraftAspect.BOUNCE;
    }
}
