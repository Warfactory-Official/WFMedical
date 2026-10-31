package com.warfactory.medical.treatment.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.warfactory.medical.body.client.BodyKeys;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class MedicalKeyMappings {

    public static final KeyMapping OPEN_SHEET = new KeyMapping(
            "key.wfmedical.open_sheet",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            BodyKeys.CATEGORY);

    public static final KeyMapping OPEN_RADIAL = new KeyMapping(
            "key.wfmedical.open_radial",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            BodyKeys.CATEGORY);

    private MedicalKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SHEET);
        event.register(OPEN_RADIAL);
    }
}
