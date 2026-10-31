package com.warfactory.medical.body;

import com.norwood.ahf.stance.AhfStance;
import com.norwood.ahf.stance.StanceState;
import net.minecraft.server.level.ServerPlayer;

/** Downed / leg-crawl -> AHF posture locks. Writes on change only: {@code getData} creates and syncs the state. */
public final class MedicalStance {

    private MedicalStance() {
    }

    public static void downed(ServerPlayer player, boolean downed) {
        StanceState s = AhfStance.get(player);
        if (s == null ? downed : s.downed() != downed) {
            AhfStance.setDowned(player, downed);
        }
    }

    public static void crawl(ServerPlayer player, boolean crawl) {
        StanceState s = AhfStance.get(player);
        if (s == null ? crawl : s.crawl() != crawl) {
            AhfStance.setCrawl(player, crawl);
        }
    }
}
