package com.warfactory.medical.compat.wflib;

import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.ClientMedicalCache;
import com.wf.wflib.client.xray.XrScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * GAMMA's screen and camera effects on this mod's body, through WFLib's {@link XrScreen}:
 * <ul>
 * <li>hits ({@code actor_effects.script Update_Impact}): health down over 5% => {@code amk_shoot.ppe} (factor = drop
 * x 100); over 20% => a random {@code hit_*.anm}; over 50% => {@code total_recall.ppe} + {@code head_shot.anm};</li>
 * <li>{@code Update_Blood}: any drop => {@code bloody.ppe}, then 2-5 s before the next;</li>
 * <li>{@code Update_Bleeding}: health under 21% => {@code alcohol.ppe} looped; bleeding over half (of
 * {@code wakeupBleedReference}) under 20% health => {@code deimos1.ppe} every 4-10 s;</li>
 * <li>psy ({@code arszi_psy.script}, GAMMA Psy rework): psy health {@code 1 - stress} under 0.9 =>
 * {@code snd_shock.ppe} looped at {@code (1 - psy) x 0.01};</li>
 * <li>a finished treatment ({@code medic_effects.script}): {@code item_use_fast.anm} + {@code surge_fade.ppe}, then
 * {@code inv_medkit.ppe}; radiation treatment: {@code item_use.anm} + {@code surge_fade.ppe}, then {@code blur.ppe}.</li>
 * </ul>
 * Gap: GAMMA's {@code pripyat_horror.anm} + {@code snd_shock.ppe} at heavy bleeding and low stamina (no stamina here).
 */
public final class ScreenEffectsClient {
    private static final String[] MIDDLE = {"hit_front", "hit_right", "hit_back", "hit_left", "hit_front_left",
            "hit_back_left", "hit_front_right", "hit_back_right"};
    private static float lastHealth = -1.0F;
    private static long bloodyAt;
    private static long deimosAt;
    private static ActiveTreatmentPacket treating;
    private static long afterMedicAt;
    private static String afterMedic;

    private ScreenEffectsClient() {
    }

    private static ResourceLocation ppe(String name) {
        return XrScreen.ppe("wflib", name);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isDeadOrDying()) {
            lastHealth = -1.0F;
            return;
        }
        long now = System.currentTimeMillis();
        float health = player.getHealth() / player.getMaxHealth();
        if (lastHealth >= 0.0F) {
            hit(lastHealth - health, now);
        }
        lastHealth = health;
        loop("wfmedical:low_health", "alcohol", health < 0.21F, 1.0F);
        double bleeding = ClientMedicalCache.stats().totalBleeding() / MedicalConfig.wakeupBleedReference();
        if (bleeding > 0.5D && health < 0.2F && now >= deimosAt) {
            XrScreen.post("wfmedical:bleeding", ppe("deimos1"), false, 1.0F);
            deimosAt = now + ThreadLocalRandom.current().nextLong(4000, 10000);
        }
        float psy = 1.0F - ClientMedicalCache.stats().stress();
        loop("wfmedical:psy", "snd_shock", psy < 0.9F, (1.0F - psy) * 0.01F);
        treatment(now);
    }

    private static void hit(float drop, long now) {
        if (drop <= 0.0F) {
            return;
        }
        if (now >= bloodyAt) {
            XrScreen.post("wfmedical:bloody", ppe("bloody"), false, 1.0F);
            bloodyAt = now + ThreadLocalRandom.current().nextLong(2000, 5000);
        }
        if (drop <= 0.05F) {
            return;
        }
        XrScreen.post("wfmedical:hit", ppe("amk_shoot"), false, Math.min(1.0F, drop * 100.0F));
        if (drop > 0.5F) {
            XrScreen.post("wfmedical:hit_huge", ppe("total_recall"), false, 1.0F);
            XrScreen.camera(XrScreen.anm("wflib", "head_shot"), 1.0F);
        } else if (drop > 0.2F) {
            XrScreen.camera(XrScreen.anm("wflib", MIDDLE[ThreadLocalRandom.current().nextInt(MIDDLE.length)]), 1.0F);
        }
    }

    private static void loop(String id, String ppe, boolean on, float factor) {
        if (on) {
            XrScreen.post(id, ppe(ppe), true, factor);
        } else if (XrScreen.running(id)) {
            XrScreen.stop(id, 0.5F);
        }
    }

    /** A treatment that ran its full time (not one cancelled) plays the medic effects. */
    private static void treatment(long now) {
        ActiveTreatmentPacket active = ClientMedicalCache.hasActiveTreatment() ? ClientMedicalCache.activeTreatment()
                : null;
        if (treating != null && active == null && treating.action() != TreatmentAction.RESUSCITATE
                && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getGameTime() >= treating.startGameTime() + treating.totalTicks() - 2) {
            boolean rad = treating.action() == TreatmentAction.TREAT_RADIATION;
            float seconds = XrScreen.camera(XrScreen.anm("wflib", rad ? "item_use" : "item_use_fast"), 1.0F);
            XrScreen.post("wfmedical:medic", ppe("surge_fade"), false, 1.0F);
            afterMedic = rad ? "blur" : "inv_medkit";
            afterMedicAt = now + (long) (seconds * 1000.0F);
        }
        treating = active;
        if (afterMedic != null && now >= afterMedicAt) {
            XrScreen.post("wfmedical:medic_after", ppe(afterMedic), false, 1.0F);
            afterMedic = null;
        }
    }
}
