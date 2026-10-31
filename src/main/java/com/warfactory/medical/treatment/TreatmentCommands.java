package com.warfactory.medical.treatment;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.limb.LimbType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import com.warfactory.medical.body.BodyHooks;
import static com.warfactory.medical.body.MedicalCommands.*;

/** {@code /wfmedical} treat. */
public final class TreatmentCommands {

    private TreatmentCommands() {
    }

    static void register() {
        BodyHooks.SUBCOMMANDS.register(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "treatment"), -300, root -> {
            root.then(Commands.literal("treat")
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("item", ResourceLocationArgument.id())
                                    .then(Commands.argument("limb", StringArgumentType.word())
                                            .suggests(LIMB_SUGGESTIONS)
                                            .executes(ctx -> cmdTreat(ctx.getSource(), players(ctx),
                                                    ResourceLocationArgument.getId(ctx, "item"),
                                                    StringArgumentType.getString(ctx, "limb")))))));
        });
    }

    /** Self-treatment with {@code item} from each target's hotbar, as the item-use flow starts it. */
    private static int cmdTreat(CommandSourceStack src, Collection<ServerPlayer> targets, ResourceLocation item,
                                String limbName) {
        LimbType limb = parseLimb(src, limbName);
        if (limb == null) {
            return 0;
        }
        int n = 0;
        for (ServerPlayer p : targets) {
            if (MedicalActionService.start(p, item, limb, -1)) {
                n++;
            }
        }
        int started = n;
        src.sendSuccess(() -> Component.literal("[wfmedical] Treatment started on " + started + " player(s)."), true);
        return n;
    }
}
