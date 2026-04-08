package com.graftingplugin.cast;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.UUID;

public record CastSourceReference(UUID entityId, Location blockLocation) {

    public CastSourceReference {
        blockLocation = blockLocation == null ? null : blockLocation.toBlockLocation();
    }

    public static CastSourceReference none() {
        return new CastSourceReference(null, null);
    }

    public static CastSourceReference ofEntity(Entity entity) {
        return new CastSourceReference(entity.getUniqueId(), null);
    }

    public static CastSourceReference ofBlock(Block block) {
        return new CastSourceReference(null, block.getLocation());
    }

    @Override
    public Location blockLocation() {
        return blockLocation == null ? null : blockLocation.clone();
    }

    public boolean hasEntity() {
        return entityId != null;
    }

    public boolean hasBlockLocation() {
        return blockLocation != null;
    }
}
