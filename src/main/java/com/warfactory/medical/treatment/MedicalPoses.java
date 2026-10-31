package com.warfactory.medical.treatment;

import com.norwood.ahf.hook.AhfHooks;
import com.norwood.ahf.pose.PoseLibrary;
import com.warfactory.medical.WFMedical;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Third-person treatment body: AHF pose sets ({@code player_animator/medkit.json}, {@code bandage.json}:
 * MineAssault {@code tools/ma.py tp-pose}, STALKER item use) while a treatment runs; server rig + AHF
 * {@code PoseSetLayers} render play the same clips. Server from the actor's active treatment
 * ({@code TreatmentNetwork.sendActiveTreatment}), clients from {@code TreatmentPosePacket}.
 */
public final class MedicalPoses {

    public enum Kind {
        NONE, MEDKIT, BANDAGE
    }

    public static final ResourceLocation MEDKIT = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "medkit");
    public static final ResourceLocation BANDAGE = ResourceLocation.fromNamespaceAndPath(WFMedical.MOD_ID, "bandage");

    /** Server thread. */
    private static final Map<Player, Kind> SERVER = new WeakHashMap<>();
    /** Entity id; client main thread. */
    private static final Int2ObjectOpenHashMap<Kind> CLIENT = new Int2ObjectOpenHashMap<>();

    private MedicalPoses() {
    }

    /** Mod construction, both sides. */
    public static void register() {
        for (ResourceLocation id : new ResourceLocation[]{MEDKIT, BANDAGE}) {
            String path = "/assets/" + id.getNamespace() + "/player_animator/" + id.getPath() + ".json";
            try (InputStream in = MedicalPoses.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + path);
                }
                PoseLibrary.register(id, in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        AhfHooks.addPoseSet(MedicalPoses::of);
    }

    /** Pose of a treatment with item {@code itemId}: injectors + medkit inject, oral none, other items wrap. */
    public static Kind kind(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item instanceof MedicalItem m && m.dose() != null || item == TreatmentContent.MEDKIT.get()) {
            return Kind.MEDKIT;
        }
        if (item instanceof MedicalItem m && m.getUseAnimation(new ItemStack(m)) != UseAnim.EAT) {
            return Kind.BANDAGE;
        }
        return Kind.NONE;
    }

    public static void server(ServerPlayer p, Kind k) {
        if (k == Kind.NONE) {
            SERVER.remove(p);
        } else {
            SERVER.put(p, k);
        }
    }

    public static Kind server(ServerPlayer p) {
        return SERVER.getOrDefault(p, Kind.NONE);
    }

    public static void client(int entityId, Kind k) {
        if (k == Kind.NONE) {
            CLIENT.remove(entityId);
        } else {
            CLIENT.put(entityId, k);
        }
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    @Nullable
    private static ResourceLocation of(LivingEntity e) {
        if (!(e instanceof Player p)) {
            return null;
        }
        Kind k = p.level().isClientSide() ? CLIENT.getOrDefault(p.getId(), Kind.NONE)
                : SERVER.getOrDefault(p, Kind.NONE);
        return switch (k) {
            case MEDKIT -> MEDKIT;
            case BANDAGE -> BANDAGE;
            case NONE -> null;
        };
    }
}
