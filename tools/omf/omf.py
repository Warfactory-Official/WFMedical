"""Minimal X-ray OMF (skeletal motion) reader, format per xrEngine/SkeletonMotions.cpp (motions_value::load).

  omf.py FILE.omf [name-substring]     list motions (name, frames, speed, bones animated)
"""
import struct, sys


def chunks(buf, off=0, end=None):
    end = len(buf) if end is None else end
    while off + 8 <= end:
        cid, size = struct.unpack_from("<II", buf, off)
        yield cid & 0x7FFFFFFF, off + 8, size
        off += 8 + size


def zstr(buf, off):
    e = buf.index(b"\0", off)
    return buf[off:e].decode("latin1"), e + 1


def load(path):
    buf = open(path, "rb").read()
    root = {cid: (o, s) for cid, o, s in chunks(buf)}
    # --- definitions (chunk 15)
    o, s = root[15]
    ver, nparts = struct.unpack_from("<HH", buf, o); o += 4
    slot = {}                                   # track index -> bone name
    for _ in range(nparts):
        _, o = zstr(buf, o)
        n, = struct.unpack_from("<H", buf, o); o += 2
        for _ in range(n):
            bone, o = zstr(buf, o)
            idx, = struct.unpack_from("<I", buf, o); o += 4
            slot[idx] = bone.lower()
    nmot, = struct.unpack_from("<H", buf, o); o += 2
    defs = []
    for _ in range(nmot):
        name, o = zstr(buf, o)
        flags, = struct.unpack_from("<I", buf, o); o += 4
        part, mot, speed, power, accrue, falloff = struct.unpack_from("<HHffff", buf, o); o += 20
        if ver >= 4:
            cnt, = struct.unpack_from("<I", buf, o); o += 4
            for _ in range(cnt):                # marks: a line of text, then intervals
                e = o
                while buf[e] not in (10, 13): e += 1
                o = e + 1
                if buf[e] == 13 and buf[o] == 10: o += 1
                n, = struct.unpack_from("<I", buf, o); o += 4 + 8 * n
        defs.append(dict(name=name.lower(), flags=flags, speed=speed, power=power, accrue=accrue, falloff=falloff))
    # --- keys (chunk 14)
    mo, ms = root[14]
    sub = {cid: (o2, s2) for cid, o2, s2 in chunks(buf, mo, mo + ms)}
    for i, d in enumerate(defs):
        o, _ = sub[i + 1]
        name, o = zstr(buf, o)
        n, = struct.unpack_from("<I", buf, o); o += 4
        d["frames"] = n
        tracks = {}
        for b in range(len(slot)):
            fl = buf[o]; o += 1
            if fl & 2:
                r = [struct.unpack_from("<4h", buf, o)] * 1; o += 8
            else:
                o += 4                           # crc
                r = [struct.unpack_from("<4h", buf, o + 8 * k) for k in range(n)]; o += 8 * n
            t = None
            if fl & 1:
                o += 4
                if fl & 4:
                    t = [struct.unpack_from("<3h", buf, o + 6 * k) for k in range(n)]; o += 6 * n
                else:
                    t = [struct.unpack_from("<3b", buf, o + 3 * k) for k in range(n)]; o += 3 * n
                size = struct.unpack_from("<3f", buf, o); o += 12
                init = struct.unpack_from("<3f", buf, o); o += 12
                t = [tuple(k[j] * size[j] + init[j] for j in range(3)) for k in t]
            else:
                t = [tuple(struct.unpack_from("<3f", buf, o))] * 1; o += 12
            q = [tuple(c / 32767.0 for c in k) for k in r]       # x,y,z,w
            tracks[slot.get(b, str(b))] = (q, t)
        d["tracks"] = tracks
    return defs


if __name__ == "__main__":
    for d in load(sys.argv[1]):
        if len(sys.argv) < 3 or sys.argv[2] in d["name"]:
            print(f'{d["name"]:46} frames={d["frames"]:4} speed={d["speed"]:.2f} fx={bool(d["flags"] & 1)} tracks={len(d["tracks"])}')
