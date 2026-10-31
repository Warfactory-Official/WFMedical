package com.warfactory.medical.body.client;

import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.network.MedicalSyncPacket.LimbSummary;
import java.util.*;
import com.warfactory.medical.body.network.MedicalSyncPacket;

public final class LimbColors {

    private LimbColors() {
    }

    public static int limbColor(float healthPercent01) {
        float p = healthPercent01;
        if (p < 0.0F) {
            p = 0.0F;
        } else if (p > 1.0F) {
            p = 1.0F;
        }
        int r;
        int g;
        if (p < 0.5F) {
            r = 255;
            g = Math.round(255.0F * (p * 2.0F));
        } else {
            r = Math.round(255.0F * ((1.0F - p) * 2.0F));
            g = 255;
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }

    public static int stateColor(HealthState state) {
        if (state == null) {
            return 0xFFFFFFFF;
        }
        return switch (state) {
            case HEALTHY -> 0xFF33CC33;
            case CRITICAL -> 0xFFE0A020;
            case UNCONSCIOUS -> 0xFFCC3030;
            case DEAD -> 0xFF404040;
        };
    }

    public static LimbSummary limbSummary(LimbType limb) {
        MedicalSyncPacket snap = ClientMedicalCache.get();
        if (snap != null) {
            LimbSummary[] limbs = snap.limbs();
            if (limbs != null) {
                for (LimbSummary s : limbs) {
                    if (s != null && s.limb() == limb) {
                        return s;
                    }
                }
            }
        }
        return new LimbSummary(limb, 1.0F, 0.0F, 0.0F, false, java.util.List.of());
    }
}
