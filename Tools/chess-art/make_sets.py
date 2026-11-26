import os, re, subprocess, sys
SRC=os.path.dirname(os.path.abspath(__file__))
OUT=sys.argv[1]
os.makedirs(OUT, exist_ok=True)
PIECES=['wP','wN','wB','wR','wQ','wK','bP','bN','bB','bR','bQ','bK']
# (name in app, source set, recolor)
SETS=[('classic','cburnett',None),('merida','merida',None),('chessnut','chessnut',None),
      ('fantasy','fantasy',None),('spatial','spatial',None),('celtic','celtic',None),
      ('papercut','papercut',None),('candy','rhosgfx',None),('minimal','totoy',None),
      ('neon','cburnett','neon'),('gold','cburnett','gold'),('tint','cburnett','tint')]
GRAD='''<defs><linearGradient id="gold" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stop-color="#FFF6C2"/><stop offset="0.45" stop-color="#F5C542"/><stop offset="1" stop-color="#B9770E"/></linearGradient>
<linearGradient id="steel" x1="0" y1="0" x2="0.4" y2="1"><stop offset="0" stop-color="#5B6477"/><stop offset="0.5" stop-color="#262B36"/><stop offset="1" stop-color="#0B0D12"/></linearGradient></defs>'''
def norm(c):
    c=c.lower()
    return {'white':'#fff','#ffffff':'#fff','black':'#000','#000000':'#000'}.get(c,c)
def recolor(svg, white, mode):
    def sub(attr, fn):
        nonlocal svg
        svg=re.sub(r'(%s=")([^"]+)(")'%attr, lambda m: m.group(1)+fn(norm(m.group(2)))+m.group(3), svg)
        svg=re.sub(r'(%s:)([^;"]+)'%attr, lambda m: m.group(1)+fn(norm(m.group(2))), svg)
    if mode=='neon':
        glow='#00F0FF' if white else '#FF2BD6'
        sub('fill', lambda c: c if c=='none' else ('#0D0B1E' if c in ('#fff','#000') else glow))
        sub('stroke', lambda c: c if c=='none' else glow)
    elif mode=='gold':
        if white:
            sub('fill', lambda c: 'url(#gold)' if c=='#fff' else ('#6B4300' if c=='#000' else c))
            sub('stroke', lambda c: '#6B4300' if c=='#000' else c)
        else:
            sub('fill', lambda c: 'url(#steel)' if c=='#000' else ('#F2C14E' if c in ('#fff','#ececec') else c))
            sub('stroke', lambda c: '#C9A13B' if c=='#000' else ('#F2C14E' if c in ('#fff','#ececec') else c))
        svg=re.sub(r'(<svg[^>]*>)', lambda m: m.group(1)+GRAD, svg, count=1)
    return svg
S=192; INNER=172
for name,src,mode in SETS:
    for p in PIECES:
        srcp=p if mode!='tint' else 'w'+p[1]
        svg=open(os.path.join(SRC,src,srcp+'.svg')).read()
        if mode: svg=recolor(svg, p[0]=='w', mode)
        tmp=os.path.join(OUT,'_t.svg'); open(tmp,'w').write(svg)
        png=os.path.join(OUT,'_t.png')
        subprocess.check_call(['rsvg-convert','-w',str(INNER),'-h',str(INNER),'--keep-aspect-ratio',tmp,'-o',png])
        out=os.path.join(OUT,'chess_%s_%s.webp'%(name,p.lower()))
        if mode=='neon':
            glow='#00F0FF' if p[0]=='w' else '#FF2BD6'
            cmd=['magick',png,'-gravity','center','-background','none','-extent','%dx%d'%(S,S),
                 '(', '+clone','-fill',glow,'-colorize','100','-channel','A','-blur','0x7','-evaluate','multiply','1.6','+channel',')',
                 '+swap','-composite']
        elif mode=='tint':
            cmd=['magick',png,'-gravity','center','-background','none','-extent','%dx%d'%(S,S)]
        else:
            cmd=['magick',png,'-gravity','center','-background','none','-extent','%dx%d'%(S,S),
                 '(', '+clone','-background','black','-shadow','38x4+0+5',')','+swap','-background','none','-layers','merge','+repage',
                 '-gravity','center','-crop','%dx%d+0-3'%(S,S),'+repage','-extent','%dx%d'%(S,S)]
        subprocess.check_call(cmd+['-define','webp:lossless=true','-quality','100',out])
    print(name)
os.remove(os.path.join(OUT,'_t.svg')); os.remove(os.path.join(OUT,'_t.png'))
