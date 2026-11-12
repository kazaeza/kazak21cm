// Film strips of the three bodies without a phone: node frames.js <ink|thread|sand> out.png [semitone]
// One body is run through a script (rest, pluck, answer) and photographed at the listed moments.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const kind = process.argv[2] || 'ink', out = process.argv[3] || 'strip.png', semi = +(process.argv[4] || 7);
const size = +(process.env.SIZE || 300);
// time, seconds: what happens
const script = [[2.0, 'pluck'], [4.2, 'answer'], [9.0, 'clear']];
const shots = (process.env.SHOTS || '1.9,2.15,2.5,3.2,4.4,4.9,5.6,7.0,8.9,10.5').split(',').map(Number);
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await b.newPage({ viewport: { width: (size + 6) * 5 + 16, height: (size + 22) * 2 + 16 }, deviceScaleFactor: 1 });
  page.on('console', m => console.log('page:', m.text()));
  page.on('pageerror', e => console.log('ERR', e.message));
  await page.setContent(`<body style="margin:0;background:#0a0909;color:#aaa;font:12px sans-serif"><div id=g style="display:flex;flex-wrap:wrap;gap:6px;padding:8px"></div>
  <script>${fs.readFileSync(path.join(__dirname, 'orbs.js'), 'utf8')}</script>`);
  await page.evaluate(({ kind, semi, size, script, shots }) => {
    const gc = document.createElement('canvas'); gc.width = gc.height = size;
    const orb = OrbLab2.makers[kind](gc);
    if (!orb) throw new Error('no WebGL2');
    const g = document.getElementById('g'); let t = 0, si = 0; const dt = 1 / 60;
    for (const at of shots) {
      while (t < at) {
        if (si < script.length && t >= script[si][0]) { const e = script[si++][1]; if (e === 'pluck') orb.pluck(1); if (e === 'answer') { orb.pluck(0.8); orb.answer(semi); } if (e === 'clear') orb.clear(); }
        orb.step(dt); t += dt;
      }
      const box = document.createElement('div'); box.style.textAlign = 'center';
      const c = document.createElement('canvas'); c.width = c.height = size; c.getContext('2d').drawImage(gc, 0, 0);
      const cap = document.createElement('div'); cap.textContent = at.toFixed(2) + ' c'; box.append(c, cap); g.append(box);
    }
  }, { kind, semi, size, script, shots });
  await page.screenshot({ path: out, timeout: 900000 });
  await b.close();
})();
