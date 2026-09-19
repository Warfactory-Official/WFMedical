package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.HitAuthority;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
import java.util.WeakHashMap;

public final class MedicalNetworking {

    private static final String PROTOCOL = "3";
    private static final Map<ServerPlayer, MedicalSyncPacket> LAST_SENT = new WeakHashMap<>();
    // Last game tick each player was re-baselined with a full authoritative sync (safety net; see syncTo).
    private static final Map<ServerPlayer, Long> LAST_FULL_TICK = new WeakHashMap<>();

    private MedicalNetworking() {
    }

    /**
     * Registers every payload with the play-phase registrar. Replaces the 1.20.1 SimpleChannel: the
     * numeric message ids are gone, each payload now carries its own namespaced type id, and the
     * protocol version string is what gates connecting to a mismatched server.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        registrar.playToClient(MedicalSyncPacket.TYPE, MedicalSyncPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToServer(MedicalActionPacket.TYPE, MedicalActionPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(ActiveTreatmentPacket.TYPE, ActiveTreatmentPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToClient(DownedStatePacket.TYPE, DownedStatePacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToServer(RemoveTourniquetPacket.TYPE, RemoveTourniquetPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToServer(ResuscitatePacket.TYPE, ResuscitatePacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(MedicalDeltaPacket.TYPE, MedicalDeltaPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToServer(PoseStreamPacket.TYPE, PoseStreamPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(HitAuthorityPacket.TYPE, HitAuthorityPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToClient(TourniquetStatePacket.TYPE, TourniquetStatePacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToServer(TreatmentTargetRequestPacket.TYPE, TreatmentTargetRequestPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(TreatmentTargetInfoPacket.TYPE, TreatmentTargetInfoPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToServer(CancelTreatmentPacket.TYPE, CancelTreatmentPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToServer(TargetSheetRequestPacket.TYPE, TargetSheetRequestPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(TargetSheetInfoPacket.TYPE, TargetSheetInfoPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleClient());
        registrar.playToServer(GiveUpPacket.TYPE, GiveUpPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
    }

    /** Server-side sender, or null when a to-server payload somehow arrives without one. */
    private static ServerPlayer sender(IPayloadContext ctx) {
        return ctx.player() instanceof ServerPlayer player ? player : null;
    }

    public static int tourniquetMask(MedicalProfile profile) {
        int mask = 0;
        for (LimbType lt : LimbType.VALUES) {
            if (profile.limb(lt).hasTourniquet()) {
                mask |= (1 << lt.ordinal());
            }
        }
        return mask;
    }

    public static void broadcastTourniquets(LivingEntity entity, MedicalProfile profile) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                new TourniquetStatePacket(entity.getId(), tourniquetMask(profile)));
    }

    public static void sendTourniquetsTo(ServerPlayer viewer, int entityId, int mask) {
        PacketDistributor.sendToPlayer(viewer, new TourniquetStatePacket(entityId, mask));
    }

    public static void sendHitAuthority(ServerPlayer player) {
        boolean stream = MedicalConfig.useClientPose();
        PacketDistributor.sendToPlayer(player, new HitAuthorityPacket(stream));
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

    public static void sendActiveTreatment(ServerPlayer player, ActiveTreatmentPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendTargetInfo(ServerPlayer medic, TreatmentTargetInfoPacket packet) {
        PacketDistributor.sendToPlayer(medic, packet);
    }

    public static void sendTargetSheet(ServerPlayer medic, TargetSheetInfoPacket packet) {
        PacketDistributor.sendToPlayer(medic, packet);
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
