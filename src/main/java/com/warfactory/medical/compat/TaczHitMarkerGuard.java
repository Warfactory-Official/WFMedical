package com.warfactory.medical.compat;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import com.warfactory.medical.core.damage.DamageCategory;
import com.warfactory.medical.core.damage.DamageClassifier;
import com.warfactory.medical.core.damage.HitGeometry;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;


public final class TaczHitMarkerGuard {

    private TaczHitMarkerGuard() {
    }

    @SubscribeEvent
    public static void onGunHurtPre(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide().isClient()) {
            return;
        }
        if (!(event.getHurtEntity() instanceof LivingEntity victim) || victim.level().isClientSide) {
            return;
        }
        DamageSource src = event.getDamageSource(GunDamageSourcePart.NON_ARMOR_PIERCING);
        if (src == null) {
            return;
        }
        DamageCategory cat = DamageClassifier.classify(src);
        if (HitGeometry.shouldRejectGap(victim, src, cat)) {
            event.setCanceled(true);
        }
    }
}
