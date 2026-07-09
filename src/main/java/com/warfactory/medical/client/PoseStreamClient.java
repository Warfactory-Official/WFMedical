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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT-ONLY pose streamer for {@link HitAuthority#CLIENT_HINT}. When the server has asked this client to
 * stream (via {@code HitAuthorityPacket}), the local player sends its own posed rig so the server can classify
 * hits against it without rebuilding the pose.
 *
 * <p>Streaming is rate-limited to {@link MedicalConfig#poseStreamMinIntervalTicks} and only sends when the
 * pose actually changed, with a {@link MedicalConfig#poseStreamMaxIntervalTicks} heartbeat so the server's
 * copy never goes stale while the player stands still. The rig itself is the same one the debug overlay uses,
 * fetched through the per-tick {@link RigCache} so it is built at most once per tick.</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PoseStreamClient {

    private static final float CHANGE_EPSILON = 1.0e-3F;
    private static volatile boolean enabled = false;
    private static long lastSentTick = Long.MIN_VALUE;
    private static float[] lastSent;

    private PoseStreamClient() {
    }

    /**
     * Enable/disable streaming for this session (driven by the server's {@code HitAuthorityPacket}).
     */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            lastSent = null;
            lastSentTick = Long.MIN_VALUE;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled) {
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
            return; // rate limit
        }
        HumanoidRig.LocalRig rig = RigCache.get(player);
        float[] flat = flatten(rig);
        boolean changed = !nearlyEqual(flat, lastSent);
        if (!changed && since < MedicalConfig.poseStreamMaxIntervalTicks()) {
            return; // unchanged and heartbeat not due yet
        }
        MedicalNetworking.sendToServer(new PoseStreamPacket(rig));
        lastSent = flat;
        lastSentTick = now;
    }

    /**
     * Flatten the rig to the same 15-scalars-per-box layout the packet uses, for change detection.
     */
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
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > CHANGE_EPSILON) {
                return false;
            }
        }
        return true;
    }
}
