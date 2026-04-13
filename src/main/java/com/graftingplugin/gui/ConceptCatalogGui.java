package com.graftingplugin.gui;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.concept.ConceptDefinition;
import com.graftingplugin.conceptgraft.ConceptGraftDefinition;
import com.graftingplugin.conceptgraft.ConceptGraftType;
import com.graftingplugin.subject.GraftSubject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class ConceptCatalogGui implements Listener {

    private static final int MAX_SIZE = 54;
    private final GraftingPlugin plugin;
    private final Map<UUID, PendingConceptAction> pendingActions = new ConcurrentHashMap<>();

    public ConceptCatalogGui(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public void openConceptualGraftMenu(Player player) {
        Collection<ConceptGraftDefinition> grafts = plugin.conceptGraftCatalog().all();
        int size = Math.min(MAX_SIZE, roundUpToNine(grafts.size() + 1));
        if (size == 0) {
            size = 9;
        }

        Inventory inventory = Bukkit.createInventory(new ConceptualGraftHolder(), size,
            Component.text("\u2726 Conceptual Grafting \u2726", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));

        int slot = 0;
        for (ConceptGraftDefinition graft : grafts) {
            if (slot >= size - 1) {
                break;
            }
            inventory.setItem(slot, createGraftIcon(graft));
            slot++;
        }

        ItemStack practicalButton = new ItemStack(Material.BOOK);
        ItemMeta practicalMeta = practicalButton.getItemMeta();
        if (practicalMeta != null) {
            practicalMeta.displayName(Component.text("Practical Concepts \u00bb", NamedTextColor.GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Open the routine concept catalog", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("for everyday aspect-based grafts.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            practicalMeta.lore(lore);
            practicalMeta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING, "open_practical");
            practicalButton.setItemMeta(practicalMeta);
        }
        inventory.setItem(size - 1, practicalButton);

        player.openInventory(inventory);
    }

    public PendingConceptAction getPendingAction(Player player) {
        return pendingActions.get(player.getUniqueId());
    }

    public void clearPendingAction(Player player) {
        pendingActions.remove(player.getUniqueId());
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
        if (event.getInventory().getHolder() instanceof ConceptualGraftHolder) {
            handleConceptualGraftClick(event);
            return;
        }
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
            new NamespacedKey(plugin, "concept_key"),
            PersistentDataType.STRING,
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

    private void handleConceptualGraftClick(InventoryClickEvent event) {
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

        String guiAction = meta.getPersistentDataContainer().getOrDefault(
            new NamespacedKey(plugin, "gui_action"), PersistentDataType.STRING, "");
        if ("open_practical".equals(guiAction)) {
            player.closeInventory();
            open(player);
            return;
        }

        String graftKey = meta.getPersistentDataContainer().getOrDefault(
            new NamespacedKey(plugin, "conceptual_graft_key"), PersistentDataType.STRING, "");
        if (graftKey.isEmpty()) {
            return;
        }

        ConceptGraftType selectedType = null;
        for (ConceptGraftType type : ConceptGraftType.values()) {
            if (type.key().equals(graftKey)) {
                selectedType = type;
                break;
            }
        }

        if (selectedType == null) {
            return;
        }

        player.closeInventory();

        if (selectedType.requiresTwoAnchors()) {
            pendingActions.put(player.getUniqueId(), new PendingConceptAction(selectedType, player.getLocation().clone()));
            player.sendMessage("\u00a75\u00a7l\u2726 Beginning anchor set at your position.");
            player.sendMessage("\u00a75  Left-Click a block to set the End anchor and identify them as one place.");
            player.sendMessage("\u00a78  Shift+Left-Click air to cancel.");
        } else {
            pendingActions.put(player.getUniqueId(), new PendingConceptAction(selectedType, null));
            player.sendMessage("\u00a75\u00a7l\u2726 " + selectedType.displayName() + " law selected.");
            player.sendMessage("\u00a75  Left-Click a block or the ground to impose that law on the zone.");
            player.sendMessage("\u00a78  Shift+Left-Click air to cancel.");
        }
    }

    private ItemStack createGraftIcon(ConceptGraftDefinition graft) {
        ItemStack icon = new ItemStack(graft.iconMaterial());
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("\u2726 " + graft.displayName(), NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text(graft.description(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (graft.type().requiresTwoAnchors()) {
                lore.add(Component.text("Identifies two locations as one", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Imposes a law over a zone", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Click to begin", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "conceptual_graft_key"), PersistentDataType.STRING, graft.key());
            icon.setItemMeta(meta);
        }
        return icon;
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
                new NamespacedKey(plugin, "concept_key"),
                PersistentDataType.STRING,
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

    public record PendingConceptAction(ConceptGraftType type, Location firstAnchor) {}

    private static final class ConceptHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class ConceptualGraftHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
