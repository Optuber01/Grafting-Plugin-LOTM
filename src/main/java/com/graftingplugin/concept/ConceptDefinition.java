package com.graftingplugin.concept;

import com.graftingplugin.aspect.GraftAspect;

import java.util.Set;

public record ConceptDefinition(String key, String displayName, Set<GraftAspect> aspects) {

    public ConceptDefinition {
        aspects = Set.copyOf(aspects);
    }
}
