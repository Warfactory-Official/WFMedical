package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.client.render.HitboxDebugRenderer;
import com.warfactory.medical.client.screen.MedInteractionScreen;
import com.warfactory.medical.client.screen.RadialMenuUI;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.compat.tacz.TaczPoseCaptureClient;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
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

    /**
     * Ticks the current {@link DeathScreen} has been open, for the stuck-respawn-button safety net.
     */
    private static int deathScreenTicks;

    private MedicalClientEvents() {
    }

    /**
     * Poll medical key bindings at end of tick; drain queued clicks so held keys don't repeat.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        keepRespawnButtonUsable(mc);
        if (mc.player == null || mc.screen != null) {
            // Still drain any queued clicks so they don't fire the moment a screen closes.
            drain(MedicalKeyMappings.OPEN_SHEET);
            drain(MedicalKeyMappings.OPEN_RADIAL);
            drain(MedicalKeyMappings.TOGGLE_DEBUG);
            drain(MedicalKeyMappings.TOGGLE_HITBOX);
            return;
        }
        while (MedicalKeyMappings.OPEN_SHEET.consumeClick()) {
            MedInteractionScreen.open();
        }
        while (MedicalKeyMappings.OPEN_RADIAL.consumeClick()) {
            RadialMenuUI.open();
        }
        while (MedicalKeyMappings.TOGGLE_DEBUG.consumeClick()) {
            ClientMedicalCache.toggleDebug();
        }
        while (MedicalKeyMappings.TOGGLE_HITBOX.consumeClick()) {
            HitboxDebugRenderer.toggle();
            mc.player.displayClientMessage(Component.literal("Hitbox overlay: "
                    + (HitboxDebugRenderer.enabled ? "on (" + HitboxDebugRenderer.style + ", scroll to change)" : "off")), true);
        }
    }

    /**
     * While the hitbox overlay is on, the scroll wheel cycles its draw style (edges &harr; filled) instead of
     * the hotbar. Off, or in a screen, scrolling behaves normally.
     */
    @SubscribeEvent
    public static void onMouseScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (!HitboxDebugRenderer.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        double delta = event.getScrollDelta();
        if (delta == 0.0) {
            return;
        }
        HitboxDebugRenderer.Style now = HitboxDebugRenderer.cycleStyle(delta > 0.0 ? 1 : -1);
        mc.player.displayClientMessage(Component.literal("Hitbox overlay: " + now), true);
        event.setCanceled(true);
    }

    private static void drain(net.minecraft.client.KeyMapping key) {
        while (key.consumeClick()) {
            // discard
        }
    }

    /**
     * Safety net for the vanilla DeathScreen respawn button. DeathScreen re-enables buttons only on the
     * EXACT tick its counter reaches 20 — missed on window resize or skipped screen tick, leaving buttons
     * disabled forever. Once the screen has been open >25 ticks with every button still disabled, force
     * them all active. Skips if any button is already active, so it never clobbers a just-clicked button.
     */
    private static void keepRespawnButtonUsable(Minecraft mc) {
        Screen screen = mc.screen;
        if (!(screen instanceof DeathScreen)) {
            deathScreenTicks = 0;
            return;
        }
        // Give vanilla its normal ~1s delay to enable the buttons; only intervene if it never did.
        if (++deathScreenTicks <= 25) {
            return;
        }
        boolean anyActive = false;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.active) {
                anyActive = true;
                break;
            }
        }
        if (anyActive) {
            return;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) {
                widget.active = true;
            }
        }
    }

    /**
     * Cancel the vanilla PLAYER_HEALTH overlay so our HealthBarOverlay replaces it; skipped in creative/spectator.
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
     * Hide the first-person hand(s) while both arms are drained/disabled — the player physically can't raise
     * them. Cancelling {@link RenderHandEvent} suppresses the held-item + arm render for that frame.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && MedicalState.isBothArmsDisabled(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * Drop all cached medical / UI state when the local player disconnects.
     */
    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMedicalCache.clear();
    }


    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (!TaczCompat.isLoaded()) {
            return;
        }
        event.getDispatcher().register(Commands.literal("wfmedtaczdump").executes(ctx -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                TaczPoseCaptureClient.dump(player, s -> player.displayClientMessage(Component.literal(s), false));
            }
            return 1;
        }));
    }
}
