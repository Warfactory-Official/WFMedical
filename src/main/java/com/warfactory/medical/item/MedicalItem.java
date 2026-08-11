package com.warfactory.medical.item;

import com.warfactory.medical.client.render.MedicalItemRenderer;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.treatment.Treatment;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.minecraft.core.registries.BuiltInRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class MedicalItem extends Item {

    private final Treatment treatment;
    private final UseAnim useAnim;
    // Oral meds (pills you swallow) can only be taken by yourself; set once at registration. Everything else
    // defaults to being applicable to another player. Overridable per item via config (see isSelfOnly()).
    private boolean selfOnlyDefault;

    public MedicalItem(Properties properties, Treatment treatment) {
        this(properties, treatment, UseAnim.BOW);
    }

    public MedicalItem(Properties properties, Treatment treatment, boolean eatAnim) {
        this(properties, treatment, eatAnim ? UseAnim.EAT : UseAnim.BOW);
    }

    public MedicalItem(Properties properties, Treatment treatment, UseAnim useAnim) {
        super(properties);
        this.treatment = treatment;
        this.useAnim = useAnim;
    }

    public Treatment getTreatment() {
        return treatment;
    }

    /** Mark this item as self-only by default (oral medication). Fluent so it can be chained at registration. */
    public MedicalItem selfOnly() {
        this.selfOnlyDefault = true;
        return this;
    }

    public boolean isSelfOnlyByDefault() {
        return selfOnlyDefault;
    }

    /**
     * Whether this item may only be applied to the user themselves (never to another player). Honors the
     * per-item {@code treatSelfOnlyOverrides} config; falls back to the item's built-in default.
     */
    public boolean isSelfOnly() {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(this);
        Boolean override = id == null ? null : MedicalConfig.treatSelfOnlyOverride(id);
        return override != null ? override : selfOnlyDefault;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int ticks = treatment != null ? treatment.useDurationTicks() : 0;
        return ticks > 0 ? ticks : 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return useAnim;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (treatment != null) {
            String key = "tooltip.wfmedical." + treatment.action().name().toLowerCase(Locale.ROOT);
            tooltip.add(Component.translatable(key + ".effect").withStyle(ChatFormatting.GRAY));
            Set<TraumaCategory> cats = treatment.applicableCategories();
            if (!cats.isEmpty()) {
                tooltip.add(Component.translatable("tooltip.wfmedical.treats", categoriesText(cats))
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
            if (treatment.bloodRestoreMl() > 0.0D) {
                tooltip.add(Component.translatable("tooltip.wfmedical.blood_restore",
                        (int) Math.round(treatment.bloodRestoreMl())).withStyle(ChatFormatting.DARK_RED));
            }
            tooltip.add(Component.translatable(key + ".limit").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.wfmedical.apply_time",
                    formatSeconds(treatment.useDurationTicks())).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(isSelfOnly()
                        ? "tooltip.wfmedical.self_only"
                        : "tooltip.wfmedical.usable_on_others")
                .withStyle(isSelfOnly() ? ChatFormatting.GOLD : ChatFormatting.DARK_AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static Component categoriesText(Set<TraumaCategory> cats) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (TraumaCategory c : cats) {
            if (!first) {
                out.append(", ");
            }
            out.append(Component.translatable("tooltip.wfmedical.category." + c.name().toLowerCase(Locale.ROOT)));
            first = false;
        }
        return out;
    }

    static String formatSeconds(int ticks) {
        double seconds = ticks / 20.0;
        return seconds == Math.floor(seconds)
                ? Integer.toString((int) seconds)
                : String.format(Locale.ROOT, "%.1f", seconds);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return MedicalItemRenderer.get();
            }
        });
    }
}
