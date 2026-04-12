package com.graftingplugin.tests;

import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.relation.RelationGraftMode;
import com.graftingplugin.relation.RelationGraftPlan;
import com.graftingplugin.relation.RelationGraftPlanner;
import com.graftingplugin.state.StateTransferMode;
import com.graftingplugin.state.StateTransferPlanner;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Set;

public final class PracticalGraftWorkflowTest {

    private PracticalGraftWorkflowTest() {
    }

    public static void run() {
        RelationGraftPlanner relPlanner = new RelationGraftPlanner();
        StateTransferPlanner statePlanner = new StateTransferPlanner();

        GraftSubject inventoryItem = new GraftSubject("item:diamond_sword", "Diamond Sword", SubjectKind.ITEM, Set.of(GraftAspect.RECEIVER));
        GraftSubject potionItem = new GraftSubject("item:potion", "Potion", SubjectKind.POTION, Set.of(GraftAspect.RECEIVER));
        GraftSubject targetItem = new GraftSubject("item:iron_pickaxe", "Iron Pickaxe", SubjectKind.ITEM, Set.of(GraftAspect.RECEIVER));
        GraftSubject chest = new GraftSubject("container:chest", "Chest", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));
        GraftSubject barrel = new GraftSubject("container:barrel", "Barrel", SubjectKind.CONTAINER, Set.of(GraftAspect.DESTINATION, GraftAspect.CONTAINER_LINK));

        RelationGraftPlan invToChest = relPlanner.plan(GraftAspect.RECEIVER, inventoryItem, chest).orElseThrow(
            () -> new AssertionError("RECEIVER on ITEM->CONTAINER should plan INVENTORY_DEPOSIT")
        );
        if (invToChest.mode() != RelationGraftMode.INVENTORY_DEPOSIT) {
            throw new AssertionError("Expected INVENTORY_DEPOSIT, got " + invToChest.mode());
        }

        RelationGraftPlan potionToChest = relPlanner.plan(GraftAspect.RECEIVER, potionItem, chest).orElseThrow(
            () -> new AssertionError("RECEIVER on POTION->CONTAINER should plan INVENTORY_DEPOSIT")
        );
        if (potionToChest.mode() != RelationGraftMode.INVENTORY_DEPOSIT) {
            throw new AssertionError("Expected INVENTORY_DEPOSIT for potion, got " + potionToChest.mode());
        }

        RelationGraftPlan slotSwap = relPlanner.plan(GraftAspect.RECEIVER, inventoryItem, targetItem).orElseThrow(
            () -> new AssertionError("RECEIVER on ITEM->ITEM should plan SLOT_SWAP")
        );
        if (slotSwap.mode() != RelationGraftMode.SLOT_SWAP) {
            throw new AssertionError("Expected SLOT_SWAP, got " + slotSwap.mode());
        }

        RelationGraftPlan chestToChest = relPlanner.plan(GraftAspect.DESTINATION, chest, barrel).orElseThrow(
            () -> new AssertionError("DESTINATION on CONTAINER->CONTAINER should plan CONTAINER_ROUTE")
        );
        if (chestToChest.mode() != RelationGraftMode.CONTAINER_ROUTE) {
            throw new AssertionError("Expected CONTAINER_ROUTE, got " + chestToChest.mode());
        }

        RelationGraftPlan containerLink = relPlanner.plan(GraftAspect.CONTAINER_LINK, chest, barrel).orElseThrow(
            () -> new AssertionError("CONTAINER_LINK on CONTAINER->CONTAINER should plan CONTAINER_ROUTE")
        );
        if (containerLink.mode() != RelationGraftMode.CONTAINER_ROUTE) {
            throw new AssertionError("Expected CONTAINER_ROUTE for CONTAINER_LINK, got " + containerLink.mode());
        }

        if (relPlanner.plan(GraftAspect.RECEIVER, inventoryItem, new GraftSubject("entity:zombie", "Zombie", SubjectKind.ENTITY, Set.of())).isPresent()) {
            throw new AssertionError("RECEIVER on ITEM->ENTITY should not plan");
        }

        GraftSubject damageable = new GraftSubject("item:iron_sword", "Iron Sword", SubjectKind.ITEM, Set.of(GraftAspect.RECEIVER));
        StateTransferMode healMode = statePlanner.plan(GraftAspect.HEAL, damageable).orElseThrow().mode();
        if (healMode != StateTransferMode.ITEM_REPAIR) {
            throw new AssertionError("HEAL on ITEM should plan ITEM_REPAIR, got " + healMode);
        }
    }
}
