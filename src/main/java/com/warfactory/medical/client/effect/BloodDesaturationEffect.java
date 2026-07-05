package com.warfactory.medical.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.warfactory.medical.WFMedical;
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
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * CLIENT-ONLY blood-loss desaturation. Drives a real full-screen {@link PostChain} post-processing effect
 * that fades the 3D scene towards grayscale as the player's blood pool drops below the low threshold, giving
 * the classic "greying out from blood loss" feel. Purely presentational: it reads the synced
 * {@link ClientMedicalCache} snapshot and never mutates any medical state.
 *
 * <p>Self-registers on the FORGE bus, {@link Dist#CLIENT} only, so a dedicated server never class-loads any
 * of the client render types referenced here.</p>
 *
 * <h2>Robustness</h2>
 * <p>This is the highest-risk client feature, so every failure mode is contained:</p>
 * <ul>
 *   <li>The {@link PostChain} is created lazily inside a {@code try/catch}; if the shader assets fail to load
 *       the effect sets {@link #disabled} and logs once, so it never retry-spams and never crashes.</li>
 *   <li>The whole render handler body is wrapped in {@code try/catch}: any GL/shader error disables the
 *       effect for the rest of the session instead of taking the game down.</li>
 *   <li>A healthy player never touches the chain at all: the {@code amount <= EPSILON} early return runs
 *       before any lazy creation, so a full-blood player pays effectively zero cost.</li>
 * </ul>
 *
 * <h2>Intensity model</h2>
 * <p>{@code f = bloodMl / maxBloodMl}. Above {@link #LOW_FRACTION} the effect is off. Below it the effect
 * ramps linearly with {@code t = (LOW - f) / LOW} (so {@code t = 0} at the threshold, {@code t = 1} at an
 * empty pool), and {@code amount = MAX_DESATURATION * t}. The shader receives {@code Saturation = 1 - amount}
 * (1 = full color, 0 = grayscale).</p>
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodDesaturationEffect {

    /** Blood fraction at/above which no desaturation is applied (mirrors PhysiologyParams bloodLowFraction). */
    private static final float LOW_FRACTION = 0.60F;
    /** Maximum desaturation amount at an empty blood pool (0 = untouched, 1 = full grayscale). */
    private static final float MAX_DESATURATION = 0.85F;
    /** Below this desaturation amount the effect is a no-op (keeps healthy players off the hot path). */
    private static final float EPSILON = 0.001F;

    private static final ResourceLocation SHADER =
            new ResourceLocation(WFMedical.MOD_ID, "shaders/post/blood_desaturate.json");

    /** Cached post-processing chain; {@code null} until first needed or after teardown. */
    private static PostChain chain;
    /** Window dimensions the cached {@link #chain} was last sized for. */
    private static int chainWidth = -1;
    private static int chainHeight = -1;
    /** Set once on unrecoverable failure; blocks all further processing for the session. */
    private static boolean disabled;

    private BloodDesaturationEffect() {
    }

    /**
     * Fires after the 3D world and held item have been drawn to the main render target but before the HUD.
     * Processing the chain here desaturates the whole scene while leaving the HUD (drawn afterwards) in full
     * color, which is exactly what we want for a readable overlay.
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
            mc.getMainRenderTarget().bindWrite(false);
        } catch (Throwable t) {
            // A shader/GL failure must never crash the game: disable and tear down for the session.
            WFMedical.LOGGER.warn("[{}] Blood desaturation effect failed; disabling for this session",
                    WFMedical.MOD_ID, t);
            disable();
        }
    }

    /** Tear down the chain when leaving a world so we do not leak GL resources or reuse a stale target. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        closeChain();
    }

    // ------------------------------------------------------------------ internals

    /**
     * Lazily create the chain (and (re)size it to the current window). Returns {@code null} if creation
     * failed, having permanently {@link #disable() disabled} the effect.
     */
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
     * Push {@code saturation} onto every pass's {@code Saturation} uniform. Passes that do not declare it
     * (e.g. the trailing blit) receive a harmless dummy uniform from {@code safeGetUniform}, so this is safe
     * to apply uniformly.
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

    /** @return the current desaturation amount (0..MAX_DESATURATION) from the synced blood fraction. */
    private static float desaturationAmount() {
        MedicalSyncPacket snap = ClientMedicalCache.get();
        if (snap == null) {
            return 0.0F;
        }
        double maxBloodMl = snap.maxBloodMl();
        if (maxBloodMl <= 0.0D) {
            return 0.0F;
        }
        float f = (float) (snap.bloodMl() / maxBloodMl);
        if (f >= LOW_FRACTION) {
            return 0.0F;
        }
        float t = (LOW_FRACTION - f) / LOW_FRACTION;
        if (t < 0.0F) {
            t = 0.0F;
        } else if (t > 1.0F) {
            t = 1.0F;
        }
        return MAX_DESATURATION * t;
    }

    /** Close the chain and forget its cached size; safe to call when already torn down. */
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

    /** Permanently disable the effect for this session and release any GL resources. */
    private static void disable() {
        disabled = true;
        closeChain();
    }
}
