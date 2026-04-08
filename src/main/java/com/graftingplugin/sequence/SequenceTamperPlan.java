package com.graftingplugin.sequence;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.SubjectKind;

public record SequenceTamperPlan(
    GraftAspect aspect,
    SubjectKind sourceKind,
    SubjectKind targetKind,
    SequenceTamperMode mode,
    String description
) {
}
