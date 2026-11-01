#!/usr/bin/env python3
"""Convert extracted GloftASC3 resources into LibGDX-friendly assets.
  sprites -> assets/sprites/<pack>_<idx>.png (module atlas, palette 0 baked) + .json (modules, frame-modules, frames, anims)
  strings -> assets/strings/<pack>_<n>.json
  audio   -> assets/audio/*.mid / *.mp3
  others  -> assets/raw/<pack>/<idx>.bin (untouched; level/entity data, see docs)
"""
import os, sys, json, struct, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import asprite
from PIL import Image

ex, out = sys.argv[1], sys.argv[2]
for sub in ('sprites', 'strings', 'audio', 'raw'): os.makedirs(os.path.join(out, sub), exist_ok=True)
AW = 1024

def shelf(imgs):
    x = y = rowh = 0; pos = []
    for im in imgs:
        if im is None: pos.append(None); continue
        w, h = im.size
        if x + w > AW: x = 0; y += rowh + 1; rowh = 0
        pos.append((x, y)); x += w + 1; rowh = max(rowh, h)
    return pos, y + rowh

stats = dict(sprites=0, strings=0, audio=0, raw=0)
for pack in sorted(os.listdir(ex), key=lambda x: int(x) if x.isdigit() else 999):
    pd = os.path.join(ex, pack)
    for f in sorted(os.listdir(pd)):
        if f == '_index.json': continue
        p = os.path.join(pd, f); b = open(p, 'rb').read(); idx = f.split('_')[0]
        if f.endswith('.mid'):
            shutil.copy(p, os.path.join(out, 'audio', 'p%s_%s.mid' % (pack, idx))); stats['audio'] += 1
        elif f.endswith('_t1.bin') and pack == '3':
            shutil.copy(p, os.path.join(out, 'audio', 'p%s_%s.mp3' % (pack, idx))); stats['audio'] += 1
        elif f.endswith('.png'):
            shutil.copy(p, os.path.join(out, 'sprites', 'p%s_%s_raw.png' % (pack, idx))); stats['sprites'] += 1
        elif b[:2] == b'\xdf\x05' and len(b) > 8:
            sp = asprite.ASprite(b)
            mods = [sp.decode_module(i) for i in range(sp.nmod)]
            pos, H = shelf(mods)
            if H > 0:
                atlas = Image.new('RGBA', (AW, max(H, 1)))
                for im, ps in zip(mods, pos):
                    if im: atlas.alpha_composite(im, ps)
                atlas.save(os.path.join(out, 'sprites', 'p%s_%s.png' % (pack, idx)))
            meta = dict(
                atlas='p%s_%s.png' % (pack, idx) if H > 0 else None, flags=sp.flags, palFormat=sp.fmt,
                modules=[dict(w=sp.mw[i], h=sp.mh[i], type=sp.mtype[i], extra=sp.mextra[i],
                              x=pos[i][0] if pos[i] else -1, y=pos[i][1] if pos[i] else -1) for i in range(sp.nmod)],
                frameModules=[dict(m=a[0], x=a[1], y=a[2], tr=a[3]) for a in sp.fm],
                frames=[dict(n=f_[0], first=f_[1], bounds=f_[2] if len(f_) > 2 else None) for f_ in sp.frames],
                animFrames=[dict(frame=a[0], dur=a[1], x=a[2], y=a[3], tr=a[4]) for a in sp.af],
                anims=[dict(n=a[0], first=a[1]) for a in sp.anims])
            json.dump(meta, open(os.path.join(out, 'sprites', 'p%s_%s.json' % (pack, idx)), 'w'), separators=(',', ':'))
            stats['sprites'] += 1
        elif pack == 'EN':
            n = struct.unpack_from('<H', b, 1)[0]
            offs = struct.unpack_from('<%dI' % n, b, 5); base = 5 + 4*n
            strs = [b[base+offs[i]: base+(offs[i+1] if i+1 < n else len(b)-base)].decode('utf-8', 'replace') for i in range(n)]
            json.dump(dict(section=b[0], strings=strs), open(os.path.join(out, 'strings', 'EN_%s.json' % idx), 'w'), ensure_ascii=False)
            stats['strings'] += 1
        elif len(b):
            d = os.path.join(out, 'raw', pack); os.makedirs(d, exist_ok=True)
            shutil.copy(p, os.path.join(d, idx + '.bin')); stats['raw'] += 1
print(stats)
