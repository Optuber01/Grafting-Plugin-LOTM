package com.graftingplugin.gui;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.concept.ConceptDefinition;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class ConceptCatalogGui implements Listener {

    private static final int MAX_SIZE = 54;
    private final GraftingPlugin plugin;

    public ConceptCatalogGui(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Collection<ConceptDefinition> concepts = plugin.conceptRegistry().allConcepts();
        int size = Math.min(MAX_SIZE, roundUpToNine(concepts.size()));
        if (size == 0) {
            size = 9;
        }

        Inventory inventory = Bukkit.createInventory(new ConceptHolder(), size, Component.text("Concept Catalog", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

        int slot = 0;
        for (ConceptDefinition concept : concepts) {
            if (slot >= size) {
                break;
            }
            ItemStack icon = createConceptIcon(concept);
            inventory.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConceptHolder)) {
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

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String conceptKey = meta.getPersistentDataContainer().getOrDefault(
            new org.bukkit.NamespacedKey(plugin, "concept_key"),
            org.bukkit.persistence.PersistentDataType.STRING,
            ""
        );

        if (conceptKey.isEmpty()) {
            return;
        }

        GraftSubject conceptSubject = plugin.subjectResolver().resolveConcept(conceptKey).orElse(null);
        if (conceptSubject == null) {
            plugin.messages().send(player, "concept-not-found", Map.of("concept", conceptKey));
            player.closeInventory();
            return;
        }

        plugin.castSelectionService().armSource(player, conceptSubject);
        player.closeInventory();
    }

    private ItemStack createConceptIcon(ConceptDefinition concept) {
        ItemStack icon = new ItemStack(iconMaterial(concept));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(concept.displayName(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Aspects:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            concept.aspects().forEach(aspect ->
                lore.add(Component.text("  " + aspect.displayName(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            );
            List<String> propertyLore = new ArrayList<>();
            for (DynamicProperty property : DynamicProperty.values()) {
                double value = concept.properties().get(property);
                if (value == 0.0D) {
                    continue;
                }
                propertyLore.add(property.name().toLowerCase(Locale.ROOT) + " " + String.format(Locale.ROOT, "%.1f", value));
            }
            if (!propertyLore.isEmpty()) {
                lore.add(Component.empty());
                lore.add(Component.text("Properties:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                propertyLore.forEach(line -> lore.add(Component.text("  " + line, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
            }
            meta.lore(lore);

            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "concept_key"),
                org.bukkit.persistence.PersistentDataType.STRING,
                concept.key()
            );
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private Material iconMaterial(ConceptDefinition concept) {
        String name = concept.key().toLowerCase();
        if (name.contains("sun") || name.contains("light")) return Material.SUNFLOWER;
        if (name.contains("gravity") || name.contains("heavy")) return Material.ANVIL;
        if (name.contains("void") || name.contains("dark")) return Material.OBSIDIAN;
        if (name.contains("fire") || name.contains("heat")) return Material.BLAZE_POWDER;
        if (name.contains("water") || name.contains("ice")) return Material.ICE;
        if (name.contains("wind") || name.contains("air")) return Material.FEATHER;
        if (name.contains("time") || name.contains("begin")) return Material.CLOCK;
        if (name.contains("path") || name.contains("distance")) return Material.COMPASS;
        return Material.NETHER_STAR;
    }

    private int roundUpToNine(int value) {
        return ((value + 8) / 9) * 9;
    }

    private static final class ConceptHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
