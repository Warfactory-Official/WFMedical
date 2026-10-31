package com.warfactory.medical.substance;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalConfig;
import com.warfactory.medical.body.MedicalEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import com.warfactory.medical.body.BodyHooks;
import static com.warfactory.medical.body.MedicalCommands.*;

/** {@code /wfmedical} drug, substance. */
public final class SubstanceCommands {

    private SubstanceCommands() {
    }

    static void register() {
        BodyHooks.SUBCOMMANDS.register(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "substance"), -200, root -> {
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
            root.then(Commands.literal("substance")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("substanceId", StringArgumentType.word())
                                    .suggests(SUBSTANCE_SUGGESTIONS)
                                    .executes(ctx -> cmdSubstance(ctx.getSource(), players(ctx),
                                            StringArgumentType.getString(ctx, "substanceId"))))));
        });
    }

    private static final int DEFAULT_OVERDOSE_TICKS = 200;

    private static final SuggestionProvider<CommandSourceStack> SUBSTANCE_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Substances.SUBSTANCES.values().stream().map(Substance::id), b);

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
                profile.setBlackoutGraceUntil(0L);
                profile.setUnconsciousLatched(false);
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

    private static int cmdSubstance(CommandSourceStack src, Collection<ServerPlayer> targets, String substanceId) {
        Substance substance = resolveSubstance(substanceId);
        if (substance == null) {
            src.sendFailure(Component.literal("[wfmedical] Unknown substance id: " + substanceId));
            return 0;
        }
        int n = forEach(src, targets, (s, p, data, profile) -> {
            boolean injected = SubstanceService.inject(p, substance);
            MedicalEngine.resync(p);
            return injected;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] Injected '" + substance.id() + "' into "
                + n + " player(s)."), true);
        return n;
    }

    private static Substance resolveSubstance(String key) {
        Substance byItem = Substances.get(key);
        if (byItem != null) {
            return byItem;
        }
        for (Substance sub : Substances.SUBSTANCES.values()) {
            if (sub.id().equalsIgnoreCase(key) || sub.itemId().equalsIgnoreCase(key)) {
                return sub;
            }
        }
        return null;
    }

    private enum DrugMode {SET, ADD, CLEAR}
}
