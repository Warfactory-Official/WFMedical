package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.Minecraft;

/**
 * Opens a purely client-side LDLib {@link ModularUI} (no server networking). CLIENT-ONLY.
 */
public final class ClientUIOpener {

    private ClientUIOpener() {
    }

    /**
     * Wire the given UI to a client ModularUIGuiContainer and display it. {@link ModularUI#initWidgets()}
     * must be called first (required by LDLib so widgets receive initWidget). Window id reuses the current
     * container id (used only for server↔client packet matching; irrelevant for a client-only UI).
     */
    public static void openClientUI(ModularUI ui) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ui == null) {
            return;
        }
        ui.initWidgets();
        int windowId = mc.player.containerMenu.containerId;
        ModularUIGuiContainer gui = new ModularUIGuiContainer(ui, windowId);
        mc.setScreen(gui);
        mc.player.containerMenu = gui.getMenu();
    }
}
