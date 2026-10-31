package com.warfactory.medical.treatment.api.client;

import com.warfactory.medical.body.trauma.TreatmentAction;
import com.warfactory.medical.treatment.client.ClientTreatmentCache;

import javax.annotation.Nullable;

public record ActiveTreatment(TreatmentAction action, long startGameTime, int totalTicks) {

    /** The local player's running treatment; null => none. */
    @Nullable
    public static ActiveTreatment local() {
        return ClientTreatmentCache.view();
    }
}
