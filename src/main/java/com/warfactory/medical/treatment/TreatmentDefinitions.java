package com.warfactory.medical.treatment;

import com.electronwill.nightconfig.core.Config;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TreatmentAction;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.warfactory.medical.body.MedicalDefinitions.*;

/** {@code [[treatment]]} of the definitions file, by item id. */
public final class TreatmentDefinitions {

    private TreatmentDefinitions() {
    }

    public static Map<String, Treatment> load(Path configDir) {
        return com.warfactory.medical.body.MedicalDefinitions.load(configDir, "treatment", TreatmentDefinitions::read,
                Map::isEmpty, TreatmentDefinitions::defaultTreatments);
    }

    /** Bad item RL or duplicate item throws. */
    public static Map<String, Treatment> read(Config root) {
        Map<String, Treatment> out = new LinkedHashMap<>();
        for (Config t : tables(root, "treatment")) {
            String item = str(t, "item", null);
            Treatment treatment = readTreatment(t);
            if (item != null && treatment != null) {
                ResourceLocation.parse(item);
                if (out.putIfAbsent(item, treatment) != null) {
                    throw new IllegalArgumentException("Duplicate treatment item: " + item);
                }
            }
        }
        return out;
    }

    private static Treatment readTreatment(Config t) {
        TreatmentAction action = parseAction(str(t, "action", null));
        if (action == null) {
            return null;
        }
        Set<TraumaCategory> categories = EnumSet.noneOf(TraumaCategory.class);
        for (String catName : strList(t, "categories")) {
            TraumaCategory c = TraumaCategory.byName(catName, null);
            if (c != null) {
                categories.add(c);
            }
        }
        return new Treatment(
                action,
                categories,
                flt(t, "magnitude", 0.0F),
                dbl(t, "bloodRestoreMl", 0.0D),
                intOf(t, "useDurationTicks", 20),
                bool(t, "removesTrauma", false)
        );
    }

    public static Map<String, Treatment> defaultTreatments() {
        Map<String, Treatment> m = new LinkedHashMap<>();
        m.put("wfmedical:bandage", new Treatment(TreatmentAction.REDUCE_BLEEDING,
                EnumSet.of(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE, TraumaCategory.CRUSH_INJURY),
                0.5F, 0.0D, 40, false));
        m.put("wfmedical:splint", new Treatment(TreatmentAction.STABILIZE_FRACTURE,
                EnumSet.of(TraumaCategory.FRACTURE), 1.0F, 0.0D, 60, false));
        m.put("wfmedical:suture_kit", new Treatment(TreatmentAction.SUTURE_WOUND,
                EnumSet.of(TraumaCategory.LACERATION, TraumaCategory.PUNCTURE, TraumaCategory.INTERNAL_BLEEDING),
                1.0F, 0.0D, 60, false));
        m.put("wfmedical:blood_bag", new Treatment(TreatmentAction.RESTORE_BLOOD,
                Collections.emptySet(), 0.0F, 1000.0D, 120, false));
        m.put("wfmedical:painkillers", new Treatment(TreatmentAction.REDUCE_PAIN,
                Collections.emptySet(), 0.5F, 0.0D, 30, false));
        m.put("wfmedical:local_anesthetic", new Treatment(TreatmentAction.NUMB_LIMB,
                Collections.emptySet(), 0.9F, 0.0D, 50, false));
        m.put("wfmedical:hemostatic", new Treatment(TreatmentAction.BOOST_CLOTTING,
                Collections.emptySet(), 0.6F, 0.0D, 50, false));
        m.put("wfmedical:tourniquet", new Treatment(TreatmentAction.APPLY_TOURNIQUET,
                Collections.emptySet(), 0.0F, 0.0D, 20, false));
        m.put("wfmedical:medkit", new Treatment(TreatmentAction.HEAL_TRAUMA,
                Collections.emptySet(), 1.0F, 250.0D, 180, true));
        m.put("wfmedical:burn_ointment", new Treatment(TreatmentAction.TREAT_BURN,
                EnumSet.of(TraumaCategory.BURN, TraumaCategory.CHEMICAL_BURN), 0.8F, 0.0D, 60, false));
        m.put("wfmedical:antirad_shot", new Treatment(TreatmentAction.TREAT_RADIATION,
                EnumSet.of(TraumaCategory.RADIATION_BURN), 1.0F, 0.0D, 40, true));
        return m;
    }
}
