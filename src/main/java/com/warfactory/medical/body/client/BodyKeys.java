package com.warfactory.medical.body.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class BodyKeys {

    public static final String CATEGORY = "key.categories.wfmedical";

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

    private BodyKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(GIVE_UP);
        event.register(TOGGLE_DEBUG);
        if (MedicalDebug.ENABLED) {
            event.register(TOGGLE_SCREEN_FX);
            event.register(TOGGLE_FX_LOG);
        }
    }
}
