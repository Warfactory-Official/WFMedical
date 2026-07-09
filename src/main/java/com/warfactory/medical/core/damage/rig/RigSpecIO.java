package com.warfactory.medical.core.damage.rig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.limb.LimbType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and writes the {@link RigSpec} override file (R2: data-driven box geometry). The file lets a pack /
 * tester tune the limb boxes and persist them WITHOUT a recompile: dial the boxes in live with
 * {@code /wfmedical hitbox set|add}, then {@code /wfmedical hitbox export file} writes the tuned geometry here
 * and applies it. On every config load/reload the file (if present) is read back and installed via
 * {@link HumanoidRig#setSpec}; when it is absent the built-in defaults are used.
 *
 * <p>Format is JSON (see {@code toJson}): a {@code base} object of six limb rows plus optional
 * {@code poseAdjust} / {@code handAdjust} trees carrying only the non-zero rows. Any row the file omits falls
 * back to the built-in default, so a partial file is always safe. Only the static geometry is data-driven &mdash;
 * {@code HumanoidRig.setupAnim} (the animation) stays in code.</p>
 */
public final class RigSpecIO {

    /**
     * Override file name, sitting in the config dir beside {@code wfmedical_definitions.toml}.
     */
    public static final String FILE_NAME = "wfmedical_hitbox_rig.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FIELDS = RigTuning.FIELDS;

    private RigSpecIO() {
    }

    /**
     * Read the override file from {@code configDir} and install it (or reset to built-in defaults when the file
     * is absent or invalid). Safe to call on every config load/reload.
     */
    public static void reload(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            HumanoidRig.setSpec(null);
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) {
                HumanoidRig.setSpec(null);
                return;
            }
            HumanoidRig.setSpec(fromJson(root));
            WFMedical.LOGGER.info("[{}] Loaded hitbox rig geometry override from {}", WFMedical.MOD_ID, FILE_NAME);
        } catch (Exception e) {
            WFMedical.LOGGER.error("[{}] Failed to load {} -- using built-in hitbox geometry",
                    WFMedical.MOD_ID, FILE_NAME, e);
            HumanoidRig.setSpec(null);
        }
    }

    /**
     * Write {@code spec} to the override file in {@code configDir}, returning the path written.
     */
    public static Path write(Path configDir, RigSpec spec) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        Files.createDirectories(configDir);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(toJson(spec), w);
        }
        return file;
    }

    /**
     * The CURRENT effective geometry with the live {@link RigTuning} deltas folded in, exactly as the source
     * {@code export} dump bakes them: STANDING deltas fold into {@link RigSpec#base} (so they apply to every
     * pose), each other pose's deltas into {@link RigSpec#poseAdjust}, and the hand-action deltas into
     * {@link RigSpec#handAdjust}. This is what {@code export file} persists.
     */
    public static RigSpec effectiveSpec() {
        int limbs = LimbType.VALUES.length;
        int poses = RigTuning.RigPose.VALUES.length;
        int hands = RigTuning.HandAction.VALUES.length;

        double[][] base = new double[limbs][];
        for (LimbType lt : LimbType.VALUES) {
            double[] row = HumanoidRig.baseSpec(lt);
            for (RigTuning.Field f : RigTuning.Field.VALUES) {
                row[f.ordinal()] += RigTuning.delta(RigTuning.RigPose.STANDING, lt, f);
            }
            base[lt.ordinal()] = row;
        }

        double[][][] poseAdjust = new double[poses][limbs][];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            for (LimbType lt : LimbType.VALUES) {
                double[] row = HumanoidRig.poseAdjustSpec(pose, lt);
                if (pose != RigTuning.RigPose.STANDING) {
                    for (RigTuning.Field f : RigTuning.Field.VALUES) {
                        row[f.ordinal()] += RigTuning.delta(pose, lt, f);
                    }
                }
                poseAdjust[pose.ordinal()][lt.ordinal()] = row;
            }
        }

        double[][][][] handAdjust = new double[poses][hands][limbs][];
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            for (RigTuning.HandAction action : RigTuning.HandAction.VALUES) {
                for (LimbType lt : LimbType.VALUES) {
                    double[] row = HumanoidRig.handAdjustSpec(pose, action, lt);
                    if (action != RigTuning.HandAction.NONE) {
                        for (RigTuning.Field f : RigTuning.Field.VALUES) {
                            row[f.ordinal()] += RigTuning.handDelta(pose, action, lt, f);
                        }
                    }
                    handAdjust[pose.ordinal()][action.ordinal()][lt.ordinal()] = row;
                }
            }
        }
        return new RigSpec(base, poseAdjust, handAdjust);
    }

    // ---------------------------------------------------------------- JSON

    private static RigSpec fromJson(JsonObject root) {
        RigSpec spec = HumanoidRig.defaultSpec(); // overlay onto a copy of the built-in defaults
        JsonObject base = root.getAsJsonObject("base");
        if (base != null) {
            for (LimbType lt : LimbType.VALUES) {
                double[] row = readRow(base, lt.name());
                if (row != null) {
                    spec.base[lt.ordinal()] = row;
                }
            }
        }
        JsonObject poseAdjust = root.getAsJsonObject("poseAdjust");
        if (poseAdjust != null) {
            for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
                JsonObject byLimb = poseAdjust.getAsJsonObject(pose.name());
                if (byLimb == null) {
                    continue;
                }
                for (LimbType lt : LimbType.VALUES) {
                    double[] row = readRow(byLimb, lt.name());
                    if (row != null) {
                        spec.poseAdjust[pose.ordinal()][lt.ordinal()] = row;
                    }
                }
            }
        }
        JsonObject handAdjust = root.getAsJsonObject("handAdjust");
        if (handAdjust != null) {
            for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
                JsonObject byAction = handAdjust.getAsJsonObject(pose.name());
                if (byAction == null) {
                    continue;
                }
                for (RigTuning.HandAction action : RigTuning.HandAction.VALUES) {
                    JsonObject byArm = byAction.getAsJsonObject(action.name());
                    if (byArm == null) {
                        continue;
                    }
                    for (LimbType lt : LimbType.VALUES) {
                        double[] row = readRow(byArm, lt.name());
                        if (row != null) {
                            spec.handAdjust[pose.ordinal()][action.ordinal()][lt.ordinal()] = row;
                        }
                    }
                }
            }
        }
        return spec;
    }

    private static JsonObject toJson(RigSpec spec) {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "WFMedical hitbox rig geometry. Model units (1/16 block): "
                + "[ox,oy,oz, sx,sy,sz, px,py,pz]. Omitted rows fall back to built-in defaults. Animation "
                + "(setupAnim) is not data-driven.");

        JsonObject base = new JsonObject();
        for (LimbType lt : LimbType.VALUES) {
            base.add(lt.name(), writeRow(spec.base[lt.ordinal()]));
        }
        root.add("base", base);

        JsonObject poseAdjust = new JsonObject();
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            if (pose == RigTuning.RigPose.STANDING) {
                continue; // STANDING geometry lives in base; its adjust is always zero
            }
            JsonObject byLimb = new JsonObject();
            for (LimbType lt : LimbType.VALUES) {
                double[] row = spec.poseAdjust[pose.ordinal()][lt.ordinal()];
                if (nonZero(row)) {
                    byLimb.add(lt.name(), writeRow(row));
                }
            }
            if (byLimb.size() > 0) {
                poseAdjust.add(pose.name(), byLimb);
            }
        }
        if (poseAdjust.size() > 0) {
            root.add("poseAdjust", poseAdjust);
        }

        JsonObject handAdjust = new JsonObject();
        for (RigTuning.RigPose pose : RigTuning.RigPose.VALUES) {
            JsonObject byAction = new JsonObject();
            for (RigTuning.HandAction action : RigTuning.HandAction.VALUES) {
                if (action == RigTuning.HandAction.NONE) {
                    continue;
                }
                JsonObject byArm = new JsonObject();
                for (LimbType lt : LimbType.VALUES) {
                    if (!lt.isArm()) {
                        continue;
                    }
                    double[] row = spec.handAdjust[pose.ordinal()][action.ordinal()][lt.ordinal()];
                    if (nonZero(row)) {
                        byArm.add(lt.name(), writeRow(row));
                    }
                }
                if (byArm.size() > 0) {
                    byAction.add(action.name(), byArm);
                }
            }
            if (byAction.size() > 0) {
                handAdjust.add(pose.name(), byAction);
            }
        }
        if (handAdjust.size() > 0) {
            root.add("handAdjust", handAdjust);
        }
        return root;
    }

    private static double[] readRow(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) {
            return null;
        }
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr.size() != FIELDS) {
            WFMedical.LOGGER.warn("[{}] {}: '{}' has {} values, expected {} -- ignoring that row",
                    WFMedical.MOD_ID, FILE_NAME, key, arr.size(), FIELDS);
            return null;
        }
        double[] row = new double[FIELDS];
        for (int i = 0; i < FIELDS; i++) {
            row[i] = arr.get(i).getAsDouble();
        }
        return row;
    }

    private static JsonArray writeRow(double[] row) {
        JsonArray arr = new JsonArray(row.length);
        for (double v : row) {
            arr.add(v);
        }
        return arr;
    }

    private static boolean nonZero(double[] row) {
        for (double v : row) {
            if (v != 0.0) {
                return true;
            }
        }
        return false;
    }
}
