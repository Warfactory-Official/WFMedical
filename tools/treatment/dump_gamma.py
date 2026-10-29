"""STALKER GAMMA healing clips -> Blender dumps for bake_treatment.py.

Runs MineAssault tools/blender/stalker_hud_dump.py (hands + item) and an `xray_import.anm` camera sampler under flatpak Blender.
usage: dump_gamma.py <out dir>   (default ~/models/med_dump)
"""
import json
import subprocess
import sys
from pathlib import Path

HOME = Path.home()
MODS = HOME / 'games/gamma/GAMMA/mods'
FDDA = MODS / '40- Food Drugs and Drinks Animations FDDA - Feel_Fried/gamedata'
BINTS = MODS / 'G.A.M.M.A. Bandage and Tourniquet Reanimation/gamedata'
MEDS = MODS / 'G.A.M.M.A. Medications Balance/gamedata'
HANDS = HOME / 'games/gamma/_blender_work/unpacked_weapons_base/meshes/anomaly_weapons/hands/wpn_hand_01.ogf'
DUMP = Path(__file__).resolve().parents[3] / 'MineAssault/tools/blender/stalker_hud_dump.py'
BLENDER = ['flatpak', 'run', f'--env=BLENDER_USER_CONFIG={HOME}/models/.blender_headless_cfg',
           f'--env=BLENDER_USER_EXTENSIONS={HOME}/models/.blender_headless_ext', '--command=blender', 'org.blender.Blender']
EAT = FDDA / 'meshes/dynamics/weapons/wpn_eat'
H = 'meshes/anomaly_weapons/hud_hands_animation/'

KINDS = {
    'bandage': (BINTS / (H + 'binthands.omf'), ['bint'], BINTS / 'meshes/dynamics/bints/bandage_hud.ogf',
                BINTS / 'meshes/anomaly_weapons/hud_animation/bintobject.omf', ['bint']),
    'tourniquet': (BINTS / (H + 'binthands.omf'), ['bint1'], BINTS / 'meshes/dynamics/bints/wpn_bint_arm_hud.ogf',
                   BINTS / 'meshes/anomaly_weapons/hud_animation/bintobject.omf', ['bint1']),
    'medkit': (FDDA / (H + 'zzzz_ea_a_main.omf'), ['aptechka_hands_eat_invtrans'],
               EAT / 'aptechki/wpn_aptechka_basic_hud.ogf', EAT / 'aptechki/wpn_aptechki_hud_anims.omf',
               ['aptechka_wpn_eat']),
    'injector': (FDDA / (H + 'zzzz_ea_a_main.omf'), ['antirad_hands_eat'],
                 EAT / 'injectors/wpn_injector_big_antirad_hud.ogf', EAT / 'injectors/injector_big_hud_anims.omf',
                 ['antirad_wpn_eat']),
    'syringe': (FDDA / (H + 'zzzz_ea_syringe.omf'), ['syringe_hand'], EAT / 'injectors/wpn_syringe_hud.ogf',
                EAT / 'injectors/syringe.omf', ['syringe']),
}
CAMERAS = {
    'bandage': BINTS / 'anims/itemuse_anm_effects/bandage.anm',
    'bint_use': FDDA / 'anims/itemuse_anm_effects/bint_use.anm',
    'aptechka': FDDA / 'anims/itemuse_anm_effects/aptechka_use.anm',
    'antirad': MEDS / 'anims/itemuse_anm_effects/antirad_use.anm',
}
ANM = '''import bpy, addon_utils, json, sys, os
bpy.ops.wm.read_factory_settings(use_empty=True)
addon_utils.enable('io_scene_xray', default_set=True, persistent=False)
out = {}
for name, path in json.load(open(sys.argv[sys.argv.index('--') + 1])).items():
    before = set(bpy.data.objects)
    folder, f = os.path.split(path)
    bpy.ops.xray_import.anm(directory=folder, files=[{'name': f}])
    o = next(o for o in bpy.data.objects if o not in before)
    a, b = (int(round(x)) for x in o.animation_data.action.frame_range)
    frames = []
    for k in range(a, b + 1):
        bpy.context.scene.frame_set(k)
        frames.append({'t': k, 'm': [list(r) for r in o.matrix_world]})
    out[name] = {'frames': frames}
json.dump(out, open(os.path.expanduser(sys.argv[sys.argv.index('--') + 2]), 'w'))
'''


def run(script, *args):
    subprocess.run([*BLENDER, '-b', '--factory-startup', '--python-exit-code', '1', '--python', str(script), '--', *map(str, args)],
                   check=True)


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    for kind, (hands, only_hands, gun, gun_motions, only_gun) in KINDS.items():
        cfg = out / f'cfg_{kind}.json'
        cfg.write_text(json.dumps({'hands': str(HANDS), 'handMotions': [str(hands)], 'onlyHands': only_hands,
                                   'gun': str(gun), 'gunMotions': [str(gun_motions)], 'onlyGun': only_gun,
                                   'out': str(out / f'{kind}.json')}))
        run(DUMP, cfg)
    (out / 'anm.py').write_text(ANM)
    (out / 'anm_cfg.json').write_text(json.dumps({k: str(v) for k, v in CAMERAS.items()}))
    run(out / 'anm.py', out / 'anm_cfg.json', out / 'anm.json')


if __name__ == '__main__':
    main(Path(sys.argv[1] if len(sys.argv) > 1 else HOME / 'models/med_dump'))
