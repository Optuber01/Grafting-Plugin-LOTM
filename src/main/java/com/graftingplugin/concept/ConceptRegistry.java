package com.graftingplugin.concept;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ConceptRegistry {

    private final Map<String, ConceptDefinition> concepts;

    private ConceptRegistry(Map<String, ConceptDefinition> concepts) {
        this.concepts = Map.copyOf(concepts);
    }

    public static ConceptRegistry fromConfig(FileConfiguration config) {
        Map<String, ConceptDefinition> loadedConcepts = loadConcepts(config);
        if (loadedConcepts.isEmpty()) {
            loadedConcepts = defaultConcepts();
        }
        return new ConceptRegistry(loadedConcepts);
    }

    public Optional<GraftSubject> resolve(String input) {
        return Optional.ofNullable(concepts.get(normalizeKey(input)))
            .map(definition -> new GraftSubject(
                "concept:" + definition.key(),
                definition.displayName(),
                SubjectKind.CONCEPT,
                definition.aspects()
            ));
    }

    public List<String> keys() {
        return concepts.keySet().stream().sorted().toList();
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
            concepts.put(normalizedKey, new ConceptDefinition(normalizedKey, displayName, aspects));
        }
        return concepts;
    }

    private static Map<String, ConceptDefinition> defaultConcepts() {
        Map<String, ConceptDefinition> defaults = new LinkedHashMap<>();
        defaults.put("sun", new ConceptDefinition("sun", "Sun", Set.of(GraftAspect.LIGHT, GraftAspect.HEAT, GraftAspect.IGNITE)));
        defaults.put("moon", new ConceptDefinition("moon", "Moon", Set.of(GraftAspect.LIGHT, GraftAspect.CONCEAL)));
        defaults.put("gravity", new ConceptDefinition("gravity", "Gravity", Set.of(GraftAspect.HEAVY, GraftAspect.PULL)));
        defaults.put("beginning", new ConceptDefinition("beginning", "Beginning", Set.of(GraftAspect.ENTRY, GraftAspect.PATH_START, GraftAspect.BEGIN)));
        defaults.put("end", new ConceptDefinition("end", "End", Set.of(GraftAspect.EXIT, GraftAspect.PATH_END, GraftAspect.END)));
        defaults.put("distance", new ConceptDefinition("distance", "Distance", Set.of(GraftAspect.NEAR, GraftAspect.FAR)));
        defaults.put("binding", new ConceptDefinition("binding", "Binding", Set.of(GraftAspect.TETHER, GraftAspect.ANCHOR)));
        defaults.put("concealment", new ConceptDefinition("concealment", "Concealment", Set.of(GraftAspect.CONCEAL)));
        return defaults;
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
