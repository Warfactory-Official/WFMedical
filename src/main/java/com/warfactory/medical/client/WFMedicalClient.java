package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.overlay.ActionProgressOverlay;
import com.warfactory.medical.client.overlay.DamageOutlineOverlay;
import com.warfactory.medical.client.overlay.HealthBarOverlay;
import com.warfactory.medical.client.render.TourniquetLayer;
import com.warfactory.medical.compat.playeranim.PlayerAnimHitbox;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client MOD-bus bootstrap: registers key bindings and HUD overlays. CLIENT-ONLY (Dist.CLIENT).
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WFMedicalClient {

    /**
     * Guards against the mod-bus overlay event firing more than once per launch.
     */
    private static boolean overlaysRegistered;

    private WFMedicalClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MedicalKeyMappings.register(event);
    }

    /**
     * Wire PlayerAnimator into the limb hitbox rig so hitboxes track played animations. Guarded by the
     * mod-presence check so {@link PlayerAnimHitbox} (which names PlayerAnimator types) is only class-loaded
     * when the {@code playeranimator} mod is installed; absent, hitboxes keep the vanilla pose.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("playeranimator")) {
            event.enqueueWork(PlayerAnimHitbox::register);
        }
    }

    /**
     * Add the worn-tourniquet render layer to every player-skin renderer so applied tourniquets show on the
     * third-person player model (arm/leg). The first-person arm is handled separately by
     * {@code TourniquetArmRenderer}.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new TourniquetLayer(renderer));
            }
        }
    }

    /**
     * Health bar above vanilla PLAYER_HEALTH slot (hearts hidden by MedicalClientEvents); action-progress above all.
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
                "wfmedical_damage_outline",
                DamageOutlineOverlay.INSTANCE);
        event.registerAboveAll(
                "wfmedical_action_progress",
                ActionProgressOverlay.INSTANCE);
    }
}
