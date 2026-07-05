package com.warfactory.medical.server;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.MedicalProfile;
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
 * Server-authoritative tracking of timed "active" treatments: applying a chosen medical item to a chosen
 * {@link LimbType} over N ticks, driving the radial / character-sheet apply flow and the action-progress
 * overlay.
 *
 * <p>The active treatment is stored transiently on the {@link MedicalProfile} (never persisted to NBT).
 * The physiology is only mutated on COMPLETION, through {@link TreatmentService#applyTargeted}; the item
 * is consumed only when that reports a change. This subsystem is entirely independent of the vanilla
 * right-click (startUsingItem / finishUsingItem) channel so the two never double-apply.</p>
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

        // Resolve the channel duration and the (presentation-only) action for the client overlay.
        int totalTicks;
        TreatmentAction action;
        if (treatment != null) {
            totalTicks = treatment.getUseDurationTicks();
            action = treatment.getAction();
        } else {
            totalTicks = ((InjectableItem) item).getSubstance().getUseDurationTicks();
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
     * Advance the active treatment one engine pass. When the elapsed game time reaches the recorded
     * duration the treatment COMPLETES: it resolves the recorded item, applies its {@link Treatment}
     * (biased to the recorded limb), consumes one item on success, then clears and notifies the client.
     * If the item has vanished from the inventory the treatment is cancelled instead.
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
     * Cancel any running treatment for {@code player} (interrupted by damage, death, item loss, ...),
     * clearing the transient state and telling the client to hide the progress overlay.
     *
     * @param reason a short human-readable reason (currently informational only)
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

    /** @return the first inventory slot holding {@code item}, or {@code -1} when the player has none. */
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
