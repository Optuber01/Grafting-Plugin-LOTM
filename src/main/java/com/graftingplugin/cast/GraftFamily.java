package com.graftingplugin.cast;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum GraftFamily {
    STATE,
    RELATION,
    TOPOLOGY,
    SEQUENCE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return switch (this) {
            case STATE -> "State Transfer";
            case RELATION -> "Relation Graft";
            case TOPOLOGY -> "Topology Graft";
            case SEQUENCE -> "Sequence Tamper";
        };
    }

    public static Optional<GraftFamily> fromInput(String input) {
        return Arrays.stream(values())
            .filter(family -> family.key().equalsIgnoreCase(input))
            .findFirst();
    }
}
