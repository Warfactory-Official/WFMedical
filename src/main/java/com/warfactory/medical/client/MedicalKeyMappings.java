package com.warfactory.medical.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Rebindable key bindings for the Warfactory Medical client. CLIENT-ONLY: {@link KeyMapping} is a client
 * type; the dedicated server never class-loads this class.
 */
public final class MedicalKeyMappings {

    public static final String CATEGORY = "key.categories.wfmedical";

    public static final KeyMapping OPEN_SHEET = new KeyMapping(
            "key.wfmedical.open_sheet",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY);

    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.wfmedical.open_radial",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    public static final KeyMapping TOGGLE_DEBUG = new KeyMapping(
            "key.wfmedical.toggle_debug",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    public static final KeyMapping TOGGLE_HITBOX = new KeyMapping(
            "key.wfmedical.toggle_hitbox",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    private MedicalKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SHEET);
        event.register(OPEN_RADIAL);
        event.register(TOGGLE_DEBUG);
        event.register(TOGGLE_HITBOX);
    }
}
