package com.warfactory.medical.compat.wflib;

import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.Stress;
import com.wf.wflib.round.RoundNearMissEvent;
import com.wf.wflib.smoking.CigaretteSmokedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** The only class naming WFLib's near-miss and cigarette events; reached behind {@link WfLibArmorCompat#isLoaded()}. */
final class StressBridge {

    private StressBridge() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StressBridge::onNearMiss);
        NeoForge.EVENT_BUS.addListener(StressBridge::onSmoked);
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
            profile.addStress(Stress.suppression(event.distance(), MedicalConfig.stressParams()),
                    event.level().getGameTime());
        }
    }

    private static void onSmoked(CigaretteSmokedEvent event) {
        MedicalProfile profile = profile(event.player());
        if (profile != null) {
            profile.setStress(profile.getStress() - MedicalConfig.stressParams().cigaretteRelief());
        }
    }
}
