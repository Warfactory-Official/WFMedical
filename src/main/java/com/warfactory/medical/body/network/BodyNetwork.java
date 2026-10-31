package com.warfactory.medical.body.network;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalProfile;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.WeakHashMap;

public final class BodyNetwork {

    private static final Map<ServerPlayer, MedicalSyncPacket> LAST_SENT = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_FULL_TICK = new WeakHashMap<>();

    private BodyNetwork() {
    }

    /** Server-side sender, or null when a to-server payload somehow arrives without one. */
    public static ServerPlayer sender(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer player ? player : null;
    }

    public static void sendFull(ServerPlayer player, MedicalProfile profile) {
        MedicalSyncPacket full = MedicalSyncPacket.fromProfile(profile);
        PacketDistributor.sendToPlayer(player, full);
        LAST_SENT.put(player, full);
        LAST_FULL_TICK.put(player, player.level().getGameTime());
        if (MedicalConfig.logMedicalSync()) {
            WFMedical.LOGGER.info("[wfmed-sync] FULL  -> {} {}", player.getGameProfile().getName(), summarize(full));
        }
    }

    public static void syncTo(ServerPlayer player, MedicalProfile profile) {
        MedicalSyncPacket prev = LAST_SENT.get(player);
        if (prev == null) {
            sendFull(player, profile);
            return;
        }

        // Safety net: periodically re-baseline with a full authoritative sync so any client-side delta drift
        // (a dropped / late / mis-based delta that left the cache stuck on a stale or falsely-healthy limb)
        // self-corrects within the interval. Invisible while in sync -- the full carries the same data the
        // deltas already produced -- so this only ever visibly does anything when a desync actually happened.
        int fullInterval = MedicalConfig.syncFullResyncIntervalTicks();
        if (fullInterval > 0) {
            long now = player.level().getGameTime();
            Long lastFull = LAST_FULL_TICK.get(player);
            if (lastFull == null || now - lastFull >= fullInterval) {
                sendFull(player, profile);
                return;
            }
        }

        MedicalSyncPacket full = MedicalSyncPacket.fromProfile(profile);
        MedicalDeltaPacket delta = MedicalDeltaPacket.diff(prev, full);
        if (delta.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, delta);
        LAST_SENT.put(player, full);
        if (MedicalConfig.logMedicalSync()) {
            WFMedical.LOGGER.info("[wfmed-sync] DELTA -> {} mask={} {}", player.getGameProfile().getName(),
                    Integer.toBinaryString(delta.mask()), summarize(full));
        }
    }

    /** Compact per-limb health%/bleed/pain/wound-count line for sync tracing (see logMedicalSync). */
    private static String summarize(MedicalSyncPacket packet) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("state=").append(packet.state()).append(" blood=").append(Math.round(packet.bloodMl()))
                .append(" limbs[");
        MedicalSyncPacket.LimbSummary[] limbs = packet.limbs();
        for (int i = 0; i < limbs.length; i++) {
            MedicalSyncPacket.LimbSummary s = limbs[i];
            if (s == null) {
                continue;
            }
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(s.limb()).append('=').append(Math.round(s.healthPercent() * 100.0F)).append('%');
            if (s.bleeding() > 0.0F) {
                sb.append(",bleed").append(String.format(java.util.Locale.ROOT, "%.2f", s.bleeding()));
            }
            if (s.pain() > 0.0F) {
                sb.append(",pain").append(String.format(java.util.Locale.ROOT, "%.2f", s.pain()));
            }
            if (s.fracture()) {
                sb.append(",fx");
            }
            int w = s.wounds() == null ? 0 : s.wounds().size();
            if (w > 0) {
                sb.append(",w").append(w);
            }
        }
        return sb.append(']').toString();
    }

    public static void broadcastDowned(ServerPlayer player, boolean downed) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new DownedStatePacket(player.getId(), downed));
    }

    public static void sendDownedTo(ServerPlayer viewer, int entityId, boolean downed) {
        PacketDistributor.sendToPlayer(viewer, new DownedStatePacket(entityId, downed));
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
