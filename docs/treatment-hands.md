# Treatment hands (first person)

WFLib present => `compat.wflib.TreatmentHandsClient` (registered in `WFMedicalClient`): vanilla hands cancelled, `HandClipView` solves arms onto baked GAMMA fist tracks, prop glbs follow item tracks, camera effector added. Absent => vanilla hands.

| kind | items | GAMMA hands clip | camera |
|---|---|---|---|
| `BANDAGE` | bandage, splint, suture_kit, blood_bag, burn_ointment | `bint` | `bandage.anm` |
| `TOURNIQUET` | tourniquet | `bint1` | `bint_use.anm` |
| `MEDKIT` | medkit | `aptechka_hands_eat_invtrans` | `aptechka_use.anm` |
| `INJECTOR` | antirad_shot, combat_stimulant_i | `antirad_hands_eat` | `antirad_use.anm` |
| `SYRINGE` | morphine_syringe, naloxone_syringe, local_anesthetic | `syringe_hand` | `antirad_use.anm` (as GAMMA morphine) |

- Vanilla hands kept: painkillers, hemostatic (eat animation), foreign items. `TreatmentHandKind.of`.
- Kind from main-hand item while `ClientMedicalCache.hasActiveTreatment()`; runs for treating others too.
- Clip time = treatment progress x clip length (clip ends with the treatment); native lengths 3.97-7.03 s vs treatment 1-9 s.
- 3P body: separate (`MedicalPoses`).
- Unverified: `tourniquet` = `bint1` (GAMMA ltx names no clip); `hands_orientation` heading sign (bandage 20 deg); ltx `hud_fov` ignored.
- Data `assets/wfmedical/treatment/<kind>.json` (wflib `HandClip`: right/left fist + quat, prop tracks `item` / `lid`, `cam`).
- Props `assets/wfmedical/models/treatment/<kind>_<track>.glb`: PLACEHOLDER boxes. Replace by overwriting: centred on the GAMMA bone bbox, rest pose in view axes (x right, y up, z back), sizes `<track>Size` in the json.
- Regenerate: `tools/treatment/dump_gamma.py` (flatpak Blender) -> `bake_treatment.py <dump> <treatment dir>`. `placeholder_models.py` OVERWRITES models.
- Baker shared with the cigarette: WF-Ballistics `tools/handclip/handclip.py`.
