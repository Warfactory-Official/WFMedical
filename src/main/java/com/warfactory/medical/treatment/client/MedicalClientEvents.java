package com.warfactory.medical.treatment.client;

import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.network.BodyNetwork;
import com.warfactory.medical.treatment.network.TargetSheetRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import com.warfactory.medical.body.client.BodyClientEvents;
import com.warfactory.medical.treatment.MedicalPoses;

public final class MedicalClientEvents {

    private static final int TARGET_SHEET_POLL_TICKS = 10;
    private static int targetSheetPoll;

    private MedicalClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ActionProgressOverlay.INSTANCE.tick();
        pollTargetSheet(mc);
        if (mc.player == null || mc.screen != null) {
            BodyClientEvents.drain(MedicalKeyMappings.OPEN_SHEET);
            BodyClientEvents.drain(MedicalKeyMappings.OPEN_RADIAL);
            return;
        }
        while (MedicalKeyMappings.OPEN_SHEET.consumeClick()) {
            int targetId = TreatmentInteractions.pickTargetEntityId();
            if (targetId >= 0) {
                MedInteractionScreen.markPendingOpen(targetId);
                BodyNetwork.sendToServer(new TargetSheetRequestPacket(targetId));
            } else {
                MedInteractionScreen.open();
            }
        }
    }

    private static void pollTargetSheet(Minecraft mc) {
        int tid = MedInteractionScreen.targetId();
        if (tid < 0) {
            targetSheetPoll = 0;
            return;
        }
        if (mc.player == null || !MedInteractionScreen.isOpen(mc)) {
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
            BodyNetwork.sendToServer(new TargetSheetRequestPacket(tid));
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

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MedInteractionScreen.clearTarget();
        MedicalPoses.clearClient();
        ClientTreatmentCache.clear();
    }
}
