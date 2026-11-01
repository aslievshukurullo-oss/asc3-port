#!/usr/bin/env python3
"""Decoder for Gameloft "ASprite" files used by GloftASC3 (class `a` in the decompiled source,
parser = a.a(byte[]), pixel decoder = a.E(int)).

Layout (little-endian):
  u16 magic (0x05DF) ; u32 flags
  u16 nModules
  per module: type byte (0 = bitmap; 255..247 = vector primitives) + optional fields (see parse())
  u16 nFrameModules ; per FM : moduleIdx, x, y, [extra], transform/hi-bits
  [flags&0x8000: u16 n ; n*4 collision/bounds values]
  u16 nFrames ; per frame: nFM, firstFM, [collision count]; [if !(flags&0x1000): 4 bounds values / frame]
  u16 nAnimFrames ; per AF: frameIdx, duration, x, y, flags
  u16 nAnims ; per anim: nAF (u8), firstAF (u16)
  if flags&0x1000000 : palettes + per-module pixel data (format code `aw`)
"""
import struct, sys, os, json
from PIL import Image


class R:
    def __init__(s, b, p=0): s.b, s.p = b, p
    def u8(s): v = s.b[s.p]; s.p += 1; return v
    def s8(s): v = s.u8(); return v - 256 if v > 127 else v
    def u16(s): v = s.b[s.p] | s.b[s.p+1] << 8; s.p += 2; return v
    def s16(s): v = s.u16(); return v - 65536 if v > 32767 else v
    def u32(s): v = struct.unpack_from('<I', s.b, s.p)[0]; s.p += 4; return v


def pal_color(fmt, r, flags_out):
    if fmt == -30584:    # 0x8888 : 4 bytes ARGB (LE int)
        v = r.u32(); return v
    if fmt == 17476:     # 0x4444 : ARGB4444
        n = r.u16()
        a, rr, g, b = (n >> 12) & 15, (n >> 8) & 15, (n >> 4) & 15, n & 15
        return (a*17) << 24 | (rr*17) << 16 | (g*17) << 8 | b*17
    if fmt == 21781:     # 0x5515 : ARGB1555 (bit15 = opaque)
        n = r.u16()
        c = 0xFF000000 if n & 0x8000 else 0
        c |= (n & 0x7C00) << 9 | (n & 0x3E0) << 6 | (n & 0x1F) << 3
        return 0xFF00FF if (c & 0xFFFFFF) == 0xF800F8 else c
    if fmt == 25861:     # 0x6505 : RGB565, 0xF81F = transparent
        n = r.u16()
        c = 0 if n == 63519 else 0xFF000000
        c |= (n & 0xF800) << 8 | (n & 0x7E0) << 5 | (n & 0x1F) << 3
        return 0xFF00FF if (c & 0xFFFFFF) == 0xF800F8 else c
    if fmt == -30712:    # 0x8888 : 3 bytes BGR, 0xFF00FF = transparent
        v = r.u8() | r.u8() << 8 | r.u8() << 16
        return 0 if v == 0xFF00FF else v | 0xFF000000
    raise ValueError('palette fmt %d' % fmt)


class ASprite:
    def __init__(s, data):
        s.d = data
        s.parse()

    def parse(s):
        d = s.d
        r = R(d, 0)
        s.magic = r.u16()
        s.flags = F = r.u32()
        n = s.nmod = r.u16()
        s.mw = [0]*n; s.mh = [0]*n; s.mtype = [0]*n; s.mpal = [0]*n; s.mextra = [None]*n
        n7 = n6 = 0
        for i in range(n):
            t = d[r.p]
            n5 = n4 = n3 = 0
            if t == 0:
                r.p += 1; n7 = 0; n6 = 1
                if F & 4: s.mpal[i] = r.u8()
            elif 255 >= t >= 247:
                r.p += 1
                s.mtype[i] = {255: 1, 254: 2, 253: 5, 252: 3, 251: 4, 250: 6, 249: 7, 248: 8, 247: 9}[t]
                if t == 255 or t == 254: n7, n6 = 1, 1
                elif t == 253: n7, n6 = 0, 1
                elif t in (252, 251): n7, n6, n5 = 1, 1, 1
                elif t in (250, 249): n7, n6, n4 = 1, 0, 1
                elif t == 248: n7, n6 = 1, 1
                elif t == 247: n7, n3, n6 = 1, 1, 1
            ex = []
            if n7: ex.append(r.u32())
            if n3: ex += [r.u32(), r.s8()]
            if n6:
                if F & 0x10: s.mw[i], s.mh[i] = r.u16(), r.u16()
                else: s.mw[i], s.mh[i] = r.u8(), r.u8()
            if n5: ex += [r.s16(), r.s16()]
            if n4: ex += [r.s16(), r.s16(), r.s16(), r.s16()]
            s.mextra[i] = ex
        # frame modules
        nfm = r.u16()
        s.fm = []
        for i in range(nfm):
            mi = r.u16() if F & 0x100000 else r.u8()
            if F & 0x400: x, y = r.s16(), r.s16()
            else: x, y = r.s8(), r.s8()
            if F & 0x4000: r.u8()
            tr = r.u8()
            if not F & 0x100000: mi |= (tr & 0xC0) << 2
            s.fm.append((mi, x, y, tr & 0xF, tr))
        if F & 0x8000:
            c = r.u16(); k = c << 2
            for _ in range(k): r.u16() if F & 0x400 else r.u8()
        # frames
        nfr = r.u16()
        s.frames = []
        for i in range(nfr):
            cnt = r.u16() if F & 0x800 else r.u8()
            first = r.u16()
            if F & 0x8000: r.u8()
            s.frames.append([cnt, first])
        if nfr and not F & 0x1000:
            for i in range(nfr):
                b = [r.s16() if F & 0x400 else r.s8() for _ in range(4)]
                s.frames[i].append(b)
        # animation frames
        naf = r.u16()
        s.af = []
        for i in range(naf):
            fi = r.u16() if F & 0x200000 else r.u8()
            dur = r.u8()
            if F & 0x40000: x, y = r.s16(), r.s16()
            else: x, y = r.s8(), r.s8()
            fl = r.u8()
            if not F & 0x200000: fi |= (fl & 0xC0) << 2
            s.af.append((fi, dur, x, y, fl & 0xF))
        nan = r.u16()
        s.anims = []
        for i in range(nan):
            c = r.u8(); first = r.u16()
            s.anims.append((c, first))
        s.pal = None; s.fmt = None; s.pix = None
        if nmod_has_img(F) and n > 0 and r.p < len(d):
            s.parse_img(r)

    def parse_img(s, r):
        F = s.flags
        pf = r.s16()
        nsets = r.s8() if F & 0x40000000 else 1
        s.pal = []
        for si in range(nsets):
            g = r.u8()
            nc = r.u8() or 256
            sets = []
            for _ in range(g):
                sets.append([pal_color(pf, r, None) for _ in range(nc)])
            s.pal.append((nc, sets))
        s.fmt = r.s16()
        s.pix = []
        big = bool(F & 0x80)
        off = r.p
        # pass 1: sizes interleaved with data
        for i in range(s.nmod):
            sz = r.u32() if big else r.s16()
            s.pix.append(r.b[r.p:r.p+sz] if sz > 0 else b'')
            r.p += sz if sz > 0 else 0

    def decode_module(s, i, pal_idx=0):
        w, h = s.mw[i], s.mh[i]
        if s.mtype[i] != 0 or not w or not h or s.pal is None: return None
        nc, sets = s.pal[s.mpal[i] if s.mpal[i] < len(s.pal) else 0]
        pal = sets[pal_idx if pal_idx < len(sets) else 0]
        data = s.pix[i]
        n = w*h
        out = [0]*n
        fmt = s.fmt
        p = 0; o = 0
        bits = 0; m = 1
        while (m) < nc: m <<= 1
        m -= 1                       # mask = nextpow2(nc)-1
        t = nc - 1; bits = 0
        while t: bits += 1; t >>= 1
        mask = (1 << bits) - 1
        try:
            if fmt in (25840, -22976):
                while o < n:
                    b = data[p]; p += 1
                    c = pal[b & mask] if (b & mask) < nc else 0
                    cnt = (b >> bits) + 1
                    for _ in range(cnt):
                        if o < n: out[o] = c; o += 1
            elif fmt in (10225, -24281):
                while o < n:
                    b = data[p]; p += 1
                    if b > 127:
                        c = pal[data[p]]; p += 1
                        for _ in range(b-128):
                            if o < n: out[o] = c; o += 1
                    else:
                        out[o] = pal[b]; o += 1
            elif fmt == 22258:
                while o < n:
                    b = data[p]; p += 1
                    if b > 127:
                        b -= 256
                    if b < 0:
                        b += 128
                        while True:
                            out[o] = pal[data[p]]; o += 1; p += 1; b -= 1
                            if b <= 0 or o >= n: break
                    else:
                        c = pal[data[p]]; p += 1
                        while True:
                            if o < n: out[o] = c; o += 1
                            b -= 1
                            if b <= 0: break
            elif fmt == 5632:
                while o < n:
                    b = data[p]; p += 1
                    out[o] = pal[b >> 4 & 15]; o += 1
                    if o < n: out[o] = pal[b & 15]; o += 1
            elif fmt == 1024:
                while o < n:
                    b = data[p]; p += 1
                    for sh in (6, 4, 2, 0):
                        if o < n: out[o] = pal[b >> sh & 3]; o += 1
            elif fmt == 512:
                while o < n:
                    b = data[p]; p += 1
                    for sh in range(7, -1, -1):
                        if o < n: out[o] = pal[b >> sh & 1]; o += 1
            elif fmt == 22018:
                for o in range(n): out[o] = pal[data[o]]
            elif fmt == -23198:
                for o in range(n):
                    a = data[p]; p += 1
                    out[o] = a << 24 | pal[data[p]] & 0xFFFFFF; p += 1
            else:
                return None
            if fmt in (-22976, -24281):          # second pass: alpha plane
                o = 0
                while o < n:
                    b = data[p]; p += 1
                    if b == 254:
                        cnt = data[p]; a = data[p+1]; p += 2
                        for _ in range(cnt):
                            if o < n: out[o] = a << 24 | out[o] & 0xFFFFFF; o += 1
                    else:
                        out[o] = b << 24 | out[o] & 0xFFFFFF; o += 1
        except IndexError:
            pass
        im = Image.new('RGBA', (w, h))
        im.putdata([((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255) for c in out])
        return im

    def render_frame(s, fi, pal_idx=0):
        cnt, first = s.frames[fi][0], s.frames[fi][1]
        parts = []
        minx = miny = 10**9; maxx = maxy = -10**9
        for k in range(cnt):
            mi, x, y, tr, raw = s.fm[first+k]
            if mi >= s.nmod: continue
            im = s.decode_module(mi, pal_idx)
            if im is None: continue
            if tr & 1: im = im.transpose(Image.FLIP_LEFT_RIGHT)
            if tr & 2: im = im.transpose(Image.FLIP_TOP_BOTTOM)
            if tr & 4: im = im.transpose(Image.ROTATE_270)
            parts.append((im, x, y))
            minx = min(minx, x); miny = min(miny, y)
            maxx = max(maxx, x+im.width); maxy = max(maxy, y+im.height)
        if not parts: return None, (0, 0)
        out = Image.new('RGBA', (maxx-minx, maxy-miny))
        for im, x, y in parts:
            out.alpha_composite(im, (x-minx, y-miny))
        return out, (minx, miny)


def nmod_has_img(F): return bool(F & 0x1000000)


def dump(path, outdir):
    data = open(path, 'rb').read()
    sp = ASprite(data)
    os.makedirs(outdir, exist_ok=True)
    meta = dict(file=os.path.basename(path), flags=hex(sp.flags), modules=sp.nmod, frames=len(sp.frames),
                anims=len(sp.anims), fmt=sp.fmt, palettes=[(nc, len(s)) for nc, s in (sp.pal or [])],
                module_size=[(sp.mw[i], sp.mh[i], sp.mtype[i]) for i in range(sp.nmod)],
                fm=sp.fm and [list(x[:4]) for x in sp.fm], frame=[[f[0], f[1]] for f in sp.frames],
                af=[list(x) for x in sp.af], anim=[list(x) for x in sp.anims])
    json.dump(meta, open(os.path.join(outdir, 'sprite.json'), 'w'))
    nm = nf = 0
    md = os.path.join(outdir, 'modules'); os.makedirs(md, exist_ok=True)
    for i in range(sp.nmod):
        im = sp.decode_module(i)
        if im: im.save(os.path.join(md, '%04d.png' % i)); nm += 1
    fd = os.path.join(outdir, 'frames'); os.makedirs(fd, exist_ok=True)
    for i in range(len(sp.frames)):
        try:
            im, off = sp.render_frame(i)
        except Exception:
            im = None
        if im: im.save(os.path.join(fd, '%04d.png' % i)); nf += 1
    return sp, nm, nf


if __name__ == '__main__':
    path, out = sys.argv[1], sys.argv[2]
    sp, nm, nf = dump(path, out)
    print(path, 'modules', sp.nmod, 'decoded', nm, 'frames', len(sp.frames), 'rendered', nf, 'fmt', sp.fmt)
