package com.warfactory.medical.damage;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.norwood.ahf.config.AhfConfig;
import com.norwood.ahf.geometry.Obb;
import com.norwood.ahf.hit.HitboxApi;
import com.norwood.ahf.hit.RayHit;
import com.norwood.ahf.part.HitboxPart;
import com.norwood.ahf.rig.HumanoidRig;
import com.norwood.ahf.rig.RigSpec;
import com.norwood.ahf.rig.RigSpecIO;
import com.norwood.ahf.rig.RigTuning;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.body.limb.LimbType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import com.warfactory.medical.body.BodyHooks;
import static com.warfactory.medical.body.MedicalCommands.*;

/** {@code /wfmedical} hittest, rig, hitbox. */
public final class DamageCommands {

    private DamageCommands() {
    }

    static void register() {
        BodyHooks.SUBCOMMANDS.register(ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "damage"), -400, root -> {
            root.then(Commands.literal("hittest")
                    .executes(ctx -> cmdHitTest(ctx.getSource(), 0.0D))
                    .then(Commands.argument("maxDist", DoubleArgumentType.doubleArg(0.0D))
                            .executes(ctx -> cmdHitTest(ctx.getSource(),
                                    DoubleArgumentType.getDouble(ctx, "maxDist")))));
            root.then(Commands.literal("rig")
                    .executes(ctx -> cmdRig(ctx.getSource(), 0.0D))
                    .then(Commands.argument("maxDist", DoubleArgumentType.doubleArg(0.0D))
                            .executes(ctx -> cmdRig(ctx.getSource(),
                                    DoubleArgumentType.getDouble(ctx, "maxDist")))));
            root.then(Commands.literal("hitbox")
                    .executes(ctx -> cmdHitboxStatus(ctx.getSource()))
                    .then(Commands.literal("status").executes(ctx -> cmdHitboxStatus(ctx.getSource())))
                    .then(Commands.literal("show")
                            .executes(ctx -> cmdHitboxShow(ctx.getSource(), null))
                            .then(Commands.argument("pose", StringArgumentType.word())
                                    .suggests(HITBOX_POSE_SUGGESTIONS)
                                    .executes(ctx -> cmdHitboxShow(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "pose")))))
                    .then(Commands.literal("export")
                            .executes(ctx -> cmdHitboxExport(ctx.getSource(), null))
                            .then(Commands.literal("file")
                                    .executes(ctx -> cmdHitboxExportFile(ctx.getSource())))
                            .then(Commands.argument("pose", StringArgumentType.word())
                                    .suggests(HITBOX_POSE_SUGGESTIONS)
                                    .executes(ctx -> cmdHitboxExport(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "pose")))))
                    .then(Commands.literal("debug")
                            .executes(ctx -> cmdHitboxDebug(ctx.getSource(), !RigTuning.ACTIVE))
                            .then(Commands.literal("on").executes(ctx -> cmdHitboxDebug(ctx.getSource(), true)))
                            .then(Commands.literal("off").executes(ctx -> cmdHitboxDebug(ctx.getSource(), false))))
                    .then(Commands.literal("set")
                            .then(Commands.argument("pose", StringArgumentType.word())
                                    .suggests(HITBOX_POSE_SUGGESTIONS)
                                    .then(Commands.argument("limb", StringArgumentType.word())
                                            .suggests(LIMB_SUGGESTIONS)
                                            .then(Commands.argument("field", StringArgumentType.word())
                                                    .suggests(HITBOX_FIELD_SUGGESTIONS)
                                                    .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                            .executes(ctx -> cmdHitboxSet(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                    StringArgumentType.getString(ctx, "limb"),
                                                                    StringArgumentType.getString(ctx, "field"),
                                                                    DoubleArgumentType.getDouble(ctx, "value"))))))))
                    .then(Commands.literal("add")
                            .then(Commands.argument("pose", StringArgumentType.word())
                                    .suggests(HITBOX_POSE_SUGGESTIONS)
                                    .then(Commands.argument("limb", StringArgumentType.word())
                                            .suggests(LIMB_SUGGESTIONS)
                                            .then(Commands.argument("field", StringArgumentType.word())
                                                    .suggests(HITBOX_FIELD_SUGGESTIONS)
                                                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                            .executes(ctx -> cmdHitboxAdd(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                    StringArgumentType.getString(ctx, "limb"),
                                                                    StringArgumentType.getString(ctx, "field"),
                                                                    DoubleArgumentType.getDouble(ctx, "amount"))))))))
                    .then(Commands.literal("reset")
                            .executes(ctx -> cmdHitboxReset(ctx.getSource(), null, null))
                            .then(Commands.argument("pose", StringArgumentType.word())
                                    .suggests(HITBOX_POSE_SUGGESTIONS)
                                    .executes(ctx -> cmdHitboxReset(ctx.getSource(),
                                            StringArgumentType.getString(ctx, "pose"), null))
                                    .then(Commands.argument("limb", StringArgumentType.word())
                                            .suggests(LIMB_SUGGESTIONS)
                                            .executes(ctx -> cmdHitboxReset(ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "pose"),
                                                    StringArgumentType.getString(ctx, "limb"))))))
                    .then(Commands.literal("envelope")
                            .then(Commands.literal("set")
                                    .then(Commands.argument("pose", StringArgumentType.word())
                                            .suggests(HITBOX_POSE_SUGGESTIONS)
                                            .then(Commands.argument("axis", StringArgumentType.word())
                                                    .suggests(HITBOX_AXIS_SUGGESTIONS)
                                                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D))
                                                            .executes(ctx -> cmdHitboxEnvSet(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                    StringArgumentType.getString(ctx, "axis"),
                                                                    DoubleArgumentType.getDouble(ctx, "value")))))))
                            .then(Commands.literal("add")
                                    .then(Commands.argument("pose", StringArgumentType.word())
                                            .suggests(HITBOX_POSE_SUGGESTIONS)
                                            .then(Commands.argument("axis", StringArgumentType.word())
                                                    .suggests(HITBOX_AXIS_SUGGESTIONS)
                                                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                            .executes(ctx -> cmdHitboxEnvAdd(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                    StringArgumentType.getString(ctx, "axis"),
                                                                    DoubleArgumentType.getDouble(ctx, "amount")))))))
                            .then(Commands.literal("reset")
                                    .executes(ctx -> cmdHitboxEnvReset(ctx.getSource()))))
                    .then(Commands.literal("hand")
                            .then(Commands.literal("set")
                                    .then(Commands.argument("pose", StringArgumentType.word())
                                            .suggests(HITBOX_POSE_SUGGESTIONS)
                                            .then(Commands.argument("action", StringArgumentType.word())
                                                    .suggests(HITBOX_HAND_SUGGESTIONS)
                                                    .then(Commands.argument("arm", StringArgumentType.word())
                                                            .suggests(HITBOX_ARM_SUGGESTIONS)
                                                            .then(Commands.argument("field", StringArgumentType.word())
                                                                    .suggests(HITBOX_FIELD_SUGGESTIONS)
                                                                    .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                                            .executes(ctx -> cmdHitboxHandSet(ctx.getSource(),
                                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                                    StringArgumentType.getString(ctx, "action"),
                                                                                    StringArgumentType.getString(ctx, "arm"),
                                                                                    StringArgumentType.getString(ctx, "field"),
                                                                                    DoubleArgumentType.getDouble(ctx, "value")))))))))
                            .then(Commands.literal("add")
                                    .then(Commands.argument("pose", StringArgumentType.word())
                                            .suggests(HITBOX_POSE_SUGGESTIONS)
                                            .then(Commands.argument("action", StringArgumentType.word())
                                                    .suggests(HITBOX_HAND_SUGGESTIONS)
                                                    .then(Commands.argument("arm", StringArgumentType.word())
                                                            .suggests(HITBOX_ARM_SUGGESTIONS)
                                                            .then(Commands.argument("field", StringArgumentType.word())
                                                                    .suggests(HITBOX_FIELD_SUGGESTIONS)
                                                                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                                            .executes(ctx -> cmdHitboxHandAdd(ctx.getSource(),
                                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                                    StringArgumentType.getString(ctx, "action"),
                                                                                    StringArgumentType.getString(ctx, "arm"),
                                                                                    StringArgumentType.getString(ctx, "field"),
                                                                                    DoubleArgumentType.getDouble(ctx, "amount")))))))))
                            .then(Commands.literal("reset")
                                    .then(Commands.argument("pose", StringArgumentType.word())
                                            .suggests(HITBOX_POSE_SUGGESTIONS)
                                            .then(Commands.argument("action", StringArgumentType.word())
                                                    .suggests(HITBOX_HAND_SUGGESTIONS)
                                                    .executes(ctx -> cmdHitboxHandReset(ctx.getSource(),
                                                            StringArgumentType.getString(ctx, "pose"),
                                                            StringArgumentType.getString(ctx, "action"), null))
                                                    .then(Commands.argument("arm", StringArgumentType.word())
                                                            .suggests(HITBOX_ARM_SUGGESTIONS)
                                                            .executes(ctx -> cmdHitboxHandReset(ctx.getSource(),
                                                                    StringArgumentType.getString(ctx, "pose"),
                                                                    StringArgumentType.getString(ctx, "action"),
                                                                    StringArgumentType.getString(ctx, "arm")))))))));
        });
    }

    private static final SuggestionProvider<CommandSourceStack> HITBOX_FIELD_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(RigTuning.Field.VALUES).map(RigTuning.Field::lower), b);

    private static final SuggestionProvider<CommandSourceStack> HITBOX_POSE_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(RigTuning.RigPose.VALUES).map(RigTuning.RigPose::lower), b);

    private static final SuggestionProvider<CommandSourceStack> HITBOX_AXIS_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(RigTuning.EnvAxis.VALUES).map(RigTuning.EnvAxis::lower), b);

    private static final SuggestionProvider<CommandSourceStack> HITBOX_HAND_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(RigTuning.HandAction.VALUES)
                            .filter(h -> h != RigTuning.HandAction.NONE)
                            .map(RigTuning.HandAction::lower), b);

    private static final SuggestionProvider<CommandSourceStack> HITBOX_ARM_SUGGESTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Arrays.stream(HitboxPart.VALUES).filter(HitboxPart::isArm).map(Enum::name), b);

    private static int cmdHitTest(CommandSourceStack src, double maxDist) throws CommandSyntaxException {
        ServerPlayer self = src.getPlayerOrException();
        double reach = maxDist > 0.0D ? maxDist : 5.0D;

        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB search = self.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(self.level(), self, eye, end, search,
                e -> e instanceof LivingEntity && e != self && e.isPickable());
        if (result == null || !(result.getEntity() instanceof LivingEntity target)) {
            src.sendFailure(Component.literal("[wfmedical] hittest: no target within "
                    + fmt(reach) + " blocks of your aim."));
            return 0;
        }

        HitboxPart limb = part(HitboxApi.classifyRay(target, eye, end));
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

    private static int cmdRig(CommandSourceStack src, double maxDist) throws CommandSyntaxException {
        ServerPlayer self = src.getPlayerOrException();
        double reach = maxDist > 0.0D ? maxDist : 5.0D;

        Vec3 eye = self.getEyePosition(1.0F);
        Vec3 look = self.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB search = self.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(self.level(), self, eye, end, search,
                e -> e instanceof LivingEntity && e != self && e.isPickable());
        if (result == null || !(result.getEntity() instanceof LivingEntity target)) {
            src.sendFailure(Component.literal("[wfmedical] rig: no target within "
                    + fmt(reach) + " blocks of your aim."));
            return 0;
        }

        HumanoidRig.LocalRig rig = HumanoidRig.compute(target);
        HitboxPart limb = part(HitboxApi.classifyRay(target, eye, end));
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

    private static int cmdHitboxStatus(CommandSourceStack src) {
        boolean cfg = AhfConfig.hitboxDebug();
        boolean active = RigTuning.ACTIVE;
        StringBuilder poses = new StringBuilder();
        for (RigTuning.RigPose p : RigTuning.RigPose.VALUES) {
            int n = 0;
            for (HitboxPart lt : HitboxPart.VALUES) {
                for (RigTuning.Field f : RigTuning.Field.VALUES) {
                    if (RigTuning.delta(p, lt, f) != 0.0) {
                        n++;
                    }
                }
            }
            if (n > 0) {
                poses.append(' ').append(p.lower()).append('=').append(n);
            }
        }
        String tuned = poses.length() > 0 ? "  deltas:" + poses : "  no deltas set";
        src.sendSuccess(() -> Component.literal("[wfmedical] hitbox tuning: "
                + (active ? "ARMED" : "inert") + " (config hitboxDebug=" + cfg + ")." + tuned
                + (active ? "" : "  Run '/wfmedical hitbox debug on' to apply live.")
                + "  '/wfmedical hitbox show [pose]' lists, 'export [pose]' bakes."), false);
        return 1;
    }

    private static int cmdHitboxDebug(CommandSourceStack src, boolean enable) {
        RigTuning.ACTIVE = enable;
        src.sendSuccess(() -> Component.literal("[wfmedical] hitbox tuning "
                + (enable ? "ARMED" : "disarmed") + " for this session"
                + (enable ? " (deltas now applied to the limb boxes)." : " (boxes back to their base spec).")
                + "  Session-only; set debug.hitboxDebug in ahf-common.toml to persist."), true);
        return 1;
    }

    private static int cmdHitboxSet(CommandSourceStack src, String poseName, String limbName,
                                    String fieldName, double value) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        HitboxPart limb = parsePart(src, limbName);
        RigTuning.Field field = parseField(src, fieldName);
        if (pose == null || limb == null || field == null) {
            return 0;
        }
        RigTuning.set(pose, limb, field, value);
        src.sendSuccess(() -> Component.literal("[wfmedical] " + pose.lower() + " " + limb.name() + "."
                + field.lower() + " delta = " + num(value) + hitboxApplyNote()), false);
        return 1;
    }

    private static int cmdHitboxAdd(CommandSourceStack src, String poseName, String limbName,
                                    String fieldName, double amount) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        HitboxPart limb = parsePart(src, limbName);
        RigTuning.Field field = parseField(src, fieldName);
        if (pose == null || limb == null || field == null) {
            return 0;
        }
        double now = RigTuning.add(pose, limb, field, amount);
        src.sendSuccess(() -> Component.literal("[wfmedical] " + pose.lower() + " " + limb.name() + "."
                + field.lower() + " delta " + (amount >= 0 ? "+" : "") + num(amount) + " -> " + num(now)
                + hitboxApplyNote()), false);
        return 1;
    }

    private static int cmdHitboxReset(CommandSourceStack src, String poseName, String limbName) {
        if (poseName == null) {
            RigTuning.reset();
            src.sendSuccess(() -> Component.literal("[wfmedical] hitbox tuning cleared (all poses)."), false);
            return 1;
        }
        RigTuning.RigPose pose = parsePose(src, poseName);
        if (pose == null) {
            return 0;
        }
        if (limbName == null) {
            RigTuning.reset(pose);
            src.sendSuccess(() -> Component.literal("[wfmedical] hitbox tuning cleared for pose "
                    + pose.lower() + "."), false);
            return 1;
        }
        HitboxPart limb = parsePart(src, limbName);
        if (limb == null) {
            return 0;
        }
        RigTuning.reset(pose, limb);
        src.sendSuccess(() -> Component.literal("[wfmedical] hitbox tuning cleared for " + pose.lower()
                + " " + limb.name() + "."), false);
        return 1;
    }

    private static int cmdHitboxShow(CommandSourceStack src, String poseName) {
        RigTuning.RigPose only = null;
        if (poseName != null) {
            only = parsePose(src, poseName);
            if (only == null) {
                return 0;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== wfmedical hitbox tuning (")
                .append(RigTuning.ACTIVE ? "ARMED" : "inert, /wfmedical hitbox debug on")
                .append(") ===\n effective spec (model units) o=offset s=size p=pivot; Δ = delta:");
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            if (only != null && pose != only) {
                continue;
            }
            sb.append("\n [").append(pose.lower()).append("]  envelope reach h=")
                    .append(num(RigTuning.envReach(pose, RigTuning.EnvAxis.HORIZONTAL)))
                    .append(" v=").append(num(RigTuning.envReach(pose, RigTuning.EnvAxis.VERTICAL)));
            for (HitboxPart lt : HitboxPart.VALUES) {
                double[] base = HumanoidRig.baseSpec(lt);
                double[] adj = HumanoidRig.poseAdjustSpec(pose, lt);
                double[] eff = new double[RigTuning.FIELDS];
                StringBuilder deltas = new StringBuilder();
                for (RigTuning.Field f : RigTuning.Field.VALUES) {
                    double d = RigTuning.delta(pose, lt, f);
                    eff[f.ordinal()] = base[f.ordinal()] + adj[f.ordinal()] + d;
                    if (d != 0.0) {
                        deltas.append(' ').append(f.lower()).append('=').append(d >= 0 ? "+" : "").append(num(d));
                    }
                }
                sb.append("\n   ").append(String.format(java.util.Locale.ROOT, "%-10s", lt.name()))
                        .append(" o=(").append(num(eff[0])).append(", ").append(num(eff[1])).append(", ").append(num(eff[2])).append(")")
                        .append(" s=(").append(num(eff[3])).append(", ").append(num(eff[4])).append(", ").append(num(eff[5])).append(")")
                        .append(" p=(").append(num(eff[6])).append(", ").append(num(eff[7])).append(", ").append(num(eff[8])).append(")")
                        .append(deltas.length() > 0 ? "  Δ" + deltas : "");
            }
            for (RigTuning.HandAction action : RigTuning.HandAction.VALUES) {
                if (action == RigTuning.HandAction.NONE) {
                    continue;
                }
                for (HitboxPart arm : HitboxPart.VALUES) {
                    if (!arm.isArm()) {
                        continue;
                    }
                    double[] hAdj = HumanoidRig.handAdjustSpec(pose, action, arm);
                    StringBuilder row = new StringBuilder();
                    boolean any = false;
                    for (RigTuning.Field f : RigTuning.Field.VALUES) {
                        double v = hAdj[f.ordinal()] + RigTuning.handDelta(pose, action, arm, f);
                        if (v != 0.0) {
                            any = true;
                            row.append(' ').append(f.lower()).append('=').append(v >= 0 ? "+" : "").append(num(v));
                        }
                    }
                    if (any) {
                        sb.append("\n   +").append(action.lower()).append(' ').append(arm.name()).append(':').append(row);
                    }
                }
            }
        }
        String dump = sb.toString();
        src.sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    private static int cmdHitboxExport(CommandSourceStack src, String poseName) {
        RigTuning.RigPose only = null;
        if (poseName != null) {
            only = parsePose(src, poseName);
            if (only == null) {
                return 0;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("// Baked hitbox spec (model units: ox,oy,oz, sx,sy,sz, px,py,pz)");
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            if (only != null && pose != only) {
                continue;
            }
            if (pose == RigTuning.RigPose.STANDING) {
                sb.append("\n// STANDING -> paste into HumanoidRig.base():");
                for (HitboxPart lt : HitboxPart.VALUES) {
                    double[] base = HumanoidRig.baseSpec(lt);
                    sb.append("\nb[HitboxPart.").append(lt.name()).append(".ordinal()] = new double[]{");
                    appendRow(sb, i -> base[i] + RigTuning.delta(pose, lt, RigTuning.Field.VALUES[i]));
                    sb.append("};");
                }
            } else {
                sb.append("\n// ").append(pose.name()).append(" -> paste into HumanoidRig.poseAdjust():");
                boolean any = false;
                for (HitboxPart lt : HitboxPart.VALUES) {
                    double[] adj = HumanoidRig.poseAdjustSpec(pose, lt);
                    boolean nonzero = false;
                    for (RigTuning.Field f : RigTuning.Field.VALUES) {
                        if (adj[f.ordinal()] + RigTuning.delta(pose, lt, f) != 0.0) {
                            nonzero = true;
                            break;
                        }
                    }
                    if (!nonzero) {
                        continue;
                    }
                    any = true;
                    sb.append("\na[RigTuning.RigPose.").append(pose.name()).append(".ordinal()][HitboxPart.")
                            .append(lt.name()).append(".ordinal()] = new double[]{");
                    appendRow(sb, i -> adj[i] + RigTuning.delta(pose, lt, RigTuning.Field.VALUES[i]));
                    sb.append("};");
                }
                if (!any) {
                    sb.append("\n// (no adjustments)");
                }
            }
        }
        StringBuilder hand = new StringBuilder();
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            if (only != null && pose != only) {
                continue;
            }
            for (RigTuning.HandAction action : RigTuning.HandAction.VALUES) {
                if (action == RigTuning.HandAction.NONE) {
                    continue;
                }
                for (HitboxPart arm : HitboxPart.VALUES) {
                    if (!arm.isArm()) {
                        continue;
                    }
                    double[] hAdj = HumanoidRig.handAdjustSpec(pose, action, arm);
                    boolean nonzero = false;
                    for (RigTuning.Field f : RigTuning.Field.VALUES) {
                        if (hAdj[f.ordinal()] + RigTuning.handDelta(pose, action, arm, f) != 0.0) {
                            nonzero = true;
                            break;
                        }
                    }
                    if (!nonzero) {
                        continue;
                    }
                    hand.append("\na[RigTuning.RigPose.").append(pose.name()).append(".ordinal()][RigTuning.HandAction.")
                            .append(action.name()).append(".ordinal()][HitboxPart.").append(arm.name())
                            .append(".ordinal()] = new double[]{");
                    appendRow(hand, i -> hAdj[i] + RigTuning.handDelta(pose, action, arm, RigTuning.Field.VALUES[i]));
                    hand.append("};");
                }
            }
        }
        if (hand.length() > 0) {
            sb.append("\n// hand-action arm overlays -> paste into HumanoidRig.handAdjust():").append(hand);
        }
        sb.append("\n// envelope reach -> ahf-common.toml [hitRegistration.envelopeReach]:");
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            if (only != null && pose != only) {
                continue;
            }
            sb.append("\n").append(pose.lower()).append("Horizontal = ")
                    .append(num(RigTuning.envReach(pose, RigTuning.EnvAxis.HORIZONTAL)));
            sb.append("\n").append(pose.lower()).append("Vertical = ")
                    .append(num(RigTuning.envReach(pose, RigTuning.EnvAxis.VERTICAL)));
        }
        String dump = sb.toString();
        src.sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    private static int cmdHitboxExportFile(CommandSourceStack src) {
        RigSpec spec = RigSpecIO.effectiveSpec();
        java.nio.file.Path file;
        try {
            file = RigSpecIO.write(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(), spec);
        } catch (Exception e) {
            src.sendFailure(Component.literal("[wfmedical] hitbox export file FAILED: " + e.getMessage()));
            return 0;
        }
        HumanoidRig.setSpec(spec);
        RigTuning.reset();
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            for (RigTuning.HandAction action : RigTuning.HandAction.VALUES) {
                if (action != RigTuning.HandAction.NONE) {
                    RigTuning.resetHand(pose, action);
                }
            }
        }
        final String path = file.toString();
        src.sendSuccess(() -> Component.literal("[wfmedical] Wrote hitbox geometry to " + path
                + " and applied it live; tuning deltas cleared (they are the baseline now). Reloads "
                + "automatically on server start / config reload."), true);
        return 1;
    }

    private static void appendRow(StringBuilder sb, java.util.function.IntToDoubleFunction cell) {
        for (int i = 0; i < RigTuning.FIELDS; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(num(cell.applyAsDouble(i)));
        }
    }

    private static String hitboxApplyNote() {
        return RigTuning.ACTIVE ? "" : "  (inert -- '/wfmedical hitbox debug on' to apply)";
    }

    private static RigTuning.Field parseField(CommandSourceStack src, String name) {
        RigTuning.Field f = RigTuning.Field.fromString(name);
        if (f == null) {
            src.sendFailure(Component.literal("[wfmedical] Unknown field: " + name
                    + " (expected one of ox oy oz sx sy sz px py pz)"));
        }
        return f;
    }

    private static RigTuning.RigPose parsePose(CommandSourceStack src, String name) {
        RigTuning.RigPose p = RigTuning.RigPose.fromString(name);
        if (p == null) {
            src.sendFailure(Component.literal("[wfmedical] Unknown pose: " + name
                    + " (expected one of standing crouching prone)"));
        }
        return p;
    }

    private static RigTuning.EnvAxis parseAxis(CommandSourceStack src, String name) {
        RigTuning.EnvAxis a = RigTuning.EnvAxis.fromString(name);
        if (a == null) {
            src.sendFailure(Component.literal("[wfmedical] Unknown axis: " + name
                    + " (expected horizontal|h or vertical|v)"));
        }
        return a;
    }

    private static RigTuning.HandAction parseHandAction(CommandSourceStack src, String name) {
        RigTuning.HandAction h = RigTuning.HandAction.fromString(name);
        if (h == null || h == RigTuning.HandAction.NONE) {
            src.sendFailure(Component.literal("[wfmedical] Unknown hand action: " + name
                    + " (expected bow, gun or block; the relaxed base is tuned with 'hitbox set')"));
            return null;
        }
        return h;
    }

    private static HitboxPart parseArm(CommandSourceStack src, String name) {
        HitboxPart limb = parsePart(src, name);
        if (limb != null && !limb.isArm()) {
            src.sendFailure(Component.literal("[wfmedical] " + limb.name() + " is not an arm; the hand overlay "
                    + "only applies to LEFT_ARM / RIGHT_ARM."));
            return null;
        }
        return limb;
    }

    private static int cmdHitboxHandSet(CommandSourceStack src, String poseName, String actionName,
                                        String armName, String fieldName, double value) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        RigTuning.HandAction action = parseHandAction(src, actionName);
        HitboxPart arm = parseArm(src, armName);
        RigTuning.Field field = parseField(src, fieldName);
        if (pose == null || action == null || arm == null || field == null) {
            return 0;
        }
        RigTuning.setHand(pose, action, arm, field, value);
        src.sendSuccess(() -> Component.literal("[wfmedical] " + pose.lower() + " " + action.lower() + " "
                + arm.name() + "." + field.lower() + " overlay = " + num(value) + hitboxApplyNote()), false);
        return 1;
    }

    private static int cmdHitboxHandAdd(CommandSourceStack src, String poseName, String actionName,
                                        String armName, String fieldName, double amount) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        RigTuning.HandAction action = parseHandAction(src, actionName);
        HitboxPart arm = parseArm(src, armName);
        RigTuning.Field field = parseField(src, fieldName);
        if (pose == null || action == null || arm == null || field == null) {
            return 0;
        }
        double now = RigTuning.addHand(pose, action, arm, field, amount);
        src.sendSuccess(() -> Component.literal("[wfmedical] " + pose.lower() + " " + action.lower() + " "
                + arm.name() + "." + field.lower() + " overlay " + (amount >= 0 ? "+" : "") + num(amount)
                + " -> " + num(now) + hitboxApplyNote()), false);
        return 1;
    }

    private static int cmdHitboxHandReset(CommandSourceStack src, String poseName, String actionName, String armName) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        RigTuning.HandAction action = parseHandAction(src, actionName);
        if (pose == null || action == null) {
            return 0;
        }
        if (armName == null) {
            RigTuning.resetHand(pose, action);
            src.sendSuccess(() -> Component.literal("[wfmedical] hand overlay cleared for " + pose.lower()
                    + " " + action.lower() + "."), false);
            return 1;
        }
        HitboxPart arm = parseArm(src, armName);
        if (arm == null) {
            return 0;
        }
        RigTuning.resetHand(pose, action, arm);
        src.sendSuccess(() -> Component.literal("[wfmedical] hand overlay cleared for " + pose.lower()
                + " " + action.lower() + " " + arm.name() + "."), false);
        return 1;
    }

    private static int cmdHitboxEnvSet(CommandSourceStack src, String poseName, String axisName, double value) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        RigTuning.EnvAxis axis = parseAxis(src, axisName);
        if (pose == null || axis == null) {
            return 0;
        }
        RigTuning.setEnv(pose, axis, value);
        src.sendSuccess(() -> Component.literal("[wfmedical] envelope " + pose.lower() + " " + axis.lower()
                + " reach = " + num(RigTuning.envReach(pose, axis)) + hitboxApplyNote()), false);
        return 1;
    }

    private static int cmdHitboxEnvAdd(CommandSourceStack src, String poseName, String axisName, double amount) {
        RigTuning.RigPose pose = parsePose(src, poseName);
        RigTuning.EnvAxis axis = parseAxis(src, axisName);
        if (pose == null || axis == null) {
            return 0;
        }
        double now = RigTuning.addEnv(pose, axis, amount);
        src.sendSuccess(() -> Component.literal("[wfmedical] envelope " + pose.lower() + " " + axis.lower()
                + " reach " + (amount >= 0 ? "+" : "") + num(amount) + " -> " + num(now) + hitboxApplyNote()), false);
        return 1;
    }

    private static int cmdHitboxEnvReset(CommandSourceStack src) {
        RigTuning.seedEnvelope(AhfConfig.envelopeReachSnapshot());
        src.sendSuccess(() -> Component.literal("[wfmedical] envelope reach reset to the config values."), false);
        return 1;
    }

    private static HitboxPart parsePart(CommandSourceStack src, String name) {
        LimbType limb = parseLimb(src, name);
        return limb == null ? null : limb.part();
    }

    private static HitboxPart part(RayHit hit) {
        return hit == null ? null : hit.part();
    }

    private static String num(double v) {
        String s = Double.toString(v);
        if (s.endsWith(".0")) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }
}
