package com.graftingplugin.state;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.SubjectKind;

public record StateTransferPlan(GraftAspect aspect, SubjectKind targetKind, StateTransferMode mode, String summary, int amplifier) {

    public StateTransferPlan(GraftAspect aspect, SubjectKind targetKind, StateTransferMode mode, String summary) {
        this(aspect, targetKind, mode, summary, 0);
    }

    public StateTransferPlan withAmplifier(int amplifier) {
        return new StateTransferPlan(aspect, targetKind, mode, summary, amplifier);
    }
}
