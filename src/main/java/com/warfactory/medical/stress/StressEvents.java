package com.warfactory.medical.stress;

import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.PsyProtection;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.Stress;
import com.wf.wflib.anomaly.api.event.PsyExposureEvent;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.round.api.event.RoundNearMissEvent;
import com.wf.wflib.smoking.api.event.CigaretteSmokedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Near-miss rounds and psy raise stress, cigarettes lower it. */
public final class StressEvents {

    private StressEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StressEvents::onNearMiss);
        if (WFSystems.enabled("wflib:smoking")) {
            NeoForge.EVENT_BUS.addListener(StressEvents::onSmoked);
        }
        if (WFSystems.enabled("wflib:anomaly")) {
            NeoForge.EVENT_BUS.addListener(StressEvents::onPsy);
        }
    }

    @Nullable
    private static MedicalProfile profile(ServerPlayer player) {
        if (!MedicalConfig.stressEnabled() || player.isCreative() && MedicalConfig.effectImmuneInCreative()) {
            return null;
        }
        IMedicalData data = MedicalAttachments.get(player);
        return data == null ? null : data.getProfile();
    }

    private static void onNearMiss(RoundNearMissEvent event) {
        MedicalProfile profile = profile(event.player());
        if (profile != null) {
            profile.addStress(Stress.suppression(event.distance(), MedicalConfig.stressParams())
                    * PsyProtection.pass(event.player()), event.level().getGameTime());
        }
    }

    /** Psy is stress here: taken (no psy damage) even while stress is off or creative-immune. */
    private static void onPsy(PsyExposureEvent event) {
        event.handle();
        MedicalProfile profile = profile(event.player());
        if (profile != null) {
            profile.addStress(event.psy() * MedicalConfig.stressParams().psyGain() * PsyProtection.pass(event.player()),
                    event.player().level().getGameTime());
        }
    }

    private static void onSmoked(CigaretteSmokedEvent event) {
        MedicalProfile profile = profile(event.player());
        if (profile != null) {
            profile.setStress(profile.getStress() - MedicalConfig.stressParams().cigaretteRelief());
        }
    }
}
