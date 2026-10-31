package com.warfactory.medical.body.client;

import com.warfactory.medical.WFMedical;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.Ui;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.widget.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class HealthBarOverlay implements UiHud.Source {

    public static final HealthBarOverlay INSTANCE = new HealthBarOverlay();

    static final Subject<HealthBarOverlay> SUBJECT =
            Subject.register("medical_health", HealthBarOverlay.class, () -> INSTANCE);

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 9;

    private int top;
    private UiInstance hud;

    private HealthBarOverlay() {
    }

    static void registerSignals() {
        Signals.num(SUBJECT, "top", o -> o.top);
        Signals.num(SUBJECT, "health", o -> player().getHealth());
        Signals.num(SUBJECT, "max", o -> player().getMaxHealth());
        Signals.num(SUBJECT, "color", o -> LimbColors.stateColor(ClientMedicalCache.state()) & 0xFFFFFFFFL);
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    @Override
    public void collect(UiHud.Collector out) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative() || player.isSpectator()
                || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || !(mc.getCameraEntity() instanceof Player)) {
            return;
        }
        if (hud == null) {
            hud = new UiLayout(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "health"),
                    Ui.place("ui.width / 2 - 91", "ui.height - medical_health.top", Point.TOP_LEFT,
                            Ui.sized(BAR_WIDTH, BAR_HEIGHT, Ui.stack(
                                    Ui.bar(BAR_WIDTH, BAR_HEIGHT,
                                            "clamp(nz(medical_health.health / medical_health.max, 0), 0, 1)",
                                            "#ffdd2222", "#ff400000"),
                                    Ui.place(Float.toString(BAR_WIDTH / 2f), Float.toString(BAR_HEIGHT / 2f),
                                            Point.CENTER, Ui.shadowed(
                                                    "{round(medical_health.health)}/{round(medical_health.max)}",
                                                    "medical_health.color"))))), 0).instantiate();
        }
        top = mc.gui.leftHeight;
        out.add(hud);
        // vanilla renderHealth contract: next left-stack layer (armour) sits above this bar
        mc.gui.leftHeight += 10;
    }
}
