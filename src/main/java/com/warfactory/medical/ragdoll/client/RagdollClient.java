package com.warfactory.medical.ragdoll.client;

import com.warfactory.medical.body.api.MedicalState;
import com.warfactory.medical.body.network.BodyNetwork;
import com.warfactory.medical.ragdoll.MedicalRagdollEntity;
import com.warfactory.medical.ragdoll.Ragdolls;
import com.warfactory.medical.treatment.client.MedInteractionScreen;
import com.warfactory.medical.treatment.network.TargetSheetRequestPacket;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class RagdollClient {
    private RagdollClient() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(RagdollClient.class);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(Ragdolls.TYPE.get(), MedicalRagdollRenderer::new));
    }

    public static boolean hidden(Entity target) {
        if (!Ragdolls.enabled()) {
            return false;
        }
        MedicalRagdollEntity body = Ragdolls.find(target);
        return body != null && body.target() == target && MedicalRagdollRenderer.ready(body);
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        if (hidden(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHandRender(RenderHandEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && hidden(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !Ragdolls.enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.screen != null || mc.player.isSpectator()
                || !(mc.hitResult instanceof EntityHitResult hit)) {
            return;
        }
        Entity picked = hit.getEntity();
        Entity target = picked instanceof MedicalRagdollEntity body ? body.target() : picked;
        if (target == null || !target.isAlive()) {
            return;
        }
        if (target instanceof CorpseEntity && (picked instanceof MedicalRagdollEntity || hidden(target))) {
            // Send the native entity-interaction packet; the server delegates to Corpse's permission-checked menu.
            event.setSwingHand(false);
            event.setCanceled(true);
            mc.gameMode.interact(mc.player, picked, InteractionHand.MAIN_HAND);
        } else if (target instanceof Player && (picked instanceof MedicalRagdollEntity || hidden(target))
                && mc.player.getMainHandItem().isEmpty()
                && !MedicalState.isHandsDisabled(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
            MedInteractionScreen.markPendingOpen(target.getId());
            BodyNetwork.sendToServer(new TargetSheetRequestPacket(target.getId()));
        }
    }
}
