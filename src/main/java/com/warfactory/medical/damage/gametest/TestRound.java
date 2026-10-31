package com.warfactory.medical.damage.gametest;

import com.norwood.ahf.hit.AhfHitSource;
import com.norwood.ahf.part.HitboxPart;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Round-shaped damage: AHF strike segment at the current tick; msgId "bullet" => BALLISTIC. */
final class TestRound extends DamageSource implements AhfHitSource {

    private final Vec3 from;
    private final Vec3 to;
    private final long gameTime;
    private final @Nullable HitboxPart part;

    TestRound(GameTestHelper helper, Vec3 from, Vec3 to, @Nullable HitboxPart part) {
        super(helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(DamageTypes.GENERIC));
        this.from = from;
        this.to = to;
        this.gameTime = helper.getLevel().getGameTime();
        this.part = part;
    }

    @Override
    public String getMsgId() {
        return "bullet";
    }

    @Override
    public Vec3 hitFrom() {
        return from;
    }

    @Override
    public Vec3 hitTo() {
        return to;
    }

    @Override
    public long hitGameTime() {
        return gameTime;
    }

    @Override
    public @Nullable HitboxPart hitPart() {
        return part;
    }
}
