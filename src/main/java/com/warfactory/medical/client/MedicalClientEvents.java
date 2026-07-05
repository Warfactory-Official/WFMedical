package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.screen.CharacterSheetUI;
import com.warfactory.medical.client.screen.RadialMenuUI;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client FORGE-bus handlers for Warfactory Medical: key polling, hiding the vanilla hearts, and clearing
 * the client cache on disconnect. CLIENT-ONLY (subscribed on {@link Dist#CLIENT}); never loaded on a
 * dedicated server.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MedicalClientEvents {

    private MedicalClientEvents() {
    }

    /**
     * Poll the medical key bindings at the end of each client tick. A binding only fires when no screen is
     * open and the local player exists; {@link net.minecraft.client.KeyMapping#consumeClick()} drains the
     * queued presses so a held key does not repeat.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            // Still drain any queued clicks so they don't fire the moment a screen closes.
            drain(MedicalKeyMappings.OPEN_SHEET);
            drain(MedicalKeyMappings.OPEN_RADIAL);
            drain(MedicalKeyMappings.TOGGLE_DEBUG);
            return;
        }
        while (MedicalKeyMappings.OPEN_SHEET.consumeClick()) {
            CharacterSheetUI.open();
        }
        while (MedicalKeyMappings.OPEN_RADIAL.consumeClick()) {
            RadialMenuUI.open();
        }
        while (MedicalKeyMappings.TOGGLE_DEBUG.consumeClick()) {
            ClientMedicalCache.toggleDebug();
        }
    }

    private static void drain(net.minecraft.client.KeyMapping key) {
        while (key.consumeClick()) {
            // discard
        }
    }

    /**
     * Hide the vanilla hearts so our own health bar (drawn by {@code HealthBarOverlay}) replaces them.
     * Only the {@code PLAYER_HEALTH} overlay is cancelled — absorption, armor, food, etc. are untouched.
     * In creative / spectator vanilla already hides the hearts, so we skip cancelling there.
     */
    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Drop all cached medical / UI state when the local player disconnects.
     */
    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMedicalCache.clear();
    }
}
