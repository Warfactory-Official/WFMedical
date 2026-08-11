package com.warfactory.medical.attachment;

import com.warfactory.medical.WFMedical;
import com.warfactory.medical.compat.OpenPersistenceCompat;
import com.warfactory.medical.config.MedicalConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Per-entity medical state, held as a NeoForge data attachment.
 *
 * <p>Replaces the 1.20.1 capability ({@code AttachCapabilitiesEvent} + {@code LazyOptional}). Because
 * attachments are lazily created on first access for <em>any</em> entity, {@link #get} keeps the old
 * capability's attach condition: players always, and other entities only when they are an OpenPersistence
 * body and that compat is enabled. Everything else reads as {@code null}, exactly as before.
 */
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

    /** True for the entities the medical system tracks; mirrors the old capability attach condition. */
    public static boolean isEligible(Entity entity) {
        if (entity == null) {
            return false;
        }
        return entity instanceof Player
                || (OpenPersistenceCompat.isPersistentBody(entity) && MedicalConfig.openPersistenceCompat());
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
