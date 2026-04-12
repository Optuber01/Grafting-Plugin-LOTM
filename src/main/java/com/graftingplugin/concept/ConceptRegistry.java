package com.graftingplugin.concept;

import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;

public final class ConceptRegistry {

    private final Map<String, ConceptDefinition> concepts;

    private ConceptRegistry(Map<String, ConceptDefinition> concepts) {
        this.concepts = Map.copyOf(concepts);
    }

    public static ConceptRegistry fromConfig(FileConfiguration config) {
        Map<String, ConceptDefinition> loadedConcepts = loadConcepts(config);
        Map<String, ConceptDefinition> defaults = defaultConcepts();
        if (loadedConcepts.isEmpty()) {
            loadedConcepts = defaults;
        } else {
            for (Map.Entry<String, ConceptDefinition> entry : defaults.entrySet()) {
                loadedConcepts.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return new ConceptRegistry(loadedConcepts);
    }

    public Optional<GraftSubject> resolve(String input) {
        return Optional.ofNullable(concepts.get(normalizeKey(input)))
            .map(definition -> new GraftSubject(
                "concept:" + definition.key(),
                definition.displayName(),
                SubjectKind.CONCEPT,
                definition.aspects(),
                definition.properties()
            ));
    }

    public List<String> keys() {
        return concepts.keySet().stream().sorted().toList();
    }

    public java.util.Collection<ConceptDefinition> allConcepts() {
        return concepts.values();
    }

    private static Map<String, ConceptDefinition> loadConcepts(FileConfiguration config) {
        ConfigurationSection conceptsSection = config.getConfigurationSection("concepts");
        if (conceptsSection == null) {
            return Map.of();
        }

        Map<String, ConceptDefinition> concepts = new LinkedHashMap<>();
        for (String key : conceptsSection.getKeys(false)) {
            ConfigurationSection conceptSection = conceptsSection.getConfigurationSection(key);
            if (conceptSection == null) {
                continue;
            }
            String normalizedKey = normalizeKey(key);
            String displayName = conceptSection.getString("name", humanize(key));
            List<String> aspectTokens = conceptSection.getStringList("aspects");
            if (aspectTokens.isEmpty()) {
                continue;
            }

            Set<GraftAspect> aspects = aspectTokens.stream()
                .map(token -> GraftAspect.fromInput(token)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown concept aspect: " + token)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            concepts.put(normalizedKey, new ConceptDefinition(normalizedKey, displayName, aspects, propertiesFromSection(conceptSection.getConfigurationSection("properties"))));
        }
        return concepts;
    }

    private static Map<String, ConceptDefinition> defaultConcepts() {
        Map<String, ConceptDefinition> defaults = new LinkedHashMap<>();
        defaults.put("sun", new ConceptDefinition("sun", "Sun", Set.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE), profile(Map.of(DynamicProperty.LUMINANCE, 3.0D, DynamicProperty.THERMAL, 3.0D))));
        defaults.put("moon", new ConceptDefinition("moon", "Moon", Set.of(GraftAspect.LIGHT, GraftAspect.CONCEAL), profile(Map.of(DynamicProperty.LUMINANCE, 1.5D, DynamicProperty.OBSCURITY, 2.0D))));
        defaults.put("gravity", new ConceptDefinition("gravity", "Gravity", Set.of(GraftAspect.HEAVY), profile(Map.of(DynamicProperty.MASS, 8.0D))));
        defaults.put("vitality", new ConceptDefinition("vitality", "Vitality", Set.of(GraftAspect.HEAL), profile(Map.of(DynamicProperty.VITALITY, 5.0D))));
        defaults.put("swiftness", new ConceptDefinition("swiftness", "Swiftness", Set.of(GraftAspect.SPEED, GraftAspect.BOUNCE), profile(Map.of(DynamicProperty.MOTILITY, 1.2D))));
        defaults.put("frost", new ConceptDefinition("frost", "Frost", Set.of(GraftAspect.FREEZE, GraftAspect.SLOW), profile(Map.of(DynamicProperty.THERMAL, -2.0D, DynamicProperty.OBSCURITY, 0.5D))));
        defaults.put("venom", new ConceptDefinition("venom", "Venom", Set.of(GraftAspect.POISON), profile(Map.of(DynamicProperty.TOXICITY, 3.0D))));
        defaults.put("radiance", new ConceptDefinition("radiance", "Radiance", Set.of(GraftAspect.LIGHT, GraftAspect.GLOW), profile(Map.of(DynamicProperty.LUMINANCE, 3.5D))));
        defaults.put("concealment", new ConceptDefinition("concealment", "Concealment", Set.of(GraftAspect.CONCEAL), profile(Map.of(DynamicProperty.OBSCURITY, 3.0D))));
        defaults.put("beginning", new ConceptDefinition("beginning", "Beginning", Set.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN), DynamicPropertyProfile.EMPTY));
        defaults.put("end", new ConceptDefinition("end", "End", Set.of(GraftAspect.EXIT, GraftAspect.PATH_END, GraftAspect.END), DynamicPropertyProfile.EMPTY));
        defaults.put("distance", new ConceptDefinition("distance", "Distance", Set.of(GraftAspect.NEAR, GraftAspect.FAR), profile(Map.of(DynamicProperty.MOTILITY, 0.5D))));
        defaults.put("binding", new ConceptDefinition("binding", "Binding", Set.of(GraftAspect.TETHER, GraftAspect.ANCHOR), profile(Map.of(DynamicProperty.MASS, 3.0D))));
        return defaults;
    }

    private static DynamicPropertyProfile propertiesFromSection(ConfigurationSection section) {
        if (section == null) {
            return DynamicPropertyProfile.EMPTY;
        }
        Map<DynamicProperty, Double> values = new EnumMap<>(DynamicProperty.class);
        for (String key : section.getKeys(false)) {
            DynamicProperty property;
            try {
                property = DynamicProperty.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            values.put(property, section.getDouble(key));
        }
        return values.isEmpty() ? DynamicPropertyProfile.EMPTY : new DynamicPropertyProfile(values);
    }

    private static DynamicPropertyProfile profile(Map<DynamicProperty, Double> values) {
        return new DynamicPropertyProfile(values);
    }

    public static String normalizeKey(String input) {
        return input.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_]+", "-");
    }

    private static String humanize(String key) {
        String[] pieces = normalizeKey(key).split("-");
        StringBuilder builder = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return builder.toString();
    }
}
