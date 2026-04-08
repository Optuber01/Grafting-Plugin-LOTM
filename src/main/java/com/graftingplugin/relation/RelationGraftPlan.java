package com.graftingplugin.relation;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.SubjectKind;

public record RelationGraftPlan(
    GraftAspect aspect,
    SubjectKind sourceKind,
    SubjectKind targetKind,
    RelationGraftMode mode,
    String description
) {
}
