package com.warfactory.medical.client;

import com.norwood.ahf.hook.AhfHooks;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.config.MedicalClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Writes {@link StressShake} into the player's rotation (aim moves with it), as {@link PainSwayHandler} does for pain. */
@EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
public final class StressShakeHandler {
    private static final float ADS_STEADINESS = 0.7F;
    private static final float UNARMED_WEIGHT = 0.3F;

    private static final StressShake SHAKE = new StressShake();
    private static float appliedYaw;
    private static float appliedPitch;

    private StressShakeHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            return;
        }
        float strength = MedicalClientConfig.stressShakeStrength();
        float targetYaw = 0.0F;
        float targetPitch = 0.0F;
        if (strength > 0.0F && !player.isSpectator() && !MedicalState.isUnconscious(player)) {
            float stress = MedicalState.stress(player);
            boolean armed = PainSwayHandler.aimingRangedWeapon(player);
            float weight = armed ? 1.0F : stress >= StressShake.CRITICAL ? UNARMED_WEIGHT : 0.0F;
            if (armed) {
                weight *= Mth.lerp(AhfHooks.gunAimProgress(player), 1.0F, ADS_STEADINESS);
            }
            SHAKE.tick(stress, weight, player.getRandom());
            targetYaw = SHAKE.yaw * strength;
            targetPitch = SHAKE.pitch * strength;
        } else {
            SHAKE.reset();
        }
        float dYaw = targetYaw - appliedYaw;
        float dPitch = targetPitch - appliedPitch;
        if (dYaw != 0.0F || dPitch != 0.0F) {
            player.setYRot(player.getYRot() + dYaw);
            player.setXRot(Mth.clamp(player.getXRot() + dPitch, -90.0F, 90.0F));
            appliedYaw = targetYaw;
            appliedPitch = targetPitch;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SHAKE.reset();
        appliedYaw = 0.0F;
        appliedPitch = 0.0F;
    }
}
