import os
d=os.path.dirname(os.path.abspath(__file__))
r=lambda n: open(os.path.join(d,n),encoding='utf-8').read()
t=r('page.template.html').replace('/*ORBS_JS*/',r('orbs.js')).replace('/*SYNTH_JS*/',r('synth.js'))
os.makedirs(os.path.join(d,'out'),exist_ok=True)
open(os.path.join(d,'out','muzykalka-lab2.html'),'w',encoding='utf-8').write(t)
print(len(t))
