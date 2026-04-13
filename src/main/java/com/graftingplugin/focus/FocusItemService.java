package com.graftingplugin.focus;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.config.PluginSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class FocusItemService {

    private static final String MARKER = "mystic_focus";

    private final NamespacedKey focusKey;
    private final PluginSettings settings;

    public FocusItemService(GraftingPlugin plugin, PluginSettings settings) {
        this.focusKey = new NamespacedKey(plugin, "focus-item");
        this.settings = settings;
    }

    public ItemStack createFocusItem() {
        ItemStack itemStack = new ItemStack(settings.focusMaterial());
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.text(stripMiniMessage(settings.focusName())));
        meta.getPersistentDataContainer().set(focusKey, PersistentDataType.STRING, MARKER);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public boolean isFocus(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != settings.focusMaterial() || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        String marker = meta.getPersistentDataContainer().get(focusKey, PersistentDataType.STRING);
        return MARKER.equals(marker);
    }

    private String stripMiniMessage(String input) {
        return input.replaceAll("<[^>]+>", "");
    }
}
