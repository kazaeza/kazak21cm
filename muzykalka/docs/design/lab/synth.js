// O-36 lab: reference synthesis of the note timbres and the wooden taps.
// Pure functions: (parameters) -> Float32Array at SR. The Kotlin port in core:dsp follows these tables and formulas.
(function (root) {
  const SR = 48000;
  const TAU = 2 * Math.PI;

  // xorshift32, as in core:dsp SynthVoice.nextNoise: -1..1
  function rng(seed) {
    let x = (seed | 0) || 0x2545F491;
    return () => { x ^= x << 13; x ^= x >>> 17; x ^= x << 5; return (x | 0) / 2147483648; };
  }
  /** Seconds to fall by 60 dB -> the factor in e^(-t*k). */
  const k60 = t60 => 6.9078 / t60;
  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  /** High notes are quieter: a small speaker and the ear are both keenest up there. 1 at and below A4, -3 dB per octave above. */
  function registerGain(hz) { return hz <= 440 ? 1 : Math.pow(440 / hz, 0.5); }

  /**
   * A bank of damped sines — a struck bar, a tine, a string. modes: [{f, a, t60, t60b?, mix?, det?}].
   * With t60b the mode has two stages (a pair of strings): `mix` of it falls fast (t60), the rest slowly (t60b)
   * and sits `det` (a fraction of f) higher, so the pair beats slowly.
   * `rise` (s) is the contact time of the hammer: the sound swells in, it does not click.
   */
  function addModes(out, at, modes, gain, rise, releaseAt, releaseT60) {
    const n = out.length;
    for (const m of modes) {
      if (m.f >= SR * 0.42 || m.a === 0) continue;
      const w = TAU * m.f / SR, d = k60(m.t60) / SR;
      const two = m.t60b !== undefined;
      const w2 = two ? TAU * m.f * (1 + (m.det || 0)) / SR : 0, d2 = two ? k60(m.t60b) / SR : 0;
      const mix = two ? (m.mix === undefined ? 0.6 : m.mix) : 1;
      for (let i = at; i < n; i++) {
        const j = i - at;
        let y = mix * Math.exp(-d * j) * Math.sin(w * j);
        if (two) y += (1 - mix) * Math.exp(-d2 * j) * Math.sin(w2 * j);
        let e = m.a * gain;
        if (rise > 0) e *= 1 - Math.exp(-j / (rise * SR));
        if (releaseAt >= 0 && i > releaseAt) e *= Math.exp(-k60(releaseT60) * (i - releaseAt) / SR);
        out[i] += e * y;
        if (j > 2400 && Math.abs(e) * (mix * Math.exp(-d * j) + (1 - mix) * Math.exp(-d2 * j)) < 2e-5) break;
      }
    }
  }

  /** A burst of noise through a one-pole low-pass (fc) and, if hp, a one-pole high-pass: the thump of felt, the tick of a nail. */
  function addNoise(out, at, seconds, fc, level, rand, hp) {
    const n = Math.min(out.length, at + Math.round(seconds * 6 * SR));
    const a = 1 - Math.exp(-TAU * fc / SR);
    let lp = 0, prev = 0;
    for (let i = at; i < n; i++) {
      const t = (i - at) / SR;
      lp += a * (rand() - lp);
      let y = lp;
      if (hp) { y = lp - prev; prev = lp; }
      // rises in a tenth of its length, then dies
      out[i] += level * y * (1 - Math.exp(-t / (seconds * 0.1))) * Math.exp(-t / seconds);
    }
  }

  // ---------------------------------------------------------------------------------------------------------
  // Notes. Every timbre: (hz, seconds held, velocity 0..1, seed) -> samples (held time + its own tail).
  // ---------------------------------------------------------------------------------------------------------

  /** What sounds now (core:dsp tone()): three static harmonics and a clockwork vibrato. Kept to compare. */
  function tone(hz, held, vel) {
    const n = Math.round((held + 0.6) * SR), out = new Float32Array(n), rel = Math.round(held * SR);
    let phase = 0;
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      let env = Math.min(1, t / 0.03) * (0.75 + 0.25 * Math.exp(-t / 0.4)) * Math.exp(-t / 6);
      if (i >= rel) env *= Math.exp(-(i - rel) / SR / 0.09);
      const vib = 0.0035 * clamp((t - 0.35) / 0.4, 0, 1);
      phase += hz * (1 + vib * Math.sin(TAU * 5.2 * t)) / SR; if (phase >= 1) phase -= 1;
      const p = TAU * phase;
      out[i] = (Math.sin(p) + 0.22 * Math.sin(2 * p) + 0.08 * Math.sin(3 * p)) * env * vel * 0.75;
    }
    return out;
  }

  /** The e-piano the app plays chords with (core:dsp ePiano()). Kept to compare. */
  function epiano(hz, held, vel) {
    const n = Math.round((held + 0.6) * SR), out = new Float32Array(n), rel = Math.round(held * SR);
    let phase = 0, mod = 0, tine = 0;
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      let env = Math.min(1, t / 0.003) * Math.exp(-t / 1.6);
      if (i >= rel) env *= Math.exp(-(i - rel) / SR / 0.12);
      const index = 1.8 * vel * Math.exp(-t / 0.5) + 0.25;
      mod += hz / SR; phase += hz / SR; if (mod >= 1) mod -= 1; if (phase >= 1) phase -= 1;
      let y = Math.sin(TAU * phase + index * Math.sin(TAU * mod));
      if (hz * 7 < SR * 0.45) { tine += hz * 7 / SR; if (tine >= 1) tine -= 1; y += 0.12 * Math.exp(-t / 0.05) * Math.sin(TAU * tine); }
      out[i] = y * env * vel * 0.8;
    }
    return out;
  }

  /**
   * Marimba: a rosewood bar over a tube. The bar is tuned so its overtones are 4 and 10 times the note; they die
   * in a blink, the note itself rings on. A yarn mallet: soft contact, so the higher the overtone the less of it.
   * And the wood itself answers every stroke with the same short knock, whatever the note — that is what says «wood».
   */
  function marimba(hz, held, vel, seed) {
    const rand = rng(seed);
    const t1 = clamp(2.6 * Math.pow(220 / hz, 0.45), 0.7, 3.0);
    const n = Math.round((Math.min(held, t1) + 0.9) * SR), out = new Float32Array(n);
    const soft = f => 1 / (1 + Math.pow(f / (1400 + 1600 * vel), 2));   // the mallet's contact
    const vary = () => 1 + 0.08 * rand();
    const g = 0.80 * vel * registerGain(hz);
    addModes(out, 0, [
      { f: hz, a: 1.0, t60: t1 },
      { f: hz * 4.0, a: 0.42 * soft(hz * 4) / soft(hz) * vary(), t60: t1 * 0.22 },
      { f: hz * 9.9, a: 0.20 * soft(hz * 9.9) / soft(hz) * vary(), t60: t1 * 0.08 },
    ], g, 0.0012, Math.round(held * SR), 0.35);
    // the knock of the wood: fixed, not tuned to the note
    addModes(out, 0, [
      { f: 1130 * (1 + 0.012 * rand()), a: 0.10 * vary(), t60: 0.045 },
      { f: 2710 * (1 + 0.012 * rand()), a: 0.05 * vary(), t60: 0.022 },
    ], vel * registerGain(hz), 0.0004, -1, 1);
    addNoise(out, 0, 0.004, 1800, 0.10 * vel * registerGain(hz), rand, false);
    return out;
  }

  /**
   * Kalimba: a steel tine on a box. Its overtones are far from the note (6.27 and 17.55 times) and gone at once —
   * a «tink» of the nail, then nearly one pure tone that sings long, with a breath of the box's octave.
   */
  function kalimba(hz, held, vel, seed) {
    const rand = rng(seed);
    const t1 = clamp(2.3 * Math.pow(220 / hz, 0.4), 0.6, 2.8);
    const n = Math.round((Math.min(held, t1) + 1.0) * SR), out = new Float32Array(n);
    const vary = () => 1 + 0.08 * rand();
    const g = 0.9 * vel * registerGain(hz);
    addModes(out, 0, [
      { f: hz, a: 1.0, t60: t1 },
      { f: hz * 2.0, a: 0.10, t60: t1 * 0.55 },
      { f: hz * 6.27, a: 0.16 * vary() * Math.min(1, 1500 / hz), t60: 0.11 },
      { f: hz * 17.55, a: 0.06 * vary() * Math.min(1, 700 / hz), t60: 0.035 },
    ], g, 0.0008, Math.round(held * SR), 0.45);
    addNoise(out, 0, 0.002, 6000, 0.07 * vel * registerGain(hz), rand, true);
    return out;
  }

  /**
   * Felt piano: a string struck through felt. Partials a little sharp of whole multiples (stiff wire), the hammer
   * an eighth along the string, the felt eating the top. Every partial is a pair of strings: most of it falls fast,
   * the rest hangs and beats slowly against it. Under it the thump of the hammer. A damper ends it.
   */
  function felt(hz, held, vel, seed) {
    const rand = rng(seed);
    const tBase = clamp(5.5 * Math.pow(110 / hz, 0.6), 0.9, 6.0);
    const n = Math.round((held + 0.5) * SR), out = new Float32Array(n);
    const B = 0.0004 * Math.pow(hz / 261.6, 1.1);
    const fc = 800 + 1700 * vel;                       // how much top the felt lets through
    const modes = [];
    let norm = 0;
    for (let k = 1; k <= 14; k++) {
      const f = k * hz * Math.sqrt(1 + B * k * k);
      if (f > 6500) break;
      const a = Math.abs(Math.sin(k * Math.PI / 8)) / k / Math.pow(1 + Math.pow(f / fc, 2), 0.75) * (1 + 0.06 * rand());
      if (k === 1) norm = a;
      const t = tBase / (1 + 0.35 * (k - 1));
      modes.push({ f, a: a / norm, t60: t * 0.38, t60b: t * 1.5, mix: 0.62, det: 0.0004 });
    }
    const fg = vel * (hz <= 440 ? 1 : Math.pow(440 / hz, 0.25));   // the felt already takes the top off: less cut up high
    addModes(out, 0, modes, 0.42 * fg, 0.004, Math.round(held * SR), 0.14);
    addNoise(out, 0, 0.022, 420, 0.30 * fg, rand, false);                          // felt on string
    addNoise(out, 0, 0.003, 2600, 0.025 * fg, rand, true);                         // the action
    return out;
  }

  /**
   * A hummed voice: «у–о» with the mouth nearly closed. The source falls off with the number of the harmonic, the
   * mouth is two fixed resonances — fixed in hertz, so each note is coloured differently, as a voice is. It scoops
   * into the note, drifts a few cents, breathes, and only a long note gets a vibrato.
   */
  function hum(hz, held, vel, seed) {
    const rand = rng(seed);
    const n = Math.round((held + 0.5) * SR), out = new Float32Array(n), rel = Math.round(held * SR);
    const F = [[400, 110, 1.0], [860, 130, 0.55], [2600, 220, 0.10]];
    const mouth = f => 0.06 + F.reduce((s, [c, bw, g]) => s + g / (1 + Math.pow((f - c) / bw, 2)), 0);
    const parts = [];
    let norm = 0;
    for (let k = 1; k * hz < 5200 && k <= 24; k++) { const a = Math.pow(k, -1.6) * mouth(k * hz); parts.push(a); norm += a; }
    // slow wander of pitch and loudness: low-passed noise, a few hertz wide
    let drift = 0, shimmer = 0, vibRate = 5.3, phase = 0, br1 = 0, br2 = 0;
    const lpD = 1 - Math.exp(-TAU * 2.2 / SR), lpS = 1 - Math.exp(-TAU * 3.0 / SR);
    const g = 0.85 * vel * registerGain(hz) / norm;
    for (let i = 0; i < n; i++) {
      const t = i / SR;
      drift += lpD * (rand() * 360 - drift);            // cents; about +-2.5 on average
      shimmer += lpS * (rand() * 120 - shimmer);          // about +-1 on average
      const scoop = -18 * Math.exp(-t / 0.045);          // cents: it comes up into the note
      const vibDepth = 14 * clamp((t - 0.45) / 0.6, 0, 1);
      vibRate += 0.0008 * rand();
      const cents = scoop + drift + vibDepth * Math.sin(TAU * vibRate * t);
      phase += hz * Math.pow(2, cents / 1200) / SR; if (phase >= 1) phase -= 1;
      let env = 1 / (1 + Math.exp(-(t - 0.045) / 0.011));   // opens in ~70 ms
      if (i >= rel) env *= Math.exp(-(i - rel) / SR / 0.11);
      const open = 1 / (1 + Math.exp(-(t - 0.07) / 0.02));  // the upper harmonics open a little later
      let y = 0;
      for (let k = 0; k < parts.length; k++) y += parts[k] * (k === 0 ? 1 : open) * Math.sin(TAU * phase * (k + 1));
      // breath: noise through the mouth's two resonances
      const x = rand();
      br1 += 0.05 * (x - br1); br2 += 0.11 * (x - br2);
      const breath = (br2 - br1) * (0.2 + 0.3 * Math.exp(-t / 0.08));
      out[i] = (y * (1 + 0.04 * shimmer) + breath * norm) * env * g;
    }
    return out;
  }

  const NOTES = {
    tone: { name: 'Сейчас', render: tone },
    marimba: { name: 'Маримба', render: marimba },
    kalimba: { name: 'Калимба', render: kalimba },
    felt: { name: 'Фетр', render: felt },
    hum: { name: 'Голос', render: hum },
    epiano: { name: 'Пиано', render: epiano },
  };

  // ---------------------------------------------------------------------------------------------------------
  // Taps: wood on wood. kind -> {modes, noise}; every tap differs a hair in pitch and level, so a run of them
  // is not a machine gun. low = true: the second voice (back, cancel) — the same stick, a little lower and softer.
  // ---------------------------------------------------------------------------------------------------------
  const TAPS = {
    sticks: { name: 'Палочки', level: 0.20, noise: [0.0015, 7000, 0.35],
      modes: [[1850, 1.0, 0.045], [2720, 0.45, 0.030], [4350, 0.22, 0.018], [6100, 0.10, 0.010]] },
    block: { name: 'Брусок', level: 0.22, noise: [0.002, 4000, 0.25],
      modes: [[920, 1.0, 0.070], [1380, 0.50, 0.050], [2490, 0.25, 0.028], [3900, 0.10, 0.015]] },
    tick: { name: 'Тик', level: 0.20, noise: [0.001, 8000, 0.45],
      modes: [[2400, 1.0, 0.016], [3900, 0.40, 0.010]] },
  };
  function tap(kind, seed, low) {
    const spec = TAPS[kind], rand = rng(seed);
    const out = new Float32Array(Math.round(0.16 * SR));
    const pitch = (low ? 0.8 : 1) * (1 + 0.015 * rand());
    const level = spec.level * (low ? 0.7 : 1) * Math.pow(10, 0.05 * rand());   // +-1 dB
    addModes(out, 0, spec.modes.map(([f, a, t60]) => ({ f: f * pitch, a: a * (1 + 0.1 * rand()), t60 })), level, 0.0003, -1, 1);
    addNoise(out, 0, spec.noise[0], spec.noise[1], level * spec.noise[2], rand, true);
    return out;
  }

  // ---------------------------------------------------------------------------------------------------------
  // The drone as it is (core:dsp DroneBank): harmonic amplitudes of its wavetable.
  // ---------------------------------------------------------------------------------------------------------
  const DRONE_HARMONICS = [1.0, 0.5, 0.36, 0.2, 0.16, 0.08, 0.06, 0.035, 0.025, 0.015];

  // ---------------------------------------------------------------------------------------------------------
  // Air: a small room. Four delay lines fed back through a Householder mix, the top damped in the loop.
  // Returns the room's answer to one click; the page convolves with it, the app runs the same loop live.
  // ---------------------------------------------------------------------------------------------------------
  const ROOM = { delays: [1031, 1327, 1523, 1871], t60: 0.55, damp: 3800, wet: 0.16, pre: 480 };
  function roomImpulse() {
    const n = Math.round(1.0 * SR), out = new Float32Array(n);
    const L = ROOM.delays, buf = L.map(len => new Float32Array(len)), idx = [0, 0, 0, 0], lp = [0, 0, 0, 0];
    const gain = L.map(len => Math.pow(10, -3 * len / (ROOM.t60 * SR)));
    const a = 1 - Math.exp(-TAU * ROOM.damp / SR);
    for (let i = 0; i < n; i++) {
      const x = i === ROOM.pre ? 1 : 0;
      const o = [0, 0, 0, 0];
      for (let k = 0; k < 4; k++) o[k] = buf[k][idx[k]];
      const sum = 0.5 * (o[0] + o[1] + o[2] + o[3]);
      for (let k = 0; k < 4; k++) {
        lp[k] += a * ((o[k] - sum) * gain[k] - lp[k]);       // Householder: o - (2/N) * sum(o), N = 4
        buf[k][idx[k]] = x + lp[k];
        idx[k] = (idx[k] + 1) % L[k];
      }
      out[i] = 0.5 * (o[0] - o[1] + o[2] - o[3]);
    }
    return out;
  }

  const api = { SR, NOTES, TAPS, tap, DRONE_HARMONICS, ROOM, roomImpulse, registerGain };
  if (typeof module !== 'undefined') module.exports = api; else root.SoundLab = api;
})(typeof window !== 'undefined' ? window : globalThis);
