package com.warfactory.medical.api;

import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.server.MedicalEngine;
import net.minecraft.server.level.ServerPlayer;

/**
 * Healing from other mods (artefacts, regen gear), server side. A plain {@code player.heal} is clamped to the medical
 * health value, so it does nothing while wounds hold health down: this closes the wounds instead.
 */
public final class MedicalHealing {

    private MedicalHealing() {
    }

    /**
     * Give back up to {@code hp} health points: first any health below what the wounds allow, then by healing
     * minor traumas (a fall's blunt trauma, burns) and treated or stabilised wounds. Untreated wounds are left for
     * medicine. Returns the points given back.
     */
    public static float heal(ServerPlayer player, float hp) {
        if (hp <= 0.0F || player.isDeadOrDying()) {
            return 0.0F;
        }
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            float before = player.getHealth();
            player.heal(hp);
            return player.getHealth() - before;
        }
        MedicalProfile profile = data.getProfile();
        float before = player.getHealth();
        float headroom = profile.cached().effectiveCurrentHealth() - before;
        if (headroom > 0.0F) {
            player.heal(Math.min(hp, headroom));
        }
        float left = hp - (player.getHealth() - before);
        if (left > 0.0F && MedicalEngine.restore(profile, left) > 0.0F) {
            MedicalEngine.resync(player);
        }
        return player.getHealth() - before;
    }

    /**
     * Help bleeding wounds close on their own for {@code ticks}, as a clotting drug does: {@code strength} 0..1
     * raises the severity a wound self-clots below and how fast. The strongest current boost and latest end win.
     */
    public static void clot(ServerPlayer player, float strength, int ticks) {
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null || strength <= 0.0F || ticks <= 0) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        long end = player.level().getGameTime() + ticks;
        profile.setClottingBoost(Math.max(profile.getClottingBoost(), Math.min(strength, 1.0F)));
        if (profile.getClottingBoostEndTick() < end) {
            profile.setClottingBoostEndTick(end);
        }
    }
}
