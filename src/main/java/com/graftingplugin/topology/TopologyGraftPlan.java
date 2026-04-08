package com.graftingplugin.topology;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.SubjectKind;

public record TopologyGraftPlan(
    GraftAspect aspect,
    SubjectKind sourceKind,
    SubjectKind targetKind,
    TopologyGraftMode mode,
    String description
) {
}
