package com.warfactory.medical.network;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.HitAuthority;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-authoritative sync channel for the medical system.
 *
 * <p>S2C packets carry authoritative state ({@link MedicalSyncPacket} full baseline +
 * {@link MedicalDeltaPacket} incremental updates, {@link ActiveTreatmentPacket} action-progress). C2S packets
 * are pure REQUESTS ({@link MedicalActionPacket} start-a-treatment, {@link SetTargetLimbPacket}
 * set-target-hint, {@link RemoveTourniquetPacket} remove-tourniquet): clients never create or remove trauma,
 * the server validates every request before acting.</p>
 */
public final class MedicalNetworking {

    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WFMedical.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    /**
     * The last full snapshot sent to each player, used to diff incremental {@link MedicalDeltaPacket}s. Weak
     * keys so an entry vanishes once the player's {@link ServerPlayer} is GC'd — a logout/respawn creates a
     * new instance with no entry, forcing a fresh full baseline. Server-thread access only.
     */
    private static final Map<ServerPlayer, MedicalSyncPacket> LAST_SENT = new WeakHashMap<>();
    private static boolean registered;

    private MedicalNetworking() {
    }

    /**
     * Register all packets (S2C state + C2S requests). Idempotent; call once from mod construction.
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.messageBuilder(MedicalSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MedicalSyncPacket::encode)
                .decoder(MedicalSyncPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // (packet id 1 retired: the write-only trauma-delta channel was removed; the full-snapshot sync
        // above already carries every change via the revision bump.)

        // C2S: request to begin a timed treatment.
        CHANNEL.messageBuilder(MedicalActionPacket.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MedicalActionPacket::encode)
                .decoder(MedicalActionPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // C2S: set the player's targeting hint.
        CHANNEL.messageBuilder(SetTargetLimbPacket.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTargetLimbPacket::encode)
                .decoder(SetTargetLimbPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: active-treatment progress state for the client overlay.
        CHANNEL.messageBuilder(ActiveTreatmentPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ActiveTreatmentPacket::encode)
                .decoder(ActiveTreatmentPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: per-entity downed (passed-out) state, broadcast to trackers so an observer can render the
        // downed body pose of a teammate who is overdose-unconscious or bleeding out.
        CHANNEL.messageBuilder(DownedStatePacket.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DownedStatePacket::encode)
                .decoder(DownedStatePacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // C2S: request to remove a tourniquet from a limb (UI-driven, no item).
        CHANNEL.messageBuilder(RemoveTourniquetPacket.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoveTourniquetPacket::encode)
                .decoder(RemoveTourniquetPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: incremental medical update — only the changed components, applied onto the client's baseline.
        CHANNEL.messageBuilder(MedicalDeltaPacket.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MedicalDeltaPacket::encode)
                .decoder(MedicalDeltaPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // C2S: the victim's own streamed pose (CLIENT_HINT authority). The server validates + ray-tests it.
        CHANNEL.messageBuilder(PoseStreamPacket.class, 8, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PoseStreamPacket::encode)
                .decoder(PoseStreamPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: tell a joining client whether to stream its pose (server's authority mode).
        CHANNEL.messageBuilder(HitAuthorityPacket.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HitAuthorityPacket::encode)
                .decoder(HitAuthorityPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: per-entity worn-tourniquet mask, broadcast to trackers so observers render the worn model.
        CHANNEL.messageBuilder(TourniquetStatePacket.class, 10, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TourniquetStatePacket::encode)
                .decoder(TourniquetStatePacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // C2S: ask the server for a treatment target's per-limb summaries (medic right-clicked someone else).
        CHANNEL.messageBuilder(TreatmentTargetRequestPacket.class, 11, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TreatmentTargetRequestPacket::encode)
                .decoder(TreatmentTargetRequestPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleServer(ctx.get().getSender());
                    ctx.get().setPacketHandled(true);
                })
                .add();

        // S2C: reply with the target's per-limb summaries so the client can open the limb wheel for them.
        CHANNEL.messageBuilder(TreatmentTargetInfoPacket.class, 12, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TreatmentTargetInfoPacket::encode)
                .decoder(TreatmentTargetInfoPacket::decode)
                .consumerMainThread((packet, ctx) -> {
                    packet.handleClient();
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    /**
     * The worn-tourniquet limb mask ({@code bit = 1 << LimbType.ordinal()}) for a profile.
     */
    public static int tourniquetMask(MedicalProfile profile) {
        int mask = 0;
        for (LimbType lt : LimbType.VALUES) {
            if (profile.limb(lt).hasTourniquet()) {
                mask |= (1 << lt.ordinal());
            }
        }
        return mask;
    }

    /**
     * Broadcast a player's worn-tourniquet mask to every client tracking them AND to the player itself, so
     * observers (and the wearer's own third-person model) render the worn tourniquets. Call on each apply/remove.
     */
    public static void broadcastTourniquets(LivingEntity entity, MedicalProfile profile) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new TourniquetStatePacket(entity.getId(), tourniquetMask(profile)));
    }

    /**
     * Send one viewer the CURRENT worn-tourniquet mask of an entity (start-tracking catch-up).
     */
    public static void sendTourniquetsTo(ServerPlayer viewer, int entityId, int mask) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new TourniquetStatePacket(entityId, mask));
    }

    /**
     * Tell a player whether the server wants its client to stream its pose (i.e. whether the authority mode
     * is {@link HitAuthority#CLIENT_HINT}). Sent on login; a live mode change takes effect on reconnect.
     */
    public static void sendHitAuthority(ServerPlayer player) {
        boolean stream = MedicalConfig.hitAuthority() == HitAuthority.CLIENT_HINT;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HitAuthorityPacket(stream));
    }

    /**
     * Send a full authoritative snapshot to one player, and record it as that player's baseline so subsequent
     * {@link #syncTo} calls can diff against it.
     */
    public static void sendFull(ServerPlayer player, MedicalProfile profile) {
        MedicalSyncPacket full = MedicalSyncPacket.fromProfile(profile);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), full);
        LAST_SENT.put(player, full);
    }

    /**
     * Push the player's current state in the cheapest correct form: a full {@link MedicalSyncPacket} baseline
     * the first time (or after a logout/respawn clears the tracked baseline), otherwise a
     * {@link MedicalDeltaPacket} of only the components that changed since the last send. A no-change call
     * sends nothing. The engine calls this wherever it previously sent a full snapshot.
     */
    public static void syncTo(ServerPlayer player, MedicalProfile profile) {
        MedicalSyncPacket prev = LAST_SENT.get(player);
        if (prev == null) {
            sendFull(player, profile);
            return;
        }
        MedicalSyncPacket full = MedicalSyncPacket.fromProfile(profile);
        MedicalDeltaPacket delta = MedicalDeltaPacket.diff(prev, full);
        if (delta.isEmpty()) {
            return; // nothing observable changed; keep the recorded baseline
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), delta);
        LAST_SENT.put(player, full);
    }

    /**
     * Send the active-treatment progress state to one player (start / completion / cancellation).
     */
    public static void sendActiveTreatment(ServerPlayer player, ActiveTreatmentPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Reply to a medic with a treatment target's per-limb summaries (so their client can open the limb wheel
     * for that target). See {@link TreatmentTargetRequestPacket} / {@link TreatmentTargetInfoPacket}.
     */
    public static void sendTargetInfo(ServerPlayer medic, TreatmentTargetInfoPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> medic), packet);
    }

    /**
     * Broadcast a player's downed (passed-out) state to every client tracking that player AND to the
     * player itself. Called by the engine on each downed edge (enter / exit overdose or bleed-out) so
     * observers can render — or stop rendering — the downed body pose.
     */
    public static void broadcastDowned(ServerPlayer player, boolean downed) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new DownedStatePacket(player.getId(), downed));
    }

    /**
     * Send a single viewer the CURRENT downed state of an entity. Used for start-tracking catch-up so a
     * late observer immediately learns a player is already downed (edge broadcasts alone would miss a
     * player who was downed before the observer began tracking them).
     */
    public static void sendDownedTo(ServerPlayer viewer, int entityId, boolean downed) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new DownedStatePacket(entityId, downed));
    }

    /**
     * Send a client-originated request packet to the server. Used by the client UI for
     * {@link MedicalActionPacket} and {@link SetTargetLimbPacket}; the server validates every request.
     */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
