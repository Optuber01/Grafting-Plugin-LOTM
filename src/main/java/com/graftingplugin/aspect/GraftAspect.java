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
    PULL(GraftFamily.STATE, "pull", "Pull"),
    STICKY(GraftFamily.STATE, "sticky", "Sticky"),
    SLIPPERY(GraftFamily.STATE, "slippery", "Slippery"),
    BOUNCE(GraftFamily.STATE, "bounce", "Bounce"),
    HEAL(GraftFamily.STATE, "heal", "Heal"),
    POISON(GraftFamily.STATE, "poison", "Poison"),
    SPEED(GraftFamily.STATE, "speed", "Speed"),
    SLOW(GraftFamily.STATE, "slow", "Slow"),
    GLOW(GraftFamily.STATE, "glow", "Glow"),
    CONCEAL(GraftFamily.STATE, "conceal", "Conceal"),
    OPEN(GraftFamily.STATE, "open", "Open"),
    POWERED(GraftFamily.STATE, "powered", "Powered"),
    EXPLOSIVE(GraftFamily.STATE, "explosive", "Explosive"),
    TARGET(GraftFamily.RELATION, "target", "Target"),
    AGGRO(GraftFamily.RELATION, "aggro", "Aggro"),
    OWNER(GraftFamily.RELATION, "owner", "Owner"),
    TETHER(GraftFamily.RELATION, "tether", "Tether"),
    DESTINATION(GraftFamily.RELATION, "destination", "Destination"),
    RECEIVER(GraftFamily.RELATION, "receiver", "Receiver"),
    PAIRED_EXIT(GraftFamily.RELATION, "paired-exit", "Paired Exit"),
    CONTAINER_LINK(GraftFamily.RELATION, "container-link", "Container Link"),
    ANCHOR(GraftFamily.TOPOLOGY, "anchor", "Anchor"),
    ENTRY(GraftFamily.TOPOLOGY, "entry", "Entry"),
    EXIT(GraftFamily.TOPOLOGY, "exit", "Exit"),
    SURFACE(GraftFamily.TOPOLOGY, "surface", "Surface"),
    VOLUME(GraftFamily.TOPOLOGY, "volume", "Volume"),
    PATH_START(GraftFamily.TOPOLOGY, "path-start", "Path Start"),
    PATH_END(GraftFamily.TOPOLOGY, "path-end", "Path End"),
    NEAR(GraftFamily.TOPOLOGY, "near", "Near"),
    FAR(GraftFamily.TOPOLOGY, "far", "Far"),
    ON_ENTER(GraftFamily.SEQUENCE, "on-enter", "On Enter"),
    ON_HIT(GraftFamily.SEQUENCE, "on-hit", "On Hit"),
    ON_OPEN(GraftFamily.SEQUENCE, "on-open", "On Open"),
    ON_CONSUME(GraftFamily.SEQUENCE, "on-consume", "On Consume"),
    BEGIN(GraftFamily.SEQUENCE, "begin", "Begin"),
    END(GraftFamily.SEQUENCE, "end", "End"),
    RETURN(GraftFamily.SEQUENCE, "return", "Return"),
    REPEAT(GraftFamily.SEQUENCE, "repeat", "Repeat");

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
