package com.graftingplugin.aspect;

import com.graftingplugin.cast.GraftFamily;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AspectEffectConfig {

    private AspectEffectConfig() {
    }

    public record EffectSpec(
        PotionEffectType primaryEffect,
        PotionEffectType secondaryEffect,
        Particle particle,
        boolean causesFire,
        boolean causesBounce,
        boolean emitsLight,
        double velocityScale
    ) {
    }

    private static final Map<GraftAspect, EffectSpec> SPECS = Map.ofEntries(
        Map.entry(GraftAspect.LIGHT,
            new EffectSpec(PotionEffectType.GLOWING, null, Particle.END_ROD, false, false, true, 1.0)),
        Map.entry(GraftAspect.GLOW,
            new EffectSpec(PotionEffectType.GLOWING, null, Particle.END_ROD, false, false, true, 1.0)),
        Map.entry(GraftAspect.SPEED,
            new EffectSpec(PotionEffectType.SPEED, null, Particle.CLOUD, false, false, false, 1.5)),
        Map.entry(GraftAspect.SLOW,
            new EffectSpec(PotionEffectType.SLOWNESS, null, Particle.SNOWFLAKE, false, false, false, 0.5)),
        Map.entry(GraftAspect.STICKY,
            new EffectSpec(PotionEffectType.SLOWNESS, null, Particle.SNOWFLAKE, false, false, false, 0.5)),
        Map.entry(GraftAspect.FREEZE,
            new EffectSpec(PotionEffectType.SLOWNESS, null, Particle.SNOWFLAKE, false, false, false, 0.5)),
        Map.entry(GraftAspect.HEAVY,
            new EffectSpec(PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, Particle.ASH, false, false, false, 0.75)),
        Map.entry(GraftAspect.POISON,
            new EffectSpec(PotionEffectType.POISON, null, Particle.SQUID_INK, false, false, false, 1.0)),
        Map.entry(GraftAspect.HEAL,
            new EffectSpec(PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION, Particle.HEART, false, false, false, 1.0)),
        Map.entry(GraftAspect.STATUS,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.CONCEAL,
            new EffectSpec(PotionEffectType.INVISIBILITY, null, Particle.SMOKE, false, false, false, 1.0)),
        Map.entry(GraftAspect.HEAT,
            new EffectSpec(null, null, Particle.FLAME, true, false, false, 1.0)),
        Map.entry(GraftAspect.IGNITE,
            new EffectSpec(null, null, Particle.FLAME, true, false, false, 1.0)),
        Map.entry(GraftAspect.BOUNCE,
            new EffectSpec(null, null, Particle.CLOUD, false, true, false, 1.0)),
        Map.entry(GraftAspect.AGGRO,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.TETHER,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.TARGET,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.RECEIVER,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.DESTINATION,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.CONTAINER_LINK,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.ANCHOR,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.ENTRY,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.EXIT,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.NEAR,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.FAR,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.PATH_START,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.PATH_END,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.BEGIN,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.END,
            new EffectSpec(null, null, Particle.PORTAL, false, false, false, 1.0)),
        Map.entry(GraftAspect.ON_HIT,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0)),
        Map.entry(GraftAspect.ON_OPEN,
            new EffectSpec(null, null, Particle.ENCHANT, false, false, false, 1.0))
    );

    public static Optional<EffectSpec> getSpec(GraftAspect aspect) {
        return Optional.ofNullable(SPECS.get(aspect));
    }

    public static Particle particleFor(GraftAspect aspect) {
        EffectSpec spec = SPECS.get(aspect);
        return spec != null ? spec.particle() : Particle.ENCHANT;
    }

    public static boolean emitsLight(GraftAspect aspect) {
        EffectSpec spec = SPECS.get(aspect);
        return spec != null && spec.emitsLight();
    }

    public static Set<GraftAspect> aspectsForEffect(PotionEffectType effectType) {
        if (effectType == null) {
            return Set.of();
        }
        return SPECS.entrySet().stream()
            .filter(entry -> entry.getKey().family() == GraftFamily.STATE)
            .filter(entry -> effectType.equals(entry.getValue().primaryEffect()) || effectType.equals(entry.getValue().secondaryEffect()))
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(() -> java.util.EnumSet.noneOf(GraftAspect.class)));
    }

    public static boolean isPayloadAspect(GraftAspect aspect) {
        return aspect.family() == GraftFamily.STATE && aspect != GraftAspect.BOUNCE && aspect != GraftAspect.STATUS;
    }
}
