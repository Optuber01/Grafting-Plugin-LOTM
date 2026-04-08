package com.graftingplugin.aspect;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable bag of numeric property scores for a graft subject.
 * Property values default to 0.0 when absent.
 */
public record DynamicPropertyProfile(Map<DynamicProperty, Double> properties) {

    public static final DynamicPropertyProfile EMPTY = new DynamicPropertyProfile(Map.of());

    public DynamicPropertyProfile {
        Objects.requireNonNull(properties, "properties");
        properties = Map.copyOf(properties);
    }

    public double get(DynamicProperty property) {
        return properties.getOrDefault(property, 0.0);
    }

    public boolean exceeds(DynamicProperty property, double threshold) {
        return get(property) >= threshold;
    }
}
