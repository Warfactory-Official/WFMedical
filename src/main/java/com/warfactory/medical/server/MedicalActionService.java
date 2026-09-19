package com.warfactory.medical.server;

import com.warfactory.medical.api.MedicalState;
import com.warfactory.medical.attachment.IMedicalData;
import com.warfactory.medical.attachment.MedicalAttachments;
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
import com.warfactory.medical.network.TargetSheetInfoPacket;
import com.warfactory.medical.network.TreatmentTargetInfoPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MedicalActionService {

    /** Sentinel "item" for the item-less resuscitation channel. */
    private static final String RESUSCITATE_ID = "wfmedical:resuscitate";

    private MedicalActionService() {
    }

    public static boolean start(ServerPlayer actor, ResourceLocation itemId, LimbType limb, int targetId) {
        if (actor == null || itemId == null) {
            return false;
        }
        if ((actor.isCreative() || actor.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }
        if (MedicalState.isHandsDisabled(actor)) {
            return false;
        }
        IMedicalData actorData = MedicalAttachments.get(actor);
        if (actorData == null) {
            return false;
        }
        MedicalProfile actorProfile = actorData.getProfile();
        if (actorProfile.hasActiveTreatment()) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (!(item instanceof MedicalItem medical)) {
            return false;
        }
        Treatment treatment = medical.getTreatment();
        boolean injectable = item instanceof InjectableItem inj && inj.getSubstance() != null;
        if (treatment == null && !injectable) {
            return false;
        }
        int slot = slotToLock(actor, item);
        if (slot < 0) {
            return false;
        }

        LivingEntity target;
        IMedicalData targetData;
        if (medical.isSelfOnly() || targetId < 0 || targetId == actor.getId()) {
            // Self-only items (oral meds) always dose the user, regardless of who they were aiming at.
            target = actor;
            targetData = actorData;
        } else {
            target = resolveOtherTarget(actor, targetId);
            if (target == null) {
                actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.out_of_reach"), true);
                return false;
            }
            targetData = medicalDataOf(target);
            if (targetData == null) {
                return false;
            }
            if (isBeingTreatedByAnother(actor, target)) {
                actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.busy"), true);
                return false;
            }
        }

        if (treatment != null && treatment.action() == TreatmentAction.APPLY_TOURNIQUET) {
            return applyTourniquet(actor, slot, target, targetData, limb, item);
        }

        int totalTicks;
        TreatmentAction action;
        if (treatment != null) {
            totalTicks = treatment.useDurationTicks();
            action = treatment.action();
        } else {
            totalTicks = ((InjectableItem) item).getSubstance().useDurationTicks();
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

    public static void requestTargetInfo(ServerPlayer actor, int targetId, ResourceLocation itemId) {
        if (actor == null || itemId == null) {
            return;
        }
        if (MedicalState.isHandsDisabled(actor)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (!(item instanceof MedicalItem medical) || findItemSlot(actor, item) < 0) {
            return;
        }
        LivingEntity target;
        IMedicalData data;
        if (targetId < 0 || targetId == actor.getId()) {
            target = actor;
            data = MedicalAttachments.get(actor);
        } else {
            target = resolveOtherTarget(actor, targetId);
            data = target == null ? null : medicalDataOf(target);
        }
        if (target == null || data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();
        if (profile.isDirty()) {
            syncTarget(target, data);
        }
        MedicalSyncPacket snap = MedicalSyncPacket.fromProfile(profile);
        int mask = TreatmentService.treatableMask(profile, medical.getTreatment());
        int replyTargetId = (target == actor) ? -1 : target.getId();
        MedicalNetworking.sendTargetInfo(actor,
                new TreatmentTargetInfoPacket(replyTargetId, itemId, snap.limbs(), mask));
    }

    public static void requestTargetSheet(ServerPlayer actor, int targetId) {
        if (actor == null || targetId < 0 || targetId == actor.getId()) {
            return;
        }
        if (MedicalState.isHandsDisabled(actor)) {
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
        MedicalProfile profile = data.getProfile();
        if (profile.isDirty()) {
            syncTarget(target, data);
        }
        MedicalNetworking.sendTargetSheet(actor, new TargetSheetInfoPacket(
                target.getId(), MedicalSyncPacket.fromProfile(profile), MedicalNetworking.tourniquetMask(profile)));
    }

    public static void tick(ServerPlayer actor, MedicalProfile actorProfile, long nowTick) {
        if (actor == null || actorProfile == null || !actorProfile.hasActiveTreatment()) {
            return;
        }
        if (nowTick - actorProfile.getActiveStartGameTime() < actorProfile.getActiveTotalTicks()) {
            return;
        }

        // Resuscitation is channelled through the same progress bar but consumes no item, so it has to
        // settle before the item/slot validation below.
        if (actorProfile.getActiveAction() == TreatmentAction.RESUSCITATE) {
            finishResuscitation(actor, actorProfile);
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(actorProfile.getActiveItemId());
        Item item = itemId == null ? null : BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        boolean injectable = item instanceof InjectableItem inj && inj.getSubstance() != null;
        if (!(item instanceof MedicalItem medical) || (medical.getTreatment() == null && !injectable)) {
            cancel(actor, "invalid_item");
            return;
        }
        int slot = actorProfile.getActiveSlot();
        Inventory inv = actor.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize() || inv.getItem(slot).getItem() != item) {
            cancel(actor, "item_changed");
            return;
        }

        int targetId = actorProfile.getActiveTargetId();
        LivingEntity target;
        IMedicalData targetData;
        if (targetId < 0 || targetId == actor.getId()) {
            target = actor;
            targetData = MedicalAttachments.get(actor);
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
            applied = SubstanceService.inject(actor, target, targetData, ((InjectableItem) item).getSubstance());
        } else {
            applied = TreatmentService.applyTargeted(targetData, actor.level().getGameTime(),
                    medical.getTreatment(), actorProfile.getActiveLimb());
        }
        if (applied) {
            if (!actor.getAbilities().instabuild) {
                inv.getItem(slot).shrink(1);
            }
            if (target != actor) {
                syncTarget(target, targetData);
            }
            actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.applied"), true);
        } else {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.no_effect"), true);
        }

        actorProfile.clearActiveTreatment();
        MedicalNetworking.sendActiveTreatment(actor, ActiveTreatmentPacket.inactive());
    }

    public static void cancel(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(player);
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

    private static boolean applyTourniquet(ServerPlayer actor, int actorSlot, LivingEntity target,
                                           IMedicalData targetData, LimbType limb, Item item) {
        if (limb == null || !(limb.isArm() || limb.isLeg())) {
            return false;
        }
        MedicalProfile profile = targetData.getProfile();
        Limb targetLimb = profile.limb(limb);
        if (targetLimb.hasTourniquet()) {
            return false;
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
        MedicalNetworking.broadcastTourniquets(target, profile);
        return true;
    }

    /**
     * Begin manual resuscitation on a downed casualty. Needs no item and no limb: it is a whole-body action
     * whose success is decided at the end of the channel from how much blood the patient has left.
     */
    public static boolean startResuscitation(ServerPlayer actor, int targetId) {
        if (actor == null) {
            return false;
        }
        if ((actor.isCreative() || actor.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }
        if (MedicalState.isHandsDisabled(actor)) {
            return false;
        }
        IMedicalData actorData = MedicalAttachments.get(actor);
        if (actorData == null) {
            return false;
        }
        MedicalProfile actorProfile = actorData.getProfile();
        if (actorProfile.hasActiveTreatment()) {
            return false;
        }
        LivingEntity target = resolveOtherTarget(actor, targetId);
        if (target == null || target == actor) {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.out_of_reach"), true);
            return false;
        }
        IMedicalData targetData = medicalDataOf(target);
        if (targetData == null) {
            return false;
        }
        if (!targetData.getProfile().cached().unconscious()) {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.resuscitate.not_downed"), true);
            return false;
        }
        if (isBeingTreatedByAnother(actor, target)) {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.busy"), true);
            return false;
        }

        int totalTicks = Math.max(1, MedicalConfig.resuscitateDurationTicks());
        long startGameTime = actor.level().getGameTime();
        actorProfile.setActiveTreatment(TreatmentAction.RESUSCITATE, null, RESUSCITATE_ID, totalTicks,
                startGameTime, target.getId(), -1);
        MedicalNetworking.sendActiveTreatment(actor, new ActiveTreatmentPacket(
                true, TreatmentAction.RESUSCITATE, null, totalTicks, startGameTime, target.getId()));
        return true;
    }

    private static void finishResuscitation(ServerPlayer actor, MedicalProfile actorProfile) {
        int targetId = actorProfile.getActiveTargetId();
        actorProfile.clearActiveTreatment();
        MedicalNetworking.sendActiveTreatment(actor, ActiveTreatmentPacket.inactive());

        LivingEntity target = resolveOtherTarget(actor, targetId);
        IMedicalData targetData = target == null ? null : medicalDataOf(target);
        if (targetData == null) {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.treat.target_gone"), true);
            return;
        }
        MedicalProfile patient = targetData.getProfile();
        if (!patient.cached().unconscious()) {
            return;
        }
        // Nothing brings back a casualty the engine has hard-locked: an overdose has to be reversed and a
        // drowning has to be pulled out of the water first.
        if (patient.isAsphyxiaUnconscious() || patient.isOverdoseUnconscious()) {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.resuscitate.failed"), true);
            return;
        }

        if (actor.getRandom().nextFloat() >= resuscitateChance(patient)) {
            actor.displayClientMessage(Component.translatable("gui.wfmedical.resuscitate.failed"), true);
            return;
        }

        patient.setUnconsciousLatched(false);
        patient.setPainKoSince(0L);
        patient.setAdrenalineExhausted(false);
        patient.setReviveGraceUntilTick(actor.level().getGameTime()
                + Math.max(0, MedicalConfig.resuscitateGraceTicks()));
        patient.markDirty();
        syncTarget(target, targetData);
        actor.displayClientMessage(Component.translatable("gui.wfmedical.resuscitate.success"), true);
        if (target instanceof ServerPlayer patientPlayer) {
            patientPlayer.displayClientMessage(
                    Component.translatable("gui.wfmedical.resuscitate.revived"), true);
        }
    }

    /**
     * Odds a single attempt brings the casualty round. Driven by how much blood is left and killed off by
     * active haemorrhage, so the answer to a failed attempt is to stop the bleeding and replace volume, not
     * to keep pumping.
     */
    public static float resuscitateChance(MedicalProfile patient) {
        double maxBlood = patient.getMaxBloodMl();
        double loss = maxBlood <= 0.0D ? 0.0D : 1.0D - (patient.getBloodMl() / maxBlood);
        double best = MedicalConfig.bloodUnconsciousLossFraction();
        double worst = MedicalConfig.bloodDeathLossFraction();
        double span = worst - best;
        double t = span <= 0.0D ? 1.0D : (worst - loss) / span;
        t = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);

        double min = MedicalConfig.resuscitateChanceMin();
        double chance = min + (MedicalConfig.resuscitateChanceMax() - min) * t;

        double bleedRef = MedicalConfig.resuscitateBleedReference();
        if (bleedRef > 0.0D) {
            double bleed = patient.cached().totalBleeding() / bleedRef;
            chance *= 1.0D - (bleed < 0.0D ? 0.0D : (bleed > 1.0D ? 1.0D : bleed));
        }
        return (float) (chance < 0.0D ? 0.0D : (chance > 1.0D ? 1.0D : chance));
    }

    public static boolean removeTourniquet(ServerPlayer actor, LimbType limb, int targetId) {
        if (actor == null || limb == null) {
            return false;
        }
        LivingEntity target;
        IMedicalData data;
        if (targetId < 0 || targetId == actor.getId()) {
            target = actor;
            data = MedicalAttachments.get(actor);
        } else {
            if (MedicalState.isHandsDisabled(actor)) {
                return false;
            }
            target = resolveOtherTarget(actor, targetId);
            data = target == null ? null : medicalDataOf(target);
        }
        if (target == null || data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        Limb targetLimb = profile.limb(limb);
        if (!targetLimb.hasTourniquet()) {
            return false;
        }
        targetLimb.setTourniquet(false);
        profile.markDirty();
        data.bumpRevision();
        syncTarget(target, data);
        MedicalNetworking.broadcastTourniquets(target, profile);
        recoverTourniquet(actor);
        return true;
    }

    private static void recoverTourniquet(ServerPlayer player) {
        if (player.getAbilities().instabuild) {
            return;
        }
        if (player.getRandom().nextFloat() >= (float) MedicalConfig.tourniquetRecoveryChance()) {
            return;
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

    private static void syncTarget(LivingEntity target, IMedicalData data) {
        if (target instanceof ServerPlayer sp) {
            MedicalEngine.resync(sp);
        } else {
            DerivedStats stats = data.getProfile().recompute(MedicalConfig.toPhysiologyParams());
            MedicalEffects.applyToBody(target, stats);
        }
    }

    private static boolean isBeingTreatedByAnother(ServerPlayer actor, LivingEntity target) {
        MinecraftServer server = actor.getServer();
        if (server == null) {
            return false;
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == actor) {
                continue;
            }
            IMedicalData otherData = MedicalAttachments.get(other);
            if (otherData == null) {
                continue;
            }
            MedicalProfile otherProfile = otherData.getProfile();
            if (otherProfile.hasActiveTreatment() && otherProfile.getActiveTargetId() == target.getId()) {
                return true;
            }
        }
        return false;
    }

    private static LivingEntity resolveOtherTarget(ServerPlayer actor, int targetId) {
        Entity e = actor.level().getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) {
            return null;
        }
        if (actor.distanceToSqr(le) > MedicalConfig.treatReachSqr()) {
            return null;
        }
        return le;
    }

    private static IMedicalData medicalDataOf(LivingEntity entity) {
        return MedicalAttachments.get(entity);
    }

    private static int slotToLock(ServerPlayer actor, Item item) {
        Inventory inv = actor.getInventory();
        if (inv.selected >= 0 && inv.selected < 9) {
            ItemStack sel = inv.getItem(inv.selected);
            if (!sel.isEmpty() && sel.getItem() == item) {
                return inv.selected;
            }
        }
        return findItemSlot(actor, item);
    }

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
