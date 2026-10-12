package com.warfactory.medical.gametest;

import com.norwood.ahf.hit.AhfHitSource;
import com.norwood.ahf.part.HitboxPart;
import com.wf.wflib.api.StrikeContext;
import com.wf.wflib.api.Threat;
import com.wf.wflib.api.ThreatKind;
import com.wf.wflib.kinetic.KineticPreset;
import com.wf.wflib.kinetic.KineticPresetRegistry;
import com.wf.wflib.round.Rounds;
import com.wf.wflib.warhead.WarheadRegistry;
import com.warfactory.medical.WFMedical;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every WFLib type the strike suite needs. Signatures name none: {@link WfLibStrikeGameTest} must load
 * without WFLib. Call only while {@code WfLibArmorCompat.isLoaded()}.
 */
final class WfLibRounds {

    /** Straight, no drag or gravity, 4 damage, stops on bodies, headshot x2. */
    private static final ResourceLocation ROUND = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "test_round");
    static final float DAMAGE = 4.0F;
    /** {@link #strike} msgId: no heuristic match. */
    static final String MSG_ID = "wfmedical_test_strike";

    static {
        KineticPresetRegistry.register(KineticPreset.builder(ROUND, null, WarheadRegistry.rl("inert"))
                .speed(4.0).drag(0.0).gravity(0.0).life(40).impactDamage(DAMAGE).headshot(2.0).caliber(5.56)
                .noChunkLoading().build());
    }

    private WfLibRounds() {
    }

    /** Launch at {@code at} moving {@code velocity}, one live step, destroy. */
    static void fire(ServerLevel level, Vec3 at, Vec3 velocity) {
        long key = Rounds.launch(level, KineticPresetRegistry.get(ROUND), at, velocity, null, null);
        Rounds.step(level, key);
        Rounds.destroy(level, key);
    }

    static List<String> threatKinds() {
        List<String> out = new ArrayList<>();
        for (ThreatKind kind : ThreatKind.values()) {
            out.add(kind.name());
        }
        return out;
    }

    /** Strike of {@code kind} (120 mm: KINETIC stays KINETIC) along {@code from -> to} now, WFLib-classified as {@code parts}. */
    static DamageSource strike(ServerLevel level, String kind, Vec3 from, Vec3 to, @Nullable List<HitboxPart> parts) {
        return new Strike(level, new Threat(ThreatKind.valueOf(kind), 120.0F, 0.0F), from, to, parts);
    }

    private static final class Strike extends DamageSource implements StrikeContext, AhfHitSource {
        private final Threat threat;
        private final Vec3 from;
        private final Vec3 to;
        private final long gameTime;
        private final @Nullable List<HitboxPart> parts;

        Strike(ServerLevel level, Threat threat, Vec3 from, Vec3 to, @Nullable List<HitboxPart> parts) {
            super(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC));
            this.threat = threat;
            this.from = from;
            this.to = to;
            this.gameTime = level.getGameTime();
            this.parts = parts;
        }

        @Override
        public String getMsgId() {
            return MSG_ID;
        }

        @Override
        public Threat threat() {
            return threat;
        }

        @Override
        public Vec3 position() {
            return from;
        }

        @Override
        public Vec3 velocity() {
            return to.subtract(from);
        }

        @Override
        public Object key() {
            return this;
        }

        @Override
        public @Nullable Entity shooter() {
            return null;
        }

        @Override
        public @Nullable List<HitboxPart> parts() {
            return parts;
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
            return parts == null ? null : parts.get(0);
        }
    }
}
