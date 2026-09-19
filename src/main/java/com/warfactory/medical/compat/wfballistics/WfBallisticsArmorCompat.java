package com.warfactory.medical.compat.wfballistics;

import com.warfactory.medical.core.damage.ArmorEvaluation;
import com.warfactory.medical.core.limb.LimbType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Armour resolution, handed to WF-Ballistics when it is there.
 *
 * <p>The point is that there is <b>one</b> reduction step with one owner. Two subsystems each doing
 * part of the job is worse than either doing all of it, and until this existed that was the state of
 * things: WF-Ballistics reduced the damage from a summed DT/DR over every worn piece, and this mod
 * separately rolled dice for blocked/partial/full off vanilla's armour attributes. The same shot
 * could produce two different injuries on two identical hits.
 *
 * <p>With the mod present, this one knows which limb was hit and asks about the piece on that limb,
 * and what comes back is deterministic: stopped by the threshold reads as BLOCKED, heavily reduced
 * reads as PARTIAL, barely touched reads as FULL. Without it, {@link ArmorEvaluation} is unchanged
 * and still rolls.
 *
 * <p>Nothing in this class names a WF-Ballistics type, so loading it in a pack without the mod
 * resolves nothing. Everything that does is in {@link ArmorBridge}, which is only ever reached behind
 * {@link #isLoaded()}.
 */
public final class WfBallisticsArmorCompat {

    private static final String MOD_ID = "wfballistics";

    private static boolean loaded;

    private WfBallisticsArmorCompat() {
    }

    /**
     * Looks for the mod and, if it is there, tells it this one owns players.
     *
     * <p>That claim is what stops both mods reducing the same hit. It also makes this mod responsible
     * for every player hit, including the ones its medical model declines, which is what
     * {@link #resolveWhole} is for.
     */
    public static void init() {
        try {
            loaded = ModList.get().isLoaded(MOD_ID);
        } catch (Throwable ignored) {
            // No mod list at all: a unit test, or a bootstrap that never got that far. Not present.
            loaded = false;
        }
        if (loaded) {
            ArmorBridge.claim();
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Whether WF-Ballistics has been told this mod answers for players. False without the mod, and
     * false before {@link #init}. Exposed so a test can pin the thing that keeps the two from both
     * reducing the same hit.
     */
    public static boolean claimedPlayers() {
        return loaded && ArmorBridge.claimed();
    }

    /**
     * Resolves one limb's armour against one hit.
     *
     * @param applyWear whether the pieces involved should be charged for what they absorbed. True for
     *                  the limb that was actually hit; false for the further limbs a penetrating round
     *                  carries on into, since a slot reached twice by one bullet is still one impact
     * @return the outcome and what reached the body, or null when WF-Ballistics is absent
     */
    @Nullable
    public static Resolved resolve(LivingEntity victim, LimbType limb, DamageSource source,
                                   float amount, boolean applyWear) {
        if (!loaded || victim == null || limb == null) {
            return null;
        }
        return ArmorBridge.resolve(victim, limb, source, amount, applyWear);
    }

    /**
     * Resolves against everything worn, weighted by how much of a body each piece covers.
     *
     * <p>For hits this mod's own handler declines: a player it is configured to leave alone still has
     * armour on, and this mod claimed the right to answer for it.
     */
    public static void resolveWhole(LivingIncomingDamageEvent event) {
        if (loaded) {
            ArmorBridge.resolveWhole(event);
        }
    }

    /**
     * @param outcome what the armour did, on this mod's own three-value scale, so {@code TraumaGenerator}
     *                does not move
     * @param through damage that reached the body, and so the wound energy
     */
    public record Resolved(ArmorEvaluation.Outcome outcome, float through) {
    }
}
