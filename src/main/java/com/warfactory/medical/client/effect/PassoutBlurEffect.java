package com.warfactory.medical.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.MedicalDebug;
import com.warfactory.medical.mixin.PostChainAccessor;
import com.warfactory.medical.network.ClientMedicalCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.opengl.GL11;

import java.util.List;

@EventBusSubscriber(modid = WFMedical.MOD_ID, value = Dist.CLIENT)
public final class PassoutBlurEffect {

    private static final float FADE_STEP = 0.06F;
    private static final float EPSILON = 0.001F;
    private static final float MAX_RADIUS = 6.0F;

    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "shaders/post/passout_blur.json");

    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    private static boolean disabled;

    private static float fade;

    private PassoutBlurEffect() {
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (disabled) {
            return;
        }
        if (!MedicalDebug.screenEffectsEnabled()) {
            fade = 0.0F;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) {
            fade = 0.0F;
            return;
        }

        boolean blur = shouldBlurVision();
        float target = blur ? 1.0F : 0.0F;
        fade += (target - fade) * FADE_STEP;
        if (fade < 0.0F) {
            fade = 0.0F;
        } else if (fade > 1.0F) {
            fade = 1.0F;
        }
        if (fade <= EPSILON) {
            return;
        }

        try {
            PostChain active = ensureChain(mc);
            if (active == null) {
                return;
            }
            setBlurUniforms(active, fade * MAX_RADIUS);
            event.getGuiGraphics().flush();
            active.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            var mainTarget = mc.getMainRenderTarget();
            mainTarget.bindWrite(false);
            RenderSystem.viewport(0, 0, mainTarget.viewWidth, mainTarget.viewHeight);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.clearDepth(1.0);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        } catch (Throwable t) {
            WFMedical.LOGGER.warn("[{}] Passed-out blur effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        fade = 0.0F;
        closeChain();
    }

    public static void reset() {
        fade = 0.0F;
    }


    private static boolean shouldBlurVision() {
        var stats = ClientMedicalCache.stats();
        return stats.unconscious() || stats.asphyxiating();
    }

    private static PostChain ensureChain(Minecraft mc) {
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(),
                        mc.getMainRenderTarget(), SHADER);
                chain.resize(width, height);
                chainWidth = width;
                chainHeight = height;
            } catch (Exception e) {
                WFMedical.LOGGER.warn("[{}] Failed to load passed-out blur shader ({}); effect disabled",
                        WFMedical.MOD_ID, SHADER, e);
                disable();
                return null;
            }
            return chain;
        }

        if (width != chainWidth || height != chainHeight) {
            chain.resize(width, height);
            chainWidth = width;
            chainHeight = height;
        }
        return chain;
    }

    private static void setBlurUniforms(PostChain postChain, float radius) {
        List<PostPass> passes = ((PostChainAccessor) postChain).wfmedical$getPasses();
        for (int i = 0; i < passes.size(); i++) {
            EffectInstance effect = passes.get(i).getEffect();
            effect.safeGetUniform("Radius").set(radius);
            AbstractUniform dir = effect.safeGetUniform("BlurDir");
            if ((i & 1) == 0) {
                dir.set(1.0F, 0.0F);
            } else {
                dir.set(0.0F, 1.0F);
            }
        }
    }

    private static void closeChain() {
        if (chain != null) {
            try {
                chain.close();
            } catch (Exception ignored) {
            }
            chain = null;
        }
        chainWidth = -1;
        chainHeight = -1;
    }

    private static void disable() {
        disabled = true;
        closeChain();
    }
}
