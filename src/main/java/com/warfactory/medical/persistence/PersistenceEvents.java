package com.warfactory.medical.persistence;

import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.DerivedStats;
import com.warfactory.medical.body.MedicalProfile;
import com.warfactory.medical.body.MedicalEffects;
import com.warfactory.medical.body.MedicalEngine;
import com.warfactory.medical.damage.DamageEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PersistenceEvents {

    private PersistenceEvents() {
    }

    @SubscribeEvent
    public static void onPersistentBodyHurt(LivingIncomingDamageEvent event) {
        if (!MedicalConfig.openPersistenceCompat()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Player || entity.level().isClientSide) {
            return;
        }
        if (!(entity instanceof LivingEntity victim) || !OpenPersistenceCompat.isPersistentBody(victim)) {
            return;
        }
        DamageSource src = event.getSource();
        if (src != null && src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        IMedicalData data = MedicalAttachments.get(victim);
        if (data == null) {
            return;
        }
        MedicalProfile profile = data.getProfile();

        DamageEvents.HurtResolution res = DamageEvents.resolveHit(victim, src, amount, profile);
        if (res.majorTrauma()) {
            profile.enterDeadState(false);
            data.bumpRevision();
            event.setAmount(Math.max(amount, victim.getHealth() + 1.0F));
            return;
        }
        if (res.traumaAdded()) {
            profile.markDirty();
            data.bumpRevision();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogoutCopyProfileToBody(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!MedicalConfig.openPersistenceCompat() || !OpenPersistenceCompat.isLoaded()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData playerData = MedicalAttachments.get(player);
        if (playerData == null) {
            return;
        }
        findPersistentBody(player).ifPresent(body -> {
            IMedicalData bodyData = MedicalAttachments.get(body);
            if (bodyData != null) {
                bodyData.load(playerData.save());
                bodyData.bumpRevision();
                if (body instanceof LivingEntity living) {
                    DerivedStats stats = bodyData.getProfile().recompute(MedicalConfig.toPhysiologyParams());
                    MedicalEffects.applyToBody(living, stats);
                }
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoginCopyProfileFromBody(PlayerEvent.PlayerLoggedInEvent event) {
        if (!MedicalConfig.openPersistenceCompat() || !OpenPersistenceCompat.isLoaded()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        IMedicalData playerData = MedicalAttachments.get(player);
        if (playerData == null) {
            return;
        }
        findPersistentBody(player).ifPresent(body -> {
            IMedicalData bodyData = MedicalAttachments.get(body);
            if (bodyData != null) {
                playerData.load(bodyData.save());
                playerData.bumpRevision();
                MedicalEngine.resync(player, true);
            }
        });
    }

    private static Optional<Entity> findPersistentBody(ServerPlayer player) {
        UUID id = player.getUUID();
        AABB box = player.getBoundingBox().inflate(4.0);
        List<Entity> found = player.level().getEntities((Entity) null, box,
                e -> OpenPersistenceCompat.isPersistentBody(e)
                        && OpenPersistenceCompat.bodyOwner(e).map(id::equals).orElse(false));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
}
