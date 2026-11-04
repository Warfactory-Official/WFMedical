package com.warfactory.medical.lab;

import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.KineticPreset;

import java.util.Locale;

/**
 * Part probabilities of a dispersed shot at a posed body ({@link HitZones}). Params: {@code stances=} (default
 * all), {@code aims=} (default all), {@code bearings=} (default 0), {@code moas=} (dispersion list replacing the
 * launchers' own, default each launcher's), {@code hit_samples=} (default 2000), ranges
 * default {@code 0,100,300,800} unless {@code ranges=}. Rows for ranges the round reaches. {@code other} = AHF box
 * fallback (no part).
 */
public final class HitZonesProducer implements LabProducer {
    public static final String ID = "wfmedical:hit_zones";

    private static final LabTable TABLE = LabTable.of("wfmedical:hit_zones")
            .dimStr("launcher").dimStr("round").dimStr("content_origin").dimNum("range_m", "m")
            .dimNum("dispersion_moa", "moa").dimStr("stance").dimStr("aim").dimNum("bearing_deg", "deg").dimStr("part")
            .measure("p_hit_part", "").measure("p_miss", "").measure("samples", "")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] ranges = ctx.param("ranges", null) != null ? ctx.ranges() : WoundsProducer.DEFAULT_RANGES;
        HitZones.Plan plan = HitZones.plan(ctx, ranges, String.join(",", HitZones.STANCES),
                String.join(",", HitZones.AIMS), true);
        for (int i = 0; i < plan.launchers().size(); i++) {
            LabLauncher l = plan.launchers().get(i);
            KineticPreset p = LabRows.preset(l);
            String origin = LabRows.origin(l);
            for (LabFlight.Sample s : plan.flights()[i]) {
                if (!s.reached()) {
                    continue;
                }
                for (double moa : plan.moas(l)) {
                    for (int st : plan.stances()) {
                        for (int a : plan.aims()) {
                            for (int b = 0; b < plan.bearings().size(); b++) {
                                HitZones.Dist d = plan.field().get(moa, s.range(), st, a, b);
                                for (var part : HitZones.PARTS) {
                                    row(sink, l, p, origin, s, moa, st, a, plan.bearings().get(b),
                                            part.name().toLowerCase(Locale.ROOT), d.part(part), d);
                                }
                                row(sink, l, p, origin, s, moa, st, a, plan.bearings().get(b), "other", d.other(), d);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void row(LabSink sink, LabLauncher l, KineticPreset p, String origin, LabFlight.Sample s, double moa,
                            int st, int a, double bearing, String part, double pPart, HitZones.Dist d) {
        sink.row(l.id(), p.id().toString(), origin, s.range(), moa, HitZones.STANCES[st], HitZones.AIMS[a], bearing, part,
                pPart, d.miss(), d.n());
    }
}
