package com.graftingplugin.cast;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.DynamicProperty;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.state.StatusTransferSupport;
import com.graftingplugin.subject.GraftSubject;
import com.graftingplugin.validation.GraftCompatibilityResult;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CastSelectionService {

    private final GraftingPlugin plugin;

    public CastSelectionService(GraftingPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean armSource(Player player, GraftSubject source) {
        return armSource(player, source, CastSourceReference.none());
    }

    public boolean armSource(Player player, GraftSubject source, CastSourceReference sourceReference) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        GraftFamily family = session.effectiveFamily();
        List<GraftAspect> compatibleAspects = plugin.compatibilityValidator().compatibleSourceAspects(family, source);
        if (compatibleAspects.isEmpty()) {
            plugin.messages().send(player, "source-incompatible", Map.of(
                "source", source.displayName(),
                "family", family.displayName()
            ));
            return false;
        }

        session.setSource(source, sourceReference);
        GraftAspect selectedAspect = compatibleAspects.getFirst();
        session.setSelectedAspect(selectedAspect);
        initializeStatusSelection(session);
        plugin.messages().send(player, "source-armed-single", Map.of(
            "source", source.displayName(),
            "aspect", selectedAspectLabel(session)
        ));
        if (compatibleAspects.size() > 1) {
            player.sendMessage("§7Available " + family.displayName() + " aspects: §b" + formatAspectList(compatibleAspects) + " §8(Shift+Right-Click or /graft next)");
        }
        String properties = formatProperties(source);
        if (!properties.isEmpty()) {
            player.sendMessage("§8Properties: §7" + properties);
        }
        return true;
    }

    public boolean selectAspect(Player player, GraftAspect aspect) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        GraftFamily family = session.family();
        if (!plugin.compatibilityValidator().supportedFamilyAspects(family).contains(aspect)) {
            if (aspect.family() != family) {
                player.sendMessage("§c" + aspect.displayName() + " belongs to " + aspect.family().displayName() + ", not " + family.displayName() + '.');
            } else {
                player.sendMessage("§c" + aspect.displayName() + " is not supported in " + family.displayName() + '.');
            }
            return false;
        }

        GraftSubject source = session.source();
        if (source == null) {
            plugin.messages().send(player, "no-source-selected");
            return false;
        }

        GraftCompatibilityResult compatibility = plugin.compatibilityValidator().validateAspectSelection(family, source, aspect);
        if (!compatibility.success()) {
            player.sendMessage("§c" + compatibility.message());
            return false;
        }

        session.setSelectedAspect(aspect);
        initializeStatusSelection(session);
        plugin.messages().send(player, "aspect-selected", "aspect", selectedAspectLabel(session));
        return true;
    }

    private void initializeStatusSelection(CastSession session) {
        if (session.selectedAspect() != GraftAspect.STATUS) {
            return;
        }
        session.setSelectedStatusEffectKey(StatusTransferSupport.effectKey(
            StatusTransferSupport.resolveEffect(plugin, session.sourceReference(), session.selectedStatusEffectKey())
        ));
    }

    private String selectedAspectLabel(CastSession session) {
        if (session.selectedAspect() != GraftAspect.STATUS) {
            return session.selectedAspect().displayName();
        }
        return StatusTransferSupport.selectedLabel(plugin, session.sourceReference(), session.selectedStatusEffectKey());
    }

    private String formatAspectList(List<GraftAspect> aspects) {
        return String.join(", ", aspects.stream().map(GraftAspect::key).toList());
    }

    private String formatProperties(GraftSubject source) {
        List<String> pieces = new ArrayList<>();
        for (DynamicProperty property : DynamicProperty.values()) {
            double value = source.properties().get(property);
            if (value <= 0.0D && property != DynamicProperty.THERMAL) {
                continue;
            }
            if (property == DynamicProperty.THERMAL && value == 0.0D) {
                continue;
            }
            pieces.add(switch (property) {
                case MASS -> "mass " + formatValue(value);
                case THERMAL -> "thermal " + formatValue(value);
                case LUMINANCE -> "luminance " + formatValue(value);
                case VOLATILITY -> "volatility " + formatValue(value);
                case MOTILITY -> "motility " + formatValue(value);
                case VITALITY -> "vitality " + formatValue(value);
                case INTEGRITY -> "integrity " + formatValue(value);
                case TOXICITY -> "toxicity " + formatValue(value);
                case OBSCURITY -> "obscurity " + formatValue(value);
            });
        }
        return String.join(", ", pieces);
    }

    private String formatValue(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
