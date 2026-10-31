package com.warfactory.medical.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.client.MedicalDebug;
import com.warfactory.medical.substance.api.Opioids;
import com.warfactory.medical.mixin.fx.PostChainAccessor;
import com.warfactory.medical.body.client.ClientMedicalCache;
import com.wf.wflib.core.api.WFSystems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public final class OpioidHazeEffect {

    private static final float FADE_STEP = 0.02F;
    private static final float MILD = 0.35F;
    private static final float EPSILON = 0.001F;
    private static final boolean SUBSTANCE = WFSystems.enabled(WFMedical.MOD_ID + ":substance");

    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "shaders/post/opioid_haze.json");

    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    private static boolean disabled;
    private static float haze;

    private OpioidHazeEffect() {
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (disabled) {
            return;
        }
        if (!MedicalDebug.screenEffectsEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isCreative()) {
            return;
        }

        float target = targetHaze();
        haze += (target - haze) * FADE_STEP;
        if (haze <= EPSILON) {
            haze = 0.0F;
            return;
        }

        try {
            PostChain active = ensureChain(mc);
            if (active == null) {
                return;
            }
            setUniforms(active, haze, (player.tickCount % 100000 + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0F);
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
            WFMedical.LOGGER.warn("[{}] Opioid haze effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        haze = 0.0F;
        closeChain();
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
                WFMedical.LOGGER.warn("[{}] Failed to load opioid haze shader ({}); effect disabled",
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

    private static float targetHaze() {
        float suppression = Math.min(1.0F, ClientMedicalCache.painSuppression());
        float active = MILD * suppression;
        if (suppression <= 0.0F || !SUBSTANCE) {
            return active;
        }
        return Math.max(active, Opioids.overdose(ClientMedicalCache.drugLoad()));
    }

    private static void setUniforms(PostChain postChain, float amount, float seconds) {
        List<PostPass> passes = ((PostChainAccessor) postChain).wfmedical$getPasses();
        for (PostPass pass : passes) {
            EffectInstance effect = pass.getEffect();
            effect.safeGetUniform("Haze").set(amount);
            effect.safeGetUniform("Time").set(seconds);
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
