// Film strips: node frames.js out.png  — rows of frames for old and new shaders at given moments.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const dir = __dirname;
const spec = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await b.newPage({ viewport: { width: spec.width || 1400, height: 800 }, deviceScaleFactor: 1 });
  page.on('console', m => console.log('page:', m.text()));
  page.on('pageerror', e => console.log('ERR', e.message));
  const html = `<body style="margin:0;background:#0a0a0c;color:#aaa;font:12px sans-serif"><div id=g style="display:flex;flex-wrap:wrap;gap:6px;padding:8px"></div>
  <script>${fs.readFileSync(path.join(dir, 'orb.js'), 'utf8')}</script>
  <script>
  const SH = ${JSON.stringify({ old: fs.readFileSync(path.join(dir, 'old-0.0.53.agsl'), 'utf8'), drop: fs.readFileSync(path.join(dir, spec.shader || 'drop.agsl'), 'utf8') })};
  const spec = ${JSON.stringify(spec)};
  const hex = h => [1,3,5].map(i => parseInt(h.slice(i,i+2),16)/255);
  const g = document.getElementById('g');
  for (const f of spec.frames) {
    const box = document.createElement('div'); box.style.cssText='text-align:center';
    const c = document.createElement('canvas'); c.width = c.height = spec.size || 260;
    box.appendChild(c); const cap = document.createElement('div'); cap.textContent = f.label; box.appendChild(cap); g.appendChild(box);
    const key = f.shader;
    if (!window.pool) window.pool = {};
    if (!pool[key]) { const gc = document.createElement('canvas'); gc.width = gc.height = c.width; pool[key] = { gc, orb: OrbLab.makeOrb(gc, SH[key]) }; }
    const orb = pool[key].orb;
    const colors = (f.colors || ['#3d6bff','#8b5cf6','#ff3d9a','#22d3ee']).map(hex);
    const plucks = f.plucks || [];
    const u = { iTime: f.time ?? 12, uRadius: 0.62 * c.width / 2, uEnergy: f.energy ?? 0.5, uDim: 1, uSeed: 0,
      uC0: colors[0], uC1: colors[1], uC2: colors[2], uC3: colors[3], uTouch: f.touch || [0,0], uLobes: f.lobes || 0, uLock: f.lock || 0 };
    if (f.shader === 'old') {
      const age = [99,99,99,99], amp = [0,0,0,0];
      plucks.slice(-4).forEach((p,i) => { age[i] = p.age; amp[i] = p.strength; });
      u.uPulseAge = age; u.uPulseAmp = amp; u.uTension = f.tension || 0;
    } else {
      const d = OrbLab.drive(plucks);
      u.uMode = d.modes; u.uPulseAge = d.age; u.uPulseAmp = d.amp; u.uKick = d.kick; u.uSpin = f.spin || 0;
      // the flow runs ahead after a pluck: the integral of the surge so far
      let ahead = 0; for (const p of plucks) { for (let s = 0; s < p.age; s += 0.01) ahead += 0.01 * (spec.surge ?? 3.0) * p.strength * OrbLab.kickOf(s); }
      u.iTime += ahead;
    }
    orb.draw(u);
    const x = c.getContext('2d'); x.fillStyle = '#0a0a0c'; x.fillRect(0,0,c.width,c.height); x.drawImage(pool[key].gc, 0, 0);
  }
  </script></body>`;
  await page.setContent(html);
  await page.waitForTimeout(300);
  const h = await page.evaluate(() => document.body.scrollHeight);
  await page.setViewportSize({ width: spec.width || 1400, height: h });
  await page.screenshot({ path: process.argv[3] });
  await b.close();
})();
