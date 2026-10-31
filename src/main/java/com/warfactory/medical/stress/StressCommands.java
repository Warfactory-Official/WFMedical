package com.warfactory.medical.stress;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.MedicalEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import com.warfactory.medical.body.BodyHooks;
import static com.warfactory.medical.body.MedicalCommands.*;

/** {@code /wfmedical} suppression, stress. */
public final class StressCommands {

    private StressCommands() {
    }

    static void register() {
        BodyHooks.SUBCOMMANDS.register(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "stress"), -100, root -> {
            root.then(Commands.literal("suppression")
                    .then(Commands.literal("set")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F))
                                            .executes(ctx -> cmdSuppression(ctx.getSource(), players(ctx),
                                                    FloatArgumentType.getFloat(ctx, "value"))))))
                    .then(Commands.literal("clear")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(ctx -> cmdSuppression(ctx.getSource(), players(ctx), 0.0F)))));
            root.then(Commands.literal("stress")
                    .then(Commands.literal("set")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F))
                                            .executes(ctx -> cmdStress(ctx.getSource(), players(ctx),
                                                    FloatArgumentType.getFloat(ctx, "value"), false)))))
                    .then(Commands.literal("add")
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .then(Commands.argument("value", FloatArgumentType.floatArg(-1.0F, 1.0F))
                                            .executes(ctx -> cmdStress(ctx.getSource(), players(ctx),
                                                    FloatArgumentType.getFloat(ctx, "value"), true))))));
        });
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

    private static int cmdStress(CommandSourceStack src, Collection<ServerPlayer> targets, float value, boolean add) {
        int n = forEach(src, targets, (s, p, data, profile) -> {
            if (add) {
                profile.addStress(value, p.level().getGameTime());
            } else {
                profile.setStress(value);
            }
            MedicalEngine.resync(p);
            return true;
        });
        src.sendSuccess(() -> Component.literal("[wfmedical] " + (add ? "Added " : "Set stress to ") + fmt(value)
                + " for " + n + " player(s)."), true);
        return n;
    }
}
