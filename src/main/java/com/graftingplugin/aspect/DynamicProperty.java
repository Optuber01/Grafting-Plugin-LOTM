package com.graftingplugin.aspect;

/**
 * Numeric properties that can be dynamically read from any Bukkit object.
 * These feed into aspect derivation and effect intensity modulation.
 */
public enum DynamicProperty {
    /** Physical weight / solidity – derived from Material.getHardness() or entity max-health. */
    MASS,
    /** Thermal energy – positive = hot, negative = cold. */
    THERMAL,
    /** Light emission level. */
    LUMINANCE,
    /** Explosive potential. */
    VOLATILITY,
    /** Movement speed capability – derived from entity movement-speed attribute. */
    MOTILITY
}
