#!/usr/bin/env python3
"""Summarise [balance] telemetry (/wfmedical balance on) from a latest.log: shots to down / to death per life."""
import re, sys, collections
log = sys.argv[1]
hit = re.compile(r'\[balance\] tick=(\d+) victim=(\S+) by=(\S+) src=(\S+) cat=(\S+) in=([\d.]+) limbs=(\S*) armor=(\S+) through=([\d.]+) lethal=([\d.]+) limbDown=(\S+) wasDowned=(\S+) finish=(\S+) instakill=(\S+) state=(\S+) blood=(\d+)')
tr = re.compile(r'\[balance\] tick=(\d+) victim=(\S+) transition (\S+) -> (\S+) blood=(\d+)')
class Life:
    def __init__(s): s.hits=[]; s.down=None; s.dead=None; s.start=None; s.expl=0
lives = []; cur = {}
def get(v, t):
    L = cur.get(v)
    if L is None or L.dead is not None:
        L = Life(); L.start = t; cur[v] = L; lives.append((v, L))
    return L
for line in open(log, errors="replace"):
    m = hit.search(line)
    if m:
        t, v, by, srcid, cat, amt, limbs, armor, thr, lethal, ld, wd, fin, ik, st, blood = m.groups()
        t = int(t)
        if float(amt) > 1000:      # the engine's own kill() call, not a hit
            continue
        prev = cur.get(v)
        if prev is not None and prev.dead is not None and t - prev.dead[0] < 400:
            continue                       # shooting a corpse
        L = get(v, t)
        L.hits.append((t, cat, limbs.split(",")[0], armor, float(thr), wd == "true"))
        if cat == "EXPLOSION": L.expl += 1
        if st == "UNCONSCIOUS" and L.down is None: L.down = (t, len(L.hits))
        if st == "DEAD" or ik == "true" or fin == "true":
            L.dead = (t, len(L.hits), "explosion" if ik == "true" and cat == "EXPLOSION" else ("headshot/lethal" if ik == "true" else ("downed-finish" if fin == "true" else "state")))
        continue
    m = tr.search(line)
    if m:
        t, v, a, b, blood = m.groups(); t = int(t)
        L = cur.get(v)
        if L is None or L.dead is not None: continue
        if b == "UNCONSCIOUS" and L.down is None: L.down = (t, len(L.hits))
        if b == "DEAD": L.dead = (t, len(L.hits), "bleed-out")
dl = []; dd = []; bl = []; causes = collections.Counter(); finish_hits = []
print(f"{'victim':10} {'hits':>4} {'->down':>7} {'s':>6} {'->dead':>7} {'s':>6}  cause")
for v, L in lives:
    if not L.hits: continue
    h0 = L.hits[0][0]
    d = L.down; k = L.dead
    print(f"{v:10} {len(L.hits):4} {str(d[1]) if d else '-':>7} {((d[0]-h0)/20 if d else 0):6.1f} {str(k[1]) if k else '-':>7} {((k[0]-h0)/20 if k else 0):6.1f}  {k[2] if k else 'alive'}")
    if d: dl.append(d[1])
    if k:
        dd.append(k[1]); causes[k[2]] += 1
        if d: bl.append((k[0]-d[0])/20)
n = len(lives)
f = lambda a: f"{sum(a)/len(a):.1f}" if a else "-"
print(f"\nlives {n}; downed {len(dl)}; died {len(dd)}; mean hits to down {f(dl)}, to death {f(dd)}; mean down->dead {f(bl)} s")
print("death causes:", dict(causes))
limbs = collections.Counter(); arm = collections.Counter(); thr = collections.defaultdict(list)
for _, L in lives:
    for t, cat, limb, a, th, wd in L.hits:
        if cat != "BALLISTIC": continue
        limbs[limb] += 1; arm[a] += 1; thr[limb].append(th)
print("ballistic hits by limb:", {k: f"{v} (avg through {sum(thr[k])/len(thr[k]):.1f})" for k, v in limbs.most_common()})
print("armor outcomes (ballistic):", dict(arm))
