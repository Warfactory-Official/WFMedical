import numpy as np, omf
PARENT = {"root_stalker": None, "bip01": "root_stalker", "bip01_pelvis": "bip01", "bip01_tail": "bip01_pelvis",
          "bip01_l_thigh": "bip01_pelvis", "bip01_l_calf": "bip01_l_thigh", "bip01_l_foot": "bip01_l_calf", "bip01_l_toe0": "bip01_l_foot",
          "bip01_r_thigh": "bip01_pelvis", "bip01_r_calf": "bip01_r_thigh", "bip01_r_foot": "bip01_r_calf", "bip01_r_toe0": "bip01_r_foot",
          "bip01_spine": "bip01_pelvis", "bip01_spine1": "bip01_spine", "bip01_spine2": "bip01_spine1",
          "bip01_neck": "bip01_spine2", "bip01_head": "bip01_neck",
          "bip01_l_clavicle": "bip01_spine2", "bip01_l_upperarm": "bip01_l_clavicle", "bip01_l_forearm": "bip01_l_upperarm", "bip01_l_hand": "bip01_l_forearm",
          "bip01_r_clavicle": "bip01_spine2", "bip01_r_upperarm": "bip01_r_clavicle", "bip01_r_forearm": "bip01_r_upperarm", "bip01_r_hand": "bip01_r_forearm"}
def qmat(q):
    x, y, z, w = q
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
                     [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
                     [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])
def fk(clip, k, transpose):
    W = {}
    def go(b):
        if b in W: return W[b]
        q, t = clip["tracks"][b]
        R = qmat(q[k if len(q) > 1 else 0]); R = R.T if transpose else R
        T = np.array(t[k if len(t) > 1 else 0])
        p = PARENT[b]
        if p is None: W[b] = (R, T)
        else:
            Rp, Tp = go(p)
            W[b] = (Rp @ R, Tp + Rp @ T)
        return W[b]
    for b in PARENT: go(b)
    return W
if __name__ == "__main__":
    M = "/home/overlord/Games/GAMMA/gamma/mods/G.A.M.M.A. NPCs Faster Reactions/gamedata/meshes/actors/stalker_animation.omf"
    d = {x["name"]: x for x in omf.load(M)}
    c = d["hitfront17"]
    for tr in (False, True):
        W = fk(c, 0, tr)
        print("transpose", tr, {b: np.round(W[b][1], 2).tolist() for b in ("bip01_pelvis", "bip01_spine2", "bip01_head", "bip01_l_hand", "bip01_l_foot")})
