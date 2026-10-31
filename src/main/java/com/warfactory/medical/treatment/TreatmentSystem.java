package com.warfactory.medical.treatment;

import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.treatment.client.ClientTourniquetTracker;
import com.warfactory.medical.treatment.client.ClientTreatmentCache;
import com.warfactory.medical.treatment.client.MedInteractionScreen;
import com.warfactory.medical.treatment.client.TreatmentClient;
import com.warfactory.medical.treatment.client.TreatmentInteractions;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.treatment.network.ActiveTreatmentPacket;
import com.warfactory.medical.treatment.network.CancelTreatmentPacket;
import com.warfactory.medical.treatment.network.MedicalActionPacket;
import com.warfactory.medical.treatment.network.RemoveTourniquetPacket;
import com.warfactory.medical.treatment.network.ResuscitatePacket;
import com.warfactory.medical.treatment.network.TargetSheetInfoPacket;
import com.warfactory.medical.treatment.network.TargetSheetRequestPacket;
import com.warfactory.medical.treatment.network.TourniquetStatePacket;
import com.warfactory.medical.treatment.network.TreatmentNetwork;
import com.warfactory.medical.treatment.network.TreatmentPosePacket;
import com.warfactory.medical.treatment.network.TreatmentTargetInfoPacket;
import com.warfactory.medical.treatment.network.TreatmentTargetRequestPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

import static com.warfactory.medical.body.network.BodyNetwork.sender;

/** Medical items, treatment actions, tourniquets, sheet and radial UI. */
public final class TreatmentSystem implements MedicalSystem {

    @Override
    public String id() {
        return "treatment";
    }

    @Override
    public Set<String> requires() {
        return Set.of("body");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        DeferredRegister.Items items = DeferredRegister.createItems(WFMedical.MOD_ID);
        TreatmentContent.register(items);
        items.register(modBus);
        ModCreativeTab.register(modBus);
        TreatmentDefinitions.load(FMLPaths.CONFIGDIR.get())
                .forEach((item, treatment) -> Treatments.TREATMENTS.register(ResourceLocation.parse(item), treatment));
        modBus.addListener(TreatmentSystem::payloads);
        MedicalPoses.register();
        BodyHooks.ACTIVE_TREATMENT.set(WFMedical.MOD_ID, new BodyHooks.ActiveTreatment() {
            @Override
            public void tick(ServerPlayer player, MedicalProfile profile, long nowTick) {
                MedicalActionService.tick(player, profile, nowTick);
            }

            @Override
            public void cancel(ServerPlayer player, String reason) {
                MedicalActionService.cancel(player, reason);
            }
        });
        BodyHooks.JOIN.register(rl("tourniquets"), 0, player -> {
            IMedicalData data = MedicalAttachments.get(player);
            if (data != null) {
                TreatmentNetwork.broadcastTourniquets(player, data.getProfile());
            }
        });
        BodyHooks.START_TRACKING.register(rl("tourniquets"), 0, (viewer, target) -> {
            IMedicalData data = MedicalAttachments.get(target);
            if (data != null) {
                TreatmentNetwork.sendTourniquetsTo(viewer, target.getId(), TreatmentNetwork.tourniquetMask(data.getProfile()));
                TreatmentNetwork.sendTreatmentPoseTo(viewer, target);
            }
        });
        TreatmentCommands.register();
    }

    @Override
    public void registerClient(IEventBus modBus) {
        TreatmentClient.register(modBus);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, path);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("treatment-1");
        registrar.playToServer(MedicalActionPacket.TYPE, MedicalActionPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(ActiveTreatmentPacket.TYPE, ActiveTreatmentPacket.STREAM_CODEC,
                (packet, ctx) -> ClientTreatmentCache.setActiveTreatment(packet));
        registrar.playToClient(TreatmentPosePacket.TYPE, TreatmentPosePacket.STREAM_CODEC,
                (packet, ctx) -> MedicalPoses.client(packet.entityId(), packet.kind()));
        registrar.playToServer(RemoveTourniquetPacket.TYPE, RemoveTourniquetPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToServer(ResuscitatePacket.TYPE, ResuscitatePacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(TourniquetStatePacket.TYPE, TourniquetStatePacket.STREAM_CODEC,
                (packet, ctx) -> ClientTourniquetTracker.set(packet.entityId(), packet.mask()));
        registrar.playToServer(TreatmentTargetRequestPacket.TYPE, TreatmentTargetRequestPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(TreatmentTargetInfoPacket.TYPE, TreatmentTargetInfoPacket.STREAM_CODEC,
                (packet, ctx) -> TreatmentInteractions.onTargetInfo(packet));
        registrar.playToServer(CancelTreatmentPacket.TYPE, CancelTreatmentPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToServer(TargetSheetRequestPacket.TYPE, TargetSheetRequestPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(sender(ctx)));
        registrar.playToClient(TargetSheetInfoPacket.TYPE, TargetSheetInfoPacket.STREAM_CODEC,
                (packet, ctx) -> MedInteractionScreen.onTargetSheetInfo(packet));
    }
}
