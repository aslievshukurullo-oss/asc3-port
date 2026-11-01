#!/usr/bin/env python3
"""Gameloft pack extractor for GloftASC3 (see k.a / k.ak / k.aj in the decompiled source).
Pack 'N': u16 total, u16 nvol, u16 first_index[nvol], then u32 offsets[n+1] for volume 0.
Volumes 'N.1'.. start directly with u32 offsets[n+1]. Offsets are absolute in each volume file.
Entry = 1 flag byte (>=127 -> LZMA-alone stream follows, type = flag-127) + payload."""
import sys, os, struct, lzma, json

def sniff(b):
    if b[:8]==b'\x89PNG\r\n\x1a\n': return 'png'
    if b[:4]==b'MThd': return 'mid'
    if b[:4]==b'RIFF': return 'wav'
    if b[:3]==b'GIF': return 'gif'
    return 'bin'

def unlzma(b):
    # header: props(1) dict(4) size(8) ; python needs FORMAT_ALONE
    return lzma.LZMADecompressor(format=lzma.FORMAT_ALONE).decompress(b)

def unpack(root, name, out):
    path=os.path.join(root,name)
    d=open(path,'rb').read()
    total,nvol=struct.unpack_from('<HH',d,0)
    first=list(struct.unpack_from('<%dH'%nvol,d,4))
    res=[]
    for v in range(nvol):
        vd = d if v==0 else open('%s.%d'%(path,v),'rb').read()
        n = (total if v==nvol-1 else first[v+1]) - first[v]
        pos = 4+2*nvol if v==0 else 0
        offs = struct.unpack_from('<%dI'%(n+1),vd,pos)
        for j in range(n):
            idx=first[v]+j
            raw=vd[offs[j]:offs[j+1]]
            if not raw: res.append((idx,None,0,b'')); continue
            flag=raw[0]; comp=flag>=127; typ=flag-127 if comp else flag
            body=raw[1:]
            if comp:
                try: body=unlzma(body)
                except Exception as e:
                    body=b'LZMAERR'+repr(e).encode()
            res.append((idx,typ,comp,body))
    od=os.path.join(out,name); os.makedirs(od,exist_ok=True)
    info=[]
    for idx,typ,comp,body in res:
        ext=sniff(body)
        fn='%03d_t%s.%s'%(idx,typ,ext)
        open(os.path.join(od,fn),'wb').write(body)
        info.append(dict(i=idx,type=typ,lzma=bool(comp),size=len(body),kind=ext))
    json.dump(info,open(os.path.join(od,'_index.json'),'w'))
    return info

if __name__=='__main__':
    root,out=sys.argv[1],sys.argv[2]
    for name in sys.argv[3:]:
        info=unpack(root,name,out)
        from collections import Counter
        print(name,len(info),Counter((x['type'],x['kind']) for x in info))
