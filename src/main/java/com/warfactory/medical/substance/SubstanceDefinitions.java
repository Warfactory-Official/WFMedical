package com.warfactory.medical.substance;

import com.electronwill.nightconfig.core.Config;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.warfactory.medical.body.MedicalDefinitions.*;

/** {@code [[substance]]} of the definitions file. */
public final class SubstanceDefinitions {

    private SubstanceDefinitions() {
    }

    public static List<Substance> load(Path configDir) {
        return com.warfactory.medical.body.MedicalDefinitions.load(configDir, "substance", SubstanceDefinitions::read,
                List::isEmpty, SubstanceDefinitions::defaultSubstances);
    }

    /** Bad item RL or duplicate item throws. */
    public static List<Substance> read(Config root) {
        List<Substance> out = new ArrayList<>();
        Set<String> items = new HashSet<>();
        for (Config t : tables(root, "substance")) {
            Substance substance = readSubstance(t);
            if (substance != null) {
                ResourceLocation.parse(substance.itemId());
                if (!items.add(substance.itemId())) {
                    throw new IllegalArgumentException("Duplicate substance item: " + substance.itemId());
                }
                out.add(substance);
            }
        }
        return out;
    }

    private static Substance readSubstance(Config t) {
        String id = str(t, "id", null);
        String item = str(t, "item", null);
        if (id == null || id.isEmpty() || item == null || item.isEmpty()) {
            return null;
        }
        return new Substance(
                id,
                item,
                flt(t, "painSuppression", 0.0F),
                flt(t, "doseLoad", 0.0F),
                flt(t, "overdoseThreshold", 1.0F),
                intOf(t, "unconsciousTicks", intOf(t, "blackoutTicks", 200)),
                flt(t, "lethalThreshold", 0.0F),
                bool(t, "antidote", false),
                flt(t, "reversalAmount", 0.0F),
                intOf(t, "useDurationTicks", 40),
                dbl(t, "bloodRestoreMl", 0.0D),
                flt(t, "clottingBoost", 0.0F),
                flt(t, "stimulantStrength", 0.0F),
                intOf(t, "effectTicks", 0)
        );
    }

    public static List<Substance> defaultSubstances() {
        return List.of(Substances.defaultMorphine(), Substances.defaultNaloxone(), Substances.defaultCombatStimulant());
    }
}
