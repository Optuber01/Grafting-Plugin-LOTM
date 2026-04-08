package com.graftingplugin.tests;

import com.graftingplugin.config.PluginSettings;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PluginSettingsTest {

    private PluginSettingsTest() {
    }

    public static void run() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("focus.material", "BLAZE_ROD");
        config.set("focus.name", "Mystic Focus");
        config.set("focus.interaction-range", 10);
        config.set("state-transfer.effect-duration-seconds", 15);
        config.set("state-transfer.field-duration-seconds", 8);
        config.set("state-transfer.field-radius", 5);
        config.set("state-transfer.pulse-interval-ticks", 30);
        config.set("state-transfer.ignite-fire-ticks", 140);
        config.set("state-transfer.heat-damage", 2.5D);
        config.set("state-transfer.heal-amount", 6.0D);
        config.set("state-transfer.bounce-count", 4);
        config.set("relation-graft.aggro-duration-seconds", 18);
        config.set("relation-graft.aggro-refresh-ticks", 8);
        config.set("relation-graft.projectile-duration-seconds", 11);
        config.set("relation-graft.projectile-refresh-ticks", 3);
        config.set("relation-graft.projectile-turn-strength", 0.45D);
        config.set("relation-graft.container-duration-seconds", 22);
        config.set("relation-graft.tether-duration-seconds", 9);
        config.set("relation-graft.tether-refresh-ticks", 4);
        config.set("relation-graft.tether-strength", 0.25D);
        config.set("relation-graft.tether-slack-distance", 3.5D);
        config.set("topology-graft.duration-seconds", 27);
        config.set("topology-graft.activation-radius", 1.75D);
        config.set("topology-graft.activation-cooldown-ticks", 14);
        config.set("sequence-tamper.payload-duration-seconds", 19);
        config.set("sequence-tamper.open-relay-duration-seconds", 24);
        config.set("sequence-tamper.relay-open-ticks", 17);

        PluginSettings settings = PluginSettings.fromConfig(config);

        if (settings.interactionRange() != 10) {
            throw new AssertionError("Interaction range was not loaded correctly");
        }
        if (settings.stateTransferSettings().effectDurationTicks() != 300) {
            throw new AssertionError("Effect duration seconds were not converted to ticks");
        }
        if (settings.stateTransferSettings().fieldDurationTicks() != 160) {
            throw new AssertionError("Field duration seconds were not converted to ticks");
        }
        if (settings.stateTransferSettings().fieldRadius() != 5 || settings.stateTransferSettings().bounceCount() != 4) {
            throw new AssertionError("State transfer settings were not loaded correctly");
        }
        if (settings.relationGraftSettings().aggroDurationTicks() != 360 || settings.relationGraftSettings().containerDurationTicks() != 440) {
            throw new AssertionError("Relation graft durations were not converted to ticks");
        }
        if (settings.relationGraftSettings().projectileRefreshTicks() != 3 || settings.relationGraftSettings().tetherRefreshTicks() != 4) {
            throw new AssertionError("Relation graft tick settings were not loaded correctly");
        }
        if (Math.abs(settings.relationGraftSettings().projectileTurnStrength() - 0.45D) > 0.0001D
            || Math.abs(settings.relationGraftSettings().tetherSlackDistance() - 3.5D) > 0.0001D) {
            throw new AssertionError("Relation graft scalar settings were not loaded correctly");
        }
        if (settings.topologyGraftSettings().durationTicks() != 540 || settings.topologyGraftSettings().activationCooldownTicks() != 14) {
            throw new AssertionError("Topology graft timing settings were not loaded correctly");
        }
        if (Math.abs(settings.topologyGraftSettings().activationRadius() - 1.75D) > 0.0001D) {
            throw new AssertionError("Topology graft activation radius was not loaded correctly");
        }
        if (settings.sequenceTamperSettings().payloadDurationTicks() != 380 || settings.sequenceTamperSettings().openRelayDurationTicks() != 480) {
            throw new AssertionError("Sequence tamper durations were not loaded correctly");
        }
        if (settings.sequenceTamperSettings().relayOpenTicks() != 17) {
            throw new AssertionError("Sequence tamper relay ticks were not loaded correctly");
        }
    }
}
