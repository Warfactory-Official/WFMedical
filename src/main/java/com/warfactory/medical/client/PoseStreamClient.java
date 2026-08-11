package com.warfactory.medical.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.damage.HitAuthority;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.damage.rig.RigCache;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.network.PoseStreamPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
public final class PoseStreamClient {

    private static volatile boolean enabled = false;
    private static long lastSentTick = Long.MIN_VALUE;
    private static float[] lastSent;

    private PoseStreamClient() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            lastSent = null;
            lastSentTick = Long.MIN_VALUE;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = player.level().getGameTime();
        long since = now - lastSentTick;
        if (since < MedicalConfig.poseStreamMinIntervalTicks()) {
            return;
        }
        HumanoidRig.LocalRig rig = RigCache.get(player);
        float[] flat = flatten(rig);
        boolean changed = !nearlyEqual(flat, lastSent);
        if (!changed && since < MedicalConfig.poseStreamMaxIntervalTicks()) {
            return;
        }
        MedicalNetworking.sendToServer(new PoseStreamPacket(rig));
        lastSent = flat;
        lastSentTick = now;
    }

    private static float[] flatten(HumanoidRig.LocalRig rig) {
        float[] a = new float[HumanoidRig.LocalRig.SLOTS.length * 15];
        int i = 0;
        for (HumanoidRig.LocalRig.Slot slot : HumanoidRig.LocalRig.SLOTS) {
            Obb o = slot.get(rig);
            i = put(a, i, o.center());
            i = put(a, i, o.axisX());
            i = put(a, i, o.axisY());
            i = put(a, i, o.axisZ());
            i = put(a, i, o.half());
        }
        return a;
    }

    private static int put(float[] a, int i, Vec3 v) {
        a[i] = (float) v.x;
        a[i + 1] = (float) v.y;
        a[i + 2] = (float) v.z;
        return i + 3;
    }

    private static boolean nearlyEqual(float[] a, float[] b) {
        if (b == null || a.length != b.length) {
            return false;
        }
        float eps = (float) MedicalConfig.poseStreamChangeEpsilon();
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > eps) {
                return false;
            }
        }
        return true;
    }
}
