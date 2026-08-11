package com.warfactory.medical.client.screen;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ClientUIOpener {

    private ClientUIOpener() {
    }

    /**
     * Opens a purely client-side ModularUI.
     *
     * <p>On LDLib 1.x this had to fake a container: {@code ModularUIGuiContainer} was a
     * {@code AbstractContainerScreen}, so opening one meant calling {@code initWidgets()} by hand,
     * borrowing the player's current {@code containerId} and then swapping {@code player.containerMenu}
     * out from under them. LDLib2 ships {@link ModularUIScreen}, a plain {@code Screen} for menu-less
     * UIs, so none of that is needed -- and the player's real container menu is left alone.
     */
    public static void openClientUI(ModularUI ui) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ui == null) {
            return;
        }
        mc.setScreen(new ModularUIScreen(ui, Component.empty()));
    }
}
