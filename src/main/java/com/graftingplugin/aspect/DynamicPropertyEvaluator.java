package com.graftingplugin.aspect;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;


public final class DynamicPropertyEvaluator {


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


    public DynamicPropertyProfile evaluateEntity(Entity entity) {
        if (entity == null) {
            return DynamicPropertyProfile.EMPTY;
        }
        Map<DynamicProperty, Double> props = new EnumMap<>(DynamicProperty.class);
        if (entity instanceof LivingEntity living) {
            props.put(DynamicProperty.MASS, entityMass(living));
            props.put(DynamicProperty.MOTILITY, entityMotility(living));
            props.put(DynamicProperty.VITALITY, entityVitality(living));
            applyLivingEffectProperties(props, living);
        }
        if (entity.getFireTicks() > 0) {
            props.put(DynamicProperty.THERMAL, 1.0);
        }
        if (entity.isGlowing()) {
            props.merge(DynamicProperty.LUMINANCE, 0.8D, Double::sum);
        }
        return new DynamicPropertyProfile(props);
    }


    public DynamicPropertyProfile evaluateItem(ItemStack itemStack) {
        if (itemStack == null || isAir(itemStack.getType())) {
            return DynamicPropertyProfile.EMPTY;
        }
        Map<DynamicProperty, Double> props = new EnumMap<>(DynamicProperty.class);
        DynamicPropertyProfile materialProfile = evaluateBlock(itemStack.getType());
        for (DynamicProperty property : DynamicProperty.values()) {
            double value = materialProfile.get(property);
            if (value != 0.0D) {
                props.put(property, value);
            }
        }
        double integrity = itemIntegrity(itemStack);
        if (integrity > 0.0D) {
            props.put(DynamicProperty.INTEGRITY, integrity);
        }
        return props.isEmpty() ? DynamicPropertyProfile.EMPTY : new DynamicPropertyProfile(props);
    }


    private double blockMass(Material material) {
        double hardness;
        try {
            float rawHardness = material.getHardness();
            hardness = rawHardness < 0 ? 50.0 : rawHardness;
        } catch (Throwable ignored) {
            hardness = estimateHardness(material);
        }

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


    private double entityMass(LivingEntity living) {
        double maxHealth = 20.0;
        try {
            AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                maxHealth = attribute.getValue();
            }
        } catch (Throwable ignored) {
        }

        return maxHealth / 20.0;
    }

    private double entityMotility(LivingEntity living) {
        try {
            AttributeInstance attribute = living.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute != null) {
                return attribute.getValue();
            }
        } catch (Throwable ignored) {

        }
        return 0.2;
    }

    private double entityVitality(LivingEntity living) {
        try {
            return Math.max(0.0D, living.getHealth()) / 20.0D;
        } catch (Throwable ignored) {
            return 1.0D;
        }
    }

    private void applyLivingEffectProperties(Map<DynamicProperty, Double> props, LivingEntity living) {
        try {
            for (PotionEffect effect : living.getActivePotionEffects()) {
                int amplifier = effect.getAmplifier() + 1;
                double durationFactor = Math.min(1.0D, effect.getDuration() / 1200.0D);
                if (effect.getType().equals(PotionEffectType.SPEED)) {
                    props.merge(DynamicProperty.MOTILITY, 0.18D * amplifier + 0.08D * durationFactor, Double::sum);
                } else if (effect.getType().equals(PotionEffectType.SLOWNESS)) {
                    props.merge(DynamicProperty.MASS, 0.75D * amplifier + 0.2D * durationFactor, Double::sum);
                    props.merge(DynamicProperty.MOTILITY, -(0.08D * amplifier), Double::sum);
                } else if (effect.getType().equals(PotionEffectType.POISON)) {
                    props.merge(DynamicProperty.TOXICITY, 0.7D * amplifier + 0.3D * durationFactor, Double::sum);
                } else if (effect.getType().equals(PotionEffectType.REGENERATION)
                    || effect.getType().equals(PotionEffectType.INSTANT_HEALTH)
                    || effect.getType().equals(PotionEffectType.HEALTH_BOOST)
                    || effect.getType().equals(PotionEffectType.ABSORPTION)
                    || effect.getType().equals(PotionEffectType.SATURATION)) {
                    props.merge(DynamicProperty.VITALITY, 0.7D * amplifier + 0.4D * durationFactor, Double::sum);
                } else if (effect.getType().equals(PotionEffectType.INVISIBILITY)) {
                    props.merge(DynamicProperty.OBSCURITY, 0.8D * amplifier + 0.3D * durationFactor, Double::sum);
                } else if (effect.getType().equals(PotionEffectType.GLOWING)) {
                    props.merge(DynamicProperty.LUMINANCE, 0.6D * amplifier + 0.2D * durationFactor, Double::sum);
                }
            }
        } catch (Throwable ignored) {

        }
    }

    private double itemIntegrity(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().getMaxDurability() <= 0 || !itemStack.hasItemMeta()) {
            return 0.0D;
        }
        if (!(itemStack.getItemMeta() instanceof Damageable damageable)) {
            return 0.0D;
        }
        int maxDurability = itemStack.getType().getMaxDurability();
        int remaining = Math.max(0, maxDurability - damageable.getDamage());
        return (remaining / (double) maxDurability) * 3.0D;
    }


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
        return 1.0;
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
