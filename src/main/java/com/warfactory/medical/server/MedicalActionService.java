package com.warfactory.medical.server;

import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.InjectableItem;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.item.ModItems;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.network.MedicalSyncPacket;
import com.warfactory.medical.network.TreatmentTargetInfoPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server-authoritative tracking of timed active treatments. State is transient (never NBT); physiology is
 * only mutated on completion via {@link TreatmentService#applyTargeted}; independent of the vanilla
 * right-click channel (now disabled on the item) so the two never double-apply.
 *
 * <p>A treatment has an ACTOR (always the {@link ServerPlayer} performing it — the timer, interruption and
 * item-consumption live on them) and a TARGET (self by default, or another player / downed body the medic
 * right-clicked). The completion mutation lands on the target's profile.</p>
 */
public final class MedicalActionService {

    /**
     * Max distance² a medic may reach to treat another entity (matches a generous interaction reach).
     */
    private static final double REACH_SQR = 6.0D * 6.0D;

    private MedicalActionService() {
    }

    /**
     * Begin a timed treatment for {@code actor} using the medical item identified by {@code itemId}, targeting
     * {@code limb} (nullable = auto-pick) on the entity {@code targetId} ({@code -1} = the actor themself).
     * Validates the item, inventory presence, the actor's hands, that no treatment is already running, reach to
     * a non-self target, and creative immunity.
     *
     * @return {@code true} when a treatment was started (and an {@link ActiveTreatmentPacket} sent).
     */
    public static boolean start(ServerPlayer actor, ResourceLocation itemId, LimbType limb, int targetId) {
        if (actor == null || itemId == null) {
            return false;
        }
        if ((actor.isCreative() || actor.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }
        // A medic who cannot use their hands (unconscious / both arms disabled) cannot start a treatment.
        if (MedicalState.isHandsDisabled(actor)) {
            return false;
        }
        IMedicalData actorData = MedicalCapabilities.get(actor);
        if (actorData == null) {
            return false;
        }
        MedicalProfile actorProfile = actorData.getProfile();
        if (actorProfile.hasActiveTreatment()) {
            return false;
        }

        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (!(item instanceof MedicalItem medical)) {
            return false;
        }
        // An item is a valid active-action source if it carries a Treatment OR injects a Substance.
        Treatment treatment = medical.getTreatment();
        boolean injectable = item instanceof InjectableItem inj && inj.getSubstance() != null;
        if (treatment == null && !injectable) {
            return false;
        }
        int slot = slotToLock(actor, item);
        if (slot < 0) {
            return false; // actor is not carrying the item they asked to use
        }

        // Resolve the target: injectables are systemic and self-only; otherwise -1/self or a validated other.
        LivingEntity target;
        IMedicalData targetData;
        if (injectable || targetId < 0 || targetId == actor.getId()) {
            target = actor;
            targetData = actorData;
        } else {
            target = resolveOtherTarget(actor, targetId);
            if (target == null) {
                return false; // stale / out of reach
            }
            targetData = medicalDataOf(target);
            if (targetData == null) {
                return false;
            }
        }

        // Tourniquet is applied INSTANTLY (not a timed treatment): toggle it onto the selected appendage.
        if (treatment != null && treatment.action() == TreatmentAction.APPLY_TOURNIQUET) {
            return applyTourniquet(actor, slot, target, targetData, limb, item);
        }

        // Resolve the channel duration and the (presentation-only) action for the client overlay.
        int totalTicks;
        TreatmentAction action;
        if (treatment != null) {
            totalTicks = treatment.useDurationTicks();
            action = treatment.action();
        } else {
            totalTicks = ((InjectableItem) item).getSubstance().useDurationTicks();
            // Injectables carry no TreatmentAction; REDUCE_PAIN is the closest presentation label and keeps the
            // overlay packet's non-null-action invariant. Completion resolves the item, not this action.
            action = TreatmentAction.REDUCE_PAIN;
        }
        if (totalTicks < 1) {
            totalTicks = 1;
        }
        long startGameTime = actor.level().getGameTime();
        int recordedTarget = (target == actor) ? -1 : target.getId();
        actorProfile.setActiveTreatment(action, limb, itemId.toString(), totalTicks, startGameTime,
                recordedTarget, slot);

        MedicalNetworking.sendActiveTreatment(actor, new ActiveTreatmentPacket(
                true, action, limb, totalTicks, startGameTime, recordedTarget));
        return true;
    }

    /**
     * Reply to a medic who right-clicked ANOTHER entity with a localized treatment: gather that target's
     * per-limb summaries (after a fresh recompute) and send them back so the client can open the limb wheel.
     * Validates the actor's hands, item and reach; no state is mutated.
     */
    public static void requestTargetInfo(ServerPlayer actor, int targetId, ResourceLocation itemId) {
        if (actor == null || itemId == null || targetId < 0) {
            return;
        }
        if (MedicalState.isHandsDisabled(actor)) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (!(item instanceof MedicalItem) || findItemSlot(actor, item) < 0) {
            return;
        }
        LivingEntity target = resolveOtherTarget(actor, targetId);
        if (target == null) {
            return;
        }
        IMedicalData data = medicalDataOf(target);
        if (data == null) {
            return;
        }
        // A live player is engine-ticked, but a downed/persistent body's cached aggregates may be stale, so
        // recompute before snapshotting to guarantee the wheel reflects the target's current injuries.
        MedicalProfile profile = data.getProfile();
        profile.recompute(MedicalConfig.toPhysiologyParams());
        MedicalSyncPacket snap = MedicalSyncPacket.fromProfile(profile);
        MedicalNetworking.sendTargetInfo(actor, new TreatmentTargetInfoPacket(target.getId(), itemId, snap.limbs()));
    }

    /**
     * Advance one engine pass; completes when elapsed >= totalTicks (applies the treatment to the recorded
     * target, consumes the actor's item, notifies the actor) or cancels if the item was swapped away or the
     * target has vanished / moved out of reach.
     */
    public static void tick(ServerPlayer actor, MedicalProfile actorProfile, long nowTick) {
        if (actor == null || actorProfile == null || !actorProfile.hasActiveTreatment()) {
            return;
        }
        if (nowTick - actorProfile.getActiveStartGameTime() < actorProfile.getActiveTotalTicks()) {
            return; // still in progress
        }

        ResourceLocation itemId = ResourceLocation.tryParse(actorProfile.getActiveItemId());
        Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
        boolean injectable = item instanceof InjectableItem inj && inj.getSubstance() != null;
        if (!(item instanceof MedicalItem medical) || (medical.getTreatment() == null && !injectable)) {
            cancel(actor, "invalid_item");
            return;
        }
        // "Cannot change items while applying": the item must still sit in the exact slot it started in.
        int slot = actorProfile.getActiveSlot();
        Inventory inv = actor.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize() || inv.getItem(slot).getItem() != item) {
            cancel(actor, "item_changed");
            return;
        }

        // Resolve the target again — it may have logged out / moved out of reach mid-treatment.
        int targetId = actorProfile.getActiveTargetId();
        LivingEntity target;
        IMedicalData targetData;
        if (targetId < 0 || targetId == actor.getId()) {
            target = actor;
            targetData = MedicalCapabilities.get(actor);
        } else {
            target = resolveOtherTarget(actor, targetId);
            targetData = target == null ? null : medicalDataOf(target);
        }
        if (targetData == null) {
            cancel(actor, "target_gone");
            return;
        }

        boolean applied;
        if (injectable) {
            // Injection is systemic and self-only (target is always the actor for injectables).
            applied = SubstanceService.inject(actor, ((InjectableItem) item).getSubstance());
        } else {
            applied = TreatmentService.applyTargeted(targetData, actor.level().getGameTime(),
                    medical.getTreatment(), actorProfile.getActiveLimb());
        }
        if (applied) {
            if (!actor.getAbilities().instabuild) {
                inv.getItem(slot).shrink(1);
            }
            // Another target needs an explicit push (its own engine pass may be a cadence behind); a self
            // target is reconciled by the surrounding engine tick that invoked us.
            if (target != actor) {
                syncTarget(target, targetData);
            }
        }

        actorProfile.clearActiveTreatment();
        MedicalNetworking.sendActiveTreatment(actor, ActiveTreatmentPacket.inactive());
    }

    /**
     * Cancel any running treatment and tell the actor's client to hide the progress overlay.
     */
    public static void cancel(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        if (!profile.hasActiveTreatment()) {
            return;
        }
        profile.clearActiveTreatment();
        MedicalNetworking.sendActiveTreatment(player, ActiveTreatmentPacket.inactive());
    }

    /**
     * Instantly apply a tourniquet (arm/leg only, one per limb) onto {@code target}: slows bleeding without
     * treating wounds. Consumes from the actor's locked slot and re-syncs the target + broadcasts the worn mask.
     */
    private static boolean applyTourniquet(ServerPlayer actor, int actorSlot, LivingEntity target,
                                           IMedicalData targetData, LimbType limb, Item item) {
        if (limb == null || !(limb.isArm() || limb.isLeg())) {
            return false;
        }
        MedicalProfile profile = targetData.getProfile();
        Limb targetLimb = profile.limb(limb);
        if (targetLimb.hasTourniquet()) {
            return false; // one tourniquet per appendage
        }
        targetLimb.setTourniquet(true);
        profile.markDirty();
        targetData.bumpRevision();
        if (!actor.getAbilities().instabuild) {
            ItemStack held = actor.getInventory().getItem(actorSlot);
            if (!held.isEmpty() && held.getItem() == item) {
                held.shrink(1);
            } else {
                int s = findItemSlot(actor, item);
                if (s >= 0) {
                    actor.getInventory().getItem(s).shrink(1);
                }
            }
        }
        syncTarget(target, targetData);
        // Broadcast the worn-tourniquet mask to trackers + self so the worn model renders on the target.
        MedicalNetworking.broadcastTourniquets(target, profile);
        return true;
    }

    /**
     * Remove the tourniquet from {@code limb} on the requesting player (UI-driven, no item consumed); no-op if
     * none present. Self-only: the remove-tourniquet UI acts on the player's own worn tourniquets.
     */
    public static boolean removeTourniquet(ServerPlayer player, LimbType limb) {
        if (player == null || limb == null) {
            return false;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        Limb target = profile.limb(limb);
        if (!target.hasTourniquet()) {
            return false;
        }
        target.setTourniquet(false);
        profile.markDirty();
        data.bumpRevision();
        MedicalEngine.resync(player);
        MedicalNetworking.broadcastTourniquets(player, profile);
        recoverTourniquet(player);
        return true;
    }

    /**
     * On a successful tourniquet removal, roll {@link MedicalConfig#tourniquetRecoveryChance} to return the
     * tourniquet item to the remover; if their inventory is full it drops at their feet, and on a failed roll it
     * is lost. Skipped in creative (the item is free there anyway).
     */
    private static void recoverTourniquet(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        if (player.getRandom().nextFloat() >= (float) MedicalConfig.tourniquetRecoveryChance()) {
            return; // lost
        }
        Item item = ModItems.TOURNIQUET.get();
        if (item == null) {
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Reconcile a treated target's vanilla body + clients after a completed treatment / tourniquet. A live
     * player gets a full engine re-sync; a downed body has its derived health re-stamped in place.
     */
    private static void syncTarget(LivingEntity target, IMedicalData data) {
        if (target instanceof ServerPlayer sp) {
            MedicalEngine.resync(sp);
        } else {
            DerivedStats stats = data.getProfile().recompute(MedicalConfig.toPhysiologyParams());
            MedicalEffects.applyToBody(target, stats);
        }
    }

    /**
     * Resolve a non-self treatment target by entity id: must be a live {@link LivingEntity} within reach.
     * Returns {@code null} when stale / dead / too far.
     */
    private static LivingEntity resolveOtherTarget(ServerPlayer actor, int targetId) {
        Entity e = actor.level().getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            return null;
        }
        if (actor.distanceToSqr(le) > REACH_SQR) {
            return null;
        }
        return le;
    }

    /**
     * @return the medical data of {@code entity} (player or persistent body), or {@code null} when absent.
     */
    private static IMedicalData medicalDataOf(LivingEntity entity) {
        return entity.getCapability(MedicalCapabilities.MEDICAL).resolve().orElse(null);
    }

    /**
     * The slot to lock for the treatment: the currently-held hotbar slot when it holds {@code item} (so the
     * client hotbar lock lines up with the hand), else the first inventory slot holding it.
     */
    private static int slotToLock(ServerPlayer actor, Item item) {
        Inventory inv = actor.getInventory();
        // A held hotbar slot (0..8) is preferred so the client hotbar lock lines up with the acting hand.
        if (inv.selected >= 0 && inv.selected < 9) {
            ItemStack sel = inv.getItem(inv.selected);
            if (!sel.isEmpty() && sel.getItem() == item) {
                return inv.selected;
            }
        }
        return findItemSlot(actor, item);
    }

    /**
     * @return the first inventory slot holding {@code item}, or {@code -1} when the player has none.
     */
    private static int findItemSlot(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }
}
