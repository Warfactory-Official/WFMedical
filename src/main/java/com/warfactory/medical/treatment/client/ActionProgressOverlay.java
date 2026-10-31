package com.warfactory.medical.treatment.client;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.TreatmentAction;
import com.warfactory.medical.treatment.network.ActiveTreatmentPacket;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.Ui;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/** Treatment progress: HUD layer + {@link #bar} shared with the sheet. */
public final class ActionProgressOverlay implements UiHud.Source {

    public static final ActionProgressOverlay INSTANCE = new ActionProgressOverlay();

    static final Subject<ActionProgressOverlay> SUBJECT =
            Subject.register("medical_treatment", ActionProgressOverlay.class, () -> INSTANCE);

    private static final int BAR_WIDTH = 100;
    private static final int LABEL_MAX = 64;

    private ActiveTreatmentPacket labelled;
    private boolean labelledTarget;
    private String label = "";
    private UiInstance hud;

    private ActionProgressOverlay() {
    }

    static void registerSignals() {
        Signals.bool(SUBJECT, "active", o -> o.packet() != null);
        Signals.num(SUBJECT, "fraction", ActionProgressOverlay::fraction);
        Signals.text(SUBJECT, "label", LABEL_MAX, o -> o.label);
    }

    /** {@code width} wide, 19 tall: label, 8 px bar at y 11, percent on the bar. */
    static WidgetDef bar(float width) {
        String half = Float.toString(width / 2);
        return Ui.when("medical_treatment.active", Ui.sized(width, 19, Ui.stack(
                Ui.place(half, "0", Point.TOP, Ui.shadowed("{medical_treatment.label}", "#ffffffff")),
                Ui.at("0", "11", Ui.bar(width, 8, "medical_treatment.fraction", "#ff33cc99", "#ff10402f")),
                Ui.place(half, "15", Point.CENTER,
                        Ui.shadowed("{round(medical_treatment.fraction * 100)}%", "#ffffffff")))));
    }

    @Override
    public void collect(UiHud.Collector out) {
        if (packet() == null || Minecraft.getInstance().level == null) {
            return;
        }
        if (hud == null) {
            hud = new UiLayout(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "action_progress"),
                    Ui.place("ui.width / 2 - " + BAR_WIDTH / 2, "ui.height - 71", Point.TOP_LEFT, bar(BAR_WIDTH)), 0)
                    .instantiate();
        }
        out.add(hud);
    }

    /** Client tick: label rebuilt when the treatment or its target's presence changes. */
    void tick() {
        ActiveTreatmentPacket a = packet();
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.level == null) {
            labelled = null;
            return;
        }
        Entity target = a.targetEntityId() >= 0 ? mc.level.getEntity(a.targetEntityId()) : null;
        boolean named = target != null && target != mc.player;
        if (a == labelled && named == labelledTarget) {
            return;
        }
        labelled = a;
        labelledTarget = named;
        String action = actionLabel(a.action(), a.limb());
        label = named ? target.getName().getString() + " -> " + action : action;
    }

    private ActiveTreatmentPacket packet() {
        ActiveTreatmentPacket a = ClientTreatmentCache.activeTreatment();
        return a != null && a.active() ? a : null;
    }

    private double fraction() {
        ActiveTreatmentPacket a = packet();
        Minecraft mc = Minecraft.getInstance();
        if (a == null || mc.level == null) {
            return 0;
        }
        if (a.totalTicks() <= 0) {
            return 1;
        }
        return Math.clamp((mc.level.getGameTime() - a.startGameTime()) / (double) a.totalTicks(), 0, 1);
    }

    private static String actionLabel(TreatmentAction action, LimbType limb) {
        String actionName = Component.translatable(action == null ? "gui.wfmedical.action.generic"
                : "gui.wfmedical.action." + action.name().toLowerCase(Locale.ROOT)).getString();
        return limb == null ? actionName : actionName + " (" + MedicalUIParts.limbName(limb).getString() + ")";
    }
}
