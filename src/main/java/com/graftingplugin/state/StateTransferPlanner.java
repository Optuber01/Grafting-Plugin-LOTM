package com.graftingplugin.state;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Optional;

public final class StateTransferPlanner {

    public Optional<StateTransferPlan> plan(GraftAspect aspect, GraftSubject target) {
        return switch (target.kind()) {
            case ENTITY -> planEntity(aspect, target.kind());
            case PROJECTILE -> planProjectile(aspect, target.kind());
            case BLOCK, LOCATION, AREA -> planField(aspect, target.kind());
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planEntity(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case LIGHT, GLOW, SPEED, SLOW, STICKY, POISON, HEAL, CONCEAL, FREEZE -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.ENTITY_EFFECT, "Apply a temporary state effect to the entity."));
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
            default -> Optional.empty();
        };
    }

    private Optional<StateTransferPlan> planField(GraftAspect aspect, SubjectKind kind) {
        return switch (aspect) {
            case LIGHT, HEAT, IGNITE, BOUNCE, SPEED, SLOW, STICKY, POISON, HEAL, GLOW, CONCEAL, FREEZE -> Optional.of(new StateTransferPlan(aspect, kind, StateTransferMode.FIELD, "Anchor a temporary state field in the world."));
            default -> Optional.empty();
        };
    }
}
