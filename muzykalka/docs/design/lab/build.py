import json,os
d=os.path.dirname(os.path.abspath(__file__))
r=lambda n: open(os.path.join(d,n),encoding='utf-8').read()
t=r('page.template.html')
t=t.replace('/*ORB_JS*/',r('orb.js')).replace('/*SYNTH_JS*/',r('synth.js')).replace('/*AGSL_JSON*/',json.dumps({'old':r('old-0.0.53.agsl'),'drop':r('drop.agsl')}))
os.makedirs(os.path.join(d,'out'),exist_ok=True)
open(os.path.join(d,'out','muzykalka-lab.html'),'w',encoding='utf-8').write(t)
print(len(t))
