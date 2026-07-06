package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.render.HitboxDebugRenderer;
import com.warfactory.medical.client.screen.CharacterSheetUI;
import com.warfactory.medical.client.screen.RadialMenuUI;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.compat.tacz.TaczPoseCaptureClient;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
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

    /**
     * Ticks the current {@link DeathScreen} has been open, for the stuck-respawn-button safety net.
     */
    private static int deathScreenTicks;

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
            CharacterSheetUI.open();
        }
        while (MedicalKeyMappings.OPEN_RADIAL.consumeClick()) {
            RadialMenuUI.open();
        }
        while (MedicalKeyMappings.TOGGLE_DEBUG.consumeClick()) {
            ClientMedicalCache.toggleDebug();
        }
        while (MedicalKeyMappings.TOGGLE_HITBOX.consumeClick()) {
            HitboxDebugRenderer.toggle();
        }
    }

    private static void drain(net.minecraft.client.KeyMapping key) {
        while (key.consumeClick()) {
            // discard
        }
    }

    /**
     * Safety net for the vanilla {@link DeathScreen} respawn/title buttons.
     *
     * <p>{@code DeathScreen} disables its buttons in {@code init()} and re-enables them only on the EXACT tick
     * its internal counter reaches 20. If that precise tick is ever missed — e.g. an {@code init()} re-run on a
     * window resize after the counter already passed 20, or a skipped screen tick — the buttons stay disabled
     * forever and the player can never click "Respawn". (Our old death handling, which cancelled the death event
     * and pinned the player alive, could also leave the client showing a death screen the server disagreed with;
     * the death redesign removes that desync, and this is belt-and-braces on top.)</p>
     *
     * <p>Once a death screen has been open a little while with EVERY button still disabled, re-enable them all.
     * We only act when nothing is active, so this never re-enables the respawn button that vanilla intentionally
     * disables right after a click (the title button stays active, so the "stuck" condition is not detected).
     * Uses only public API ({@link Screen#children()}, {@link AbstractWidget#active}).</p>
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
