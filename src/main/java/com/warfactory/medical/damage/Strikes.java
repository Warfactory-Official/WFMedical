package com.warfactory.medical.damage;

import com.norwood.ahf.part.HitboxPart;
import com.wf.wflib.damage.api.StrikeContext;
import com.wf.wflib.damage.api.ThreatKind;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Strikes {

    private Strikes() {
    }

    /** @return null: not a strike */
    @Nullable
    public static DamageCategory category(DamageSource source) {
        StrikeContext strike = StrikeContext.of(source);
        return strike == null ? null : category(strike.threat().kind());
    }

    public static DamageCategory category(ThreatKind kind) {
        return switch (kind) {
            case SMALL_ARMS, KINETIC -> DamageCategory.BALLISTIC;
            case HE, HEAT, TANDEM_HEAT -> DamageCategory.EXPLOSION;
        };
    }

    /** @return entry-ordered parts; null: not a strike, or no part model */
    @Nullable
    public static List<HitboxPart> parts(DamageSource source) {
        StrikeContext strike = StrikeContext.of(source);
        return strike == null ? null : strike.parts();
    }
}
