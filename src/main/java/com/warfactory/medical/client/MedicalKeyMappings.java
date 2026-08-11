package com.warfactory.medical.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

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

    public static final KeyMapping GIVE_UP = new KeyMapping(
            "key.wfmedical.give_up",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE,
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

    public static final KeyMapping TOGGLE_SCREEN_FX = new KeyMapping(
            "key.wfmedical.toggle_screen_fx",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    public static final KeyMapping TOGGLE_FX_LOG = new KeyMapping(
            "key.wfmedical.toggle_fx_log",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY);

    private MedicalKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SHEET);
        event.register(OPEN_RADIAL);
        event.register(GIVE_UP);
        event.register(TOGGLE_DEBUG);
        event.register(TOGGLE_HITBOX);
        if (MedicalDebug.ENABLED) {
            event.register(TOGGLE_SCREEN_FX);
            event.register(TOGGLE_FX_LOG);
        }
    }
}
