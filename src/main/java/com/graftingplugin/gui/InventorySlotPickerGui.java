package com.graftingplugin.gui;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.cast.CastSourceReference;
import com.graftingplugin.subject.GraftSubject;
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

public final class InventorySlotPickerGui implements Listener {

    private static final int GUI_SIZE = 36;
    private final GraftingPlugin plugin;

    public InventorySlotPickerGui(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(new SlotPickerHolder(), GUI_SIZE, Component.text("Select Inventory Source", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
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
        if (!(event.getInventory().getHolder() instanceof SlotPickerHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        GraftSubject subject = plugin.subjectResolver().resolveItem(clicked).orElse(null);
        if (subject == null) {
            plugin.messages().send(player, "no-source-found");
            player.closeInventory();
            return;
        }

        plugin.castSelectionService().armSource(player, subject, CastSourceReference.none());
        player.closeInventory();
    }

    private static final class SlotPickerHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
