package com.warfactory.medical.item;

import com.warfactory.medical.core.substance.Substance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

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
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int ticks = substance != null ? substance.useDurationTicks() : 0;
        return ticks > 0 ? ticks : 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (substance != null) {
            String key = "tooltip.wfmedical." + substance.id().toLowerCase(Locale.ROOT);
            tooltip.add(Component.translatable(key + ".effect").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(key + ".limit").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.wfmedical.apply_time",
                    formatSeconds(substance.useDurationTicks())).withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
