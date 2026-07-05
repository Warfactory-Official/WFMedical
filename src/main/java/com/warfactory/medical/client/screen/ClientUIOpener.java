package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.Minecraft;

/**
 * Client-only helper that opens a purely client-side {@link ModularUI} (no server networking), mirroring
 * {@code UIFactory.initClientUI}. CLIENT-ONLY: references {@code net.minecraft.client.Minecraft} and the
 * {@code @Environment(CLIENT)} {@link ModularUIGuiContainer}; only call it from client code.
 */
public final class ClientUIOpener {

    private ClientUIOpener() {
    }

    /**
     * Wire the given UI to a client {@link ModularUIGuiContainer} and display it. Calls
     * {@link ModularUI#initWidgets()} first (required so widgets receive {@code gui}/{@code initWidget()}),
     * then swaps the player's container menu to the UI's menu so vanilla input routing works.
     *
     * <p>No-op when there is no local player. The window id reuses the current container id, which is only
     * used for server↔client widget-update packet matching and is irrelevant for a client-only UI.</p>
     *
     * @param ui the fully-built (but not yet initialised) ModularUI to open
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
