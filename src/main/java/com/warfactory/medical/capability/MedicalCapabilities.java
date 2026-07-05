package com.warfactory.medical.capability;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * Central access point for the per-player medical capability. Provides the {@link Capability} token,
 * a null-safe resolver, and death/dimension copy support.
 *
 * <p>In Forge 1.20.1 the {@link CapabilityManager#get(CapabilityToken)} call below IS the registration
 * for this capability; there is deliberately no {@code RegisterCapabilitiesEvent.register()} call, which
 * would double-register the same implementation and crash mod loading.</p>
 */
public final class MedicalCapabilities {

    public static final Capability<IMedicalData> MEDICAL =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private MedicalCapabilities() {
    }

    /**
     * @return the player's medical data, or {@code null} when the capability is absent (e.g. on a
     * non-player entity or before it has been attached).
     */
    public static IMedicalData get(Player player) {
        if (player == null) {
            return null;
        }
        return player.getCapability(MEDICAL).resolve().orElse(null);
    }

    /**
     * Deep-copy the medical profile from {@code original} onto {@code clone} across death or dimension
     * change. Copies through NBT so the clone owns an independent profile and trauma graph.
     */
    public static void copy(Player original, Player clone) {
        IMedicalData oldData = get(original);
        IMedicalData newData = get(clone);
        if (oldData == null || newData == null) {
            return;
        }
        newData.load(oldData.save());
        newData.bumpRevision();
    }
}
