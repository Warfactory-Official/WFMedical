package com.warfactory.medical.item;

import com.warfactory.medical.core.substance.Substance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Injectable substance item (opioid or antidote). Uses a {@code null} Treatment so the existing
 * {@code instanceof MedicalItem} plumbing picks it up. Substance behavior is server authoritative;
 * the stack is consumed only when {@link SubstanceService#inject} reports a change.
 */
public class InjectableItem extends MedicalItem {

    private final Substance substance;

    public InjectableItem(Properties properties, Substance substance) {
        super(properties, null);
        this.substance = substance;
    }

    public Substance getSubstance() {
        return substance;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        int ticks = substance != null ? substance.useDurationTicks() : 0;
        return ticks > 0 ? ticks : 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Like MedicalItem: right-click is handled by the client-side treatment flow, which injects the
        // substance into the actor (systemic, self-only) via the authoritative MedicalActionPacket. The vanilla
        // hold-to-use channel is disabled here so the two never race.
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
