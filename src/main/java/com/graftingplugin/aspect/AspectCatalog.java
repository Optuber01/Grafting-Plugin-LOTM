package com.graftingplugin.aspect;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumSet;
import java.util.Locale;
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
            aspects.add(GraftAspect.SLIPPERY);
        }
        if (material == Material.TNT) {
            aspects.add(GraftAspect.EXPLOSIVE);
        }
        if (isDoorLike(material)) {
            aspects.add(GraftAspect.OPEN);
            aspects.add(GraftAspect.ENTRY);
            aspects.add(GraftAspect.EXIT);
        }
        if (isPortalLike(material)) {
            aspects.add(GraftAspect.ENTRY);
            aspects.add(GraftAspect.EXIT);
            aspects.add(GraftAspect.ANCHOR);
        }
        if (isContainer(material)) {
            aspects.add(GraftAspect.OPEN);
            aspects.add(GraftAspect.ON_OPEN);
            aspects.add(GraftAspect.CONTAINER_LINK);
            aspects.add(GraftAspect.DESTINATION);
        }
        if (isPoweredBlock(material)) {
            aspects.add(GraftAspect.POWERED);
        }

        // All blocks can act as an anchor point
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
            addEffectAspect(aspects, livingEntity, PotionEffectType.SPEED, GraftAspect.SPEED);
            addEffectAspect(aspects, livingEntity, PotionEffectType.SLOWNESS, GraftAspect.SLOW);
            addEffectAspect(aspects, livingEntity, PotionEffectType.POISON, GraftAspect.POISON);
            if (livingEntity.hasPotionEffect(PotionEffectType.REGENERATION)
                || livingEntity.hasPotionEffect(PotionEffectType.HEALTH_BOOST)
                || livingEntity.hasPotionEffect(PotionEffectType.ABSORPTION)) {
                aspects.add(GraftAspect.HEAL);
            }
        }
        aspects.add(GraftAspect.TETHER);
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            aspects.add(GraftAspect.AGGRO);
            aspects.add(GraftAspect.TARGET);
        }
        if (entity instanceof Tameable tameable && tameable.getOwner() != null) {
            aspects.add(GraftAspect.OWNER);
        }

        deriveFromProperties(aspects, evaluator.evaluateEntity(entity));
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
        if (material == Material.TNT) {
            aspects.add(GraftAspect.EXPLOSIVE);
        }
        return Set.copyOf(aspects);
    }

    public Set<GraftAspect> projectileAspects(Projectile projectile) {
        if (projectile == null) {
            return Set.of();
        }

        EnumSet<GraftAspect> aspects = EnumSet.of(GraftAspect.ON_HIT, GraftAspect.RECEIVER, GraftAspect.TARGET);
        aspects.add(GraftAspect.TETHER);
        if (projectile.getShooter() != null) {
            aspects.add(GraftAspect.OWNER);
        }
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
        return Set.of(GraftAspect.ANCHOR, GraftAspect.VOLUME);
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

    private void addEffectAspect(EnumSet<GraftAspect> aspects, LivingEntity livingEntity, PotionEffectType type, GraftAspect aspect) {
        if (livingEntity.hasPotionEffect(type)) {
            aspects.add(aspect);
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
        if (profile.get(DynamicProperty.VOLATILITY) > 0 && !aspects.contains(GraftAspect.EXPLOSIVE)) {
            aspects.add(GraftAspect.EXPLOSIVE);
        }
        if (profile.get(DynamicProperty.MOTILITY) > 0.25) {
            aspects.add(GraftAspect.SPEED);
        }
        if (profile.get(DynamicProperty.MOTILITY) > 0 && profile.get(DynamicProperty.MOTILITY) < 0.15) {
            aspects.add(GraftAspect.SLOW);
        }
    }
}
