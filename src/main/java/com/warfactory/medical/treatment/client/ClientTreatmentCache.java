package com.warfactory.medical.treatment.client;

import com.warfactory.medical.treatment.api.client.ActiveTreatment;
import com.warfactory.medical.treatment.network.ActiveTreatmentPacket;

/** The local player's active treatment, as last sent. */
public final class ClientTreatmentCache {

    private static volatile ActiveTreatmentPacket activeTreatment;
    private static volatile ActiveTreatment view;

    private ClientTreatmentCache() {
    }

    public static void setActiveTreatment(ActiveTreatmentPacket packet) {
        activeTreatment = packet;
        view = packet.active() ? new ActiveTreatment(packet.action(), packet.startGameTime(), packet.totalTicks()) : null;
    }

    public static ActiveTreatment view() {
        return view;
    }

    public static ActiveTreatmentPacket activeTreatment() {
        return activeTreatment;
    }

    public static boolean hasActiveTreatment() {
        ActiveTreatmentPacket a = activeTreatment;
        return a != null && a.active();
    }

    public static void clear() {
        activeTreatment = null;
        view = null;
    }
}
