package com.graftingplugin.state;

import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Optional;

public final class StateTransferPlanner {

    public Optional<StateTransferPlan> plan(GraftAspect aspect, GraftSubject target) {
        return switch (target.kind()) {
            case ENTITY -> planEntity(aspect, target.kind());
            case PROJECTILE -> planProjectile(aspect, target.kind());
            case ITEM, POTION -> planItem(aspect, target.kind());
            case BLOCK -> planBlock(aspect, target.kind());
            case FLUID -> planFluid(aspect, target.kind());
            case LOCATION, AREA -> planField(aspect, target.kind());
            default -> Optional.empty();
        };
    }

    public Optional<StateTransferPlan> plan(GraftAspect aspect, GraftSubject target, DynamicPropertyProfile sourceProfile) {
        PropertyModifier modifier = PropertyModifier.fromProfile(aspect, sourceProfile);
        return plan(aspect, target).map(base -> {

            int legacyAmp = amplifierFromProfile(aspect, sourceProfile);
            int combinedAmp = Math.max(legacyAmp, modifier.amplifier());
            return base.withModifier(modifier.withAmplifier(combinedAmp));
        });
    }


    public static int amplifierFromProfile(GraftAspect aspect, DynamicPropertyProfile profile) {
        if (profile == null || profile == DynamicPropertyProfile.EMPTY) {
            return 0;
        }
        return switch (aspect) {
            case HEAVY -> {
                double mass = profile.get(DynamicProperty.MASS);
                yield mass >= 10.0 ? 2 : (mass >= 3.0 ? 1 : 0);
            }
            case HEAT, IGNITE -> profile.get(DynamicProperty.THERMAL) >= 2.0 ? 1 : 0;
            case LIGHT, GLOW -> profile.get(DynamicProperty.LUMINANCE) >= 2.0 ? 1 : 0;
            case SPEED -> profile.get(DynamicProperty.MOTILITY) > 0.3 ? 1 : 0;
            case SLOW -> profile.get(DynamicProperty.MASS) >= 5.0 ? 1 : 0;
            default -> 0;
        };
    }

    private Optional<StateTransferPlan> planEntity(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case LIGHT, GLOW, SPEED, SLOW, STICKY, POISON, HEAL, STATUS, CONCEAL, FREEZE, HEAVY -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.ENTITY_EFFECT, "Apply a temporary state effect to the entity."));
            case IGNITE, HEAT -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.ENTITY_FIRE, "Apply direct heat or ignition to the entity."));
            case BOUNCE -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.ENTITY_BOUNCE, "Grant temporary bounce behavior to the entity."));
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planProjectile(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case SPEED, SLOW, LIGHT, GLOW -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.PROJECTILE_TRAIT, "Modify projectile movement or visibility."));
            case POISON, IGNITE, HEAT -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.PROJECTILE_PAYLOAD, "Bind an on-hit state payload to the projectile."));
            case BOUNCE -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.PROJECTILE_BOUNCE, "Grant limited bounce behavior to the projectile."));
            case HEAVY -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.PROJECTILE_TRAIT, "Add mass to the projectile, increasing impact."));
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planItem(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case HEAL -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.ITEM_REPAIR, "Restore integrity to a carried item."));
            case HEAT, IGNITE -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.ITEM_DAMAGE, "Scorch or wear down a carried item."));
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planBlock(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case LIGHT, HEAT, IGNITE, BOUNCE, SPEED, SLOW, STICKY, POISON, HEAL, GLOW, CONCEAL, FREEZE, HEAVY -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.BLOCK_MANIFEST, "Manifest a temporary state directly onto the block."));
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planFluid(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case LIGHT, HEAT, IGNITE, BOUNCE, SPEED, SLOW, STICKY, POISON, HEAL, GLOW, CONCEAL, FREEZE, HEAVY -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.BLOCK_MANIFEST, "Manifest a temporary state directly onto the fluid."));
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planField(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case LIGHT, HEAT, IGNITE, BOUNCE, SPEED, SLOW, STICKY, POISON, HEAL, GLOW, CONCEAL, FREEZE, HEAVY -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.FIELD, "Anchor a temporary state field in the world."));
            default -> Optional.empty();
        };
    }
}
