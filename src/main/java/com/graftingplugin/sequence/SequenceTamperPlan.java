package com.graftingplugin.sequence;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.SubjectKind;

public record SequenceTamperPlan(
    GraftAspect aspect,
    SubjectKind sourceKind,
    SubjectKind targetKind,
    SequenceTamperMode mode,
    String description,
    PropertyModifier modifier
) {

    public SequenceTamperPlan(GraftAspect aspect, SubjectKind sourceKind, SubjectKind targetKind, SequenceTamperMode mode, String description) {
        this(aspect, sourceKind, targetKind, mode, description, PropertyModifier.BASE);
    }
}
