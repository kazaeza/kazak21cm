import sys, os
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
LS='/usr/share/fonts/truetype/liberation/LiberationSans-'; LF='/usr/share/fonts/truetype/liberation/LiberationSerif-'
LM='/usr/share/fonts/truetype/liberation/LiberationMono-'; IN='/usr/share/fonts/opentype/inter/Inter-'
import glob
P=lambda s: glob.glob(f'/usr/share/fonts/**/P052-{s}.otf',recursive=True)[0]
M=[
 ('Arial','Regular',LS+'Regular.ttf'),('Arial','Bold',LS+'Bold.ttf'),('Arial','Italic',LS+'Italic.ttf'),('Arial','Black',LS+'Bold.ttf'),
 ('Times New Roman','Regular',LF+'Regular.ttf'),('Times New Roman','Bold',LF+'Bold.ttf'),('Times New Roman','Italic',LF+'Italic.ttf'),
 ('Courier New','Regular',LM+'Regular.ttf'),('Courier New','Bold',LM+'Bold.ttf'),
 ('Georgia','Regular',P('Roman')),('Georgia','Bold',P('Bold')),
 ('Book Antiqua','Regular',P('Roman')),('Book Antiqua','Bold',P('Bold')),('Book Antiqua','Italic',P('Italic')),
 ('Cochin LT [Rus by me]','Bold',P('Bold')),
 ('HelveticaNeueCyr','Roman',LS+'Regular.ttf'),('HelveticaNeueCyr','Medium',LS+'Regular.ttf'),('HelveticaNeueCyr','Bold',LS+'Bold.ttf'),
 ('SF Pro Text','Regular',IN+'Regular.otf'),('SF Pro Text','Medium',IN+'Medium.otf'),('SF Pro Text','Semibold',IN+'SemiBold.otf'),
 ('SF Pro Display','Light',IN+'Light.otf'),('SF Pro Display','Medium',IN+'Medium.otf'),('SF Pro Display','Bold',IN+'Bold.otf'),
 ('Bebas Neue Cyrillic','Regular','OSWALD'),
]
for fam,sty,src in M:
    if src=='OSWALD':
        f=instancer.instantiateVariableFont(TTFont(sys.argv[2]),{'wght':500})
    else:
        f=TTFont(src)
    n=f['name']
    for rec in list(n.names):
        if rec.nameID in (16,17,21,22,25): n.removeNames(nameID=rec.nameID)
    ps=(fam+'-'+sty).replace(' ','').replace('[','').replace(']','')
    for pid,eid,lid in ((3,1,0x409),(1,0,0)):
        n.setName(fam,1,pid,eid,lid); n.setName(sty,2,pid,eid,lid)
        n.setName(f'{fam} {sty}',4,pid,eid,lid); n.setName(ps,6,pid,eid,lid)
    ext='.otf' if f.sfntVersion=='OTTO' else '.ttf'
    f.save(os.path.join(OUT,ps+ext)); print('made',fam,sty,'<-',os.path.basename(src))
