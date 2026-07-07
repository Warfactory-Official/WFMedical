package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.compat.tacz.TaczGunState;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PainSwayHandler {

    private static final float SWAY_START = 0.15F;
    /**
     * Peak sway amplitude (degrees) at full intensity, on each axis.
     */
    private static final float MAX_SWAY_DEG = 2.5F;
    /**
     * Noise advance per tick (radians); low, so the drift is a slow unsteadiness with a faster tremor on top.
     */
    private static final double PHASE_STEP = 0.09;
    /**
     * Sway multiplier at full ADS (bracing): 0.35 = a 65% reduction, but never zero.
     */
    private static final float ADS_STEADINESS = 0.35F;

    /**
     * Rolling noise phase; advanced only while sway is active.
     */
    private static double swayPhase;
    /**
     * Sway offset (degrees) applied last tick, so we can apply only the per-tick delta.
     */
    private static double appliedYaw;
    private static double appliedPitch;

    private PainSwayHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            return;
        }

        double targetYaw = 0.0;
        double targetPitch = 0.0;
        boolean active = MedicalConfig.painSwayEnabled()
                && !player.isSpectator()
                && ClientMedicalCache.state() != HealthState.UNCONSCIOUS;
        if (active) {
            float intensity = swayIntensity(ClientMedicalCache.stats().totalPain());
            // A tourniquet on an arm makes the hands shaky (restricted circulation) -> a sway floor, treated
            // like pain (so ADS bracing still steadies it partially below).
            if (ClientMedicalCache.stats().anyArmTourniquet()) {
                intensity = Math.max(intensity, (float) MedicalConfig.tourniquetArmSway());
            }
            float ads = TaczCompat.isLoaded() ? TaczGunState.aimingProgress(player) : 0.0F;
            if (intensity > 0.0F && ads > 0.0F) {
                intensity *= Mth.lerp(ads, 1.0F, ADS_STEADINESS);
            }
            // A BROKEN ARM cannot hold a bow / gun steady
            if (ClientMedicalCache.stats().anyArmFracture() && aimingRangedWeapon(player)) {
                intensity = Math.max(intensity, (float) MedicalConfig.brokenArmAimSway());
            }
            if (intensity > 0.0F) {
                float amp = MAX_SWAY_DEG * (float) MedicalConfig.painSwayStrength();
                swayPhase += PHASE_STEP;
                targetYaw = intensity * amp * noiseYaw(swayPhase);
                targetPitch = intensity * amp * noisePitch(swayPhase);
            }
        }

        double dYaw = targetYaw - appliedYaw;
        double dPitch = targetPitch - appliedPitch;
        if (dYaw != 0.0 || dPitch != 0.0) {
            player.setYRot(player.getYRot() + (float) dYaw);
            player.setXRot(Mth.clamp(player.getXRot() + (float) dPitch, -90.0F, 90.0F));
            appliedYaw = targetYaw;
            appliedPitch = targetPitch;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        swayPhase = 0.0;
        appliedYaw = 0.0;
        appliedPitch = 0.0;
    }

    private static float swayIntensity(float pain) {
        if (pain <= SWAY_START) {
            return 0.0F;
        }
        float full = MedicalConfig.painUnconsciousThreshold();
        float span = full - SWAY_START;
        if (span <= 0.0F) {
            return 1.0F;
        }
        float t = (pain - SWAY_START) / span;
        return Math.min(t, 1.0F);
    }

    private static boolean aimingRangedWeapon(LocalPlayer player) {
        if (player.isUsingItem()) {
            Item item = player.getUseItem().getItem();
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                return true;
            }
        }
        return TaczCompat.isLoaded()
                && (TaczCompat.isHeldGun(player.getMainHandItem()) || TaczCompat.isHeldGun(player.getOffhandItem()));
    }

    private static double noiseYaw(double p) {
        return Math.sin(p) * 0.6 + Math.sin(p * 2.3 + 1.7) * 0.4;
    }

    private static double noisePitch(double p) {
        return Math.cos(p * 0.9 + 0.5) * 0.6 + Math.sin(p * 1.7 + 3.1) * 0.4;
    }
}
