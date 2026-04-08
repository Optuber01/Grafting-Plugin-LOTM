package com.graftingplugin.validation;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraftCompatibilityValidator {

    private final Map<GraftAspect, CompatibilityRule> rules;

    public GraftCompatibilityValidator() {
        this.rules = buildRules();
    }

    public List<GraftAspect> compatibleSourceAspects(GraftFamily family, GraftSubject source) {
        return source.aspectsFor(family).stream()
            .filter(aspect -> supportsSourceKind(source.kind(), aspect))
            .toList();
    }

    public GraftCompatibilityResult validateAspectSelection(GraftFamily family, GraftSubject source, GraftAspect aspect) {
        if (aspect.family() != family) {
            return GraftCompatibilityResult.failure("Aspect belongs to a different graft family.");
        }
        if (!source.exposes(aspect)) {
            return GraftCompatibilityResult.failure("Source subject does not expose aspect " + aspect.displayName() + '.');
        }
        if (!supportsSourceKind(source.kind(), aspect)) {
            return GraftCompatibilityResult.failure("Source subject kind cannot provide aspect " + aspect.displayName() + '.');
        }
        return GraftCompatibilityResult.ok();
    }

    public GraftCompatibilityResult validateTarget(GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult sourceValidation = validateAspectSelection(aspect.family(), source, aspect);
        if (!sourceValidation.success()) {
            return sourceValidation;
        }

        CompatibilityRule rule = rules.get(aspect);
        if (rule == null || !rule.targetKinds().contains(target.kind())) {
            return GraftCompatibilityResult.failure("Target subject cannot accept aspect " + aspect.displayName() + '.');
        }
        return GraftCompatibilityResult.ok();
    }

    public Set<SubjectKind> supportedTargetKinds(GraftAspect aspect) {
        CompatibilityRule rule = rules.get(aspect);
        return rule == null ? Set.of() : rule.targetKinds();
    }

    private boolean supportsSourceKind(SubjectKind kind, GraftAspect aspect) {
        CompatibilityRule rule = rules.get(aspect);
        return rule != null && rule.sourceKinds().contains(kind);
    }

    private Map<GraftAspect, CompatibilityRule> buildRules() {
        Map<GraftAspect, CompatibilityRule> mappedRules = new EnumMap<>(GraftAspect.class);
        add(mappedRules, Set.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE, GraftAspect.FREEZE, GraftAspect.CONCEAL, GraftAspect.GLOW),
            kinds(SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.ITEM, SubjectKind.PROJECTILE, SubjectKind.CONCEPT),
            kinds(SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.PROJECTILE, SubjectKind.LOCATION, SubjectKind.AREA));
        add(mappedRules, Set.of(GraftAspect.HEAVY, GraftAspect.PULL),
            kinds(SubjectKind.ENTITY, SubjectKind.ITEM, SubjectKind.CONCEPT),
            kinds(SubjectKind.ENTITY, SubjectKind.PROJECTILE, SubjectKind.AREA));
        add(mappedRules, Set.of(GraftAspect.STICKY, GraftAspect.SLOW),
            kinds(SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.POTION, SubjectKind.CONCEPT),
            kinds(SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.PROJECTILE, SubjectKind.AREA));
        add(mappedRules, Set.of(GraftAspect.SLIPPERY),
            kinds(SubjectKind.BLOCK, SubjectKind.CONCEPT),
            kinds(SubjectKind.BLOCK, SubjectKind.AREA, SubjectKind.ENTITY));
        add(mappedRules, Set.of(GraftAspect.BOUNCE),
            kinds(SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.ITEM, SubjectKind.CONCEPT),
            kinds(SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.PROJECTILE));
        add(mappedRules, Set.of(GraftAspect.HEAL, GraftAspect.POISON, GraftAspect.SPEED),
            kinds(SubjectKind.ENTITY, SubjectKind.POTION, SubjectKind.ITEM, SubjectKind.CONCEPT, SubjectKind.PROJECTILE),
            kinds(SubjectKind.ENTITY, SubjectKind.PROJECTILE, SubjectKind.AREA));
        add(mappedRules, Set.of(GraftAspect.OPEN, GraftAspect.POWERED, GraftAspect.EXPLOSIVE),
            kinds(SubjectKind.BLOCK, SubjectKind.CONTAINER, SubjectKind.ITEM, SubjectKind.CONCEPT),
            kinds(SubjectKind.BLOCK, SubjectKind.CONTAINER, SubjectKind.PROJECTILE));
        add(mappedRules, Set.of(GraftAspect.TARGET, GraftAspect.AGGRO, GraftAspect.OWNER, GraftAspect.TETHER),
            kinds(SubjectKind.ENTITY, SubjectKind.PROJECTILE, SubjectKind.CONCEPT),
            kinds(SubjectKind.ENTITY, SubjectKind.PROJECTILE, SubjectKind.LOCATION));
        add(mappedRules, Set.of(GraftAspect.DESTINATION, GraftAspect.RECEIVER, GraftAspect.CONTAINER_LINK, GraftAspect.PAIRED_EXIT),
            kinds(SubjectKind.CONTAINER, SubjectKind.PROJECTILE, SubjectKind.CONCEPT, SubjectKind.LOCATION, SubjectKind.BLOCK),
            kinds(SubjectKind.CONTAINER, SubjectKind.BLOCK, SubjectKind.ENTITY, SubjectKind.LOCATION));
        add(mappedRules, Set.of(GraftAspect.ANCHOR, GraftAspect.ENTRY, GraftAspect.EXIT, GraftAspect.SURFACE, GraftAspect.VOLUME, GraftAspect.PATH_START, GraftAspect.PATH_END, GraftAspect.NEAR, GraftAspect.FAR),
            kinds(SubjectKind.BLOCK, SubjectKind.LOCATION, SubjectKind.AREA, SubjectKind.CONCEPT),
            kinds(SubjectKind.BLOCK, SubjectKind.LOCATION, SubjectKind.AREA));
        add(mappedRules, Set.of(GraftAspect.ON_ENTER, GraftAspect.ON_HIT, GraftAspect.ON_OPEN, GraftAspect.ON_CONSUME, GraftAspect.BEGIN, GraftAspect.END, GraftAspect.RETURN, GraftAspect.REPEAT),
            kinds(SubjectKind.PROJECTILE, SubjectKind.CONTAINER, SubjectKind.ITEM, SubjectKind.POTION, SubjectKind.LOCATION, SubjectKind.AREA, SubjectKind.CONCEPT),
            kinds(SubjectKind.PROJECTILE, SubjectKind.CONTAINER, SubjectKind.ITEM, SubjectKind.BLOCK, SubjectKind.LOCATION, SubjectKind.AREA));
        return mappedRules;
    }

    private void add(Map<GraftAspect, CompatibilityRule> mappedRules, Set<GraftAspect> aspects, Set<SubjectKind> sourceKinds, Set<SubjectKind> targetKinds) {
        CompatibilityRule rule = new CompatibilityRule(sourceKinds, targetKinds);
        for (GraftAspect aspect : aspects) {
            mappedRules.put(aspect, rule);
        }
    }

    private Set<SubjectKind> kinds(SubjectKind first, SubjectKind... remaining) {
        return Set.copyOf(EnumSet.of(first, remaining));
    }

    private record CompatibilityRule(Set<SubjectKind> sourceKinds, Set<SubjectKind> targetKinds) {
    }
}
