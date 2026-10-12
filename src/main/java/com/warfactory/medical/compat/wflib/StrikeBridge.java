package com.warfactory.medical.compat.wflib;

import com.norwood.ahf.part.HitboxPart;
import com.wf.wflib.api.StrikeContext;
import com.wf.wflib.api.ThreatKind;
import com.warfactory.medical.core.damage.DamageCategory;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Names WFLib types; reached only behind {@link WfLibArmorCompat#isLoaded()}. */
final class StrikeBridge {

    private StrikeBridge() {
    }

    @Nullable
    static DamageCategory category(DamageSource source) {
        StrikeContext strike = StrikeContext.of(source);
        return strike == null ? null : category(strike.threat().kind());
    }

    static DamageCategory category(ThreatKind kind) {
        return switch (kind) {
            case SMALL_ARMS, KINETIC -> DamageCategory.BALLISTIC;
            case HE, HEAT, TANDEM_HEAT -> DamageCategory.EXPLOSION;
        };
    }

    @Nullable
    static List<HitboxPart> parts(DamageSource source) {
        StrikeContext strike = StrikeContext.of(source);
        return strike == null ? null : strike.parts();
    }
}
