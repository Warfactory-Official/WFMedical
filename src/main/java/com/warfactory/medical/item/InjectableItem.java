package com.warfactory.medical.item;

import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.server.SubstanceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A medical item that injects a {@link Substance} (opioid analgesic or antidote) when used. Extends
 * {@link MedicalItem} with a {@code null} {@link com.warfactory.medical.core.treatment.Treatment} so it is
 * automatically picked up by the existing {@code instanceof MedicalItem} channel-display / available-items
 * plumbing; the substance behaviour is entirely separate from the trauma-treatment path.
 *
 * <p>The mutation is server authoritative: only {@link SubstanceService#inject(ServerPlayer, Substance)}
 * may touch drug load / unconsciousness state, and the stack is consumed only when that call reports a change.
 * Clients merely play the use animation and swing.</p>
 */
public class InjectableItem extends MedicalItem {

    private final Substance substance;

    public InjectableItem(Properties properties, Substance substance) {
        super(properties, null);
        this.substance = substance;
    }

    /**
     * The substance this item injects.
     */
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
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer && substance != null) {
            boolean applied = SubstanceService.inject(serverPlayer, substance);
            if (applied) {
                level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.2F);
                if (!serverPlayer.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        } else if (level.isClientSide && entity instanceof Player) {
            entity.swing(entity.getUsedItemHand());
        }
        return stack;
    }
}
