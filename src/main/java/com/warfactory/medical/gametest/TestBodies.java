package com.warfactory.medical.gametest;

import com.mojang.authlib.GameProfile;
import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
import com.warfactory.medical.compat.TaczCompat;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.trauma.Trauma;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared fixtures for the in-world suite. Not a {@code @GameTestHolder}, so the scanner ignores it.
 *
 * <p>The reason this exists rather than each test rolling its own player: a test-constructed
 * {@code ServerPlayer} cannot be hurt, and it fails <em>silently</em>. Two independent gates sit in front
 * of {@code LivingIncomingDamageEvent}, so a damage test written on a plain {@link FakePlayer} passes
 * while never invoking a line of WFMedical. Both fixes belong in one place.
 */
public final class TestBodies {

    private TestBodies() {
    }

    /**
     * A server player that can actually be damaged.
     *
     * <p>{@link FakePlayer} hard-codes two refusals, because it is meant for machines acting on the world
     * rather than for taking hits: {@code isInvulnerableTo} always returns {@code true}, and
     * {@code canHarmPlayer} always returns {@code false}. The second only bites player-versus-player
     * damage, so a suite can pass every environmental damage test and still never land a single melee hit.
     */
    public static final class Victim extends FakePlayer {
        private Victim(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        /**
         * The rule {@link ServerPlayer} and {@link net.minecraft.world.entity.player.Player} would apply,
         * restored. Deliberately not a bare {@code true}: the server PvP flag and the team policy are real
         * gates in front of the mod, and a test that skips them stops resembling a fight between players.
         */
        @Override
        public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
            MinecraftServer server = getServer();
            if (server == null || !server.isPvpAllowed()) {
                return false;
            }
            net.minecraft.world.scores.Team team = getTeam();
            return team == null || !team.isAlliedTo(other.getTeam()) || team.isAllowFriendlyFire();
        }
    }

    /** A damageable survival player at (1, 1, 1) in the test structure, facing +Z with zero rotation. */
    public static Victim victim(GameTestHelper helper) {
        return victim(helper, new BlockPos(1, 1, 1), 0.0F);
    }

    /** As {@link #victim(GameTestHelper)}, at a chosen relative position and yaw. */
    public static Victim victim(GameTestHelper helper, BlockPos relative, float yaw) {
        Victim v = new Victim(helper.getLevel(), new GameProfile(UUID.randomUUID(), "wfmed_victim"));
        BlockPos abs = helper.absolutePos(relative);
        v.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, yaw, 0.0F);
        face(v, yaw);
        v.setPose(Pose.STANDING);
        // Creative/spectator is skipped outright by onLivingHurt when effectImmuneInCreative is on.
        v.setGameMode(GameType.SURVIVAL);
        v.setHealth(v.getMaxHealth());
        v.invulnerableTime = 0;
        // A fresh ServerPlayer starts with 60 ticks of spawn invulnerability, and ServerPlayer.hurt
        // refuses everything that does not bypass invulnerability while it lasts. It only decays in
        // ServerPlayer.tick, which FakePlayer no-ops -- so without this every hurt() silently returns
        // false and the test passes by never testing anything. (Widened by our access transformer.)
        v.spawnInvulnerableTime = 0;
        return v;
    }

    /**
     * An attacker standing {@code distance} blocks in front of {@code target} and looking straight at it.
     *
     * <p>Position matters for melee: {@code HitGeometry.shouldRejectGap} traces the attacker's eye ray out
     * to {@code meleeReach} and throws the hit away as a whiff if it clears every limb box. Two players
     * constructed at the same spot both look along +Z, so the ray never crosses the victim and every melee
     * test silently measures a rejected swing instead of a landed one.
     *
     * <p>PvP is enabled here as well. A gametest server starts with it off, and {@code ServerPlayer.hurt}
     * answers {@code false} to any player-dealt damage before the incoming-damage event is fired -- so a
     * player-vs-player test on a default gametest server never reaches a line of WFMedical, and the
     * failure looks exactly like a hit-registration bug.
     */
    public static Victim attacker(GameTestHelper helper, Victim target, double distance) {
        helper.getLevel().getServer().setPvpAllowed(true);
        Victim a = victim(helper);
        Vec3 eye = target.getEyePosition();
        // Stand back along the direction the target is facing, then look back at them.
        Vec3 facing = target.getLookAngle();
        Vec3 stand = target.position().subtract(facing.scale(distance));
        a.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        Vec3 toTarget = eye.subtract(a.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(toTarget.y,
                Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z))));
        face(a, yaw);
        a.setXRot(pitch);
        return a;
    }

    /**
     * Point every rotation field at {@code yaw}. Body rotation is the one that matters for the rig: set
     * only {@code yRot} and the limb boxes stay where they were, so a yaw sweep becomes a no-op.
     */
    public static void face(Victim v, float yaw) {
        v.setYRot(yaw);
        v.setXRot(0.0F);
        v.setYHeadRot(yaw);
        v.yBodyRot = yaw;
        v.yBodyRotO = yaw;
        v.yHeadRot = yaw;
        v.yHeadRotO = yaw;
    }

    public static MedicalProfile profileOf(GameTestHelper helper, Victim v) {
        IMedicalData data = MedicalAttachments.get(v);
        if (data == null) {
            helper.fail("the victim has no medical attachment -- MedicalAttachments.isEligible "
                    + "should be true for any Player");
        }
        return data.getProfile();
    }

    public static List<Trauma> allTraumas(MedicalProfile profile) {
        List<Trauma> out = new ArrayList<>();
        for (LimbType limb : LimbType.VALUES) {
            out.addAll(profile.limb(limb).getTraumas());
        }
        return out;
    }

    /** Compact per-limb wound count, for failure messages. */
    public static String describe(MedicalProfile profile) {
        StringBuilder sb = new StringBuilder();
        for (LimbType limb : LimbType.VALUES) {
            int n = profile.limb(limb).getTraumas().size();
            if (n > 0) {
                sb.append(limb).append('=').append(n).append(' ');
            }
        }
        return sb.length() == 0 ? "(no traumas)" : sb.toString().trim();
    }

    /** Whether TACZ is on this run's mod list, without referencing a TACZ class. */
    public static boolean taczPresent() {
        return LoadingModList.get().getModFileById(TaczCompat.MOD_ID) != null;
    }
}
