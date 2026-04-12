package com.graftingplugin.gui;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.cast.CastSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class InventoryTargetPickerGui implements Listener {

    private static final int GUI_SIZE = 36;
    private final GraftingPlugin plugin;

    public InventoryTargetPickerGui(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(new TargetPickerHolder(), GUI_SIZE, Component.text("Select Target Slot", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(contents.length, GUI_SIZE); i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                gui.setItem(i, contents[i].clone());
            }
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TargetPickerHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getSlot();
        if (slot < 0 || slot >= GUI_SIZE) {
            return;
        }

        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        session.setSelectedTargetSlot(slot);
        plugin.messages().send(player, "target-slot-set", "slot", String.valueOf(slot + 1));
        player.closeInventory();
    }

    private static final class TargetPickerHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
