package com.warfactory.medical.item;

import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.treatment.Treatment;
import com.warfactory.medical.server.TreatmentService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A medical item whose use starts a timed "treatment". The actual physiology mutation is server
 * authoritative: only {@link TreatmentService#apply(ServerPlayer, Treatment)} may touch trauma, and the
 * item stack is only consumed when that call reports a change. Clients merely play the use animation and
 * swing; they never create or remove trauma.
 */
public class MedicalItem extends Item {

    private final Treatment treatment;
    private final boolean eatAnim;

    public MedicalItem(Properties properties, Treatment treatment) {
        this(properties, treatment, false);
    }

    public MedicalItem(Properties properties, Treatment treatment, boolean eatAnim) {
        super(properties);
        this.treatment = treatment;
        this.eatAnim = eatAnim;
    }

    public Treatment getTreatment() {
        return treatment;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        int ticks = treatment != null ? treatment.useDurationTicks() : 0;
        return ticks > 0 ? ticks : 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return eatAnim ? UseAnim.EAT : UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer && treatment != null) {
            // Bias vanilla right-click use toward whatever limb the player selected in the UI, if any.
            IMedicalData data = MedicalCapabilities.get(serverPlayer);
            LimbType preferred = data != null ? data.getProfile().getPreferredLimb() : null;
            boolean applied = TreatmentService.applyTargeted(serverPlayer, treatment, preferred);
            if (applied) {
                level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.0F);
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
