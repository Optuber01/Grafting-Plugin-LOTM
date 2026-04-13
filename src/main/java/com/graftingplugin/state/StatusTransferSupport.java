package com.graftingplugin.state;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.cast.CastSourceReference;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class StatusTransferSupport {

    private StatusTransferSupport() {
    }

    public static List<PotionEffectType> availableEffects(GraftingPlugin plugin, CastSourceReference sourceReference) {
        if (plugin == null || sourceReference == null || !sourceReference.hasEntity()) {
            return List.of();
        }
        Entity entity = plugin.getServer().getEntity(sourceReference.entityId());
        if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isValid()) {
            return List.of();
        }
        return livingEntity.getActivePotionEffects().stream()
            .map(PotionEffect::getType)
            .distinct()
            .sorted(Comparator.comparing(StatusTransferSupport::displayName))
            .toList();
    }

    public static String effectKey(PotionEffectType effectType) {
        return effectType == null ? "" : effectType.getKey().getKey();
    }

    public static String displayName(PotionEffectType effectType) {
        if (effectType == null) {
            return "Status";
        }
        String raw = effectType.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(Locale.ROOT);
    }

    public static PotionEffectType resolveEffect(GraftingPlugin plugin, CastSourceReference sourceReference, String selectedKey) {
        List<PotionEffectType> available = availableEffects(plugin, sourceReference);
        if (available.isEmpty()) {
            return null;
        }
        if (selectedKey == null || selectedKey.isBlank()) {
            return available.getFirst();
        }
        for (PotionEffectType effectType : available) {
            if (effectKey(effectType).equalsIgnoreCase(selectedKey)) {
                return effectType;
            }
        }
        return available.getFirst();
    }

    public static String selectedLabel(GraftingPlugin plugin, CastSourceReference sourceReference, String selectedKey) {
        PotionEffectType effectType = resolveEffect(plugin, sourceReference, selectedKey);
        return effectType == null ? "Status" : displayName(effectType);
    }

    public static String formatAvailableEffects(GraftingPlugin plugin, CastSourceReference sourceReference) {
        List<PotionEffectType> available = availableEffects(plugin, sourceReference);
        if (available.isEmpty()) {
            return "none";
        }
        return String.join(", ", available.stream().map(StatusTransferSupport::displayName).toList());
    }
}
