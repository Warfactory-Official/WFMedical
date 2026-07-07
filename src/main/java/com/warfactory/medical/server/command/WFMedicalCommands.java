package com.warfactory.medical.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.capability.IMedicalData;
import com.warfactory.medical.capability.MedicalCapabilities;
import com.warfactory.medical.config.MedicalConfig;
import com.warfactory.medical.core.DerivedStats;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.damage.HitGeometry;
import com.warfactory.medical.core.damage.rig.HumanoidRig;
import com.warfactory.medical.core.damage.rig.Obb;
import com.warfactory.medical.core.limb.Limb;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.core.substance.Substance;
import com.warfactory.medical.core.substance.SubstanceRegistry;
import com.warfactory.medical.core.trauma.Trauma;
import com.warfactory.medical.core.trauma.TraumaCategory;
import com.warfactory.medical.core.trauma.TraumaRegistry;
import com.warfactory.medical.core.trauma.TraumaType;
import com.warfactory.medical.network.MedicalNetworking;
import com.warfactory.medical.server.MedicalEngine;
import com.warfactory.medical.server.SubstanceService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Admin/debug command suite rooted at {@code /wfmedical} (alias {@code /wfmed}), permission level 2.
 * FORGE-bus only (server-authoritative). Each mutating subcommand calls {@link MedicalEngine#resync}
 * so derived stats, vanilla body, and the client snapshot all update immediately. Targets without a
 * medical capability are skipped with a warning; bad input reports failure and returns 0.
 */
@Mod.EventBusSubscriber(modid = WFMedical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WFMedicalCommands {

    private static final int DEFAULT_OVERDOSE_TICKS = 200;
    private static final SuggestionProvider<CommandSourceStack> LIMB_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(LimbType.VALUES).map(Enum::name), b);

    // ------------------------------------------------------------------ suggestion providers
    private static final SuggestionProvider<CommandSourceStack> TRAUMA_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    TraumaRegistry.active().all().stream().map(TraumaType::getId), b);
    private static final SuggestionProvider<CommandSourceStack> SUBSTANCE_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    SubstanceRegistry.active().all().stream().map(Substance::id), b);
    private static final SuggestionProvider<CommandSourceStack> STATE_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HealthState.values()).map(Enum::name), b);

    private WFMedicalCommands() {
    }

    // ------------------------------------------------------------------ registration

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wfmedical")
                .requires(src -> src.hasPermission(2));

        // --- query [targets]
        root.then(Commands.literal("query")
                .executes(ctx -> cmdQuery(ctx.getSource(), self(ctx)))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdQuery(ctx.getSource(), players(ctx)))));

        // --- heal [targets]
        root.then(Commands.literal("heal")
                .executes(ctx -> cmdHeal(ctx.getSource(), self(ctx)))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdHeal(ctx.getSource(), players(ctx)))));

        // --- reset [targets]
        root.then(Commands.literal("reset")
                .executes(ctx -> cmdReset(ctx.getSource(), self(ctx)))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdReset(ctx.getSource(), players(ctx)))));

        // --- kill [targets]
        root.then(Commands.literal("kill")
                .executes(ctx -> cmdKill(ctx.getSource(), self(ctx)))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdKill(ctx.getSource(), players(ctx)))));

        // --- revive [targets]
        root.then(Commands.literal("revive")
                .executes(ctx -> cmdRevive(ctx.getSource(), self(ctx)))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdRevive(ctx.getSource(), players(ctx)))));

        // --- trauma add|remove|clear
        root.then(Commands.literal("trauma")
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("limb", StringArgumentType.word())
                                        .suggests(LIMB_SUGGESTIONS)
                                        .then(Commands.argument("traumaId", StringArgumentType.word())
                                                .suggests(TRAUMA_SUGGESTIONS)
                                                .executes(ctx -> cmdTraumaAdd(ctx.getSource(), players(ctx),
                                                        StringArgumentType.getString(ctx, "limb"),
                                                        StringArgumentType.getString(ctx, "traumaId"),
                                                        Float.NaN))
                                                .then(Commands.argument("severity", FloatArgumentType.floatArg(0.0F))
                                                        .executes(ctx -> cmdTraumaAdd(ctx.getSource(), players(ctx),
                                                                StringArgumentType.getString(ctx, "limb"),
                                                                StringArgumentType.getString(ctx, "traumaId"),
                                                                FloatArgumentType.getFloat(ctx, "severity"))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("limb", StringArgumentType.word())
                                        .suggests(LIMB_SUGGESTIONS)
                                        .then(Commands.argument("traumaId", StringArgumentType.word())
                                                .suggests(TRAUMA_SUGGESTIONS)
                                                .executes(ctx -> cmdTraumaRemove(ctx.getSource(), players(ctx),
                                                        StringArgumentType.getString(ctx, "limb"),
                                                        StringArgumentType.getString(ctx, "traumaId")))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> cmdTraumaClear(ctx.getSource(), players(ctx), null))
                                .then(Commands.argument("limb", StringArgumentType.word())
                                        .suggests(LIMB_SUGGESTIONS)
                                        .executes(ctx -> cmdTraumaClear(ctx.getSource(), players(ctx),
                                                StringArgumentType.getString(ctx, "limb")))))));

        // --- blood set|add
        root.then(Commands.literal("blood")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("ml", DoubleArgumentType.doubleArg(0.0D))
                                        .executes(ctx -> cmdBlood(ctx.getSource(), players(ctx),
                                                DoubleArgumentType.getDouble(ctx, "ml"), false)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("ml", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> cmdBlood(ctx.getSource(), players(ctx),
                                                DoubleArgumentType.getDouble(ctx, "ml"), true))))));

        // --- suppression set|clear
        root.then(Commands.literal("suppression")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F))
                                        .executes(ctx -> cmdSuppression(ctx.getSource(), players(ctx),
                                                FloatArgumentType.getFloat(ctx, "value"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> cmdSuppression(ctx.getSource(), players(ctx), 0.0F)))));

        // --- drug set|add|clear
        root.then(Commands.literal("drug")
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("load", FloatArgumentType.floatArg(0.0F))
                                        .executes(ctx -> cmdDrug(ctx.getSource(), players(ctx),
                                                FloatArgumentType.getFloat(ctx, "load"), DrugMode.SET)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("load", FloatArgumentType.floatArg())
                                        .executes(ctx -> cmdDrug(ctx.getSource(), players(ctx),
                                                FloatArgumentType.getFloat(ctx, "load"), DrugMode.ADD)))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> cmdDrug(ctx.getSource(), players(ctx), 0.0F, DrugMode.CLEAR)))));

        // --- substance <targets> <substanceId>
        root.then(Commands.literal("substance")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("substanceId", StringArgumentType.word())
                                .suggests(SUBSTANCE_SUGGESTIONS)
                                .executes(ctx -> cmdSubstance(ctx.getSource(), players(ctx),
                                        StringArgumentType.getString(ctx, "substanceId"))))));

        // --- unconscious <targets> [ticks]
        //   Replaces the removed `bleedout` and `overdose` subcommands with a single command for the one
        //   unified UNCONSCIOUS state, exposing both of its internal causes:
        //     no ticks   -> bleed-out unconscious (death timer runs)      -- the old bleed-out cause
        //     with ticks -> timed overdose unconscious (wake timer runs)  -- the old overdose cause
        root.then(Commands.literal("unconscious")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdUnconscious(ctx.getSource(), players(ctx), -1))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> cmdUnconscious(ctx.getSource(), players(ctx),
                                        IntegerArgumentType.getInteger(ctx, "ticks"))))));

        // --- asphyxia <targets>
        root.then(Commands.literal("asphyxia")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> cmdAsphyxia(ctx.getSource(), players(ctx)))));

        // --- state <targets> <state>
        root.then(Commands.literal("state")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests(STATE_SUGGESTIONS)
                                .executes(ctx -> cmdState(ctx.getSource(), players(ctx),
                                        StringArgumentType.getString(ctx, "state"))))));

        // --- fracture <targets> <limb>
        root.then(Commands.literal("fracture")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("limb", StringArgumentType.word())
                                .suggests(LIMB_SUGGESTIONS)
                                .executes(ctx -> cmdFracture(ctx.getSource(), players(ctx),
                                        StringArgumentType.getString(ctx, "limb"))))));

        // --- bleed <targets> <limb> [severity]
        root.then(Commands.literal("bleed")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("limb", StringArgumentType.word())
                                .suggests(LIMB_SUGGESTIONS)
                                .executes(ctx -> cmdBleed(ctx.getSource(), players(ctx),
                                        StringArgumentType.getString(ctx, "limb"), Float.NaN))
                                .then(Commands.argument("severity", FloatArgumentType.floatArg(0.0F))
                                        .executes(ctx -> cmdBleed(ctx.getSource(), players(ctx),
                                                StringArgumentType.getString(ctx, "limb"),
                                                FloatArgumentType.getFloat(ctx, "severity")))))));

        // --- hittest [maxDist]
        //   Debug look-test: ray-cast from the caller's eye and classify the aimed-at entity's limb via
        //   HitGeometry.classifyRay. Read-only, op-gated (inherited from root), server-authoritative.
        root.then(Commands.literal("hittest")
                .executes(ctx -> cmdHitTest(ctx.getSource(), 0.0D))
                .then(Commands.argument("maxDist", DoubleArgumentType.doubleArg(0.0D))
                        .executes(ctx -> cmdHitTest(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "maxDist")))));

        // --- rig [maxDist]
        //   Debug Tier-2 rig dump: ray-cast from the caller's eye and, for the aimed-at LivingEntity, dump
        //   the six posed limb OBBs (limb, entity-local centre, half-extents) from HumanoidRig.compute plus
        //   the LimbType that HitGeometry.classifyRay resolves for the caller's aim ray. Read-only, op-gated
        //   (inherited from root), server-authoritative.
        root.then(Commands.literal("rig")
                .executes(ctx -> cmdRig(ctx.getSource(), 0.0D))
                .then(Commands.argument("maxDist", DoubleArgumentType.doubleArg(0.0D))
                        .executes(ctx -> cmdRig(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "maxDist")))));

        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
        dispatcher.register(Commands.literal("wfmed").requires(src -> src.hasPermission(2)).redirect(node));
    }

    // ------------------------------------------------------------------ target resolution helpers

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return Collections.singletonList(ctx.getSource().getPlayerOrException());
    }

    private static Collection<ServerPlayer> players(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    // ------------------------------------------------------------------ subcommand implementations

    /**
     * Dump the full medical state for each target (read-only; recomputes but does NOT resync).
     */
    private static int cmdQuery(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer p : targets) {
            IMedicalData data = MedicalCapabilities.get(p);
            if (data == null) {
                warnNoCap(src, p);
                continue;
            }
            MedicalProfile profile = data.getProfile();
            DerivedStats stats = profile.recompute(MedicalConfig.toPhysiologyParams());
            String dump = buildDump(p, profile, stats);
            src.sendSuccess(() -> Component.literal(dump), false);
            count++;
        }
        return count;
    }

    /**
     * Debug look-test for the geometric hit-location classifier. Ray-casts from the executor's eye, finds
     * the nearest aimed-at {@link LivingEntity}, and reports the classified {@link LimbType}. Read-only.
     */
    private static int cmdHitTest(CommandSourceStack src, double maxDist) throws CommandSyntaxException {
        ServerPlayer self = src.getPlayerOrException();
        double reach = maxDist > 0.0D ? maxDist : 5.0D;

        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        // Nearest LivingEntity crossed by the eye->end segment (self excluded).
        AABB search = self.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(self.level(), self, eye, end, search,
                e -> e instanceof LivingEntity && e != self && e.isPickable());
        if (result == null || !(result.getEntity() instanceof LivingEntity target)) {
            src.sendFailure(Component.literal("[wfmedical] hittest: no target within "
                    + fmt(reach) + " blocks of your aim."));
            return 0;
        }

        LimbType limb = HitGeometry.classifyRay(target, eye, end);
        Optional<Vec3> worldHit = target.getBoundingBox().clip(eye, end);
        String targetName = target.getName().getString();
        String limbText = (limb != null)
                ? limb.name()
                : "(none - downed/flattened box -> weighted-sampler fallback)";
        String hitText = worldHit
                .map(v -> " @ (" + fmt(v.x) + ", " + fmt(v.y) + ", " + fmt(v.z) + ")")
                .orElse("");
        src.sendSuccess(() -> Component.literal("[wfmedical] hittest -> " + targetName
                + ": limb=" + limbText + hitText), false);
        return 1;
    }

    /**
     * Debug dump of the Tier-2 humanoid rig OBBs for the aimed-at entity. Read-only.
     */
    private static int cmdRig(CommandSourceStack src, double maxDist) throws CommandSyntaxException {
        ServerPlayer self = src.getPlayerOrException();
        double reach = maxDist > 0.0D ? maxDist : 5.0D;

        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        // Nearest LivingEntity crossed by the eye->end segment (self excluded).
        AABB search = self.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(self.level(), self, eye, end, search,
                e -> e instanceof LivingEntity && e != self && e.isPickable());
        if (result == null || !(result.getEntity() instanceof LivingEntity target)) {
            src.sendFailure(Component.literal("[wfmedical] rig: no target within "
                    + fmt(reach) + " blocks of your aim."));
            return 0;
        }

        HumanoidRig.LocalRig rig = HumanoidRig.compute(target);
        LimbType limb = HitGeometry.classifyRay(target, eye, end);
        String limbText = (limb != null)
                ? limb.name()
                : "(none - rig miss / downed -> Tier-1 / weighted-sampler fallback)";

        StringBuilder sb = new StringBuilder();
        sb.append("=== wfmedical rig: ").append(target.getName().getString()).append(" ===");
        sb.append("\n classifyRay(aim) -> ").append(limbText);
        sb.append("\n local OBBs (feet origin, Y-up, X=right, Z=front):");
        for (Obb obb : rig.all()) {
            sb.append("\n [").append(obb.limb().name()).append("]")
                    .append(" c=(").append(fmt(obb.center().x)).append(", ")
                    .append(fmt(obb.center().y)).append(", ")
                    .append(fmt(obb.center().z)).append(")")
                    .append(" half=(").append(fmt(obb.half().x)).append(", ")
                    .append(fmt(obb.half().y)).append(", ")
                    .append(fmt(obb.half().z)).append(")");
        }
        String dump = sb.toString();
        src.sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    /**
     * Full heal: clear all trauma, top up blood, drop pain/drug/overdose/bleed-out, back to full health.
     */
    private static int cmdHeal(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            clearAllTrauma(profile);
            profile.setBloodMl(profile.getMaxBloodMl());
            profile.setPainSuppression(0.0F);
            profile.setPainKoSince(0L);
            profile.setAdrenalineExhausted(false);
            profile.setDrugLoad(0.0F);
            profile.setStimulant(0.0F);
            profile.setClottingBoost(0.0F);
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.clearAsphyxia();
            profile.setBleedoutSinceTick(-1L);
            profile.setForcedState(null);
            profile.setState(HealthState.HEALTHY);
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Fully healed " + n + " player(s)."), true);
        return n;
    }

    /**
     * Replace the profile with a pristine default, then resync to full derived health.
     */
    private static int cmdReset(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            MedicalProfile fresh = new MedicalProfile(MedicalConfig.maxBloodMl());
            // Carry the last broadcast-downed mirror onto the fresh profile so the resync's edge-detection
            // sees the true->false transition and pushes a downed=false packet. Without this, a player who
            // was bleeding out / overdose-unconscious when reset would keep a stale downed pose on all trackers (the
            // fresh profile's mirror defaults to false, matching the now-false state, so nothing is sent).
            fresh.setLastBroadcastDowned(profile.isLastBroadcastDowned());
            data.setProfile(fresh);
            data.bumpRevision();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Reset " + n + " player(s) to a pristine profile."), true);
        return n;
    }

    /**
     * Kill each target regardless of bleed-out. Marks the profile DEAD so the bleed-out interception stands
     * down, then routes through {@code p.kill()} (genericKill, bypasses invulnerability, invokes die()).
     */
    private static int cmdKill(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.clearAsphyxia();
            profile.setBleedoutSinceTick(-1L);
            profile.setForcedState(null);
            profile.setState(HealthState.DEAD);
            if (profile.isLastBroadcastDowned()) {
                MedicalNetworking.broadcastDowned(p, false);
                profile.setLastBroadcastDowned(false);
            }
            profile.markDirty();
            data.bumpRevision();
            p.kill(); // hurt(genericKill, MAX) -> die(); bypasses the bleed-out interception via the /kill fix.
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Killed " + n + " player(s)."), true);
        return n;
    }

    /**
     * Bring a downed player back up by reversing the lethal CAUSE before resyncing (just flipping transient
     * downed flags would be re-derived back to UNCONSCIOUS immediately). Clears forced overrides, overdose
     * markers, major trauma (leaves minor wounds), and tops blood above the low threshold. Never resurrects
     * a dead/removed entity.
     */
    private static int cmdRevive(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (!p.isAlive() || p.isRemoved()) {
                src.sendFailure(Component.literal("[wfmedical] " + name(p) + " is not alive; cannot revive."));
                return false;
            }
            // Clear every downed CAUSE, not just the state label.
            profile.setForcedState(null);
            profile.setState(HealthState.HEALTHY);
            profile.setBleedoutSinceTick(-1L);
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.clearAsphyxia();
            profile.setDrugLoad(0.0F); // otherwise a severe overdose would immediately re-knock-out on the next pass
            // Evacuate the life-threatening trauma that drives effectiveMaxHealth to 0 (major wounds only;
            // minor scratches are left in place so this stays a revive, not a full heal).
            clearMajorTrauma(profile);
            // Top blood above the low-penalty threshold so blood loss no longer forces lethal/critical.
            double safeBlood = MedicalConfig.bloodLowFraction() * profile.getMaxBloodMl();
            if (profile.getBloodMl() < safeBlood) {
                profile.setBloodMl(safeBlood);
            }
            profile.markDirty();
            MedicalEngine.resync(p);
            DerivedStats stats = profile.cached();
            float target = Math.min(stats.effectiveMaxHealth(), Math.max(1.0F, stats.effectiveMaxHealth() * 0.5F));
            if (p.getHealth() < target) {
                p.setHealth(target);
            }
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Revived " + n + " player(s)."), true);
        return n;
    }

    /**
     * Add a trauma of the given registry id to a limb (severity optional, default = base contribution).
     */
    private static int cmdTraumaAdd(CommandSourceStack src, Collection<ServerPlayer> targets,
                                    String limbName, String traumaId, float severity) {
        LimbType limb = parseLimb(src, limbName);
        if (limb == null) {
            return 0;
        }
        TraumaType type = TraumaRegistry.active().get(traumaId);
        if (type == null) {
            src.sendFailure(Component.literal("[wfmedical] Unknown trauma id: " + traumaId));
            return 0;
        }
        float sev = Float.isNaN(severity) ? type.getSeverityContribution() : severity;
        int n = forEach(src, targets, (s, p, data, profile) -> {
            long now = p.level().getGameTime();
            profile.limb(limb).tryMerge(new Trauma(type, limb, sev, now), MedicalConfig.maxTraumaPerLimb());
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Added trauma '" + type.getId() + "' (sev "
                + fmt(sev) + ") to " + limb.name() + " on " + n + " player(s)."), true);
        return n;
    }

    /**
     * Remove the first trauma matching the id on a limb.
     */
    private static int cmdTraumaRemove(CommandSourceStack src, Collection<ServerPlayer> targets,
                                       String limbName, String traumaId) {
        LimbType limb = parseLimb(src, limbName);
        if (limb == null) {
            return 0;
        }
        int n = forEach(src, targets, (s, p, data, profile) -> {
            Limb l = profile.limb(limb);
            Trauma match = null;
            for (Trauma t : l.getTraumas()) {
                if (t.getType().getId().equals(traumaId)) {
                    match = t;
                    break;
                }
            }
            if (match == null) {
                return false;
            }
            l.removeTrauma(match);
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Removed trauma '" + traumaId + "' from "
                + limb.name() + " on " + n + " player(s)."), true);
        return n;
    }

    /**
     * Clear all trauma on one limb, or on every limb when {@code limbName} is null.
     */
    private static int cmdTraumaClear(CommandSourceStack src, Collection<ServerPlayer> targets, String limbName) {
        final LimbType only;
        if (limbName != null) {
            only = parseLimb(src, limbName);
            if (only == null) {
                return 0;
            }
        } else {
            only = null;
        }
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (only != null) {
                clearLimbTrauma(profile.limb(only));
            } else {
                clearAllTrauma(profile);
            }
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Cleared trauma on "
                + (only == null ? "all limbs" : only.name()) + " for " + n + " player(s)."), true);
        return n;
    }

    private static int cmdBlood(CommandSourceStack src, Collection<ServerPlayer> targets, double ml, boolean add) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            profile.setBloodMl(add ? profile.getBloodMl() + ml : ml);
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] " + (add ? "Added " : "Set ") + fmt(ml)
                + " ml blood for " + n + " player(s)."), true);
        return n;
    }

    private static int cmdSuppression(CommandSourceStack src, Collection<ServerPlayer> targets, float value) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            profile.setPainSuppression(value);
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Set pain suppression to " + fmt(value)
                + " for " + n + " player(s)."), true);
        return n;
    }

    /**
     * Set/add/clear drug load. When load crosses the lethal threshold an unconsciousness is forced immediately
     * (otherwise the engine would only trigger it on the next physiology pass).
     */
    private static int cmdDrug(CommandSourceStack src, Collection<ServerPlayer> targets, float load, DrugMode mode) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            float next = switch (mode) {
                case SET -> load;
                case ADD -> profile.getDrugLoad() + load;
                case CLEAR -> 0.0F;
            };
            profile.setDrugLoad(next);
            if (mode == DrugMode.CLEAR) {
                profile.setOverdoseUnconscious(false);
                profile.setOverdoseUntilTick(0L);
            } else {
                double lethal = MedicalConfig.overdoseLethalThreshold();
                if (lethal > 0.0D && profile.getDrugLoad() >= lethal) {
                    profile.setOverdoseUntilTick(p.level().getGameTime() + DEFAULT_OVERDOSE_TICKS);
                    profile.setOverdoseUnconscious(true);
                }
            }
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Drug load " + mode.name().toLowerCase()
                + " -> applied for " + n + " player(s)."), true);
        return n;
    }

    /**
     * Inject a registered substance directly, exercising the full opioid / overdose / antidote path.
     */
    private static int cmdSubstance(CommandSourceStack src, Collection<ServerPlayer> targets, String substanceId) {
        Substance substance = resolveSubstance(substanceId);
        if (substance == null) {
            src.sendFailure(Component.literal("[wfmedical] Unknown substance id: " + substanceId));
            return 0;
        }
        int n = forEach(src, targets, (s, p, data, profile) -> {
            boolean injected = SubstanceService.inject(p, substance);
            // Reconcile the downed broadcast (an overdose unconsciousness may have started) and re-push the snapshot.
            MedicalEngine.resync(p);
            return injected;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Injected '" + substance.id() + "' into "
                + n + " player(s)."), true);
        return n;
    }

    /**
     * Force the unified {@link HealthState#UNCONSCIOUS} state via one of its two internal causes:
     * no-arg = bleed-out cause (admin-forced override + death timer runs); ticks arg = overdose cause
     * (wake timer runs, player recovers after {@code ticks}).
     */
    private static int cmdUnconscious(CommandSourceStack src, Collection<ServerPlayer> targets, int ticks) {
        boolean timed = ticks >= 1;
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (timed) {
                profile.setOverdoseUntilTick(p.level().getGameTime() + ticks);
                profile.setOverdoseUnconscious(true);
            } else {
                profile.setForcedState(HealthState.UNCONSCIOUS);
                profile.setState(HealthState.UNCONSCIOUS);
                profile.setBleedoutSinceTick(p.level().getGameTime());
            }
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Rendered " + n + " player(s) unconscious"
                + (timed ? " for " + ticks + " tick(s) (overdose wake timer)." : " (bleed-out death timer).")), true);
        return n;
    }

    /**
     * Force the ASPHYXIA phase for testing. Raises drug load to at least the asphyxia threshold so the
     * cause stays active; drains air each tick, passes the player out, and kills if untreated.
     */
    private static int cmdAsphyxia(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (!MedicalConfig.asphyxiaEnabled()) {
                src.sendFailure(Component.literal("[wfmedical] Asphyxia is disabled in the config."));
                return false;
            }
            float floor = (float) MedicalConfig.asphyxiaThreshold();
            if (profile.getDrugLoad() < floor) {
                profile.setDrugLoad(floor);
            }
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.startAsphyxia(p.level().getGameTime());
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Triggered asphyxia on " + n + " player(s)."), true);
        return n;
    }

    /**
     * Set the {@link HealthState} directly; DEAD routes through the proper kill semantics.
     */
    private static int cmdState(CommandSourceStack src, Collection<ServerPlayer> targets, String stateName) {
        HealthState state = parseState(src, stateName);
        if (state == null) {
            return 0;
        }
        if (state == HealthState.DEAD) {
            return cmdKill(src, targets);
        }
        final HealthState target = state;
        int n = forEach(src, targets, (s, p, data, profile) -> {
            // Pin a non-HEALTHY target through the forced-state override so the resync's recompute cannot
            // clobber it back to the physiology-derived state; HEALTHY clears any prior override so the
            // player returns to their real derived condition.
            profile.setForcedState(target == HealthState.HEALTHY ? null : target);
            profile.setState(target);
            if (target == HealthState.UNCONSCIOUS) {
                // Forcing UNCONSCIOUS via /state is treated as a bleed-out cause (set the bleed-out marker so
                // the engine's death timer runs), matching the dedicated /unconscious subcommand.
                profile.setBleedoutSinceTick(p.level().getGameTime());
            } else {
                profile.setBleedoutSinceTick(-1L);
                if (target == HealthState.HEALTHY) {
                    profile.setOverdoseUnconscious(false);
                    profile.setOverdoseUntilTick(0L);
                    profile.clearAsphyxia();
                }
            }
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Set state " + target.name() + " on "
                + n + " player(s)."), true);
        return n;
    }

    /**
     * Convenience: add the fracture trauma to a limb (requires the fracture feature enabled).
     */
    private static int cmdFracture(CommandSourceStack src, Collection<ServerPlayer> targets, String limbName) {
        if (!MedicalConfig.enableFractures()) {
            src.sendFailure(Component.literal("[wfmedical] Fractures are disabled in the config."));
            return 0;
        }
        TraumaType type = resolveTrauma("fracture", TraumaCategory.FRACTURE);
        if (type == null) {
            src.sendFailure(Component.literal("[wfmedical] No fracture trauma type is registered."));
            return 0;
        }
        return addTraumaConvenience(src, targets, limbName, type, "Fractured");
    }

    /**
     * Convenience: add a bleeding laceration trauma to a limb.
     */
    private static int cmdBleed(CommandSourceStack src, Collection<ServerPlayer> targets,
                                String limbName, float severity) {
        TraumaType type = resolveTrauma("laceration", TraumaCategory.LACERATION);
        if (type == null) {
            src.sendFailure(Component.literal("[wfmedical] No laceration/bleeding trauma type is registered."));
            return 0;
        }
        LimbType limb = parseLimb(src, limbName);
        if (limb == null) {
            return 0;
        }
        float sev = Float.isNaN(severity) ? type.getSeverityContribution() : severity;
        int n = forEach(src, targets, (s, p, data, profile) -> {
            profile.limb(limb).tryMerge(new Trauma(type, limb, sev, p.level().getGameTime()),
                    MedicalConfig.maxTraumaPerLimb());
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Applied bleeding '" + type.getId() + "' to "
                + limb.name() + " on " + n + " player(s)."), true);
        return n;
    }

    /**
     * Resolves capability once per player, skips those without it, swallows per-player errors.
     */
    private static int forEach(CommandSourceStack src, Collection<ServerPlayer> targets, ProfileAction action) {
        int count = 0;
        for (ServerPlayer p : targets) {
            IMedicalData data = MedicalCapabilities.get(p);
            if (data == null) {
                warnNoCap(src, p);
                continue;
            }
            try {
                if (action.apply(src, p, data, data.getProfile())) {
                    count++;
                }
            } catch (RuntimeException ex) {
                src.sendFailure(Component.literal("[wfmedical] Error on " + name(p) + ": " + ex.getMessage()));
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ shared internals

    private static int addTraumaConvenience(CommandSourceStack src, Collection<ServerPlayer> targets,
                                            String limbName, TraumaType type, String verb) {
        LimbType limb = parseLimb(src, limbName);
        if (limb == null) {
            return 0;
        }
        int n = forEach(src, targets, (s, p, data, profile) -> {
            profile.limb(limb).tryMerge(new Trauma(type, limb, type.getSeverityContribution(),
                    p.level().getGameTime()), MedicalConfig.maxTraumaPerLimb());
            profile.markDirty();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] " + verb + " " + limb.name() + " on "
                + n + " player(s)."), true);
        return n;
    }

    /**
     * Remove every MAJOR trauma across all limbs (the wounds that reduce effective max health and bleed
     * heavily), leaving minor trauma untouched. Used by {@link #cmdRevive} to lift the lethal condition
     * driving a bleed-out unconsciousness without performing a full heal.
     */
    private static void clearMajorTrauma(MedicalProfile profile) {
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = profile.limb(lt);
            List<Trauma> traumas = limb.getTraumas();
            boolean changed = false;
            for (int i = traumas.size() - 1; i >= 0; i--) {
                if (!traumas.get(i).isMinor()) {
                    traumas.remove(i);
                    changed = true;
                }
            }
            if (changed) {
                limb.markDirty();
            }
        }
        profile.markDirty();
    }

    private static void clearAllTrauma(MedicalProfile profile) {
        for (LimbType lt : LimbType.VALUES) {
            clearLimbTrauma(profile.limb(lt));
        }
        profile.markDirty();
    }

    private static void clearLimbTrauma(Limb limb) {
        if (!limb.getTraumas().isEmpty()) {
            limb.getTraumas().clear();
        }
        limb.setMinorDamage(0.0F);
        limb.setLocalNumbing(0.0F);
        limb.markDirty();
    }

    private static LimbType parseLimb(CommandSourceStack src, String name) {
        for (LimbType lt : LimbType.VALUES) {
            if (lt.name().equalsIgnoreCase(name)) {
                return lt;
            }
        }
        src.sendFailure(Component.literal("[wfmedical] Unknown limb: " + name
                + " (expected one of " + Arrays.toString(LimbType.VALUES) + ")"));
        return null;
    }

    private static HealthState parseState(CommandSourceStack src, String name) {
        for (HealthState st : HealthState.values()) {
            if (st.name().equalsIgnoreCase(name)) {
                return st;
            }
        }
        src.sendFailure(Component.literal("[wfmedical] Unknown state: " + name));
        return null;
    }

    private static Substance resolveSubstance(String key) {
        SubstanceRegistry reg = SubstanceRegistry.active();
        Substance byItem = reg.get(key);
        if (byItem != null) {
            return byItem;
        }
        for (Substance sub : reg.all()) {
            if (sub.id().equalsIgnoreCase(key) || sub.itemId().equalsIgnoreCase(key)) {
                return sub;
            }
        }
        return null;
    }

    private static TraumaType resolveTrauma(String preferredId, TraumaCategory fallbackCategory) {
        TraumaType byId = TraumaRegistry.active().get(preferredId);
        if (byId != null) {
            return byId;
        }
        return TraumaRegistry.active().firstOfCategory(fallbackCategory);
    }

    private static void warnNoCap(CommandSourceStack src, ServerPlayer p) {
        src.sendFailure(Component.literal("[wfmedical] " + name(p) + " has no medical capability; skipped."));
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /**
     * Build the multi-line debug dump for one player.
     */
    private static String buildDump(ServerPlayer p, MedicalProfile profile, DerivedStats stats) {
        long now = p.level().getGameTime();
        long remaining = profile.getOverdoseUntilTick() > 0L ? Math.max(0L, profile.getOverdoseUntilTick() - now) : 0L;
        StringBuilder sb = new StringBuilder();
        sb.append("=== wfmedical: ").append(name(p)).append(" ===");
        sb.append("\n health(derived): ").append(fmt(stats.effectiveCurrentHealth()))
                .append(" / ").append(fmt(stats.effectiveMaxHealth()))
                .append("  |  vanilla: ").append(fmt(p.getHealth())).append(" / ").append(fmt(p.getMaxHealth()));
        sb.append("\n blood: ").append(fmt(profile.getBloodMl())).append(" / ").append(fmt(profile.getMaxBloodMl())).append(" ml");
        sb.append("\n pain: perceived=").append(fmt(stats.totalPain()))
                .append("  systemic=").append(fmt(stats.systemicPain()))
                .append("  analgesia=").append(fmt(profile.getPainSuppression()))
                .append("  drugLoad=").append(fmt(profile.getDrugLoad()));
        sb.append("\n drugs: stimulant=").append(fmt(profile.getStimulant()))
                .append("  clotting=").append(fmt(profile.getClottingBoost()));
        sb.append("\n adrenaline: painKoPending=").append(stats.painKoPending())
                .append("  exhausted=").append(profile.isAdrenalineExhausted())
                .append("  since=").append(profile.getPainKoSince() > 0L ? (now - profile.getPainKoSince()) + "t" : "-");
        sb.append("\n state: ").append(profile.getState())
                .append("  isDowned: ").append(profile.isDowned());
        // Unified unconsciousness line: one externally-visible UNCONSCIOUS state, with an internal cause hint
        // (overdose => wake timer; bleed-out => death timer) so the debug dump still distinguishes them.
        if (profile.getState() == HealthState.UNCONSCIOUS) {
            if (profile.isAsphyxiaUnconscious()) {
                long dieIn = Math.max(0L, profile.getAsphyxiaDeadlineTick() - now);
                sb.append("\n unconscious: cause=asphyxia  death in ").append(dieIn).append("t (unless cause cleared)");
            } else if (profile.isOverdoseUnconscious()) {
                sb.append("\n unconscious: cause=overdose  wake in ").append(remaining).append("t");
            } else {
                long since = profile.getBleedoutSinceTick();
                long deathIn = since >= 0L
                        ? Math.max(0L, MedicalConfig.bleedoutTicks() - (now - since))
                        : -1L;
                sb.append("\n unconscious: cause=bleed-out  death in ")
                        .append(deathIn >= 0L ? (deathIn + "t") : "(timer not started)");
            }
        } else if (profile.isAsphyxiating()) {
            // Conscious asphyxia struggle (drowning or drug respiratory depression); passes out then kills.
            long outIn = Math.max(0L, (profile.getAsphyxiaSince() + MedicalConfig.asphyxiaStruggleTicks()) - now);
            sb.append("\n asphyxia: struggling  air ").append(p.getAirSupply()).append(" / ")
                    .append(p.getMaxAirSupply()).append("  passout in ").append(outIn).append("t");
        }
        sb.append("\n movement x").append(fmt(stats.movementMultiplier()))
                .append("  sprintBlocked=").append(stats.sprintBlocked())
                .append("  jump x").append(fmt(stats.jumpMultiplier()));
        sb.append("\n fractures: leg=").append(stats.anyLegFracture())
                .append(" arm=").append(stats.anyArmFracture());
        for (LimbType lt : LimbType.VALUES) {
            Limb limb = profile.limb(lt);
            float maxH = limb.getMaxHealth();
            float pct = maxH > 0.0F ? Math.max(0.0F, 1.0F - limb.getCachedHealthReduction() / maxH) * 100.0F : 0.0F;
            sb.append("\n [").append(lt.name()).append("] hp~").append(fmt(pct)).append("%")
                    .append(" bleed=").append(fmt(limb.getCachedBleeding()))
                    .append(" pain=").append(fmt(limb.getCachedPain()))
                    .append(" anesthetic=").append(fmt(limb.getLocalNumbing()))
                    .append(" fracture=").append(limb.hasCachedFracture());
            List<Trauma> traumas = limb.getTraumas();
            if (traumas.isEmpty()) {
                sb.append(" (no trauma)");
            } else {
                for (Trauma t : traumas) {
                    sb.append("\n    - ").append(t.getType().getId())
                            .append(" sev=").append(fmt(t.getSeverity()))
                            .append(t.isMinor() ? " [minor]" : " [major]")
                            .append(" treated=").append(t.isTreated())
                            .append(" sutured=").append(t.isSutured())
                            .append(" stabilized=").append(t.isStabilized());
                }
            }
        }
        return sb.toString();
    }

    private enum DrugMode {SET, ADD, CLEAR}

    @FunctionalInterface
    private interface ProfileAction {
        boolean apply(CommandSourceStack src, ServerPlayer player, IMedicalData data, MedicalProfile profile);
    }
}
