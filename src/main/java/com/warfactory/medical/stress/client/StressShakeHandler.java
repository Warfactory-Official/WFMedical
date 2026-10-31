package com.warfactory.medical.stress.client;

import com.norwood.ahf.hook.AhfHooks;
import com.warfactory.medical.body.api.MedicalState;
import com.warfactory.medical.body.client.MedicalClientConfig;
import com.wf.wflib.sway.api.PainSway;
import com.wf.wflib.sway.api.client.CameraSway;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** {@link StressShake} as a camera sway source (aim moves with it). */
final class StressShakeHandler implements CameraSway.Source {
    private static final float ADS_STEADINESS = 0.7F;
    private static final float UNARMED_WEIGHT = 0.3F;

    private final StressShake shake = new StressShake();

    @Override
    public void offset(LocalPlayer player, double time, float[] out) {
        float strength = MedicalClientConfig.stressShakeStrength();
        if (strength <= 0.0F || MedicalState.isUnconscious(player)) {
            shake.reset();
            return;
        }
        float stress = MedicalState.stress(player);
        boolean armed = PainSway.aimingRanged(player);
        float weight = armed ? 1.0F : stress >= StressShake.CRITICAL ? UNARMED_WEIGHT : 0.0F;
        if (armed) {
            weight *= Mth.lerp(AhfHooks.gunAimProgress(player), 1.0F, ADS_STEADINESS);
        }
        shake.tick(stress, weight, player.getRandom());
        out[0] += shake.yaw * strength;
        out[1] += shake.pitch * strength;
    }

    @Override
    public void reset() {
        shake.reset();
    }
}
