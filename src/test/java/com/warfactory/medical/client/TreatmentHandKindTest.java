package com.warfactory.medical.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TreatmentHandKindTest {

    @Test
    void everyRegisteredItemMapsOrIsDeliberatelyVanilla() {
        Map<String, TreatmentHandKind> actual = new TreeMap<>();
        ModItems.ITEMS.getEntries().forEach(e -> actual.put(e.getId().getPath(), TreatmentHandKind.of(e.getId())));
        Map<String, TreatmentHandKind> expected = new TreeMap<>(Map.of(
                "bandage", TreatmentHandKind.BANDAGE, "splint", TreatmentHandKind.BANDAGE,
                "suture_kit", TreatmentHandKind.BANDAGE, "blood_bag", TreatmentHandKind.BANDAGE,
                "burn_ointment", TreatmentHandKind.BANDAGE, "tourniquet", TreatmentHandKind.TOURNIQUET,
                "medkit", TreatmentHandKind.MEDKIT, "antirad_shot", TreatmentHandKind.INJECTOR,
                "combat_stimulant_i", TreatmentHandKind.INJECTOR, "morphine_syringe", TreatmentHandKind.SYRINGE));
        expected.put("naloxone_syringe", TreatmentHandKind.SYRINGE);
        expected.put("local_anesthetic", TreatmentHandKind.SYRINGE);
        // oral: eat animation stays
        expected.put("painkillers", null);
        expected.put("hemostatic", null);
        assertEquals(expected, actual);
    }

    @Test
    void foreignItemsStayVanilla() {
        assertNull(TreatmentHandKind.of(ResourceLocation.fromNamespaceAndPath("minecraft", "bandage")));
    }

    @Test
    void everyKindShipsItsClipAndPropModels() throws IOException {
        for (TreatmentHandKind kind : TreatmentHandKind.values()) {
            String path = "/assets/" + WFMedical.MOD_ID + "/" + kind.clip.getPath();
            try (InputStream in = TreatmentHandKindTest.class.getResourceAsStream(path)) {
                assertNotNull(in, path);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                int frames = json.get("frames").getAsInt();
                for (String track : new String[]{"right", "left", "cam"}) {
                    assertEquals(frames, json.getAsJsonArray(track).size(), kind + " " + track);
                }
                for (String track : kind.tracks) {
                    assertEquals(frames, json.getAsJsonArray(track).size(), kind + " " + track);
                    String model = "/assets/" + WFMedical.MOD_ID + "/" + kind.model(track).getPath();
                    try (InputStream glb = TreatmentHandKindTest.class.getResourceAsStream(model)) {
                        assertNotNull(glb, model);
                    }
                }
            }
        }
    }
}
