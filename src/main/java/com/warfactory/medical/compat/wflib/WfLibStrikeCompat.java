package com.warfactory.medical.compat.wflib;

import com.norwood.ahf.part.HitboxPart;
import com.warfactory.medical.core.damage.DamageCategory;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** WFLib strikes ({@code StrikeContext}): category from threat, limbs from the parts WFLib classified. */
public final class WfLibStrikeCompat {

    private WfLibStrikeCompat() {
    }

    /** @return null: WFLib absent or not a strike */
    @Nullable
    public static DamageCategory category(DamageSource source) {
        return WfLibArmorCompat.isLoaded() ? StrikeBridge.category(source) : null;
    }

    /** @return entry-ordered parts; null: WFLib absent, not a strike, or no part model */
    @Nullable
    public static List<HitboxPart> parts(DamageSource source) {
        return WfLibArmorCompat.isLoaded() ? StrikeBridge.parts(source) : null;
    }
}
