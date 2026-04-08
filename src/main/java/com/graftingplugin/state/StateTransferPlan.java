package com.graftingplugin.state;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.SubjectKind;

public record StateTransferPlan(GraftAspect aspect, SubjectKind targetKind, StateTransferMode mode, String summary) {
}
