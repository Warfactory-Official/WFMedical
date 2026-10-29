"""STALKER GAMMA healing clips -> assets/wfmedical/treatment/<kind>.json (wflib HandClip format).

Dumps from dump_gamma.py. Per kind: GAMMA hands clip + item clip + camera effector + item ltx `hands_position` /
`hands_orientation` (Bandage and Tourniquet Reanimation, FDDA, Asnen's/Grok's Better Cigarettes Animations).
usage: bake_treatment.py <dump dir> <out dir>
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'WF-Ballistics/tools/handclip'))
import handclip  # noqa: E402

# kind: (hands clip, item clip, camera, hands_position, hands_orientation, {track: (bone, vertex bones)})
KINDS = {
    'bandage': ('bint', 'bint', 'bandage', (0.0, -0.11, 0.17), (0.0, 20.0, 0.0), {'item': ('wpn_body', ['wpn_body'])}),
    'tourniquet': ('bint1', 'bint1', 'bint_use', (-0.0055, -0.05, 0.35), (0.0, 0.0, 0.0), {'item': ('bint', ['bint'])}),
    'medkit': ('aptechka_hands_eat_invtrans', 'aptechka_wpn_eat', 'aptechka', (-0.0055, -0.07, 0.36), (0.0, 0.0, 0.0),
               {'item': ('wpn_body', ['wpn_body']), 'lid': ('krishka', ['krishka'])}),
    'injector': ('antirad_hands_eat', 'antirad_wpn_eat', 'antirad', (-0.0055, -0.05, 0.32), (0.0, 0.0, 0.0),
                 {'item': ('wpn_body', ['wpn_body'])}),
    'syringe': ('syringe_hand', 'syringe', 'antirad', (-0.0055, 0.02, 0.04), (0.0, 0.0, 0.0),
                {'item': ('wpn_body', ['wpn_body'])}),
}


def main(dump, out):
    cams = json.load(open(dump / 'anm.json'))
    out.mkdir(parents=True, exist_ok=True)
    for kind, (hands_clip, item_clip, cam, position, orientation, props) in KINDS.items():
        d = json.load(open(dump / f'{kind}.json'))
        baked = handclip.bake(d['hands']['clips'][hands_clip], d['gun'], item_clip, props, cams[cam]['frames'],
                              position, orientation)
        json.dump(baked, open(out / f'{kind}.json', 'w'), separators=(',', ':'))
        print(kind, baked['frames'], 'frames', baked['duration'], 's', {k: v for k, v in baked.items() if k.endswith('Size')})


if __name__ == '__main__':
    main(Path(sys.argv[1]), Path(sys.argv[2]))
