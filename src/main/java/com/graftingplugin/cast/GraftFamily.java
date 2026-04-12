package com.graftingplugin.cast;

import java.util.Locale;
import java.util.Optional;

public enum GraftFamily {
    STATE,
    RELATION,
    TOPOLOGY,
    SEQUENCE;

    public String key() {
        return switch (this) {
            case STATE -> "state";
            case RELATION -> "link";
            case TOPOLOGY -> "location";
            case SEQUENCE -> "event";
        };
    }

    public String displayName() {
        return switch (this) {
            case STATE -> "State Graft";
            case RELATION -> "Link Graft";
            case TOPOLOGY -> "Location Graft";
            case SEQUENCE -> "Event Graft";
        };
    }

    public String description() {
        return switch (this) {
            case STATE -> "Move a state like heat, light, speed, or poison";
            case RELATION -> "Link a source to a target with aggro, tethers, or reroutes";
            case TOPOLOGY -> "Bend routes and anchor points in space";
            case SEQUENCE -> "Load an On Hit or On Open trigger";
        };
    }

    public String icon() {
        return switch (this) {
            case STATE -> "\u00a79\u25c6";
            case RELATION -> "\u00a7c\u2764";
            case TOPOLOGY -> "\u00a7a\u2726";
            case SEQUENCE -> "\u00a76\u26a1";
        };
    }

    public static Optional<GraftFamily> fromInput(String input) {
        String normalized = normalize(input);
        for (GraftFamily family : values()) {
            if (matches(family, normalized)) {
                return Optional.of(family);
            }
        }
        return Optional.empty();
    }

    private static boolean matches(GraftFamily family, String normalized) {
        return switch (family) {
            case STATE -> normalized.equals("state") || normalized.equals("stategraft") || normalized.equals("attributes");
            case RELATION -> normalized.equals("link") || normalized.equals("linkgraft") || normalized.equals("relation") || normalized.equals("connection");
            case TOPOLOGY -> normalized.equals("location") || normalized.equals("locationgraft") || normalized.equals("topology") || normalized.equals("spatial");
            case SEQUENCE -> normalized.equals("event") || normalized.equals("eventgraft") || normalized.equals("sequence") || normalized.equals("trigger");
        };
    }

    private static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "");
    }
}
