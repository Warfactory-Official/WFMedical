package com.warfactory.medical.body;

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
import com.warfactory.medical.body.attachment.IMedicalData;
import com.warfactory.medical.body.attachment.MedicalAttachments;
import com.warfactory.medical.body.limb.Limb;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.body.trauma.Trauma;
import com.warfactory.medical.body.trauma.TraumaCategory;
import com.warfactory.medical.body.trauma.TraumaTypes;
import com.warfactory.medical.body.trauma.TraumaType;
import com.warfactory.medical.body.network.BodyNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

/** {@code /wfmedical} (alias {@code /wfmed}): root, body subcommands, helpers for {@link BodyHooks#SUBCOMMANDS} contributors. */
public final class MedicalCommands {

    public static final SuggestionProvider<CommandSourceStack> LIMB_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(LimbType.VALUES).map(Enum::name), b);

    private static final SuggestionProvider<CommandSourceStack> TRAUMA_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    TraumaTypes.LIVE.all().stream().map(TraumaType::getId), b);

    private static final SuggestionProvider<CommandSourceStack> STATE_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HealthState.values()).map(Enum::name), b);

    private MedicalCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wfmedical")
                .requires(src -> src.hasPermission(2));
        BodyHooks.SUBCOMMANDS.values().forEach(contributor -> contributor.accept(root));

        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
        dispatcher.register(Commands.literal("wfmed").requires(src -> src.hasPermission(2)).redirect(node));
    }

    static void registerOwn() {
        BodyHooks.SUBCOMMANDS.register(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "body"), 0, root -> {
            root.then(Commands.literal("query")
                    .executes(ctx -> cmdQuery(ctx.getSource(), self(ctx)))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdQuery(ctx.getSource(), players(ctx)))));
            root.then(Commands.literal("heal")
                    .executes(ctx -> cmdHeal(ctx.getSource(), self(ctx)))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdHeal(ctx.getSource(), players(ctx)))));
            root.then(Commands.literal("reset")
                    .executes(ctx -> cmdReset(ctx.getSource(), self(ctx)))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdReset(ctx.getSource(), players(ctx)))));
            root.then(Commands.literal("kill")
                    .executes(ctx -> cmdKill(ctx.getSource(), self(ctx)))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdKill(ctx.getSource(), players(ctx)))));
            root.then(Commands.literal("revive")
                    .executes(ctx -> cmdRevive(ctx.getSource(), self(ctx)))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdRevive(ctx.getSource(), players(ctx)))));
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
            root.then(Commands.literal("unconscious")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdUnconscious(ctx.getSource(), players(ctx), -1))
                            .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                    .executes(ctx -> cmdUnconscious(ctx.getSource(), players(ctx),
                                            IntegerArgumentType.getInteger(ctx, "ticks"))))));
            root.then(Commands.literal("asphyxia")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> cmdAsphyxia(ctx.getSource(), players(ctx)))));
            root.then(Commands.literal("state")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("state", StringArgumentType.word())
                                    .suggests(STATE_SUGGESTIONS)
                                    .executes(ctx -> cmdState(ctx.getSource(), players(ctx),
                                            StringArgumentType.getString(ctx, "state"))))));
            root.then(Commands.literal("fracture")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("limb", StringArgumentType.word())
                                    .suggests(LIMB_SUGGESTIONS)
                                    .executes(ctx -> cmdFracture(ctx.getSource(), players(ctx),
                                            StringArgumentType.getString(ctx, "limb"))))));
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
        });
    }

    public static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return Collections.singletonList(ctx.getSource().getPlayerOrException());
    }

    public static Collection<ServerPlayer> players(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "targets");
    }

    private static int cmdQuery(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer p : targets) {
            IMedicalData data = MedicalAttachments.get(p);
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
            profile.setBlackoutGraceUntil(0L);
            profile.setUnconsciousLatched(false);
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

    private static int cmdReset(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            MedicalProfile fresh = new MedicalProfile(MedicalConfig.maxBloodMl());
            fresh.setLastBroadcastDowned(profile.isLastBroadcastDowned());
            data.setProfile(fresh);
            data.bumpRevision();
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Reset " + n + " player(s) to a pristine profile."), true);
        return n;
    }

    private static int cmdKill(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.clearAsphyxia();
            profile.setBlackoutGraceUntil(0L);
            profile.setUnconsciousLatched(false);
            profile.setBleedoutSinceTick(-1L);
            profile.setForcedState(null);
            profile.setState(HealthState.DEAD);
            if (profile.isLastBroadcastDowned()) {
                BodyNetwork.broadcastDowned(p, false);
                profile.setLastBroadcastDowned(false);
            }
            profile.markDirty();
            data.bumpRevision();
            p.kill();
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Killed " + n + " player(s)."), true);
        return n;
    }

    private static int cmdRevive(CommandSourceStack src, Collection<ServerPlayer> targets) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (!p.isAlive() || p.isRemoved()) {
                src.sendFailure(Component.literal("[wfmedical] " + name(p) + " is not alive; cannot revive."));
                return false;
            }
            profile.setForcedState(null);
            profile.setState(HealthState.HEALTHY);
            profile.setBleedoutSinceTick(-1L);
            profile.setOverdoseUnconscious(false);
            profile.setOverdoseUntilTick(0L);
            profile.clearAsphyxia();
            profile.setBlackoutGraceUntil(0L);
            profile.setUnconsciousLatched(false);
            profile.setDrugLoad(0.0F);
            clearMajorTrauma(profile);
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

    private static int cmdTraumaAdd(CommandSourceStack src, Collection<ServerPlayer> targets,
                                    String limbName, String traumaId, float severity) {
        LimbType limb = parseLimb(src, limbName);
        if (limb == null) {
            return 0;
        }
        TraumaType type = TraumaTypes.LIVE.get(traumaId);
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

    private static int cmdUnconscious(CommandSourceStack src, Collection<ServerPlayer> targets, int ticks) {
        boolean timed = ticks >= 1;
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (timed) {
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
                + (timed ? " (overdose cause; comes to via the wake roll once stable)." : " (bleed-out death timer).")), true);
        return n;
    }

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
            profile.setUnconsciousLatched(false);
            profile.setBlackoutGraceUntil(0L);
            profile.setForcedState(target == HealthState.HEALTHY ? null : target);
            profile.setState(target);
            if (target == HealthState.UNCONSCIOUS) {
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

    public static int forEach(CommandSourceStack src, Collection<ServerPlayer> targets, ProfileAction action) {
        int count = 0;
        for (ServerPlayer p : targets) {
            IMedicalData data = MedicalAttachments.get(p);
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

    public static LimbType parseLimb(CommandSourceStack src, String name) {
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

    private static TraumaType resolveTrauma(String preferredId, TraumaCategory fallbackCategory) {
        TraumaType byId = TraumaTypes.LIVE.get(preferredId);
        if (byId != null) {
            return byId;
        }
        return TraumaTypes.LIVE.firstOfCategory(fallbackCategory);
    }

    public static void warnNoCap(CommandSourceStack src, ServerPlayer p) {
        src.sendFailure(Component.literal("[wfmedical] " + name(p) + " has no medical capability; skipped."));
    }

    public static String name(ServerPlayer p) {
        return p.getGameProfile().getName();
    }

    public static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String buildDump(ServerPlayer p, MedicalProfile profile, DerivedStats stats) {
        long now = p.level().getGameTime();
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
        if (profile.getState() == HealthState.UNCONSCIOUS) {
            double wake = MedicalEngine.wakeupScore(profile, stats);
            double thr = MedicalConfig.wakeupScoreThreshold();
            String wakeInfo = "  wakeupScore=" + fmt(wake) + "/" + fmt(thr)
                    + (wake <= thr ? " (rolling " + fmt(MedicalConfig.wakeChance()) + " per recompute)" : " (too high to wake)")
                    + "  latched=" + profile.isUnconsciousLatched();
            if (profile.isAsphyxiaUnconscious()) {
                long dieIn = Math.max(0L, profile.getAsphyxiaDeadlineTick() - now);
                sb.append("\n unconscious: cause=asphyxia  death in ").append(dieIn).append("t (unless cause cleared)");
            } else if (profile.isOverdoseUnconscious()) {
                sb.append("\n unconscious: cause=overdose").append(wakeInfo);
            } else {
                sb.append("\n unconscious: cause=bleed-out").append(wakeInfo);
            }
        } else if (profile.getBlackoutGraceUntil() > 0L) {
            sb.append("\n blackout grace: ").append(Math.max(0L, profile.getBlackoutGraceUntil() - now))
                    .append("t until KO (sealed – antidote no longer prevents it)");
        } else if (profile.isAsphyxiating()) {
            long outIn = Math.max(0L, (profile.getAsphyxiaSince()
                    + MedicalConfig.asphyxiaStruggleTicks() + MedicalConfig.blackoutGraceTicks()) - now);
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
                            .append(" stabilized=").append(t.isStabilized())
                            .append(" closed=").append(t.isClosed())
                            .append(" bleedFactor=").append(fmt(t.getBleedFactor()));
                }
            }
        }
        return sb.toString();
    }

    @FunctionalInterface
    public interface ProfileAction {
        boolean apply(CommandSourceStack src, ServerPlayer player, IMedicalData data, MedicalProfile profile);
    }
}
