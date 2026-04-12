package com.graftingplugin.aspect;

import com.graftingplugin.cast.GraftFamily;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum GraftAspect {
    LIGHT(GraftFamily.STATE, "light", "Light"),
    HEAT(GraftFamily.STATE, "heat", "Heat"),
    IGNITE(GraftFamily.STATE, "ignite", "Ignite"),
    FREEZE(GraftFamily.STATE, "freeze", "Freeze"),
    HEAVY(GraftFamily.STATE, "heavy", "Heavy"),
    STICKY(GraftFamily.STATE, "sticky", "Sticky"),
    BOUNCE(GraftFamily.STATE, "bounce", "Bounce"),
    HEAL(GraftFamily.STATE, "heal", "Heal"),
    POISON(GraftFamily.STATE, "poison", "Poison"),
    SPEED(GraftFamily.STATE, "speed", "Speed"),
    SLOW(GraftFamily.STATE, "slow", "Slow"),
    GLOW(GraftFamily.STATE, "glow", "Glow"),
    CONCEAL(GraftFamily.STATE, "conceal", "Conceal"),
    TARGET(GraftFamily.RELATION, "target", "Target"),
    AGGRO(GraftFamily.RELATION, "aggro", "Aggro"),
    TETHER(GraftFamily.RELATION, "tether", "Tether"),
    DESTINATION(GraftFamily.RELATION, "destination", "Destination"),
    RECEIVER(GraftFamily.RELATION, "receiver", "Receiver"),
    CONTAINER_LINK(GraftFamily.RELATION, "container-link", "Container Link"),
    ANCHOR(GraftFamily.TOPOLOGY, "anchor", "Anchor"),
    ENTRY(GraftFamily.TOPOLOGY, "entry", "Entry"),
    EXIT(GraftFamily.TOPOLOGY, "exit", "Exit"),
    PATH_START(GraftFamily.TOPOLOGY, "path-start", "Path Start"),
    PATH_END(GraftFamily.TOPOLOGY, "path-end", "Path End"),
    NEAR(GraftFamily.TOPOLOGY, "near", "Near"),
    FAR(GraftFamily.TOPOLOGY, "far", "Far"),
    BEGIN(GraftFamily.TOPOLOGY, "begin", "Begin"),
    END(GraftFamily.TOPOLOGY, "end", "End"),
    ON_HIT(GraftFamily.SEQUENCE, "on-hit", "On Hit"),
    ON_OPEN(GraftFamily.SEQUENCE, "on-open", "On Open");

    private final GraftFamily family;
    private final String key;
    private final String displayName;

    GraftAspect(GraftFamily family, String key, String displayName) {
        this.family = family;
        this.key = key;
        this.displayName = displayName;
    }

    public GraftFamily family() {
        return family;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<GraftAspect> fromInput(String input) {
        String normalized = normalize(input);
        return Arrays.stream(values())
            .filter(aspect -> aspect.key.equals(normalized))
            .findFirst();
    }

    public static List<GraftAspect> forFamily(GraftFamily family) {
        return Arrays.stream(values())
            .filter(aspect -> aspect.family == family)
            .toList();
    }

    public static String normalize(String input) {
        return input.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\s_]+", "-");
    }
}
