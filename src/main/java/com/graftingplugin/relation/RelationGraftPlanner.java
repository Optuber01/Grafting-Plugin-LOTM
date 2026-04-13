package com.graftingplugin.relation;

import com.graftingplugin.aspect.DynamicPropertyProfile;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.aspect.PropertyModifier;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.subject.SubjectKind;

import java.util.Optional;

public final class RelationGraftPlanner {

    public Optional<RelationGraftPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target) {
        return plan(aspect, source, target, source.properties());
    }

    public Optional<RelationGraftPlan> plan(GraftAspect aspect, GraftSubject source, GraftSubject target, DynamicPropertyProfile sourceProfile) {
        PropertyModifier modifier = PropertyModifier.fromProfile(aspect, sourceProfile);
        return switch (aspect) {
            case AGGRO -> planAggro(source.kind(), target.kind(), aspect, modifier);
            case TARGET -> planRetarget(source.kind(), target.kind(), aspect, modifier);
            case RECEIVER -> planReceiver(source.kind(), target.kind(), aspect, modifier);
            case DESTINATION, CONTAINER_LINK -> planContainerRoute(source.kind(), target.kind(), aspect, modifier);
            case TETHER -> planTether(source.kind(), target.kind(), aspect, modifier);
            default -> Optional.empty();
        };
    }

    private Optional<RelationGraftPlan> planAggro(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if (sourceKind == SubjectKind.ENTITY && targetKind == SubjectKind.ENTITY) {
            return Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.MOB_AGGRO, "Redirect hostile attention to a new living target.", modifier));
        }
        return Optional.empty();
    }

    private Optional<RelationGraftPlan> planRetarget(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if (sourceKind != SubjectKind.PROJECTILE) {
            return Optional.empty();
        }
        return switch (targetKind) {
            case ENTITY -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.PROJECTILE_RETARGET_ENTITY, "Retarget a projectile toward another entity.", modifier));
            case LOCATION -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.PROJECTILE_RETARGET_LOCATION, "Retarget a projectile toward an anchor point.", modifier));
            default -> Optional.empty();
        };
    }

    private Optional<RelationGraftPlan> planReceiver(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if (sourceKind == SubjectKind.PROJECTILE) {
            return switch (targetKind) {
                case ENTITY -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.PROJECTILE_RETARGET_ENTITY, "Retarget a projectile toward another entity.", modifier));
                case LOCATION -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.PROJECTILE_RETARGET_LOCATION, "Retarget a projectile toward an anchor point.", modifier));
                default -> Optional.empty();
            };
        }
        if (sourceKind == SubjectKind.ITEM || sourceKind == SubjectKind.POTION) {
            return switch (targetKind) {
                case CONTAINER -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.INVENTORY_DEPOSIT, "Deposit the item into the target container.", modifier));
                case ENTITY -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.INVENTORY_HANDOFF, "Hand the selected item into a player's inventory.", modifier));
                case ITEM, POTION -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.SLOT_SWAP, "Swap the item into the selected target slot.", modifier));
                default -> Optional.empty();
            };
        }
        if (sourceKind == SubjectKind.CONTAINER && targetKind == SubjectKind.ENTITY) {
            return Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.CONTAINER_WITHDRAW, "Withdraw the first present stack into a player's inventory.", modifier));
        }
        return Optional.empty();
    }

    private Optional<RelationGraftPlan> planContainerRoute(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if (sourceKind == SubjectKind.CONTAINER && targetKind == SubjectKind.CONTAINER) {
            return Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.CONTAINER_ROUTE, "Reroute inserted items into a linked container.", modifier));
        }
        return Optional.empty();
    }

    private Optional<RelationGraftPlan> planTether(SubjectKind sourceKind, SubjectKind targetKind, GraftAspect aspect, PropertyModifier modifier) {
        if (sourceKind != SubjectKind.ENTITY && sourceKind != SubjectKind.PROJECTILE) {
            return Optional.empty();
        }
        return switch (targetKind) {
            case ENTITY -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.TETHER_ENTITY, "Bind the source toward another entity.", modifier));
            case LOCATION -> Optional.of(new RelationGraftPlan(aspect, sourceKind, targetKind, RelationGraftMode.TETHER_LOCATION, "Bind the source toward a fixed anchor.", modifier));
            default -> Optional.empty();
        };
    }
}
