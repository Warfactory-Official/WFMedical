"""Placeholder first-person props (baseColorFactor boxes) -> assets/wfmedical/models/treatment/<kind>_<track>.glb.

Box = baked `<track>Size` (view axes x right, y up, z back at rest), centred; the track rotates it from that rest pose.
Replace by overwriting the glb with the same centring and rest orientation.
usage: placeholder_models.py <treatment json dir> <out dir>
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'WF-Ballistics/tools/smoking'))
import placeholder_models as glb  # noqa: E402

COLOURS = {'bandage': [0.9, 0.88, 0.8, 1.0], 'tourniquet': [0.12, 0.12, 0.12, 1.0], 'medkit': [0.75, 0.12, 0.12, 1.0],
           'injector': [0.55, 0.58, 0.6, 1.0], 'syringe': [0.8, 0.85, 0.9, 1.0]}
LID = [0.92, 0.92, 0.92, 1.0]


def main(src, out):
    out.mkdir(parents=True, exist_ok=True)
    for path in sorted(src.glob('*.json')):
        data = json.load(open(path))
        for key in data:
            if not key.endswith('Size'):
                continue
            track = key[:-4]
            half = [s / 2 for s in data[key]]
            mesh = glb.Mesh()
            glb.box(mesh, 0, [-h for h in half], half)
            glb.write(out / f'{path.stem}_{track}.glb', f'{path.stem}_{track}', mesh,
                      [(track, LID if track == 'lid' else COLOURS[path.stem])])
            print(path.stem, track, data[key])


if __name__ == '__main__':
    main(Path(sys.argv[1]), Path(sys.argv[2]))
