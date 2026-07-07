package com.warfactory.medical.server;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.core.treatment.TreatmentAction;
import com.warfactory.medical.item.InjectableItem;
import com.warfactory.medical.item.MedicalItem;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.MedicalNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server-authoritative tracking of timed active treatments. State is transient (never NBT); physiology is
 * only mutated on completion via {@link TreatmentService#applyTargeted}; independent of the vanilla
 * right-click channel so the two never double-apply.
 */
public final class MedicalActionService {

    private MedicalActionService() {
    }

    /**
     * Begin a timed treatment for {@code player} using the medical item identified by {@code itemId},
     * targeting {@code limb} (nullable = auto-pick). Validates that the item exists, is a
     * {@link MedicalItem} with a {@link Treatment}, is actually present in the player's inventory, that no
     * treatment is already running, and that the player is not creative-immune.
     *
     * @return {@code true} when a treatment was started (and an {@link ActiveTreatmentPacket} sent).
     */
    public static boolean start(ServerPlayer player, ResourceLocation itemId, LimbType limb) {
        if (player == null || itemId == null) {
            return false;
        }
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }
        IMedicalData data = MedicalCapabilities.get(player);
        if (data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        if (profile.hasActiveTreatment()) {
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
        if (findItemSlot(player, item) < 0) {
            return false; // player is not carrying the item they asked to use
        }

        // Tourniquet is applied INSTANTLY (not a timed treatment): toggle it onto the selected appendage.
        if (treatment != null && treatment.action() == TreatmentAction.APPLY_TOURNIQUET) {
            return applyTourniquet(player, data, profile, limb, item);
        }

        // Resolve the channel duration and the (presentation-only) action for the client overlay.
        int totalTicks;
        TreatmentAction action;
        if (treatment != null) {
            totalTicks = treatment.useDurationTicks();
            action = treatment.action();
        } else {
            totalTicks = ((InjectableItem) item).getSubstance().useDurationTicks();
            // Injectables carry no TreatmentAction; REDUCE_PAIN is the closest presentation label and keeps
            // the overlay packet's non-null-action invariant. Completion resolves the item, not this action.
            action = TreatmentAction.REDUCE_PAIN;
        }
        if (totalTicks < 1) {
            totalTicks = 1;
        }
        long startGameTime = player.level().getGameTime();
        profile.setActiveTreatment(action, limb, itemId.toString(), totalTicks, startGameTime);

        MedicalNetworking.sendActiveTreatment(player, new ActiveTreatmentPacket(
                true, action, limb, totalTicks, startGameTime));
        return true;
    }

    /**
     * Advance one engine pass; completes when elapsed >= totalTicks (applies treatment, consumes item, notifies
     * client) or cancels if the item has vanished from the inventory.
     */
    public static void tick(ServerPlayer player, MedicalProfile profile, long nowTick) {
        if (player == null || profile == null || !profile.hasActiveTreatment()) {
            return;
        }
        if (nowTick - profile.getActiveStartGameTime() < profile.getActiveTotalTicks()) {
            return; // still in progress
        }

        ResourceLocation itemId = ResourceLocation.tryParse(profile.getActiveItemId());
        Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
        boolean injectable = item instanceof InjectableItem inj && inj.getSubstance() != null;
        if (!(item instanceof MedicalItem medical) || (medical.getTreatment() == null && !injectable)) {
            cancel(player, "invalid_item");
            return;
        }
        int slot = findItemSlot(player, item);
        if (slot < 0) {
            cancel(player, "item_gone");
            return;
        }

        boolean applied;
        if (injectable) {
            // Injection is systemic; the recorded limb is ignored.
            applied = SubstanceService.inject(player, ((InjectableItem) item).getSubstance());
        } else {
            LimbType limb = profile.getActiveLimb();
            applied = TreatmentService.applyTargeted(player, medical.getTreatment(), limb);
        }
        if (applied && !player.getAbilities().instabuild) {
            player.getInventory().getItem(slot).shrink(1);
        }

        profile.clearActiveTreatment();
        MedicalNetworking.sendActiveTreatment(player, ActiveTreatmentPacket.inactive());
    }

    /**
     * Cancel any running treatment and tell the client to hide the progress overlay.
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
     * Instantly apply a tourniquet (arm/leg only, one per limb): slows bleeding without treating wounds.
     */
    private static boolean applyTourniquet(ServerPlayer player, IMedicalData data, MedicalProfile profile,
                                           LimbType limb, Item item) {
        if (limb == null || !(limb.isArm() || limb.isLeg())) {
            return false;
        }
        Limb target = profile.limb(limb);
        if (target.hasTourniquet()) {
            return false; // one tourniquet per appendage
        }
        target.setTourniquet(true);
        profile.markDirty();
        data.bumpRevision();
        if (!player.getAbilities().instabuild) {
            int slot = findItemSlot(player, item);
            if (slot >= 0) {
                player.getInventory().getItem(slot).shrink(1);
            }
        }
        MedicalEngine.resync(player);
        return true;
    }

    /**
     * Remove the tourniquet from {@code limb} (UI-driven, no item consumed); no-op if none present.
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
        return true;
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
