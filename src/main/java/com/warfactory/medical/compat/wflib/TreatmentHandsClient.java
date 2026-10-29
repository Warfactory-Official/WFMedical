package com.warfactory.medical.compat.wflib;

import com.warfactory.medical.client.TreatmentHandKind;
import com.warfactory.medical.network.ActiveTreatmentPacket;
import com.warfactory.medical.network.ClientMedicalCache;
import com.wf.wflib.client.view.HandClip;
import com.wf.wflib.client.view.HandClipView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * First person during a treatment: GAMMA hand tracks (wflib {@link HandClipView}) stretched over the treatment.
 * Registered on the game bus only with WFLib present (WFMedicalClient).
 */
public final class TreatmentHandsClient {
    private static final Map<TreatmentHandKind, HandClip> CLIPS = new EnumMap<>(TreatmentHandKind.class);
    private static final Map<TreatmentHandKind, HandClipView.Prop[]> PROPS = new EnumMap<>(TreatmentHandKind.class);

    private TreatmentHandsClient() {
    }

    @Nullable
    private static TreatmentHandKind kind(LocalPlayer p) {
        return ClientMedicalCache.hasActiveTreatment()
                ? TreatmentHandKind.of(BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem())) : null;
    }

    private static HandClip clip(TreatmentHandKind kind) {
        return CLIPS.computeIfAbsent(kind,
                k -> HandClip.load(Minecraft.getInstance().getResourceManager(), k.clip));
    }

    /** Clip seconds: treatment progress x clip length, so the clip ends with the treatment. */
    private static float seconds(LocalPlayer p, HandClip clip, float partial) {
        ActiveTreatmentPacket a = ClientMedicalCache.activeTreatment();
        float elapsed = p.level().getGameTime() - a.startGameTime() + partial;
        return Math.clamp(elapsed / Math.max(1, a.totalTicks()), 0.0f, 1.0f) * clip.duration;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        TreatmentHandKind kind = player == null || mc.getCameraEntity() != player ? null : kind(player);
        if (kind == null) {
            return;
        }
        HandClip clip = clip(kind);
        HandClipView.Prop[] props = PROPS.computeIfAbsent(kind, k -> {
            HandClipView.Prop[] out = new HandClipView.Prop[k.tracks.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = new HandClipView.Prop(k.tracks[i], k.model(k.tracks[i]));
            }
            return out;
        });
        HandClipView.render(event, player, clip, seconds(player, clip, event.getPartialTick()), props);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        TreatmentHandKind kind = player == null || !mc.options.getCameraType().isFirstPerson() ? null : kind(player);
        if (kind != null) {
            HandClip clip = clip(kind);
            HandClipView.camera(event, clip, seconds(player, clip, (float) event.getPartialTick()));
        }
    }
}
