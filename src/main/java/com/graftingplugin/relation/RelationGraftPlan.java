package com.graftingplugin.relation;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.SubjectKind;

public record RelationGraftPlan(
    GraftAspect aspect,
    SubjectKind sourceKind,
    SubjectKind targetKind,
    RelationGraftMode mode,
    String description,
    PropertyModifier modifier
) {
    public RelationGraftPlan(GraftAspect aspect, SubjectKind sourceKind, SubjectKind targetKind, RelationGraftMode mode, String description) {
        this(aspect, sourceKind, targetKind, mode, description, PropertyModifier.BASE);
    }
}
