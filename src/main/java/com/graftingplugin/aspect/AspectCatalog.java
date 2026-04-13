package com.graftingplugin.aspect;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AspectCatalog {

    private static final double MASS_HEAVY_THRESHOLD = 3.0;

    private final DynamicPropertyEvaluator evaluator = new DynamicPropertyEvaluator();

    public DynamicPropertyProfile blockProperties(Material material) {
        return evaluator.evaluateBlock(material);
    }

    public DynamicPropertyProfile entityProperties(Entity entity) {
        return evaluator.evaluateEntity(entity);
    }

    public DynamicPropertyProfile itemProperties(ItemStack itemStack) {
        return evaluator.evaluateItem(itemStack);
    }

    public DynamicPropertyProfile itemProperties(Material material) {
        return evaluator.evaluateBlock(material);
    }

    public Set<GraftAspect> blockAspects(Material material) {
        if (isAir(material)) {
            return Set.of();
        }

        EnumSet<GraftAspect> aspects = EnumSet.noneOf(GraftAspect.class);
        if (isHeatSource(material)) {
            aspects.add(GraftAspect.HEAT);
            aspects.add(GraftAspect.IGNITE);
        }
        if (isLightSource(material)) {
            aspects.add(GraftAspect.LIGHT);
        }
        if (material == Material.SLIME_BLOCK) {
            aspects.add(GraftAspect.BOUNCE);
        }
        if (material == Material.HONEY_BLOCK || material == Material.COBWEB) {
            aspects.add(GraftAspect.STICKY);
            aspects.add(GraftAspect.SLOW);
        }
        if (isIce(material)) {
            aspects.add(GraftAspect.FREEZE);
        }
        if (isDoorLike(material)) {
            aspects.add(GraftAspect.ON_OPEN);
            aspects.add(GraftAspect.ENTRY);
            aspects.add(GraftAspect.EXIT);
        }
        if (isPortalLike(material)) {
            aspects.add(GraftAspect.ENTRY);
            aspects.add(GraftAspect.EXIT);
            aspects.add(GraftAspect.ANCHOR);
        }
        if (isContainer(material)) {
            aspects.add(GraftAspect.ON_OPEN);
            aspects.add(GraftAspect.CONTAINER_LINK);
            aspects.add(GraftAspect.DESTINATION);
        }
        if (isPoweredBlock(material)) {
            aspects.add(GraftAspect.ON_OPEN);
        }


        aspects.add(GraftAspect.ANCHOR);

        deriveFromProperties(aspects, evaluator.evaluateBlock(material));
        return Set.copyOf(aspects);
    }

    public Set<GraftAspect> entityAspects(Entity entity) {
        if (entity == null) {
            return Set.of();
        }

        EnumSet<GraftAspect> aspects = EnumSet.noneOf(GraftAspect.class);
        if (entity.isGlowing()) {
            aspects.add(GraftAspect.GLOW);
        }
        if (entity instanceof LivingEntity livingEntity) {
            aspects.add(GraftAspect.HEAL);
            if (!livingEntity.getActivePotionEffects().isEmpty()) {
                aspects.add(GraftAspect.STATUS);
            }
            addLivingEffectAspects(aspects, livingEntity);
        }
        switch (entity.getType()) {
            case SLIME, MAGMA_CUBE -> aspects.add(GraftAspect.BOUNCE);
            case SPIDER, CAVE_SPIDER -> aspects.add(GraftAspect.STICKY);
            case ENDERMAN -> aspects.add(GraftAspect.CONCEAL);
            case BLAZE, GHAST -> {
                aspects.add(GraftAspect.HEAT);
                aspects.add(GraftAspect.IGNITE);
            }
            default -> {
            }
        }
        if (entity instanceof TNTPrimed || entity instanceof Creeper) {
            aspects.add(GraftAspect.HEAT);
        }
        aspects.add(GraftAspect.TETHER);
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            aspects.add(GraftAspect.AGGRO);
        }

        deriveEntityTraits(aspects, evaluator.evaluateEntity(entity));
        return Set.copyOf(aspects);
    }

    public Set<GraftAspect> itemAspects(ItemStack itemStack) {
        if (itemStack == null || isAir(itemStack.getType())) {
            return Set.of();
        }

        EnumSet<GraftAspect> aspects = EnumSet.noneOf(GraftAspect.class);
        aspects.addAll(itemAspects(itemStack.getType()));
        if (itemStack.containsEnchantment(Enchantment.FIRE_ASPECT) || itemStack.containsEnchantment(Enchantment.FLAME)) {
            aspects.add(GraftAspect.IGNITE);
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta instanceof PotionMeta potionMeta) {
            aspects.addAll(potionAspects(potionMeta));
        }

        deriveFromProperties(aspects, evaluator.evaluateItem(itemStack));

        return Set.copyOf(aspects);
    }

    public Set<GraftAspect> itemAspects(Material material) {
        if (isAir(material)) {
            return Set.of();
        }

        EnumSet<GraftAspect> aspects = EnumSet.noneOf(GraftAspect.class);
        if (material == Material.SPLASH_POTION || material == Material.LINGERING_POTION || material == Material.TIPPED_ARROW) {
            aspects.add(GraftAspect.ON_HIT);
        }
        aspects.add(GraftAspect.RECEIVER);
        return Set.copyOf(aspects);
    }

    public Set<GraftAspect> projectileAspects(Projectile projectile) {
        if (projectile == null) {
            return Set.of();
        }

        EnumSet<GraftAspect> aspects = EnumSet.of(GraftAspect.ON_HIT, GraftAspect.RECEIVER, GraftAspect.TARGET);
        aspects.add(GraftAspect.TETHER);
        if (projectile instanceof Fireball) {
            aspects.add(GraftAspect.HEAT);
            aspects.add(GraftAspect.IGNITE);
        }
        if (projectile instanceof AbstractArrow abstractArrow && abstractArrow instanceof org.bukkit.entity.Arrow arrow) {
            if (arrow.getBasePotionType() != null) {
                aspects.addAll(potionTypeAspects(arrow.getBasePotionType()));
            }
        }
        return Set.copyOf(aspects);
    }

    public Set<GraftAspect> locationAspects(Location location) {
        if (location == null) {
            return Set.of();
        }
        return Set.of(GraftAspect.ANCHOR);
    }

    public Set<GraftAspect> areaAspects(Location center, int radius) {
        if (center == null || radius <= 0) {
            return Set.of();
        }
        return Set.of(GraftAspect.ANCHOR);
    }

    public Set<GraftAspect> fluidAspects(Material material) {
        if (material == null) {
            return Set.of();
        }
        return switch (material) {
            case WATER, WATER_CAULDRON -> Set.of(GraftAspect.FREEZE, GraftAspect.SLOW, GraftAspect.ANCHOR);
            case LAVA, LAVA_CAULDRON -> Set.of(GraftAspect.HEAT, GraftAspect.IGNITE, GraftAspect.LIGHT, GraftAspect.SLOW, GraftAspect.ANCHOR);
            default -> Set.of();
        };
    }

    public DynamicPropertyProfile fluidProperties(Material material) {
        if (material == null) {
            return DynamicPropertyProfile.EMPTY;
        }
        return switch (material) {
            case WATER, WATER_CAULDRON -> new DynamicPropertyProfile(Map.of(
                DynamicProperty.THERMAL, -0.5,
                DynamicProperty.MASS, 1.0
            ));
            case LAVA, LAVA_CAULDRON -> new DynamicPropertyProfile(Map.of(
                DynamicProperty.THERMAL, 2.0,
                DynamicProperty.LUMINANCE, 1.0,
                DynamicProperty.MASS, 3.0
            ));
            default -> DynamicPropertyProfile.EMPTY;
        };
    }

    public Set<GraftAspect> voidAspects() {
        return Set.of(GraftAspect.CONCEAL, GraftAspect.LIGHT, GraftAspect.ANCHOR);
    }

    public DynamicPropertyProfile voidProperties() {
        return DynamicPropertyProfile.EMPTY;
    }

    public boolean isFluid(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case WATER, WATER_CAULDRON, LAVA, LAVA_CAULDRON -> true;
            default -> false;
        };
    }

    public boolean isContainer(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, HOPPER, DROPPER, DISPENSER, FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND, SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    private Set<GraftAspect> potionAspects(PotionMeta potionMeta) {
        EnumSet<GraftAspect> aspects = EnumSet.noneOf(GraftAspect.class);
        if (potionMeta.getBasePotionType() != null) {
            aspects.addAll(potionTypeAspects(potionMeta.getBasePotionType()));
        }
        for (PotionEffect effect : potionMeta.getCustomEffects()) {
            String key = effect.getType().getKey().getKey().toLowerCase(Locale.ROOT);
            switch (key) {
                case "speed" -> aspects.add(GraftAspect.SPEED);
                case "slowness" -> aspects.add(GraftAspect.SLOW);
                case "poison" -> aspects.add(GraftAspect.POISON);
                case "regeneration", "instant_health", "health_boost", "absorption" -> aspects.add(GraftAspect.HEAL);
                default -> {
                }
            }
        }
        return Set.copyOf(aspects);
    }

    private Set<GraftAspect> potionTypeAspects(PotionType potionType) {
        return switch (potionType) {
            case SWIFTNESS -> Set.of(GraftAspect.SPEED);
            case SLOWNESS -> Set.of(GraftAspect.SLOW);
            case POISON -> Set.of(GraftAspect.POISON);
            case HEALING, REGENERATION -> Set.of(GraftAspect.HEAL);
            default -> Set.of();
        };
    }

    private void addLivingEffectAspects(EnumSet<GraftAspect> aspects, LivingEntity livingEntity) {
        for (PotionEffect effect : livingEntity.getActivePotionEffects()) {
            PotionEffectType type = effect.getType();
            if (type.equals(PotionEffectType.SPEED)) {
                aspects.add(GraftAspect.SPEED);
            } else if (type.equals(PotionEffectType.SLOWNESS)) {
                aspects.add(GraftAspect.SLOW);
            } else if (type.equals(PotionEffectType.POISON)) {
                aspects.add(GraftAspect.POISON);
            } else if (type.equals(PotionEffectType.REGENERATION)
                || type.equals(PotionEffectType.INSTANT_HEALTH)
                || type.equals(PotionEffectType.HEALTH_BOOST)
                || type.equals(PotionEffectType.ABSORPTION)
                || type.equals(PotionEffectType.SATURATION)) {
                aspects.add(GraftAspect.HEAL);
            } else if (type.equals(PotionEffectType.INVISIBILITY)) {
                aspects.add(GraftAspect.CONCEAL);
            } else if (type.equals(PotionEffectType.GLOWING)) {
                aspects.add(GraftAspect.GLOW);
            } else if (type.equals(PotionEffectType.MINING_FATIGUE)) {
                aspects.add(GraftAspect.HEAVY);
            } else if (type.equals(PotionEffectType.JUMP_BOOST)) {
                aspects.add(GraftAspect.BOUNCE);
            }
        }
    }

    private void deriveEntityTraits(EnumSet<GraftAspect> aspects, DynamicPropertyProfile profile) {
        if (profile.exceeds(DynamicProperty.MASS, 2.5D)) {
            aspects.add(GraftAspect.HEAVY);
        }
        if (profile.get(DynamicProperty.MOTILITY) > 0.55D) {
            aspects.add(GraftAspect.SPEED);
        }
        if (profile.get(DynamicProperty.VITALITY) > 1.5D) {
            aspects.add(GraftAspect.HEAL);
        }
        if (profile.get(DynamicProperty.THERMAL) > 0.5D) {
            aspects.add(GraftAspect.HEAT);
        }
    }

    private boolean isHeatSource(Material material) {
        return switch (material) {
            case LAVA, LAVA_CAULDRON, MAGMA_BLOCK, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE -> true;
            default -> false;
        };
    }

    private boolean isAir(Material material) {
        return material == null
            || material == Material.AIR
            || material == Material.CAVE_AIR
            || material == Material.VOID_AIR;
    }

    private boolean isLightSource(Material material) {
        return switch (material) {
            case TORCH, SOUL_TORCH, LANTERN, SOUL_LANTERN, GLOWSTONE, SEA_LANTERN, SHROOMLIGHT, END_ROD, REDSTONE_LAMP, JACK_O_LANTERN,
                 BEACON, CAMPFIRE, SOUL_CAMPFIRE, FIRE, SOUL_FIRE, LAVA, LAVA_CAULDRON -> true;
            default -> false;
        };
    }

    private boolean isIce(Material material) {
        return switch (material) {
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> true;
            default -> false;
        };
    }

    private boolean isDoorLike(Material material) {
        String name = material.name();
        return name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE");
    }

    private boolean isPortalLike(Material material) {
        return switch (material) {
            case NETHER_PORTAL, END_PORTAL, END_GATEWAY, RESPAWN_ANCHOR -> true;
            default -> false;
        };
    }

    private boolean isPoweredBlock(Material material) {
        String name = material.name();
        return material == Material.DAYLIGHT_DETECTOR
            || name.endsWith("_BUTTON")
            || name.endsWith("_PRESSURE_PLATE")
            || name.endsWith("_RAIL")
            || name.contains("REDSTONE")
            || material == Material.LEVER
            || material == Material.OBSERVER
            || material == Material.REPEATER
            || material == Material.COMPARATOR;
    }

    private void deriveFromProperties(EnumSet<GraftAspect> aspects, DynamicPropertyProfile profile) {
        if (profile.exceeds(DynamicProperty.MASS, MASS_HEAVY_THRESHOLD)) {
            aspects.add(GraftAspect.HEAVY);
        }
        if (profile.get(DynamicProperty.THERMAL) > 0 && !aspects.contains(GraftAspect.HEAT)) {
            aspects.add(GraftAspect.HEAT);
        }
        if (profile.get(DynamicProperty.THERMAL) < 0 && !aspects.contains(GraftAspect.FREEZE)) {
            aspects.add(GraftAspect.FREEZE);
        }
        if (profile.get(DynamicProperty.LUMINANCE) > 0 && !aspects.contains(GraftAspect.LIGHT)) {
            aspects.add(GraftAspect.LIGHT);
        }
        if ((profile.get(DynamicProperty.VITALITY) >= 0.75 || profile.get(DynamicProperty.INTEGRITY) >= 1.0) && !aspects.contains(GraftAspect.HEAL)) {
            aspects.add(GraftAspect.HEAL);
        }
        if (profile.get(DynamicProperty.MOTILITY) > 0.45) {
            aspects.add(GraftAspect.SPEED);
        }
        if (profile.get(DynamicProperty.MOTILITY) > 0 && profile.get(DynamicProperty.MOTILITY) < 0.15) {
            aspects.add(GraftAspect.SLOW);
        }
    }
}
