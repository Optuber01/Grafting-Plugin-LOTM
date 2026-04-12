package com.graftingplugin.topology;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.SubjectKind;

public record TopologyGraftPlan(
    GraftAspect aspect,
    SubjectKind sourceKind,
    SubjectKind targetKind,
    TopologyGraftMode mode,
    String description,
    PropertyModifier modifier
) {
    public TopologyGraftPlan(GraftAspect aspect, SubjectKind sourceKind, SubjectKind targetKind, TopologyGraftMode mode, String description) {
        this(aspect, sourceKind, targetKind, mode, description, PropertyModifier.BASE);
    }
}
