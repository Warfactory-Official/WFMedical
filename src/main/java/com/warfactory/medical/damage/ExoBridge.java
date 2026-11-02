package com.warfactory.medical.damage;

import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.trauma.TreatmentAction;
import com.warfactory.medical.treatment.MedicalItem;
import com.warfactory.medical.treatment.Treatment;
import com.warfactory.medical.treatment.TreatmentService;
import com.wf.wflib.armor.api.exo.LifeSupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Exoskeleton life support through the medical model: danger from blood and state, treatments from the carried medical items. */
final class ExoBridge {
    /** Lower = tried first; unlisted actions (tourniquet, local anaesthetic, resuscitation) need a target and are skipped. */
    private static final Map<TreatmentAction, Integer> ORDER = Map.of(
            TreatmentAction.RESTORE_BLOOD, 0,
            TreatmentAction.REDUCE_BLEEDING, 1,
            TreatmentAction.BOOST_CLOTTING, 2,
            TreatmentAction.SUTURE_WOUND, 3,
            TreatmentAction.HEAL_TRAUMA, 4,
            TreatmentAction.STABILIZE_FRACTURE, 5,
            TreatmentAction.TREAT_BURN, 6,
            TreatmentAction.REDUCE_PAIN, 7,
            TreatmentAction.TREAT_RADIATION, 8);

    private record Candidate(Container container, int slot, Treatment treatment) {
    }

    private ExoBridge() {
    }

    static void register() {
        LifeSupport.DANGER.register(ResourceLocation.fromNamespaceAndPath("wfmedical", "exo_danger"), 10,
                ExoBridge::inDanger);
        LifeSupport.HANDLERS.register(ResourceLocation.fromNamespaceAndPath("wfmedical", "exo_treatment"), 10,
                ExoBridge::treat);
    }

    private static boolean inDanger(ServerPlayer player) {
        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();
        return profile.getState() == HealthState.CRITICAL || profile.getState() == HealthState.UNCONSCIOUS
                || profile.getBloodMl() < MedicalConfig.bloodLowFraction() * profile.getMaxBloodMl();
    }

    private static boolean treat(ServerPlayer player) {
        List<Candidate> candidates = new ArrayList<>();
        for (Container c : LifeSupport.sources(player)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (stack.getItem() instanceof MedicalItem item && item.getTreatment() != null
                        && ORDER.containsKey(item.getTreatment().action())) {
                    candidates.add(new Candidate(c, i, item.getTreatment()));
                }
            }
        }
        candidates.sort(Comparator.comparingInt(c -> ORDER.get(c.treatment().action())));
        for (Candidate c : candidates) {
            if (TreatmentService.applyTargeted(player, c.treatment(), null)) {
                c.container().getItem(c.slot()).shrink(1);
                c.container().setChanged();
                return true;
            }
        }
        return false;
    }
}
