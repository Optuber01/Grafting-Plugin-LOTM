package com.graftingplugin.validation;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.cast.GraftFamily;
import com.graftingplugin.relation.RelationGraftPlanner;
import com.graftingplugin.sequence.SequenceTamperPlanner;
import com.graftingplugin.state.StateTransferPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import com.graftingplugin.topology.TopologyGraftPlanner;

import java.util.EnumMap;
import java.util.HashSet;
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
            .filter(rules::containsKey)
            .filter(aspect -> supportsSourceKind(source.kind(), aspect))
            .toList();
    }

    public List<GraftAspect> supportedFamilyAspects(GraftFamily family) {
        return GraftAspect.forFamily(family).stream()
            .filter(rules::containsKey)
            .toList();
    }

    public GraftCompatibilityResult validateAspectSelection(GraftFamily family, GraftSubject source, GraftAspect aspect) {
        if (aspect.family() != family) {
            return GraftCompatibilityResult.failure("That aspect belongs to another mode.");
        }
        if (!rules.containsKey(aspect)) {
            return GraftCompatibilityResult.failure("That aspect is not supported right now.");
        }
        if (!source.exposes(aspect)) {
            return GraftCompatibilityResult.failure(source.displayName() + " does not have " + aspect.displayName() + '.');
        }
        if (!supportsSourceKind(source.kind(), aspect)) {
            return GraftCompatibilityResult.failure(source.displayName() + " cannot use " + aspect.displayName() + '.');
        }
        return GraftCompatibilityResult.ok();
    }

    public GraftCompatibilityResult validateTarget(GraftSubject source, GraftAspect aspect, GraftSubject target) {
        GraftCompatibilityResult sourceValidation = validateAspectSelection(aspect.family(), source, aspect);
        if (!sourceValidation.success()) {
            return sourceValidation;
        }

        CompatibilityRule rule = rules.get(aspect);
        if (rule == null || !rule.supports(source.kind(), target.kind())) {
            return GraftCompatibilityResult.failure(target.displayName() + " cannot receive " + aspect.displayName() + '.');
        }
        return GraftCompatibilityResult.ok();
    }

    public Set<SubjectKind> supportedTargetKinds(GraftAspect aspect) {
        CompatibilityRule rule = rules.get(aspect);
        return rule == null ? Set.of() : rule.targetKinds();
    }

    private boolean supportsSourceKind(SubjectKind kind, GraftAspect aspect) {
        CompatibilityRule rule = rules.get(aspect);
        return rule != null && rule.supportsSource(kind);
    }

    private Map<GraftAspect, CompatibilityRule> buildRules() {
        Map<GraftAspect, CompatibilityRule> mappedRules = new EnumMap<>(GraftAspect.class);
        addStateRules(mappedRules);
        addBinaryRules(mappedRules, GraftFamily.RELATION, (aspect, source, target) -> new RelationGraftPlanner().plan(aspect, source, target).isPresent());
        addBinaryRules(mappedRules, GraftFamily.TOPOLOGY, (aspect, source, target) -> new TopologyGraftPlanner().plan(aspect, source, target).isPresent());
        addBinaryRules(mappedRules, GraftFamily.SEQUENCE, (aspect, source, target) -> new SequenceTamperPlanner().plan(aspect, source, target).isPresent());
        return mappedRules;
    }

    private void addStateRules(Map<GraftAspect, CompatibilityRule> mappedRules) {
        StateTransferPlanner planner = new StateTransferPlanner();
        for (GraftAspect aspect : GraftAspect.forFamily(GraftFamily.STATE)) {
            Set<CompatibilityPair> supportedPairs = new HashSet<>();
            for (SubjectKind targetKind : SubjectKind.values()) {
                if (planner.plan(aspect, stub(targetKind, aspect)).isEmpty()) {
                    continue;
                }
                for (SubjectKind sourceKind : SubjectKind.values()) {
                    supportedPairs.add(new CompatibilityPair(sourceKind, targetKind));
                }
            }
            if (!supportedPairs.isEmpty()) {
                mappedRules.put(aspect, new CompatibilityRule(supportedPairs));
            }
        }
    }

    private void addBinaryRules(Map<GraftAspect, CompatibilityRule> mappedRules, GraftFamily family, BinaryPlannerProbe probe) {
        for (GraftAspect aspect : GraftAspect.forFamily(family)) {
            Set<CompatibilityPair> supportedPairs = new HashSet<>();
            for (SubjectKind sourceKind : SubjectKind.values()) {
                GraftSubject source = stub(sourceKind, aspect);
                for (SubjectKind targetKind : SubjectKind.values()) {
                    GraftSubject target = stub(targetKind, aspect);
                    if (probe.supports(aspect, source, target)) {
                        supportedPairs.add(new CompatibilityPair(sourceKind, targetKind));
                    }
                }
            }
            if (!supportedPairs.isEmpty()) {
                mappedRules.put(aspect, new CompatibilityRule(supportedPairs));
            }
        }
    }

    private GraftSubject stub(SubjectKind kind, GraftAspect aspect) {
        return new GraftSubject("stub:" + kind.name(), kind.name(), kind, Set.of(aspect));
    }

    @FunctionalInterface
    private interface BinaryPlannerProbe {
        boolean supports(GraftAspect aspect, GraftSubject source, GraftSubject target);
    }

    private record CompatibilityRule(Set<CompatibilityPair> supportedPairs) {

        private CompatibilityRule {
            supportedPairs = Set.copyOf(supportedPairs);
        }

        private boolean supportsSource(SubjectKind sourceKind) {
            return supportedPairs.stream().anyMatch(pair -> pair.sourceKind() == sourceKind);
        }

        private boolean supports(SubjectKind sourceKind, SubjectKind targetKind) {
            return supportedPairs.contains(new CompatibilityPair(sourceKind, targetKind));
        }

        private Set<SubjectKind> targetKinds() {
            Set<SubjectKind> targetKinds = new HashSet<>();
            for (CompatibilityPair pair : supportedPairs) {
                targetKinds.add(pair.targetKind());
            }
            return Set.copyOf(targetKinds);
        }
    }

    private record CompatibilityPair(SubjectKind sourceKind, SubjectKind targetKind) {
    }
}
