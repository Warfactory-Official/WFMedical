package com.warfactory.medical.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.client.MedicalDebug;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.mixin.PostChainAccessor;
import com.warfactory.medical.network.ClientMedicalCache;
import com.warfactory.medical.network.MedicalSyncPacket;
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
public final class BloodDesaturationEffect {

    private static final float MAX_DESATURATION = 0.85F;
    private static final float EPSILON = 0.001F;

    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "shaders/post/blood_desaturate.json");

    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    private static boolean disabled;
    private static long logFrame;

    private BloodDesaturationEffect() {
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

        float amount = desaturationAmount();
        if (amount <= EPSILON) {
            return;
        }

        try {
            PostChain active = ensureChain(mc);
            if (active == null) {
                return;
            }
            float saturation = 1.0F - amount;
            setSaturationUniform(active, saturation);
            event.getGuiGraphics().flush();

            boolean logNow = MedicalDebug.verbose() && (logFrame++ % 12L == 0L);
            float[] before = logNow ? MedicalDebug.sampleCenterPixel(mc.getMainRenderTarget()) : null;

            active.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            var target = mc.getMainRenderTarget();
            target.bindWrite(false);
            RenderSystem.viewport(0, 0, target.viewWidth, target.viewHeight);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.depthMask(true);
            RenderSystem.clearDepth(1.0);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

            if (logNow) {
                float[] after = MedicalDebug.sampleCenterPixel(target);
                WFMedical.LOGGER.info(
                        "[{}][desat] amount={} saturation={} eyeInWater={} underwater={} target={}x{} "
                                + "| centre before={} after={} (before=scene+overlay, after=post-desaturation-blit)",
                        WFMedical.MOD_ID,
                        String.format("%.3f", amount),
                        String.format("%.3f", saturation),
                        MedicalDebug.localPlayerEyeInWater(),
                        player.isUnderWater(),
                        target.width, target.height,
                        MedicalDebug.fmt(before),
                        MedicalDebug.fmt(after));
            }
        } catch (Throwable t) {
            WFMedical.LOGGER.warn("[{}] Blood desaturation effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
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
                WFMedical.LOGGER.warn("[{}] Failed to load blood desaturation shader ({}); effect disabled",
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

    private static void setSaturationUniform(PostChain postChain, float saturation) {
        List<PostPass> passes = ((PostChainAccessor) postChain).wfmedical$getPasses();
        for (PostPass pass : passes) {
            EffectInstance effect = pass.getEffect();
            AbstractUniform uniform = effect.safeGetUniform("Saturation");
            uniform.set(saturation);
        }
    }

    private static float desaturationAmount() {
        MedicalSyncPacket snap = ClientMedicalCache.get();
        if (snap == null) {
            return 0.0F;
        }
        double maxBloodMl = snap.maxBloodMl();
        if (maxBloodMl <= 0.0D) {
            return 0.0F;
        }
        float remaining = (float) (snap.bloodMl() / maxBloodMl);
        float loss = 1.0F - remaining;
        float deathLoss = (float) MedicalConfig.bloodDeathLossFraction();
        if (deathLoss <= 0.0F) {
            return 0.0F;
        }
        float t = loss / deathLoss;
        if (t < 0.0F) {
            t = 0.0F;
        } else if (t > 1.0F) {
            t = 1.0F;
        }
        return MAX_DESATURATION * t;
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
