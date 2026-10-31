package com.warfactory.medical.body.client;

import com.warfactory.medical.body.api.MedicalState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

public final class BodyClientEvents {

    private static int deathScreenTicks;

    private BodyClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        keepRespawnButtonUsable(mc);
        GiveUpHandler.tick(mc);
        if (mc.player == null || mc.screen != null) {
            drain(BodyKeys.TOGGLE_DEBUG);
            if (MedicalDebug.ENABLED) {
                drain(BodyKeys.TOGGLE_SCREEN_FX);
                drain(BodyKeys.TOGGLE_FX_LOG);
            }
            return;
        }
        while (BodyKeys.TOGGLE_DEBUG.consumeClick()) {
            ClientMedicalCache.toggleDebug();
        }
        if (MedicalDebug.ENABLED) {
            while (BodyKeys.TOGGLE_SCREEN_FX.consumeClick()) {
                boolean on = MedicalDebug.toggleScreenEffects();
                mc.player.displayClientMessage(Component.literal("WFMedical screen effects: "
                        + (on ? "ON" : "OFF (desaturation/blur/vignettes/outline)")), true);
            }
            while (BodyKeys.TOGGLE_FX_LOG.consumeClick()) {
                boolean on = MedicalDebug.toggleVerbose();
                mc.player.displayClientMessage(Component.literal("WFMedical effect logging: "
                        + (on ? "ON (see log)" : "OFF")), true);
            }
        }
    }

    public static void drain(net.minecraft.client.KeyMapping key) {
        while (key.consumeClick()) {
        }
    }

    private static void keepRespawnButtonUsable(Minecraft mc) {
        Screen screen = mc.screen;
        if (!(screen instanceof DeathScreen)) {
            deathScreenTicks = 0;
            return;
        }
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

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && MedicalState.isBothArmsDisabled(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMedicalCache.clear();
        ClientDownedTracker.clear();
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            ClientDownedTracker.forget(event.getEntity().getId());
        }
    }

    @SubscribeEvent
    public static void onRespawnClone(ClientPlayerNetworkEvent.Clone event) {
        LocalPlayer player = event.getNewPlayer();
        if (player != null) {
            ClientDownedTracker.set(player.getId(), false);
        }
        UnconsciousOverlay.reset();
    }
}
