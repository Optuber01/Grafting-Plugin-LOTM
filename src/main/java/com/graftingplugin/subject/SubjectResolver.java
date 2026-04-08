package com.graftingplugin.subject;

import com.graftingplugin.aspect.AspectCatalog;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.concept.ConceptRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class SubjectResolver {

    private final AspectCatalog aspectCatalog;
    private final ConceptRegistry conceptRegistry;

    public SubjectResolver(AspectCatalog aspectCatalog, ConceptRegistry conceptRegistry) {
        this.aspectCatalog = aspectCatalog;
        this.conceptRegistry = conceptRegistry;
    }

    public Optional<GraftSubject> resolveConcept(String input) {
        return conceptRegistry.resolve(input);
    }

    public Optional<GraftSubject> resolveBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        return resolveBlock(block.getType());
    }

    public Optional<GraftSubject> resolveBlock(Material material) {
        Set<GraftAspect> aspects = aspectCatalog.blockAspects(material);
        if (aspects.isEmpty()) {
            return Optional.empty();
        }
        SubjectKind kind = aspectCatalog.isContainer(material) ? SubjectKind.CONTAINER : SubjectKind.BLOCK;
        return Optional.of(new GraftSubject(
            kind.name().toLowerCase(Locale.ROOT) + ':' + material.name().toLowerCase(Locale.ROOT),
            humanize(material.name()),
            kind,
            aspects
        ));
    }

    public Optional<GraftSubject> resolveEntity(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Set<GraftAspect> aspects = aspectCatalog.entityAspects(entity);
        if (aspects.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GraftSubject(
            "entity:" + entity.getType().name().toLowerCase(Locale.ROOT),
            humanize(entity.getType().name()),
            SubjectKind.ENTITY,
            aspects
        ));
    }

    public Optional<GraftSubject> resolveItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || itemStack.getType() == Material.CAVE_AIR || itemStack.getType() == Material.VOID_AIR) {
            return Optional.empty();
        }
        Set<GraftAspect> aspects = aspectCatalog.itemAspects(itemStack);
        if (aspects.isEmpty()) {
            return Optional.empty();
        }
        SubjectKind kind = switch (itemStack.getType()) {
            case POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> SubjectKind.POTION;
            default -> SubjectKind.ITEM;
        };
        return Optional.of(new GraftSubject(
            kind.name().toLowerCase(Locale.ROOT) + ':' + itemStack.getType().name().toLowerCase(Locale.ROOT),
            humanize(itemStack.getType().name()),
            kind,
            aspects
        ));
    }

    public Optional<GraftSubject> resolveProjectile(Projectile projectile) {
        if (projectile == null) {
            return Optional.empty();
        }
        Set<GraftAspect> aspects = aspectCatalog.projectileAspects(projectile);
        if (aspects.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GraftSubject(
            "projectile:" + projectile.getType().name().toLowerCase(Locale.ROOT),
            humanize(projectile.getType().name()),
            SubjectKind.PROJECTILE,
            aspects
        ));
    }

    public Optional<GraftSubject> resolveLocation(Location location) {
        Set<GraftAspect> aspects = aspectCatalog.locationAspects(location);
        if (aspects.isEmpty()) {
            return Optional.empty();
        }
        String world = location.getWorld() == null ? "world" : location.getWorld().getName().toLowerCase(Locale.ROOT);
        String displayName = world + " @ " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
        return Optional.of(new GraftSubject(
            "location:" + world + ':' + location.getBlockX() + ':' + location.getBlockY() + ':' + location.getBlockZ(),
            displayName,
            SubjectKind.LOCATION,
            aspects
        ));
    }

    public Optional<GraftSubject> resolveArea(Location center, int radius) {
        Set<GraftAspect> aspects = aspectCatalog.areaAspects(center, radius);
        if (aspects.isEmpty()) {
            return Optional.empty();
        }
        String world = center.getWorld() == null ? "world" : center.getWorld().getName().toLowerCase(Locale.ROOT);
        String displayName = "Area " + radius + " around " + world + " @ " + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ();
        return Optional.of(new GraftSubject(
            "area:" + world + ':' + center.getBlockX() + ':' + center.getBlockY() + ':' + center.getBlockZ() + ':' + radius,
            displayName,
            SubjectKind.AREA,
            aspects
        ));
    }

    private String humanize(String raw) {
        String[] pieces = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
        }
        return builder.toString();
    }
}
