package com.warfactory.medical.body;

import com.wf.wflib.artifact.api.Artifacts;
import net.minecraft.world.entity.player.Player;

public final class PsyProtection {

    private PsyProtection() {
    }

    /** Share of a stress rise that reaches {@code player}: worn artefact psy protection cuts it, negative worsens it. */
    public static float pass(Player player) {
        return 1.0F - Artifacts.psyProtection(player);
    }
}
