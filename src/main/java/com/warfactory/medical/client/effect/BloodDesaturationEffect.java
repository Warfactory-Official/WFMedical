package com.warfactory.medical.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.warfactory.medical.WFMedical;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * CLIENT-ONLY blood-loss desaturation. Fades the 3D scene toward grayscale as blood drops, anchored to the
 * bleed-out DEATH threshold (not an empty pool) so the full desaturation range is visible before death.
 * PostChain created lazily; all failures disable the effect for the session rather than crashing.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodDesaturationEffect {

    /**
     * 0 = untouched, 1 = full grayscale; reached exactly at the bleed-out death threshold.
     */
    private static final float MAX_DESATURATION = 0.85F;
    /**
     * Below this the effect is a no-op; keeps healthy players off the hot path.
     */
    private static final float EPSILON = 0.001F;

    private static final ResourceLocation SHADER =
            new ResourceLocation(WFMedical.MOD_ID, "shaders/post/blood_desaturate.json");

    private static PostChain chain;
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    /**
     * Set once on unrecoverable failure; blocks all further processing for the session.
     */
    private static boolean disabled;

    private BloodDesaturationEffect() {
    }

    /**
     * Fires before HUD: desaturates the scene while leaving HUD overlays in full colour.
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (disabled) {
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
            // Flush any pending GUI batch so the chain reads a complete frame.
            event.getGuiGraphics().flush();
            active.process(event.getPartialTick());
            // Rebind the main target so subsequent HUD rendering draws to the right place.
            var target = mc.getMainRenderTarget();
            target.bindWrite(false);
            // PostPass leaves BLEND DISABLED (from the blit pass's blend mode) and never restores the caller's
            // render state; without this the HUD drawn next -- and translucent world/sky elements next frame --
            // render wrong (reads as UI + sky corruption while bleeding). Restore GUI-safe defaults.
            RenderSystem.viewport(0, 0, target.viewWidth, target.viewHeight);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable t) {
            // A shader/GL failure must never crash the game: disable and tear down for the session.
            WFMedical.LOGGER.warn("[{}] Blood desaturation effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        closeChain();
    }

    // ------------------------------------------------------------------ internals

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

    /**
     * {@code safeGetUniform} returns a dummy for undeclared names so this is safe on every pass including the blit.
     */
    private static void setSaturationUniform(PostChain postChain, float saturation) {
        List<PostPass> passes = ((PostChainAccessor) postChain).wfmedical$getPasses();
        for (PostPass pass : passes) {
            EffectInstance effect = pass.getEffect();
            // safeGetUniform never returns null (it yields a dummy for undeclared names), so passes such as
            // the trailing blit that lack "Saturation" simply ignore this call.
            AbstractUniform uniform = effect.safeGetUniform("Saturation");
            uniform.set(saturation);
        }
    }

    /**
     * Returns desaturation (0..MAX_DESATURATION) ramped so it peaks exactly at the bleed-out death threshold.
     */
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
                // Closing is best-effort; nothing actionable if it fails.
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
