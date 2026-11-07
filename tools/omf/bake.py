#!/usr/bin/env python3
"""Bake xray-monolith's stalker hit clips into src/main/resources/wfmedical_hit_clips.json.

Source: GAMMA's stalker_animation.omf (the 'hit*17' flinch clips of character_hit_animations.cpp and the
norm_2_critical_hit_* stagger clips of the critical-wound system). Each clip is forward-kinematics'd with the
biped chain, and every PlayerAnimator part gets the WORLD rotation delta of its bone chain against the clip's first
frame, as a quaternion [x,y,z,w] in Minecraft model space (x left, y down, z back = X-ray rotated 180 deg about x),
plus the pelvis offset in blocks. Run:  python3 bake.py [stalker_animation.omf]
"""
import json, sys, numpy as np, omf, fk

SRC = "/home/overlord/Games/GAMMA/gamma/mods/G.A.M.M.A. NPCs Faster Reactions/gamedata/meshes/actors/stalker_animation.omf"
OUT = "../../src/main/resources/wfmedical_hit_clips.json"
CLIPS = {  # json name -> omf name
    "hit_front": "hitfront17", "hit_back": "hitback17", "hit_right": "hitf_right17", "hit_left": "hitf_left17",
    "turn_right": "hit_right_shoulder17", "turn_left": "hit_left_shoulder17", "down": "hitf_down17",
    "down_l": "hit_downl", "down_r": "hit_downr",
    "head_0": "norm_2_critical_hit_head_0",
    "torso_0": "norm_2_critical_hit_torso_0", "torso_1": "norm_2_critical_hit_torso_1",
    "arm_l_0": "norm_2_critical_hit_hend_left_0", "arm_r_0": "norm_2_critical_hit_hend_right_0",
    "leg_l_0": "norm_2_critical_hit_legs_left_0", "leg_r_0": "norm_2_critical_hit_legs_right_0",
}
PARTS = {"torso": "bip01_spine1", "head": "bip01_head", "rightArm": "bip01_r_upperarm", "leftArm": "bip01_l_upperarm",
         "rightLeg": "bip01_r_thigh", "leftLeg": "bip01_l_thigh"}
D = np.diag([1.0, -1.0, -1.0])


def quat(R):
    t = np.trace(R)
    if t > 0:
        s = np.sqrt(t + 1) * 2; q = [(R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s, s / 4]
    elif R[0, 0] > R[1, 1] and R[0, 0] > R[2, 2]:
        s = np.sqrt(1 + R[0, 0] - R[1, 1] - R[2, 2]) * 2; q = [s / 4, (R[0, 1] + R[1, 0]) / s, (R[0, 2] + R[2, 0]) / s, (R[2, 1] - R[1, 2]) / s]
    elif R[1, 1] > R[2, 2]:
        s = np.sqrt(1 + R[1, 1] - R[0, 0] - R[2, 2]) * 2; q = [(R[0, 1] + R[1, 0]) / s, s / 4, (R[1, 2] + R[2, 1]) / s, (R[0, 2] - R[2, 0]) / s]
    else:
        s = np.sqrt(1 + R[2, 2] - R[0, 0] - R[1, 1]) * 2; q = [(R[0, 2] + R[2, 0]) / s, (R[1, 2] + R[2, 1]) / s, s / 4, (R[1, 0] - R[0, 1]) / s]
    q = np.array(q); q /= np.linalg.norm(q)
    return q if q[3] >= 0 else -q


def main():
    clips = {d["name"]: d for d in omf.load(sys.argv[1] if len(sys.argv) > 1 else SRC)}
    out = {"fps": 30, "clips": {}}
    for name, src in CLIPS.items():
        c = clips[src]
        n = c["frames"]
        W0 = fk.fk(c, 0, True)
        parts = {p: [] for p in PARTS}
        pelvis = []
        for k in range(n):
            W = fk.fk(c, k, True)
            for p, b in PARTS.items():
                R = D @ (W[b][0] @ W0[b][0].T) @ D
                parts[p].append([round(float(v), 4) for v in quat(R)])
            dp = W["bip01_pelvis"][1] - W0["bip01_pelvis"][1]
            pelvis.append([round(float(v), 4) for v in (dp[0], -dp[1], -dp[2])])
        out["clips"][name] = {"frames": n, "parts": parts, "pelvis": pelvis}
    with open(OUT, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    print(OUT, sum(len(c["parts"]["torso"]) for c in out["clips"].values()), "frames")


if __name__ == "__main__":
    main()
