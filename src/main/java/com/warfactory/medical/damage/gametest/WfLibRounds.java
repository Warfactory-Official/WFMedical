package com.warfactory.medical.damage.gametest;

import com.norwood.ahf.hit.AhfHitSource;
import com.norwood.ahf.part.HitboxPart;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.damage.api.StrikeContext;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.smoking.api.event.CigaretteSmokedEvent;
import com.wf.wflib.warhead.api.Warheads;
import com.warfactory.medical.WFMedical;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.smoking.api.SmokingContent;

/** WFLib fixtures shared by the strike, stress and artefact suites. */
@EventBusSubscriber(modid = WFMedical.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WfLibRounds {

    /** Straight, no drag or gravity, 4 damage, stops on bodies, headshot x2. */
    private static final ResourceLocation ROUND = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "test_round");
    public static final float DAMAGE = 4.0F;
    /** {@link #strike} msgId: no heuristic match. */
    public static final String MSG_ID = "wfmedical_test_strike";

    /** Mod construction; gametest runs only. */
    public static void request() {
        if (gametest()) {
            WFSystems.require(WFMedical.MOD_ID, "wflib:smoking", "wflib:anomaly");
            WFLibContent.armor(WFMedical.MOD_ID + " tests", "plate_carrier");
        }
    }

    /** Setup runs before the load-complete preset freeze; gametest runs only. */
    @SubscribeEvent
    public static void registerFixtures(FMLCommonSetupEvent event) {
        if (!gametest()) {
            return;
        }
        KineticPresets.registerItemless(KineticPreset.builder(ROUND, null, Warheads.rl("inert"))
                .speed(4.0).drag(0.0).gravity(0.0).life(40).impactDamage(DAMAGE).headshot(2.0).caliber(5.56)
                .noChunkLoading().build());
    }

    private WfLibRounds() {
    }

    private static boolean gametest() {
        String namespaces = System.getProperty("neoforge.enabledGameTestNamespaces");
        return namespaces != null && List.of(namespaces.split(",")).contains(WFMedical.MOD_ID);
    }

    /** Launch at {@code at} moving {@code velocity}, one live step, destroy. */
    public static void fire(ServerLevel level, Vec3 at, Vec3 velocity) {
        long key = Rounds.launch(level, KineticPresets.get(ROUND), at, velocity, null, null);
        Rounds.step(level, key);
        Rounds.destroy(level, key);
    }

    /** What worn artefacts do once a second: WFLib's healer (negative drains) and clotter (negative thins). */
    public static void artifactTick(ServerPlayer player, float hp, float bleedingPerSecond) {
        com.wf.wflib.artifact.api.Artifacts.HEALER.get().accept(player, hp);
        com.wf.wflib.artifact.api.Artifacts.CLOTTER.get().accept(player, bleedingPerSecond);
    }

    /** WFLib psy (an anomaly's telepatic hit) at {@code player}; whether something took it. */
    public static boolean psy(ServerPlayer player, float units) {
        return NeoForge.EVENT_BUS.post(new com.wf.wflib.anomaly.api.event.PsyExposureEvent(player, units)).handled();
    }

    /** {@code player} smoked a cigarette to the end. */
    public static void smoked(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new CigaretteSmokedEvent(player, new ItemStack(SmokingContent.CIGARETTE.get())));
    }

    public static List<String> threatKinds() {
        List<String> out = new ArrayList<>();
        for (ThreatKind kind : ThreatKind.values()) {
            out.add(kind.name());
        }
        return out;
    }

    /** Strike of {@code kind} (120 mm: KINETIC stays KINETIC) along {@code from -> to} now, WFLib-classified as {@code parts}. */
    public static DamageSource strike(ServerLevel level, String kind, Vec3 from, Vec3 to, @Nullable List<HitboxPart> parts) {
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
