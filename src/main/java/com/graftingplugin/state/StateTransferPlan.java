package com.graftingplugin.state;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.SubjectKind;

public record StateTransferPlan(GraftAspect aspect, SubjectKind targetKind, StateTransferMode mode, String summary, int amplifier, PropertyModifier modifier) {

    public StateTransferPlan(GraftAspect aspect, SubjectKind targetKind, StateTransferMode mode, String summary) {
        this(aspect, targetKind, mode, summary, 0, PropertyModifier.BASE);
    }

    public StateTransferPlan withAmplifier(int amplifier) {
        return new StateTransferPlan(aspect, targetKind, mode, summary, amplifier, modifier);
    }

    public StateTransferPlan withModifier(PropertyModifier modifier) {
        return new StateTransferPlan(aspect, targetKind, mode, summary, amplifier, modifier);
    }
}
