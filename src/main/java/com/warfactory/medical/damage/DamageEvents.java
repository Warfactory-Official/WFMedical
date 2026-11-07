package com.warfactory.medical.damage;

import com.warfactory.medical.body.BalanceLog;
import com.warfactory.medical.body.BodyEvents;
import com.warfactory.medical.body.BodyHooks;
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.MedicalProfile;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.hit.HitSampler;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.part.HitboxPart;
import com.warfactory.medical.body.limb.Limb;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.Trauma;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.body.trauma.TraumaType;
import com.wf.wflib.armor.api.ArmorOutcome;
import com.wf.wflib.armor.api.ArmorResult;
import com.warfactory.medical.body.MedicalEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.ArrayList;
import java.util.List;

public final class DamageEvents {

    private static final float BLOCKED_RESIDUAL_FRACTION = 0.15F;
    private static final float BLOCKED_RESIDUAL_MAX = 1.0F;
    private static final float OVERFLOW_BLEED_FACTOR = 0.8F;
    private static final float OVERFLOW_BLEED_MAX = 1.0F;

    private DamageEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttackGapReject(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        boolean isPlayer = victim instanceof Player;
        boolean isBody = !isPlayer && BodyHooks.PERSISTENT_BODY.get().test(victim);
        if (!isPlayer && !isBody) {
            return;
        }
        DamageSource src = event.getSource();
        if (src == null || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        DamageCategory cat = DamageClassifier.classify(src);
        if (HitboxApi.shouldRejectGap(victim, src, cat.hitCategory())) {
            event.setCanceled(true);
        }
    }

    /** Players are claimed from WFLib armour: a declined hit still gets whole-body armour here. */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        if (!handleMedicalHit(event, player)) {
            com.wf.wflib.armor.api.ArmorResolution.applyToEvent(event);
        }
    }

    /** @return true if the medical model took this hit, so nothing else needs to answer for it */
    private static boolean handleMedicalHit(LivingIncomingDamageEvent event, ServerPlayer player) {
        if ((player.isCreative() || player.isSpectator()) && MedicalConfig.effectImmuneInCreative()) {
            return false;
        }

        DamageSource src = event.getSource();
        if (src != null && src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        IMedicalData data = MedicalAttachments.get(player);
        if (data == null) {
            return false;
        }
        MedicalProfile profile = data.getProfile();

        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return false;
        }

        recordDamagingPlayer(profile, src, player);

        boolean alreadyDowned = profile.isDowned() || profile.getState() == HealthState.UNCONSCIOUS;
        if (!alreadyDowned) {
            profile.setDownedHitEnergy(0.0F);
        }
        boolean finishDowned = alreadyDowned && MedicalConfig.finishDownedOnHit();

        HurtResolution res = finishDowned ? null : resolveHit(player, src, amount, profile);
        if (alreadyDowned && res != null && !res.majorTrauma()) {
            finishDowned = finishesDowned(profile, res);
        }
        if (BalanceLog.on()) {
            profile.recompute(MedicalConfig.toPhysiologyParams());
            BalanceLog.hit(player, src, DamageClassifier.classify(src), amount, alreadyDowned, finishDowned,
                    res != null && res.majorTrauma(), profile);
        }
        if (finishDowned || (res != null && res.majorTrauma())) {
            BodyEvents.markDead(player, data, profile);
            event.setAmount(Math.max(amount, player.getHealth() + 1.0F));
            return true;
        }

        if (profile.hasActiveTreatment()) {
            BodyHooks.ACTIVE_TREATMENT.get().cancel(player, "damaged");
        }
        if (!res.traumaAdded()) {
            return true;
        }
        profile.markDirty();
        // Now, not next engine pass: else a downing hit leaves the victim upright for updateIntervalTicks.
        MedicalEngine.resync(player, true);

        if (res.armor() == ArmorOutcome.STOPPED) {
            event.setAmount(Math.min(amount * BLOCKED_RESIDUAL_FRACTION, BLOCKED_RESIDUAL_MAX));
        } else {
            event.setAmount(0.0F);
        }
        return true;
    }

    /**
     * A downed casualty is helpless, not immune: what gets through armour is added up, a head hit counting for
     * more, and past downedFinishEnergy the next hit kills. A few more rounds end it; a graze does not.
     */
    private static boolean finishesDowned(MedicalProfile profile, HurtResolution res) {
        double limit = MedicalConfig.downedFinishEnergy();
        if (limit <= 0.0D) {
            return false;
        }
        float energy = res.through();
        if (res.primary() == LimbType.HEAD) {
            energy *= (float) MedicalConfig.downedHeadMultiplier();
        }
        profile.setDownedHitEnergy(profile.getDownedHitEnergy() + energy);
        return profile.getDownedHitEnergy() >= limit;
    }

    private static void recordDamagingPlayer(MedicalProfile profile, DamageSource src, ServerPlayer victim) {
        if (src == null) {
            return;
        }
        if (src.getEntity() instanceof Player attacker && attacker != victim) {
            profile.setLastDamagingPlayer(attacker.getUUID(), victim.level().getGameTime());
        }
    }

    public static HurtResolution resolveHit(LivingEntity victim, DamageSource src, float amount, MedicalProfile profile) {
        RandomSource rand = victim.getRandom();
        long nowTick = victim.level().getGameTime();
        TraumaTypes.Lookup registry = TraumaTypes.LIVE;

        DamageCategory cat = DamageClassifier.classify(src);
        List<LimbType> limbs = pickLimbs(victim, src, cat, rand);

        LimbType primary = limbs.get(0);
        ArmorResult resolved = ArmorResolution.resolve(victim, primary, src, amount, true);
        ArmorOutcome primaryOutcome = resolved.outcome();
        // Wound energy = armour throughput, not incoming amount.
        float throughEnergy = (float) resolved.through();

        float lethal = MedicalConfig.maxHealthPoints() * (float) DamageConfig.majorTraumaFraction(cat);
        // Armour that was damaged but not stopped still passes some of what it ate on as spall.
        float woundEnergy = (float) resolved.through()
                + (primaryOutcome == ArmorOutcome.REDUCED
                ? (float) (resolved.absorbed() * MedicalConfig.armorSpallFraction()) : 0.0F);
        boolean majorTrauma = primaryOutcome != ArmorOutcome.STOPPED
                && DamageConfig.canInstakillOnImpact(cat)
                && throughEnergy >= lethal;
        boolean limbDowned = majorTrauma && cat == DamageCategory.BALLISTIC && !primary.isVital()
                && MedicalConfig.enableBleedout()
                && throughEnergy < lethal * (float) MedicalConfig.majorTraumaLimbMultiplier();
        if (BalanceLog.on()) {
            StringBuilder names = new StringBuilder();
            for (LimbType lt : limbs) {
                names.append(lt.name().toLowerCase(java.util.Locale.ROOT)).append(',');
            }
            BalanceLog.resolved(names.toString(), primaryOutcome, throughEnergy, lethal, limbDowned);
        }
        if (majorTrauma && !limbDowned) {
            return new HurtResolution(true, false, primaryOutcome, woundEnergy, primary);
        }
        if (limbDowned) {
            profile.setUnconsciousLatched(true);
            profile.markDirty();
        }

        boolean addedAny = false;
        double falloff = AhfConfig.penetrationEnergyFalloff();
        float energy = throughEnergy;
        for (int i = 0; i < limbs.size(); i++) {
            LimbType limb = limbs.get(i);
            ArmorOutcome outcome;
            float absorbed;
            if (i == 0) {
                outcome = primaryOutcome;
                absorbed = (float) resolved.absorbed();
            } else {
                ArmorResult onward = ArmorResolution.resolve(victim, limb, src, energy, false);
                outcome = onward.outcome();
                absorbed = (float) onward.absorbed();
                energy = (float) onward.through();
            }
            addedAny |= applyLimbTrauma(cat, outcome, limb, energy, absorbed, profile, registry, nowTick, rand);
            energy = (float) (energy * falloff);
        }
        return new HurtResolution(false, addedAny || limbDowned, primaryOutcome, woundEnergy, primary);
    }

    private static List<LimbType> pickLimbs(LivingEntity victim, DamageSource src, DamageCategory cat,
                                            RandomSource rand) {
        List<HitboxPart> parts = Strikes.parts(src);
        boolean pierce = AhfConfig.penetrationEnabled();
        if (parts == null) {
            if (!pierce) {
                return List.of(LimbType.of(HitSampler.pick(victim, src, cat.hitCategory(), rand)));
            }
            parts = HitSampler.pickPierced(victim, src, cat.hitCategory(), rand);
        } else if (!pierce) {
            return List.of(LimbType.of(parts.get(0)));
        }
        List<LimbType> limbs = new ArrayList<>(parts.size());
        for (HitboxPart part : parts) {
            limbs.add(LimbType.of(part));
        }
        return limbs;
    }

    /** Package-private so the balance suite can drive the wound model without an entity. */
    static boolean applyLimbTrauma(DamageCategory cat, ArmorOutcome outcome, LimbType limbType,
                                   float energy, float absorbed, MedicalProfile profile, TraumaTypes.Lookup registry,
                                   long nowTick, RandomSource rand) {
        List<Trauma> generated = TraumaGenerator.generate(cat, outcome, limbType, energy, absorbed, registry,
                nowTick, rand);
        Limb targetLimb = profile.limb(limbType);
        targetLimb.rebuildCache();
        float beforeReduction = targetLimb.getCachedHealthReduction();
        boolean added = mergeTrauma(profile, limbType, generated);
        if (added) {
            TraumaEscalation.escalate(targetLimb, limbType, registry, MedicalConfig.maxTraumaPerLimb(), nowTick);
            applyDepletionEffects(targetLimb, limbType, beforeReduction, registry, nowTick, rand);
        }
        return added;
    }

    private static void applyDepletionEffects(Limb limb, LimbType limbType, float beforeReduction,
                                              TraumaTypes.Lookup registry, long nowTick, RandomSource rand) {
        float cap = MedicalConfig.healthShare(limbType) * MedicalConfig.maxHealthPoints();
        if (cap <= 0.0F) {
            return;
        }
        limb.rebuildCache();
        float afterReduction = limb.getCachedHealthReduction();
        if (afterReduction < cap) {
            return;
        }
        int maxPerLimb = MedicalConfig.maxTraumaPerLimb();
        if (MedicalConfig.enableFractures() && !limb.hasCachedFracture()
                && (limbType.isArm() || limbType.isLeg())) {
            TraumaType fracture = resolveTrauma(registry, "fracture", TraumaCategory.FRACTURE);
            if (fracture != null) {
                limb.tryMerge(new Trauma(fracture, limbType, 1.0F, nowTick), maxPerLimb, MedicalConfig::maxTraumaPerCategory);
            }
        }
        float overflow = afterReduction - Math.max(cap, beforeReduction);
        if (overflow > 0.0F && MedicalConfig.enableBleeding()) {
            TraumaType bleed = resolveTrauma(registry, "laceration_large", TraumaCategory.LACERATION);
            if (bleed != null) {
                float sev = Math.min(overflow * OVERFLOW_BLEED_FACTOR, OVERFLOW_BLEED_MAX);
                if (sev > 0.0F) {
                    limb.tryMerge(new Trauma(bleed, limbType, sev, nowTick), maxPerLimb, MedicalConfig::maxTraumaPerCategory);
                }
            }
        }
        limb.rebuildCache();
    }

    private static TraumaType resolveTrauma(TraumaTypes.Lookup registry, String id, TraumaCategory category) {
        TraumaType type = registry.get(id);
        if (type == null) {
            type = registry.firstOfCategory(category);
        }
        return type;
    }

    private static boolean mergeTrauma(MedicalProfile profile, LimbType limb, List<Trauma> generated) {
        boolean added = false;
        int maxPerLimb = MedicalConfig.maxTraumaPerLimb();
        Limb targetLimb = profile.limb(limb);
        for (int i = 0; i < generated.size(); i++) {
            Trauma t = generated.get(i);
            if (t.isFracture() && !MedicalConfig.enableFractures()) {
                continue;
            }
            targetLimb.tryMerge(t, maxPerLimb, MedicalConfig::maxTraumaPerCategory);
            added = true;
        }
        return added;
    }

    public record HurtResolution(boolean majorTrauma, boolean traumaAdded, ArmorOutcome armor, float through,
                                 LimbType primary) {
        public HurtResolution(boolean majorTrauma, boolean traumaAdded, ArmorOutcome armor) {
            this(majorTrauma, traumaAdded, armor, 0.0F, null);
        }
    }
}
