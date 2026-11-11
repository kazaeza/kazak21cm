const S = require('./synth.js');
const SR = S.SR;
function fft(re, im) { const n = re.length; for (let i = 1, j = 0; i < n; i++) { let bit = n >> 1; for (; j & bit; bit >>= 1) j ^= bit; j ^= bit; if (i < j) { [re[i], re[j]] = [re[j], re[i]]; [im[i], im[j]] = [im[j], im[i]]; } }
  for (let len = 2; len <= n; len <<= 1) { const ang = -2 * Math.PI / len; for (let i = 0; i < n; i += len) for (let k = 0; k < len / 2; k++) { const c = Math.cos(ang * k), s = Math.sin(ang * k); const a = i + k, b = a + len / 2; const xr = re[b] * c - im[b] * s, xi = re[b] * s + im[b] * c; re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi; } } }
function spectrum(x, from, n) { const re = new Float64Array(n), im = new Float64Array(n); for (let i = 0; i < n; i++) re[i] = (x[from + i] || 0) * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / n)); fft(re, im); const m = new Float64Array(n / 2); for (let i = 0; i < n / 2; i++) m[i] = Math.hypot(re[i], im[i]); return m; }
function centroid(m) { let a = 0, b = 0; for (let i = 1; i < m.length; i++) { const p = m[i] * m[i]; a += p * i * SR / (2 * m.length); b += p; } return b ? a / b : 0; }
function hf(m, hz) { let a = 0, b = 0; for (let i = 1; i < m.length; i++) { const p = m[i] * m[i]; b += p; if (i * SR / (2 * m.length) > hz) a += p; } return b ? a / b : 0; }
// A-weighting (power) for a rough loudness
function aw(f) { const f2 = f * f; const r = 12194 ** 2 * f2 * f2 / ((f2 + 20.6 ** 2) * Math.sqrt((f2 + 107.7 ** 2) * (f2 + 737.9 ** 2)) * (f2 + 12194 ** 2)); return r * 1.2589; }
function loud(m) { let a = 0; for (let i = 1; i < m.length; i++) { const f = i * SR / (2 * m.length); a += (m[i] * aw(f)) ** 2; } return 10 * Math.log10(a + 1e-20); }
const rms = (x, a, b) => { let s = 0; for (let i = a; i < b; i++) s += x[i] * x[i]; return Math.sqrt(s / (b - a)); };
const db = v => (20 * Math.log10(v + 1e-12)).toFixed(1).padStart(6);
console.log('timbre    hz    peak  rms0-.3 rms.6-1  jump   cent50ms cent400ms  hf>9k    loudA(0-.34s) t-40dB');
for (const key of Object.keys(S.NOTES)) for (const hz of [220, 440, 880, 1318.5]) {
  const x = S.NOTES[key].render(hz, 1.0, 0.8, 12345 + hz);
  let peak = 0, jump = 0; for (let i = 0; i < x.length; i++) { peak = Math.max(peak, Math.abs(x[i])); if (i > 0 && i < 480) jump = Math.max(jump, Math.abs(x[i] - x[i - 1])); }
  const m0 = spectrum(x, 0, 4096), m1 = spectrum(x, Math.round(0.4 * SR), 4096), mL = spectrum(x, 0, 16384);
  let end = x.length - 1; while (end > 0 && Math.abs(x[end]) < peak * 0.01) end--;
  console.log(key.padEnd(8), String(hz).padStart(6), peak.toFixed(3), db(rms(x, 0, 14400)), db(rms(x, 28800, 48000)), jump.toFixed(3),
    centroid(m0).toFixed(0).padStart(7), centroid(m1).toFixed(0).padStart(8), (100 * hf(mL, 9000)).toFixed(3).padStart(8) + '%', loud(mL).toFixed(1).padStart(8), (end / SR).toFixed(2).padStart(8));
}
console.log('\ntap       peak   len(-40dB) centroid  jump');
for (const key of Object.keys(S.TAPS)) for (const low of [false, true]) {
  const x = S.tap(key, 777, low); let peak = 0, jump = 0; for (let i = 0; i < x.length; i++) { peak = Math.max(peak, Math.abs(x[i])); if (i > 0) jump = Math.max(jump, Math.abs(x[i] - x[i - 1])); }
  let end = x.length - 1; while (end > 0 && Math.abs(x[end]) < peak * 0.01) end--;
  console.log((key + (low ? '-low' : '')).padEnd(10), peak.toFixed(3), (1000 * end / SR).toFixed(0).padStart(6) + ' ms', centroid(spectrum(x, 0, 4096)).toFixed(0).padStart(8), jump.toFixed(3));
}
const ir = S.roomImpulse(); let e = 0; for (const v of ir) e += v * v; console.log('\nroom IR energy', e.toFixed(3), 'peak', Math.max(...ir.map(Math.abs)).toFixed(3));
