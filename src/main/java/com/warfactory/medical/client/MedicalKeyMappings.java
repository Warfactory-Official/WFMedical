package com.warfactory.medical.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The rebindable key bindings for the Warfactory Medical client. All three live under the
 * {@code key.categories.wfmedical} category and default to keys that do not collide with the vanilla
 * survival bindings.
 *
 * <p>This class is CLIENT-ONLY: {@link KeyMapping} is a client type. It is only touched from
 * {@code Dist.CLIENT}-guarded subscribers ({@link WFMedicalClient}, {@link MedicalClientEvents}). The
 * dedicated server never class-loads it.</p>
 */
public final class MedicalKeyMappings {

    /**
     * Translation key of the shared keybind category (add to the client lang file).
     */
    public static final String CATEGORY = "key.categories.wfmedical";

    /**
     * Open the full character / trauma sheet (default {@code H}).
     */
    public static final KeyMapping OPEN_SHEET = new KeyMapping(
            "key.wfmedical.open_sheet",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    /**
     * Open the radial medical-interaction menu (default {@code G}).
     */
    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.wfmedical.open_radial",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    /**
     * Toggle the client medical-debug overlay flag (default {@code J}).
     */
    public static final KeyMapping TOGGLE_DEBUG = new KeyMapping(
            "key.wfmedical.toggle_debug",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    private MedicalKeyMappings() {
    }

    /**
     * Register all three key mappings; called from the mod-bus {@link RegisterKeyMappingsEvent} handler.
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SHEET);
        event.register(OPEN_RADIAL);
        event.register(TOGGLE_DEBUG);
    }
}
