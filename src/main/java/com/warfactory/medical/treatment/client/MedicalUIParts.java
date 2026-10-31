package com.warfactory.medical.treatment.client;

import com.warfactory.medical.body.HealthState;
import com.warfactory.medical.body.limb.LimbType;
import com.warfactory.medical.treatment.MedicalItem;
import com.warfactory.medical.body.network.MedicalSyncPacket.LimbSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;
import java.util.function.Function;
import com.warfactory.medical.body.client.ClientMedicalCache;
import com.warfactory.medical.body.network.BodyNetwork;
import com.warfactory.medical.treatment.network.CancelTreatmentPacket;
import com.warfactory.medical.treatment.network.MedicalActionPacket;
import com.warfactory.medical.treatment.network.RemoveTourniquetPacket;
import com.warfactory.medical.treatment.network.ResuscitatePacket;

public final class MedicalUIParts {

    private MedicalUIParts() {
    }

    public static LimbType selectedLimb() {
        return ClientMedicalCache.selectedLimb();
    }

    public static void selectLimb(LimbType limb) {
        ClientMedicalCache.setSelectedLimb(limb);
    }

    public static void requestAction(ItemStack medicalItemStack, LimbType limb, int targetEntityId) {
        if (medicalItemStack == null || medicalItemStack.isEmpty()
                || !(medicalItemStack.getItem() instanceof MedicalItem)) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(medicalItemStack.getItem());
        if (id == null) {
            return;
        }
        BodyNetwork.sendToServer(new MedicalActionPacket(id, limb, targetEntityId));
    }

    public static void requestResuscitate(int targetEntityId) {
        if (targetEntityId < 0) {
            return;
        }
        BodyNetwork.sendToServer(new ResuscitatePacket(targetEntityId));
    }

    public static void requestRemoveTourniquet(LimbType limb, int targetEntityId) {
        if (limb == null) {
            return;
        }
        BodyNetwork.sendToServer(new RemoveTourniquetPacket(limb, targetEntityId));
    }

    public static void requestCancelTreatment() {
        BodyNetwork.sendToServer(new CancelTreatmentPacket());
    }

    public static List<ItemStack> availableMedicalItems() {
        List<ItemStack> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return out;
        }
        Inventory inv = mc.player.getInventory();
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof MedicalItem && seen.add(stack.getItem())) {
                out.add(stack);
            }
        }
        return out;
    }

    public static Component limbName(LimbType limb) {
        return Component.translatable("gui.wfmedical.limb." + limb.name().toLowerCase(Locale.ROOT));
    }

    public static Component stateName(HealthState state) {
        HealthState s = state == null ? HealthState.HEALTHY : state;
        return Component.translatable("gui.wfmedical.state." + s.name().toLowerCase(Locale.ROOT));
    }

    public static List<Component> limbTooltip(LimbType limb, Function<LimbType, LimbSummary> source) {
        LimbSummary s = source.apply(limb);
        List<Component> lines = new ArrayList<>(4);
        lines.add(limbName(limb));
        lines.add(Component.translatable("gui.wfmedical.health")
                .append(Component.literal(": " + Math.round(s.healthPercent() * 100.0F) + "%")));
        lines.add(Component.translatable("gui.wfmedical.bleeding")
                .append(Component.literal(": " + String.format(Locale.ROOT, "%.1f", s.bleeding()))));
        lines.add(Component.translatable("gui.wfmedical.pain")
                .append(Component.literal(": " + String.format(Locale.ROOT, "%.1f", s.pain()))));
        if (s.fracture()) {
            lines.add(Component.translatable("gui.wfmedical.fracture"));
        }
        return lines;
    }
}
