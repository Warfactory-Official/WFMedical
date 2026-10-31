package com.warfactory.medical.body.attachment;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.BodyHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Per-entity medical state (data attachment). Attachments exist for any entity => {@link #get} gates: players + {@code BodyHooks.PERSISTENT_BODY}. */
public final class MedicalAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFMedical.MOD_ID);

    public static final Supplier<AttachmentType<MedicalData>> MEDICAL = ATTACHMENT_TYPES.register(
            "medical", () -> AttachmentType.serializable(MedicalData::new).build());

    private MedicalAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    public static boolean isEligible(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof Player || BodyHooks.PERSISTENT_BODY.get().test(entity);
    }

    /** Medical data for {@code entity}, or {@code null} if it is not a tracked entity. */
    public static IMedicalData get(Entity entity) {
        if (!isEligible(entity)) {
            return null;
        }
        return entity.getData(MEDICAL);
    }

    public static void copy(Player original, Player clone) {
        IMedicalData oldData = get(original);
        IMedicalData newData = get(clone);
        if (oldData == null || newData == null) {
            return;
        }
        newData.load(oldData.save());
        newData.bumpRevision();
    }
}
