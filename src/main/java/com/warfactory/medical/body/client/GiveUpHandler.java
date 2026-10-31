package com.warfactory.medical.body.client;

import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.network.GiveUpPacket;
import com.warfactory.medical.body.network.BodyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class GiveUpHandler {

    private static int held;
    private static boolean sent;

    private GiveUpHandler() {
    }

    public static void tick(Minecraft mc) {
        if (available(mc) && BodyKeys.GIVE_UP.isDown()) {
            held++;
            if (!sent && held >= threshold()) {
                BodyNetwork.sendToServer(new GiveUpPacket());
                sent = true;
            }
        } else {
            held = 0;
            sent = false;
        }
    }

    public static boolean available() {
        return available(Minecraft.getInstance());
    }

    private static boolean available(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null
                && mc.screen == null
                && MedicalConfig.enableGiveUp()
                && ClientMedicalCache.stats().unconscious();
    }

    public static float progress01() {
        int t = threshold();
        float p = (float) held / (float) t;
        return p < 0.0F ? 0.0F : (Math.min(p, 1.0F));
    }

    private static int threshold() {
        return Math.max(1, MedicalConfig.giveUpHoldTicks());
    }
}
