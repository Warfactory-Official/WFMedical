package com.warfactory.medical.item;

import com.warfactory.medical.client.render.MedicalItemRenderer;
import com.warfactory.medical.core.treatment.Treatment;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * A medical item that carries a {@link Treatment}. Right-clicking one no longer uses the vanilla hold-to-use
 * channel (see {@link #use}); instead the client-side treatment wheel picks a target + limb and sends an
 * authoritative request. The physiology mutation is server-authoritative
 * ({@code MedicalActionService} / {@code TreatmentService}); the stack is consumed only when the treatment
 * actually changes state. Clients never create or remove trauma.
 */
public class MedicalItem extends Item {

    private final Treatment treatment;
    private final UseAnim useAnim;

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

    @Override
    public int getUseDuration(ItemStack stack) {
        int ticks = treatment != null ? treatment.useDurationTicks() : 0;
        return ticks > 0 ? ticks : 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return useAnim;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Right-click handling for medical items is driven entirely by the client-side treatment wheel (see
        // TreatmentInteractions): it detects the target, opens the limb wheel or auto-applies, and sends the
        // authoritative MedicalActionPacket. The vanilla hold-to-use channel is intentionally disabled here so
        // the two never race – returning FAIL means the client sends no use packet and the server never acts.
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    /**
     * Route this item through the {@link MedicalItemRenderer} BEWLR so its OBJ model renders (for items whose
     * baked model is {@code builtin/entity}). Client-only: the consumer runs only on the client, so the
     * client-only renderer reference is never classloaded on a dedicated server. Covers {@link InjectableItem}.
     */
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
