// Numbers for the new sound, since nobody in this room can hear it: node analyze.js > analysis.txt
// Drone: level, peak, where the spectrum sits and how much it moves, how wide it is, whether the loop has a seam.
// Note: pitch, loudness across the register, how fast it starts, how long it rings, harshness.
require('./synth.js');
const S = globalThis.Synth2, SR = S.SR;
const db = x => 20 * Math.log10(Math.max(x, 1e-9));
function fftMag(x, from, N) {
  const re = new Float64Array(N), im = new Float64Array(N);
  for (let i = 0; i < N; i++) re[i] = (x[from + i] || 0) * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
  for (let i = 1, j = 0; i < N; i++) { let bit = N >> 1; for (; j & bit; bit >>= 1) j ^= bit; j ^= bit; if (i < j) { const t = re[i]; re[i] = re[j]; re[j] = t; } }
  for (let len = 2; len <= N; len <<= 1) { const ang = -2 * Math.PI / len; for (let i = 0; i < N; i += len) for (let j = 0; j < len / 2; j++) { const c = Math.cos(ang * j), s = Math.sin(ang * j), a = i + j, b = a + len / 2, xr = re[b] * c - im[b] * s, xi = re[b] * s + im[b] * c; re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi; } }
  const m = new Float64Array(N / 2); for (let i = 0; i < N / 2; i++) m[i] = Math.hypot(re[i], im[i]); return m;
}
const aWeight = f => { const f2 = f * f; return (12194 ** 2 * f2 * f2) / ((f2 + 20.6 ** 2) * Math.sqrt((f2 + 107.7 ** 2) * (f2 + 737.9 ** 2)) * (f2 + 12194 ** 2)) * 1.2589; };
function spectrumStats(x, from, N) {
  const m = fftMag(x, from, N); let e = 0, ce = 0, hi = 0, la = 0;
  for (let i = 1; i < N / 2; i++) { const f = i * SR / N, p = m[i] * m[i]; e += p; ce += p * f; if (f > 9000) hi += p; la += p * aWeight(f) ** 2; }
  return { centroid: ce / e, hi: hi / e, loudA: 10 * Math.log10(la / N / N + 1e-20), m };
}
const rms = (x, a, b) => { let e = 0; for (let i = a; i < b; i++) e += x[i] * x[i]; return Math.sqrt(e / (b - a)); };
const peak = x => { let p = 0; for (let i = 0; i < x.length; i++) p = Math.max(p, Math.abs(x[i])); return p; };
function pitchCents(x, f) {
  const N = 32768, m = fftMag(x, Math.round(0.12 * SR), N); let best = 0, bi = 0;
  for (let i = Math.round(f * 0.94 * N / SR); i <= f * 1.06 * N / SR; i++) if (m[i] > best) { best = m[i]; bi = i; }
  const a = Math.log(m[bi - 1]), b = Math.log(m[bi]), c = Math.log(m[bi + 1]), d = 0.5 * (a - c) / (a - 2 * b + c);
  return 1200 * Math.log2((bi + d) * SR / N / f);
}
const out = [];
const t0 = Date.now();
out.push('DRONES (tonic 130.8 Hz + octave), loop is exact by construction');
out.push('patch     seconds  rms dB  peak dB  centroid Hz (min..max over the loop)  L/R corr  seam step  render ms');
for (const p of S.PATCHES) {
  const t = Date.now(), d = S.drone(p.id, 130.81), n = d.L.length; const cs = [];
  for (let k = 0; k + 16384 < n; k += Math.round(0.5 * SR)) cs.push(spectrumStats(d.L, k, 16384).centroid);
  let lr = 0, ll = 0, rr = 0; for (let i = 0; i < n; i++) { lr += d.L[i] * d.R[i]; ll += d.L[i] ** 2; rr += d.R[i] ** 2; }
  let inner = 0; for (let i = 1; i < n; i++) inner = Math.max(inner, Math.abs(d.L[i] - d.L[i - 1]));
  const seam = Math.abs(d.L[0] - d.L[n - 1]);
  out.push(`${p.id.padEnd(9)} ${d.seconds.toFixed(2).padStart(6)}  ${db(rms(d.L, 0, n)).toFixed(1).padStart(6)}  ${db(Math.max(peak(d.L), peak(d.R))).toFixed(1).padStart(6)}   ${Math.min(...cs).toFixed(0).padStart(5)}..${Math.max(...cs).toFixed(0).padEnd(5)}                        ${(lr / Math.sqrt(ll * rr)).toFixed(2).padStart(5)}    ${seam.toFixed(4)} (max inside ${inner.toFixed(4)})  ${Date.now() - t}`);
}
out.push('');
out.push('NOTES (1 s held)');
out.push('patch     f Hz    cents  peak dB  rms0.4 dB  loudA dB  centroid  >9k %  to -6dB ms  rings s  L/R corr');
for (const p of S.PATCHES) {
  const loud = [];
  for (const f of [220, 440, 880, 1318.5]) {
    const x = S.note(p.id, f, 1.0, null, 3), n = x.L.length, st = spectrumStats(x.L, 0, 16384), pk = Math.max(peak(x.L), peak(x.R));
    let on = 0; const win = 96; let env = []; for (let i = 0; i + win < n; i += win) env.push(rms(x.L, i, i + win)); const emax = Math.max(...env);
    on = env.findIndex(v => v > emax * 0.5) * win / SR * 1000; let last = env.length - 1; while (last > 0 && env[last] < emax * 0.001) last--;
    let lr = 0, ll = 0, rr = 0; for (let i = 0; i < n; i++) { lr += x.L[i] * x.R[i]; ll += x.L[i] ** 2; rr += x.R[i] ** 2; }
    loud.push(st.loudA);
    out.push(`${p.id.padEnd(9)} ${f.toFixed(1).padStart(6)}  ${pitchCents(x.L, f).toFixed(1).padStart(5)}  ${db(pk).toFixed(1).padStart(6)}   ${db(rms(x.L, 0, Math.round(0.4 * SR))).toFixed(1).padStart(6)}    ${st.loudA.toFixed(1).padStart(6)}   ${st.centroid.toFixed(0).padStart(6)}   ${(st.hi * 100).toFixed(2).padStart(5)}   ${on.toFixed(0).padStart(6)}     ${(last * win / SR - 1).toFixed(2).padStart(5)}    ${(lr / Math.sqrt(ll * rr)).toFixed(2)}`);
  }
  out.push(`${p.id.padEnd(9)} loudness spread A3..E6: ${(Math.max(...loud) - Math.min(...loud)).toFixed(1)} dB`);
}
out.push('');
out.push('SPACE: impulse response of the reverb (t60 asked 3.2 s)');
{ const n = 5 * SR, a = new Float32Array(n), b = new Float32Array(n); a[0] = 1; b[0] = 1; const [wL, wR] = S.reverbWet(a, b, 3.2, 4500);
  const e0 = rms(wL, Math.round(0.1 * SR), Math.round(0.3 * SR)); let t60 = 0; for (let t = 0.3; t < 4.8; t += 0.05) { if (rms(wL, Math.round(t * SR), Math.round((t + 0.2) * SR)) < e0 * 0.001) { t60 = t; break; } }
  let lr = 0, ll = 0, rr = 0, e = 0; for (let i = 0; i < n; i++) { lr += wL[i] * wR[i]; ll += wL[i] ** 2; rr += wR[i] ** 2; }
  out.push(`energy L ${ll.toFixed(3)} R ${rr.toFixed(3)}  peak ${peak(wL).toFixed(3)}  measured t60 ~ ${t60.toFixed(2)} s (-60 dB from the level at 0.1-0.3 s)  L/R corr ${(lr / Math.sqrt(ll * rr)).toFixed(2)}`); }
out.push(''); out.push(`total ${Date.now() - t0} ms`);
console.log(out.join('\n'));
