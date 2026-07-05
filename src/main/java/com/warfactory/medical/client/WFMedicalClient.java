package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.overlay.ActionProgressOverlay;
import com.warfactory.medical.client.overlay.HealthBarOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client MOD-bus bootstrap for Warfactory Medical. Registers the key bindings and the HUD overlays. This
 * class is only subscribed on {@link Dist#CLIENT}, so a dedicated server never class-loads any of the
 * client-only types it references.
 *
 * <p>The overlay {@code INSTANCE} fields are provided by a later overlay agent
 * ({@link HealthBarOverlay#INSTANCE}, {@link ActionProgressOverlay#INSTANCE}); they are forward references
 * here and are resolved when the whole project compiles.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WFMedicalClient {

    /** Guards against the mod-bus overlay event reaching this subscriber more than once per launch. */
    private static boolean overlaysRegistered;

    private WFMedicalClient() {
    }

    /** Register the three rebindable medical key mappings. */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MedicalKeyMappings.register(event);
    }

    /**
     * Register the HUD overlays. The custom health bar renders just above the vanilla player-health slot
     * (the vanilla hearts are hidden by {@link MedicalClientEvents}); the action-progress overlay renders
     * on top of everything.
     */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        if (overlaysRegistered) {
            return;
        }
        overlaysRegistered = true;
        event.registerAbove(
                VanillaGuiOverlay.PLAYER_HEALTH.id(),
                "wfmedical_health",
                HealthBarOverlay.INSTANCE);
        event.registerAboveAll(
                "wfmedical_action_progress",
                ActionProgressOverlay.INSTANCE);
    }
}
