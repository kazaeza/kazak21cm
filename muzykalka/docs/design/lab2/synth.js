// O-39: the reference of the new sound. Three instruments, each one voice for the drone AND for the note over it,
// both in the same space: stereo, a real reverb, movement inside the tone. Plain sample-by-sample JavaScript so
// that it can be measured in Node (analyze.js) and carried to Kotlin line by line.
//   drone(patch, f0, o) -> { L, R, seconds }  an exact loop: every frequency and every LFO fits the loop a whole
//                                             number of times, so there is no seam and no crossfade
//   note(patch, f, dur, o, seed) -> { L, R }  one note with its tail
(function (root) {
  'use strict';
  const SR = 48000, TAU = 2 * Math.PI;
  function rng(seed) { let s = (seed >>> 0) || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
  const cents = c => Math.pow(2, c / 1200);
  /** High notes are quieter: -3 dB per octave above A4 (a phone speaker adds its own brightness). */
  const registerGain = f => (f > 440 ? Math.pow(440 / f, 0.5) : 1);
  const fit = (f, T) => (T ? Math.max(1, Math.round(f * T)) / T : f);
  const polyblep = (t, dt) => (t < dt ? ((t /= dt), t + t - t * t - 1) : t > 1 - dt ? ((t = (t - 1) / dt), t * t + t + t + 1) : 0);

  /** State-variable filter (TPT): lp() or bp() per sample; set() may be called as often as the cutoff moves. */
  function svf() {
    let ic1 = 0, ic2 = 0, a1 = 0, a2 = 0, a3 = 0, k = 1, v1 = 0, v2 = 0;
    const run = x => { const v3 = x - ic2; v1 = a1 * ic1 + a2 * v3; v2 = ic2 + a2 * ic1 + a3 * v3; ic1 = 2 * v1 - ic1; ic2 = 2 * v2 - ic2; };
    return {
      set(fc, q) { const g = Math.tan(Math.PI * Math.min(Math.max(fc, 20), SR * 0.45) / SR); k = 1 / q; a1 = 1 / (1 + g * (g + k)); a2 = g * a1; a3 = g * a2; },
      lp(x) { run(x); return v2; },
      bp(x) { run(x); return v1 * k; },
    };
  }

  // ------------------------------------------------------------------ the space: 8 delay lines that feed each other
  /** Wet signal only. Two of the lines breathe (their length moves a few samples), which keeps the tail from ringing. */
  function reverbWet(xL, xR, t60, dampHz, T) {
    const n = xL.length, wL = new Float32Array(n), wR = new Float32Array(n);
    const lens = [1423, 1597, 1789, 1993, 2251, 2503, 2797, 3089], PAD = 64;
    const buf = lens.map(l => new Float32Array(l + PAD)), w = new Int32Array(8), lp = new Float64Array(8), s = new Float64Array(8);
    const g = lens.map(l => Math.pow(10, -3 * l / (t60 * SR))), a = Math.exp(-TAU * dampHz / SR);
    const mk = ls => ls.map(l => ({ b: new Float32Array(l), i: 0 })), apL = mk([241, 373, 491, 617]), apR = mk([263, 359, 521, 601]);
    const pre = Math.round(0.018 * SR), pbL = new Float32Array(pre), pbR = new Float32Array(pre); let pi = 0;
    const inc1 = TAU * fit(0.31, T) / SR, inc2 = TAU * fit(0.47, T) / SR;
    for (let i = 0; i < n; i++) {
      let l = pbL[pi], r = pbR[pi]; pbL[pi] = xL[i]; pbR[pi] = xR[i]; if (++pi === pre) pi = 0;
      for (const d of apL) { const y = d.b[d.i] - 0.62 * l; d.b[d.i] = l + 0.62 * y; if (++d.i === d.b.length) d.i = 0; l = y; }
      for (const d of apR) { const y = d.b[d.i] - 0.62 * r; d.b[d.i] = r + 0.62 * y; if (++d.i === d.b.length) d.i = 0; r = y; }
      let sum = 0;
      for (let k = 0; k < 8; k++) {
        const size = lens[k] + PAD; let o;
        if (k === 2 || k === 5) {
          const pos = w[k] - lens[k] - (12 + 9 * Math.sin(k === 2 ? inc1 * i : inc2 * i + 1.7)) + size * 2, i0 = Math.floor(pos), fr = pos - i0;
          o = buf[k][i0 % size] * (1 - fr) + buf[k][(i0 + 1) % size] * fr;
        } else o = buf[k][(w[k] - lens[k] + size) % size];
        lp[k] = o + a * (lp[k] - o); s[k] = lp[k] * g[k]; sum += s[k];
      }
      sum *= 0.25;
      for (let k = 0; k < 8; k++) { buf[k][w[k]] = s[k] - sum + (k % 2 ? r : l) * (k % 4 < 2 ? 1 : -1); if (++w[k] === lens[k] + PAD) w[k] = 0; }
      wL[i] = (s[0] - s[2] + s[4] - s[6]) * 0.5; wR[i] = (s[1] - s[3] + s[5] - s[7]) * 0.5;
    }
    return [wL, wR];
  }
  function addSpace(L, R, send, t60, dampHz, T) {
    const n = L.length, a = new Float32Array(n), b = new Float32Array(n);
    for (let i = 0; i < n; i++) { a[i] = L[i] * send; b[i] = R[i] * send; }
    const [wL, wR] = reverbWet(a, b, t60, dampHz, T);
    for (let i = 0; i < n; i++) { L[i] += wL[i]; R[i] += wR[i]; }
  }

  /** The chorus of an old string machine: a short delay whose length swings one way on the left, the other way on the right. */
  function chorus(L, R, o, T) {
    const n = L.length, size = 4096, bL = new Float32Array(size), bR = new Float32Array(size);
    const r1 = fit(o.rate, T) / SR, r2 = TAU * fit(o.rate2 || 5.9, T) / SR, norm = 1 / (1 + 0.6 * o.mix); let ph = 0.25;
    for (let i = 0; i < n; i++) {
      const tri = 4 * Math.abs(ph - 0.5) - 1, fast = (o.depth2 || 0) * Math.sin(r2 * i); ph += r1; if (ph >= 1) ph -= 1;
      const w = i % size; bL[w] = L[i]; bR[w] = R[i];
      const read = (b, ms) => { const pos = i - ms * SR / 1000 + size * 4, i0 = Math.floor(pos), fr = pos - i0; return b[i0 % size] * (1 - fr) + b[(i0 + 1) % size] * fr; };
      const wl = read(bL, o.base + o.depth * tri + fast), wr = read(bR, o.base - o.depth * tri - fast);
      L[i] = (L[i] + o.mix * wl) * norm; R[i] = (R[i] + o.mix * wr) * norm;
    }
  }

  // ------------------------------------------------------------------ PADsynth: a tone as wide as a choir, for free
  function ifft(re, im) {
    const n = re.length;
    for (let i = 1, j = 0; i < n; i++) { let bit = n >> 1; for (; j & bit; bit >>= 1) j ^= bit; j ^= bit; if (i < j) { let t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; } }
    for (let len = 2; len <= n; len <<= 1) {
      const ang = TAU / len, wr = Math.cos(ang), wi = Math.sin(ang);
      for (let i = 0; i < n; i += len) {
        let cr = 1, ci = 0;
        for (let j = 0; j < len / 2; j++) {
          const a = i + j, b = a + len / 2, xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
          re[b] = re[a] - xr; im[b] = im[a] - xi; re[a] += xr; im[a] += xi;
          const t = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = t;
        }
      }
    }
  }
  /** Every harmonic is not a line but a narrow band of noise (wider for higher ones): summed, it is an ensemble. */
  function padTable(f, bits, amp, bwCents, seed) {
    const N = 1 << bits, re = new Float64Array(N), im = new Float64Array(N), rnd = rng(seed);
    const fq = Math.round(f * N / SR) * SR / N; // the table holds a whole number of periods
    for (let h = 1; fq * h < Math.min(9000, SR * 0.4); h++) {
      const A = amp(h, fq * h); if (A < 1e-4) continue;
      const bw = (cents(bwCents) - 1) * fq * Math.pow(h, 0.92), sigma = bw / SR * N, c = fq * h / SR * N;
      for (let i = Math.max(1, Math.floor(c - 4 * sigma)); i <= c + 4 * sigma && i < N / 2; i++) { const x = (i - c) / sigma; re[i] += A * Math.exp(-x * x) / sigma; }
    }
    for (let i = 1; i < N / 2; i++) { const m = re[i], p = TAU * rnd(); re[i] = m * Math.cos(p); im[i] = m * Math.sin(p); re[N - i] = re[i]; im[N - i] = -im[i]; }
    ifft(re, im);
    let e = 0; for (let i = 0; i < N; i++) e += re[i] * re[i]; const k = 1 / Math.sqrt(e / N);
    const out = new Float32Array(N); for (let i = 0; i < N; i++) out[i] = re[i] * k;
    return out;
  }
  const glassAmp = warm => (h, F) => Math.pow(h, -1.2 + 0.3 * warm) * (1 + 1.5 * Math.exp(-Math.pow((F - 650) / 380, 2)) + 0.8 * Math.exp(-Math.pow((F - 1300) / 520, 2)) + 0.4 * Math.exp(-Math.pow((F - 2700) / 700, 2))) / Math.sqrt(1 + Math.pow(F / (3000 * Math.pow(2, warm)), 4));

  const DEF = { warm: 0, space: 0.5, fifth: false };
  const opt = o => Object.assign({}, DEF, o || {});
  const spaceOf = (o, send, t60) => [send * (0.35 + 1.3 * o.space), t60 * (0.65 + 0.7 * o.space)];
  function level(L, R, from, to, rms) { let e = 0; for (let i = from; i < to; i++) e += L[i] * L[i] + R[i] * R[i]; const k = rms / Math.sqrt(e / (2 * (to - from)) + 1e-12); return k; }
  function cut(L, R, from, to, k) { const a = new Float32Array(to - from), b = new Float32Array(to - from); for (let i = from; i < to; i++) { a[i - from] = L[i] * k; b[i - from] = R[i] * k; } return { L: a, R: b, seconds: (to - from) / SR }; }

  // ================================================================== the drones
  const DRONE_RMS = 0.09;
  const drones = {
    /** «Аналог»: seven saws a note, spread across the stereo field, through a low-pass that slowly opens and closes. */
    analog(f0, o) {
      const T = 12, P = 5, n = (T + P) * SR, L = new Float32Array(n), R = new Float32Array(n), rnd = rng(7);
      const det = [-1, -0.62, -0.24, 0, 0.21, 0.6, 1];
      for (const [f, lvl] of [[f0, 1], [2 * f0, 0.5]]) for (let v = 0; v < 7; v++) {
        const inc = fit(f * cents(det[v] * 11), T) / SR, pan = 0.5 + (v % 2 ? 1 : -1) * (v === 3 ? 0 : 0.1 + 0.36 * Math.abs(det[v]));
        const gl = Math.cos(pan * Math.PI / 2) * lvl * (v === 3 ? 1 : 0.72), gr = Math.sin(pan * Math.PI / 2) * lvl * (v === 3 ? 1 : 0.72); let ph = rnd();
        for (let i = 0; i < n; i++) { const s = 2 * ph - 1 - polyblep(ph, inc); L[i] += s * gl; R[i] += s * gr; ph += inc; if (ph >= 1) ph -= 1; }
      }
      const base = Math.min(1800, Math.max(700, f0 * 9)) * Math.pow(2, 0.9 * o.warm);
      [L, R].forEach((x, ch) => {
        const f1 = svf(), f2 = svf();
        for (let i = 0; i < n; i++) {
          if (i % 32 === 0) { const t = i / SR, fc = base * Math.pow(2, 0.38 * Math.sin(TAU * t / T + ch * 0.9) + 0.17 * Math.sin(TAU * 2 * t / T + 2 + ch * 1.3)); f1.set(fc, 0.6); f2.set(fc, 1.25); }
          x[i] = Math.tanh(f2.lp(f1.lp(x[i])) * 0.35) * (1 + 0.08 * Math.sin(TAU * 3 * i / SR / T + 1 + ch));
        }
      });
      chorus(L, R, { base: 5.2, depth: 2.4, rate: 0.42, mix: 0.8, rate2: 5.9, depth2: 0.1 }, T);
      const [send, t60] = spaceOf(o, 0.30, 3.2); addSpace(L, R, send, t60, 4500, T);
      return cut(L, R, P * SR, n, level(L, R, P * SR, n, DRONE_RMS));
    },
    /** «Стекло»: a choir of glass. Each note is a PADsynth table read at two places (left, right); the two notes breathe apart. */
    glass(f0, o) {
      const bits = 17, N = 1 << bits, T = 4 * N / SR, P = 6, n = Math.round((T + P) * SR), L = new Float32Array(n), R = new Float32Array(n);
      [[f0, 1, 11], [2 * f0, 0.6, 23]].forEach(([f, lvl, seed], j) => {
        const tab = padTable(f, bits, glassAmp(o.warm), 26, seed), sub = TAU * (Math.round(f * N / SR) * SR / N) / SR;
        for (let i = 0; i < n; i++) {
          const t = i / SR, br = lvl * (1 + (j ? 0.3 : 0.14) * Math.sin(TAU * (j ? 2 : 1) * t / T + j * 2.2)), a = tab[i % N], b = tab[(i + N / 2) % N], s = 0.22 * Math.sin(sub * i) * (j ? 0 : 1);
          L[i] += br * (a + 0.3 * b + s); R[i] += br * (b + 0.3 * a + s);
        }
      });
      const [send, t60] = spaceOf(o, 0.45, 4.5); addSpace(L, R, send, t60, 5200, T);
      return cut(L, R, n - Math.round(T * SR), n, level(L, R, n - Math.round(T * SR), n, DRONE_RMS));
    },
    /**
     * «Танпура»: four strings plucked in turn, each ringing for seconds. The curved bridge (jivari) keeps feeding the
     * high harmonics: a band of them sweeps down through the tone after every pluck. Here: a saw (what such a string
     * gives) through a low-pass for the body and through a band-pass that slides from the 24th harmonic to the 4th.
     */
    tanpura(f0, o) {
      const STEP = 1.35, T = 8 * STEP, P = 10, n = Math.round((T + P) * SR), L = new Float32Array(n), R = new Float32Array(n);
      const strings = [o.fifth ? 1.5 * f0 : 2 * f0, 2 * f0 * 1.0009, 2 * f0 * 0.9994, f0], pans = [0.38, 0.6, 0.45, 0.52], rnd = rng(31), vary = [];
      for (let k = 0; k < 8; k++) vary.push([0.88 + 0.24 * rnd(), (rnd() - 0.5) * 0.04]);
      const RING = 9;
      for (let p = -Math.ceil(RING / STEP); p * STEP < T; p++) {
        const k = ((p % 8) + 8) % 8, f = strings[k % 4], [amp, late] = vary[k], start = Math.round((P + p * STEP + late) * SR), len = Math.round(RING * SR);
        const body = svf(), buzz = svf(); body.set(2.2 * f, 0.7); let ph = 0;
        const gl = Math.cos(pans[k % 4] * Math.PI / 2) * amp, gr = Math.sin(pans[k % 4] * Math.PI / 2) * amp, bright = Math.pow(2, 0.8 * o.warm);
        let inc = f / SR, gB = 0, gZ = 0, tB = 0, tZ = 0;
        for (let i = 0; i < len; i++) {
          const at = start + i; if (at >= n) break;
          if (i % 32 === 0) { // the slow things are worked out 1500 times a second and glide in between
            const t = i / SR, centre = Math.min(5500 / f, (3.5 + 16 * Math.exp(-t / 0.85)) * bright), fade = t > RING - 0.5 ? (RING - t) / 0.5 : 1;
            buzz.set(f * centre, 3.2); inc = f * (1 + 0.0035 * Math.exp(-t / 0.18)) / SR;
            tB = (1 - Math.exp(-t / 0.003)) * Math.exp(-t / 3.2) * fade; tZ = Math.pow(centre, 0.7) * (1 - Math.exp(-t / 0.12)) * Math.exp(-t / 2.4) * fade;
          }
          gB += (tB - gB) * 0.12; gZ += (tZ - gZ) * 0.12;
          const saw = 2 * ph - 1 - polyblep(ph, inc); ph += inc; if (ph >= 1) ph -= 1;
          const y = body.lp(saw) * gB + buzz.bp(saw) * gZ;
          if (at >= 0) { L[at] += y * gl; R[at] += y * gr; }
        }
      }
      const [send, t60] = spaceOf(o, 0.32, 2.6); addSpace(L, R, send, t60, 5000, T);
      return cut(L, R, n - Math.round(T * SR), n, level(L, R, n - Math.round(T * SR), n, DRONE_RMS));
    },
    /** As in 0.0.59, for comparison: one fixed wavetable, two oscillators, mono, dry. */
    old(f0) {
      const T = 12, n = T * SR, L = new Float32Array(n), R = new Float32Array(n), H = [1, 0.5, 0.36, 0.2, 0.16, 0.08, 0.06, 0.035, 0.025, 0.015];
      for (const f of [f0, 2 * f0]) for (const d of [1.0012, 0.9988]) { const w = TAU * fit(f * d, T) / SR; for (let i = 0; i < n; i++) { let y = 0; for (let h = 0; h < 10; h++) y += H[h] * Math.sin((h + 1) * w * i); L[i] += y; R[i] += y; } }
      return cut(L, R, 0, n, level(L, R, 0, n, DRONE_RMS));
    },
  };

  // ================================================================== the notes
  const TAIL = { analog: 3.0, glass: 4.2, tanpura: 2.6, old: 0.3 };
  const dry = {
    /** «Аналог»: two saws a few cents apart and a sine under them, through a filter that opens with the note and closes. */
    analog(f, dur, o, seed, n) {
      const L = new Float32Array(n), R = new Float32Array(n), rnd = rng(seed), f1 = svf(), f2 = svf();
      const ph = [rnd(), rnd(), 0], det = [cents(-5 - rnd()), cents(5 + rnd()), 1], warm = Math.pow(2, 0.9 * o.warm) * (0.96 + 0.08 * rnd()); let vib = 0;
      for (let i = 0; i < n; i++) {
        const t = i / SR, rel = t > dur ? Math.exp(-(t - dur) / 0.09) : 1; if (rel < 1e-4) break;
        const vd = Math.min(1, Math.max(0, (t - 0.45) / 0.5)) * 7; vib += TAU * 5.1 / SR; const bend = cents(vd * Math.sin(vib));
        let x = 0, side = 0;
        for (let k = 0; k < 2; k++) { const inc = f * det[k] * bend / SR, sw = 2 * ph[k] - 1 - polyblep(ph[k], inc); x += sw; side += k ? -sw : sw; ph[k] += inc; if (ph[k] >= 1) ph[k] -= 1; }
        ph[2] += f * bend / SR; x = x * 0.5 + 0.5 * Math.sin(TAU * ph[2]); side = Math.max(-1, Math.min(1, side * 0.35));
        if (i % 16 === 0) { const ef = (1 - Math.exp(-t / 0.012)) * (0.3 + 0.7 * Math.exp(-t / 0.22)), fc = Math.min(7000, f * (1.5 + 5.5 * ef) * warm); f1.set(fc, 0.6); f2.set(fc, 1.3); }
        const amp = (1 - Math.exp(-t / 0.006)) * (0.78 + 0.22 * Math.exp(-t / 0.4)) * rel, y = Math.tanh(f2.lp(f1.lp(x)) * 0.9) * amp;
        L[i] = y * (1 + 0.5 * side); R[i] = y * (1 - 0.5 * side);
      }
      return [L, R, ...spaceOf(o, 0.28, 3.2), 4500];
    },
    /** «Стекло»: a small bell for the start (so that the pitch is there at once) and a breath of the glass choir after it. */
    glass(f, dur, o, seed, n) {
      const L = new Float32Array(n), R = new Float32Array(n), bits = 16, N = 1 << bits;
      const tab = padTable(f, bits, (h, F) => Math.pow(h, -1.9 + 0.3 * o.warm) / Math.sqrt(1 + Math.pow(F / (3600 * Math.pow(2, o.warm)), 4)), 9, seed);
      const fq = Math.round(f * N / SR) * SR / N, w = TAU * fq / SR, ratio = Math.max(2, Math.min(14, Math.round(6500 / f))), index = 1.1 * Math.sqrt(Math.min(1, 600 / f));
      for (let i = 0; i < n; i++) {
        const t = i / SR, rel = t > dur ? Math.exp(-(t - dur) / 0.14) : 1, relPad = t > dur ? Math.exp(-(t - dur) / 0.28) : 1; if (relPad < 1e-4) break;
        const on = 1 - Math.exp(-t / 0.002), tine = Math.sin(w * i + index * Math.exp(-t / 0.045) * Math.sin(ratio * w * i)) * Math.exp(-t / 0.7) * 0.5;
        const bar = Math.sin(w * i + 0.6 * Math.exp(-t / 0.35) * Math.sin(w * i)) * (0.4 + 0.6 * Math.exp(-t / 1.1)) * 0.8, pad = (1 - Math.exp(-t / 0.06)) * 0.22 * relPad;
        const m = (tine + bar) * on * rel;
        L[i] = m + pad * (tab[i % N] + 0.3 * tab[(i + N / 2) % N]); R[i] = m + pad * (tab[(i + N / 2) % N] + 0.3 * tab[i % N]);
      }
      return [L, R, ...spaceOf(o, 0.40, 4.5), 5200];
    },
    /** «Танпура» goes with a bamboo flute: it slides up into the note, breathes, and starts to sway only when held. */
    tanpura(f, dur, o, seed, n) {
      const L = new Float32Array(n), R = new Float32Array(n), rnd = rng(seed), air = svf(); air.set(2.2 * f, 0.9);
      let ph = 0, vib = rnd() * TAU, drift = 0, bn = 0; const bright = Math.pow(2, 0.5 * o.warm);
      for (let i = 0; i < n; i++) {
        const t = i / SR, rel = t > dur ? Math.exp(-(t - dur) / 0.06) : 1; if (rel < 1e-4) break;
        drift += ((rnd() - 0.5) * 2 - drift) * 0.0004; bn += ((rnd() - 0.5) * 2 - bn) * 0.002;
        const sway = Math.min(1, Math.max(0, (t - 0.35) / 0.4)); vib += TAU * 4.7 / SR;
        const c = -30 * Math.exp(-t / 0.035) + 9 * sway * Math.sin(vib) + 4 * drift; ph += f * cents(c) / SR;
        const env = (1 - Math.exp(-t / 0.03)) * (0.85 + 0.15 * (1 - Math.exp(-t / 0.4))) * (1 + 0.06 * sway * Math.sin(vib)) * rel;
        const b = Math.min(1.3, (0.55 + 0.45 * env + 0.25 * bn) * bright), p = TAU * ph;
        const tone = Math.sin(p) + 0.42 * b * Math.sin(2 * p) + 0.16 * b * b * Math.sin(3 * p) + 0.07 * b * b * b * Math.sin(4 * p) + 0.03 * b * b * b * b * Math.sin(5 * p);
        const y = (tone * 0.6 + air.bp((rnd() - 0.5) * 2) * (0.07 + 0.3 * Math.exp(-t / 0.05))) * env;
        L[i] = y; R[i] = y;
      }
      return [L, R, ...spaceOf(o, 0.34, 2.6), 5000];
    },
    /** «Как раньше» (the CLASSIC tone): three fixed harmonics, a clockwork vibrato, dry. */
    old(f, dur, o, seed, n) {
      const L = new Float32Array(n), R = new Float32Array(n); let ph = 0;
      for (let i = 0; i < n; i++) {
        const t = i / SR, env = Math.min(1, t / 0.03) * (t > dur ? Math.max(0, 1 - (t - dur) / 0.08) : 1);
        ph += f * (1 + 0.004 * Math.sin(TAU * 5.2 * t)) / SR; const p = TAU * ph, y = (Math.sin(p) + 0.22 * Math.sin(2 * p) + 0.08 * Math.sin(3 * p)) * env * 0.7;
        L[i] = y; R[i] = y;
      }
      return [L, R, 0, 1, 4000];
    },
  };
  const NOTE_RMS = 0.16, norm = {};
  function rawNote(patch, f, dur, o, seed) {
    const n = Math.round((dur + TAIL[patch]) * SR), [L, R, send, t60, damp] = dry[patch](f, dur, o, seed, n);
    if (send > 0) addSpace(L, R, send, t60, damp);
    return [L, R, n];
  }
  /** One loudness for a patch: its A4 is brought to NOTE_RMS over the first 0.4 s; the register curve does the rest. */
  function note(patch, f, dur, o, seed) {
    o = opt(o); const key = patch + '|' + o.warm + '|' + o.space;
    if (!norm[key]) { const [a, b] = rawNote(patch, 440, 1, o, 1); norm[key] = level(a, b, 0, Math.round(0.4 * SR), NOTE_RMS); }
    const [L, R, n] = rawNote(patch, f, dur, o, seed || 1), k = norm[key] * registerGain(f), fade = Math.round(0.03 * SR);
    for (let i = 0; i < n; i++) { const w = i > n - fade ? (n - i) / fade : 1; L[i] *= k * w; R[i] *= k * w; }
    return { L, R, seconds: n / SR };
  }
  const drone = (patch, f0, o) => drones[patch](f0, opt(o));

  root.Synth2 = {
    SR, drone, note, reverbWet, registerGain,
    PATCHES: [
      { id: 'tanpura', name: 'Танпура', about: 'Четыре струны по кругу, у каждой после щипка сверху вниз проходит полоса обертонов. Нота — бамбуковая флейта: подъезжает, дышит, качается только когда тянется.' },
      { id: 'analog', name: 'Аналог', about: 'Струнная машина: по семь пил на ноту, разведённых по стерео, фильтр медленно открывается и закрывается, хорус. Нота — мягкий аналоговый голос с фильтром, который раскрывается на атаке.' },
      { id: 'glass', name: 'Стекло', about: 'Стеклянный хор: каждый обертон — не линия, а узкая полоса, поэтому звук широкий и живой сам по себе. Нота — колокольчик в начале и выдох того же хора после.' },
      { id: 'old', name: 'Как в 0.0.59', about: 'Для сравнения: одна неподвижная волна, моно, без пространства; нота — три гармоники с ровным вибрато.' },
    ],
  };
})(typeof window !== 'undefined' ? window : globalThis);
