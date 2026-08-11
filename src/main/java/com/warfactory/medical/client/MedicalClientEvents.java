package com.warfactory.medical.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.client.render.HitboxDebugRenderer;
import com.warfactory.medical.client.screen.MedInteractionScreen;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.compat.tacz.TaczPoseCaptureClient;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.network.TargetSheetRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
public final class MedicalClientEvents {

    private static int deathScreenTicks;

    private static final int TARGET_SHEET_POLL_TICKS = 10;
    private static int targetSheetPoll;

    private MedicalClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        keepRespawnButtonUsable(mc);
        pollTargetSheet(mc);
        GiveUpHandler.tick(mc);
        if (mc.player == null || mc.screen != null) {
            drain(MedicalKeyMappings.OPEN_SHEET);
            drain(MedicalKeyMappings.OPEN_RADIAL);
            drain(MedicalKeyMappings.TOGGLE_DEBUG);
            drain(MedicalKeyMappings.TOGGLE_HITBOX);
            if (MedicalDebug.ENABLED) {
                drain(MedicalKeyMappings.TOGGLE_SCREEN_FX);
                drain(MedicalKeyMappings.TOGGLE_FX_LOG);
            }
            return;
        }
        while (MedicalKeyMappings.OPEN_SHEET.consumeClick()) {
            int targetId = TreatmentInteractions.pickTargetEntityId();
            if (targetId >= 0) {
                MedInteractionScreen.markPendingOpen(targetId);
                MedicalNetworking.sendToServer(new TargetSheetRequestPacket(targetId));
            } else {
                MedInteractionScreen.open();
            }
        }
        while (MedicalKeyMappings.TOGGLE_DEBUG.consumeClick()) {
            ClientMedicalCache.toggleDebug();
        }
        while (MedicalKeyMappings.TOGGLE_HITBOX.consumeClick()) {
            HitboxDebugRenderer.toggle();
            mc.player.displayClientMessage(Component.literal("Hitbox overlay: "
                    + (HitboxDebugRenderer.enabled ? "on (" + HitboxDebugRenderer.style + ", scroll to change)" : "off")), true);
        }
        if (MedicalDebug.ENABLED) {
            while (MedicalKeyMappings.TOGGLE_SCREEN_FX.consumeClick()) {
                boolean on = MedicalDebug.toggleScreenEffects();
                mc.player.displayClientMessage(Component.literal("WFMedical screen effects: "
                        + (on ? "ON" : "OFF (desaturation/blur/vignettes/outline)")), true);
            }
            while (MedicalKeyMappings.TOGGLE_FX_LOG.consumeClick()) {
                boolean on = MedicalDebug.toggleVerbose();
                mc.player.displayClientMessage(Component.literal("WFMedical effect logging: "
                        + (on ? "ON (see log)" : "OFF")), true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (!HitboxDebugRenderer.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        double delta = event.getScrollDeltaY();
        if (delta == 0.0) {
            return;
        }
        HitboxDebugRenderer.Style now = HitboxDebugRenderer.cycleStyle(delta > 0.0 ? 1 : -1);
        mc.player.displayClientMessage(Component.literal("Hitbox overlay: " + now), true);
        event.setCanceled(true);
    }

    private static void drain(net.minecraft.client.KeyMapping key) {
        while (key.consumeClick()) {
        }
    }

    private static void pollTargetSheet(Minecraft mc) {
        int tid = MedInteractionScreen.targetId();
        if (tid < 0) {
            targetSheetPoll = 0;
            return;
        }
        if (mc.player == null || !(mc.screen instanceof ModularUIScreen)) {
            MedInteractionScreen.clearTarget();
            targetSheetPoll = 0;
            return;
        }
        if (targetOutOfReach(mc, tid)) {
            mc.setScreen(null);
            MedInteractionScreen.clearTarget();
            targetSheetPoll = 0;
            return;
        }
        if (++targetSheetPoll >= TARGET_SHEET_POLL_TICKS) {
            targetSheetPoll = 0;
            MedicalNetworking.sendToServer(new TargetSheetRequestPacket(tid));
        }
    }

    private static boolean targetOutOfReach(Minecraft mc, int targetId) {
        if (mc.level == null || mc.player == null) {
            return true;
        }
        Entity e = mc.level.getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            return true;
        }
        return mc.player.distanceToSqr(le) > MedicalConfig.treatReachSqr();
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
        MedInteractionScreen.clearTarget();
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
