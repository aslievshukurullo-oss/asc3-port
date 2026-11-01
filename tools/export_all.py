#!/usr/bin/env python3
import os, sys, json, traceback
sys.path.insert(0, os.path.dirname(__file__))
import asprite
ex, out = sys.argv[1], sys.argv[2]
rep = []
for pack in sorted(os.listdir(ex), key=lambda x: int(x) if x.isdigit() else 999):
    pd = os.path.join(ex, pack)
    for f in sorted(os.listdir(pd)):
        if not f.endswith('.bin'): continue
        b = open(os.path.join(pd, f), 'rb').read()
        if b[:2] != b'\xdf\x05' or len(b) < 8: continue
        o = os.path.join(out, 'sprites', pack, f.split('_')[0])
        try:
            sp, nm, nf = asprite.dump(os.path.join(pd, f), o)
            rep.append(dict(pack=pack, idx=f.split('_')[0], modules=sp.nmod, decoded=nm, frames=len(sp.frames), rendered=nf, anims=len(sp.anims), fmt=sp.fmt, ok=True))
        except Exception as e:
            rep.append(dict(pack=pack, idx=f.split('_')[0], ok=False, err=repr(e)))
json.dump(rep, open(os.path.join(out, 'sprites_report.json'), 'w'), indent=1)
ok = [r for r in rep if r['ok']]
print('sprites:', len(rep), 'ok:', len(ok), 'failed:', len(rep)-len(ok))
print('modules decoded %d/%d, frames rendered %d/%d' % (sum(r['decoded'] for r in ok), sum(r['modules'] for r in ok), sum(r['rendered'] for r in ok), sum(r['frames'] for r in ok)))
for r in rep:
    if not r['ok'] or r['decoded'] < r['modules'] or r['rendered'] < r['frames']: print(' partial/fail', r)
