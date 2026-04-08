package com.graftingplugin.subject;

import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.GraftFamily;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GraftSubject(String key, String displayName, SubjectKind kind, Set<GraftAspect> aspects, DynamicPropertyProfile properties) {

    public GraftSubject {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(aspects, "aspects");
        aspects = Set.copyOf(aspects);
        if (properties == null) {
            properties = DynamicPropertyProfile.EMPTY;
        }
    }

    public GraftSubject(String key, String displayName, SubjectKind kind, Set<GraftAspect> aspects) {
        this(key, displayName, kind, aspects, DynamicPropertyProfile.EMPTY);
    }

    public boolean exposes(GraftAspect aspect) {
        return aspects.contains(aspect);
    }

    public List<GraftAspect> aspectsFor(GraftFamily family) {
        return aspects.stream()
            .filter(aspect -> aspect.family() == family)
            .sorted()
            .toList();
    }
}
