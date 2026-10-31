package com.warfactory.medical.body;

import com.warfactory.medical.MedicalSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.api.MedicalState;
import com.warfactory.medical.body.client.BodyClient;
import com.warfactory.medical.body.client.ClientDownedTracker;
import com.warfactory.medical.body.client.ClientMedicalCache;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.client.MedicalClientConfig;
import com.warfactory.medical.body.network.BodyNetwork;
import com.warfactory.medical.body.network.DownedStatePacket;
import com.warfactory.medical.body.network.GiveUpPacket;
import com.warfactory.medical.body.network.MedicalDeltaPacket;
import com.warfactory.medical.body.network.MedicalSyncPacket;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.wf.wflib.sway.api.PainSway;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Set;

/** Physiology, profile, limbs, trauma model, sync, HUD, {@code /wfmedical} root. */
public final class BodySystem implements MedicalSystem {

    @Override
    public String id() {
        return "body";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public Set<String> requires() {
        return Set.of("wflib:artifact", "wflib:sway");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MedicalClientConfig.SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, MedicalConfig::onConfig);
        modBus.addListener(ModConfigEvent.Reloading.class, MedicalConfig::onConfig);
        modBus.addListener(ModConfigEvent.Loading.class, MedicalClientConfig::onConfig);
        modBus.addListener(ModConfigEvent.Reloading.class, MedicalClientConfig::onConfig);
        MedicalAttachments.register(modBus);
        modBus.addListener(BodySystem::payloads);
        MedicalDefinitions.traumas(FMLPaths.CONFIGDIR.get()).all()
                .forEach(type -> TraumaTypes.TYPES.register(TraumaTypes.key(type.getId()), type));
        ArtifactHealing.claim();
        NeoForge.EVENT_BUS.register(BodyEvents.class);
        NeoForge.EVENT_BUS.addListener(MedicalCommands::onRegisterCommands);
        MedicalCommands.registerOwn();
        PainSway.SOURCE.set(WFMedical.MOD_ID, new PainSway.Source() {
            @Override
            public float intensity(Player p) {
                return MedicalState.painSwayIntensity(p);
            }

            @Override
            public float aimFloor(Player p) {
                return MedicalConfig.painSwayEnabled() && MedicalState.hasArmFracture(p) && !MedicalState.isUnconscious(p)
                        ? (float) MedicalConfig.brokenArmAimSway() : 0.0F;
            }

            @Override
            public float amplitudeDeg() {
                return 2.5F * (float) MedicalConfig.painSwayStrength();
            }
        });
    }

    @Override
    public void registerClient(IEventBus modBus) {
        BodyClient.register(modBus);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("body-1");
        registrar.playToClient(MedicalSyncPacket.TYPE, MedicalSyncPacket.STREAM_CODEC,
                (packet, ctx) -> ClientMedicalCache.set(packet));
        registrar.playToClient(DownedStatePacket.TYPE, DownedStatePacket.STREAM_CODEC,
                (packet, ctx) -> ClientDownedTracker.set(packet.entityId(), packet.downed()));
        registrar.playToClient(MedicalDeltaPacket.TYPE, MedicalDeltaPacket.STREAM_CODEC,
                (packet, ctx) -> ClientMedicalCache.applyDelta(packet));
        registrar.playToServer(GiveUpPacket.TYPE, GiveUpPacket.STREAM_CODEC,
                (packet, ctx) -> packet.handleServer(BodyNetwork.sender(ctx)));
    }
}
