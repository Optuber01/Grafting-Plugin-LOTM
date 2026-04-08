package com.graftingplugin.cast;

import com.graftingplugin.GraftingPlugin;
import com.graftingplugin.aspect.GraftAspect;
import com.graftingplugin.subject.GraftSubject;
import org.bukkit.entity.Player;

import java.util.List;
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
        List<GraftAspect> compatibleAspects = plugin.compatibilityValidator().compatibleSourceAspects(session.family(), source);
        if (compatibleAspects.isEmpty()) {
            plugin.messages().send(player, "source-incompatible", Map.of(
                "source", source.displayName(),
                "family", session.family().displayName()
            ));
            return false;
        }

        session.setSource(source, sourceReference);
        if (compatibleAspects.size() == 1) {
            GraftAspect selectedAspect = compatibleAspects.getFirst();
            session.setSelectedAspect(selectedAspect);
            plugin.messages().send(player, "source-armed-single", Map.of(
                "source", source.displayName(),
                "aspect", selectedAspect.displayName()
            ));
            return true;
        }

        plugin.messages().send(player, "source-armed-multiple", Map.of(
            "source", source.displayName(),
            "family", session.family().displayName(),
            "aspects", formatAspectList(compatibleAspects)
        ));
        return true;
    }

    public boolean selectAspect(Player player, GraftAspect aspect) {
        CastSession session = plugin.castSessionManager().session(player.getUniqueId());
        if (!plugin.compatibilityValidator().supportedFamilyAspects(session.family()).contains(aspect)) {
            plugin.messages().send(player, "invalid-aspect", "aspect", aspect.displayName());
            return false;
        }

        GraftSubject source = session.source();
        if (source == null) {
            plugin.messages().send(player, "no-source-selected");
            return false;
        }

        if (!plugin.compatibilityValidator().validateAspectSelection(session.family(), source, aspect).success()) {
            plugin.messages().send(player, "invalid-aspect", "aspect", aspect.displayName());
            return false;
        }

        session.setSelectedAspect(aspect);
        plugin.messages().send(player, "aspect-selected", "aspect", aspect.displayName());
        return true;
    }

    private String formatAspectList(List<GraftAspect> aspects) {
        return String.join(", ", aspects.stream().map(GraftAspect::key).toList());
    }
}
