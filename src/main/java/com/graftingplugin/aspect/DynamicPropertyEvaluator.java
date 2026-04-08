package com.graftingplugin.aspect;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Evaluates native Bukkit properties into a {@link DynamicPropertyProfile}.
 * <p>
 * Methods try the real Bukkit API first and fall back to name-based heuristics
 * so the code works both on a live server and inside the offline test harness.
 */
public final class DynamicPropertyEvaluator {

    // ── blocks ──────────────────────────────────────────────────────────

    public DynamicPropertyProfile evaluateBlock(Material material) {
        if (material == null || isAir(material)) {
            return DynamicPropertyProfile.EMPTY;
        }
        Map<DynamicProperty, Double> props = new EnumMap<>(DynamicProperty.class);
        props.put(DynamicProperty.MASS, blockMass(material));
        props.put(DynamicProperty.THERMAL, blockThermal(material));
        props.put(DynamicProperty.LUMINANCE, blockLuminance(material));
        props.put(DynamicProperty.VOLATILITY, blockVolatility(material));
        return new DynamicPropertyProfile(props);
    }

    // ── entities ────────────────────────────────────────────────────────

    public DynamicPropertyProfile evaluateEntity(Entity entity) {
        if (entity == null) {
            return DynamicPropertyProfile.EMPTY;
        }
        Map<DynamicProperty, Double> props = new EnumMap<>(DynamicProperty.class);
        if (entity instanceof LivingEntity living) {
            props.put(DynamicProperty.MASS, entityMass(living));
            props.put(DynamicProperty.MOTILITY, entityMotility(living));
        }
        if (entity.getFireTicks() > 0) {
            props.put(DynamicProperty.THERMAL, 1.0);
        }
        return new DynamicPropertyProfile(props);
    }

    // ── items ───────────────────────────────────────────────────────────

    public DynamicPropertyProfile evaluateItem(ItemStack itemStack) {
        if (itemStack == null || isAir(itemStack.getType())) {
            return DynamicPropertyProfile.EMPTY;
        }
        return evaluateBlock(itemStack.getType());
    }

    // ── block property helpers ──────────────────────────────────────────

    private double blockMass(Material material) {
        double hardness;
        try {
            float rawHardness = material.getHardness();
            hardness = rawHardness < 0 ? 50.0 : rawHardness; // -1 = indestructible (bedrock)
        } catch (Throwable ignored) {
            hardness = estimateHardness(material);
        }
        // Normalize: Stone (hardness 1.5) = mass 1.0
        return hardness / 1.5;
    }

    private double blockThermal(Material material) {
        if (isHeatSource(material)) {
            return 1.0;
        }
        if (isIce(material)) {
            return -1.0;
        }
        return 0.0;
    }

    private double blockLuminance(Material material) {
        return isLightSource(material) ? 1.0 : 0.0;
    }

    private double blockVolatility(Material material) {
        return material == Material.TNT ? 1.0 : 0.0;
    }

    // ── entity property helpers ─────────────────────────────────────────

    private double entityMass(LivingEntity living) {
        double maxHealth;
        try {
            maxHealth = living.getMaxHealth();
        } catch (Throwable ignored) {
            maxHealth = 20.0;
        }
        // Normalize: Zombie/Player (health 20.0) = mass 1.0
        return maxHealth / 20.0;
    }

    private double entityMotility(LivingEntity living) {
        try {
            AttributeInstance attribute = living.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute != null) {
                return attribute.getValue();
            }
        } catch (Throwable ignored) {
            // Fallback
        }
        return 0.2; // default mob speed
    }

    // ── classification helpers (work offline, no server needed) ──────────

    private double estimateHardness(Material material) {
        String name = material.name();
        if (name.contains("BEDROCK") || name.contains("BARRIER") || name.contains("COMMAND")) return 50.0;
        if (name.contains("OBSIDIAN") || name.contains("CRYING_OBSIDIAN") || name.contains("REINFORCED")) return 50.0;
        if (name.contains("NETHERITE")) return 50.0;
        if (name.contains("IRON_BLOCK") || name.contains("GOLD_BLOCK") || name.contains("DIAMOND_BLOCK") || name.contains("EMERALD_BLOCK")) return 5.0;
        if (name.contains("ANVIL")) return 5.0;
        if (name.contains("DEEPSLATE")) return 3.0;
        if (name.endsWith("_ORE")) return 3.0;
        if (name.contains("STONE") || name.contains("BRICK") || name.contains("COBBLESTONE") || name.contains("SANDSTONE")) return 1.5;
        if (name.contains("TERRACOTTA") || name.contains("CONCRETE") || name.contains("PRISMARINE")) return 1.5;
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) return 2.0;
        if (name.contains("DIRT") || name.contains("GRASS_BLOCK") || name.contains("SAND") || name.contains("GRAVEL") || name.contains("CLAY") || name.contains("SOUL")) return 0.5;
        if (name.contains("GLASS") || name.contains("ICE")) return 0.3;
        if (name.contains("WOOL") || name.contains("CARPET") || name.contains("SPONGE") || name.contains("SNOW")) return 0.2;
        if (name.contains("LAVA") || name.contains("WATER")) return 0.0;
        if (isAir(material)) return 0.0;
        return 1.0; // unknown solid block default
    }

    private boolean isHeatSource(Material material) {
        return switch (material) {
            case LAVA, LAVA_CAULDRON, MAGMA_BLOCK, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE -> true;
            default -> false;
        };
    }

    private boolean isIce(Material material) {
        return switch (material) {
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> true;
            default -> false;
        };
    }

    private boolean isLightSource(Material material) {
        return switch (material) {
            case TORCH, SOUL_TORCH, LANTERN, SOUL_LANTERN, GLOWSTONE, SEA_LANTERN, SHROOMLIGHT, END_ROD, REDSTONE_LAMP, JACK_O_LANTERN,
                 BEACON, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE, LAVA, LAVA_CAULDRON -> true;
            default -> false;
        };
    }

    private boolean isAir(Material material) {
        return material == Material.AIR
            || material == Material.CAVE_AIR
            || material == Material.VOID_AIR;
    }
}
