// O-37 lab: the drop's dynamics (to be ported to Kotlin as is) and a WebGL host for the AGSL text.
(function (root) {
  const LIFE = 2.5, TAIL_FROM = 1.2;
  /** A pluck makes the inner flow run ahead: this many extra seconds of flow per second, times the kick. */
  const SURGE = 3.0;

  // --- dynamics -----------------------------------------------------------------------------
  const MODES = [2, 3, 4, 5];
  const W = [0.50, 0.28, 0.15, 0.07];  // how much of a pluck goes into each mode
  const F2 = 0.9;                       // Hz of the slowest mode (l = 2); the others follow a drop: sqrt(l(l-1)(l+2)/8)
  function tail(t) { const s = Math.min(1, Math.max(0, (t - TAIL_FROM) / (LIFE - TAIL_FROM))); return 1 - s * s * (3 - 2 * s); }
  /** Amplitude of mode i (l = i + 2), t seconds after a pluck of strength 1. */
  function dropMode(i, t) {
    if (t <= 0 || t >= LIFE) return 0;
    const l = MODES[i];
    const f = F2 * Math.sqrt(l * (l - 1) * (l + 2) / 8);
    return W[i] * (1 - Math.exp(-t / 0.012)) * Math.exp(-t * (0.9 + 0.5 * (l - 2))) * tail(t) * Math.cos(2 * Math.PI * f * t);
  }
  /** How hard the plucks push now: a soft rise, then a slow fall. */
  function kickOf(t) { return t <= 0 || t >= LIFE ? 0 : (1 - Math.exp(-t / 0.05)) * Math.exp(-t * 2.0) * tail(t); }

  /** plucks: [{age, strength}] -> what the shader needs */
  function drive(plucks) {
    const modes = [0, 0, 0, 0]; let kick = 0;
    const age = [99, 99, 99, 99], amp = [0, 0, 0, 0];
    plucks.slice(-4).forEach((p, i) => { age[i] = p.age; amp[i] = p.strength * tail(p.age); });
    for (const p of plucks) {
      for (let i = 0; i < 4; i++) modes[i] += p.strength * dropMode(i, p.age);
      kick += p.strength * kickOf(p.age);
    }
    return { modes, kick: Math.min(kick, 1.5), age, amp };
  }

  // --- WebGL host -----------------------------------------------------------------------------
  function agslToGlsl(src) {
    return 'precision highp float;\n' + src.replace('vec4 main(vec2 fragCoord)', 'vec4 agslMain(vec2 fragCoord)') +
      '\nvoid main() { gl_FragColor = agslMain(vec2(gl_FragCoord.x, iResolution.y - gl_FragCoord.y)); }\n';
  }
  function makeOrb(canvas, agsl) {
    const gl = canvas.getContext('webgl', { premultipliedAlpha: true, alpha: true });
    const vs = gl.createShader(gl.VERTEX_SHADER);
    gl.shaderSource(vs, 'attribute vec2 a; void main(){ gl_Position = vec4(a, 0.0, 1.0); }'); gl.compileShader(vs);
    const fs = gl.createShader(gl.FRAGMENT_SHADER);
    gl.shaderSource(fs, agslToGlsl(agsl)); gl.compileShader(fs);
    if (!gl.getShaderParameter(fs, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(fs));
    const pr = gl.createProgram(); gl.attachShader(pr, vs); gl.attachShader(pr, fs); gl.linkProgram(pr);
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(pr));
    gl.useProgram(pr);
    const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const loc = gl.getAttribLocation(pr, 'a'); gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
    const U = name => gl.getUniformLocation(pr, name);
    function set(name, v) {
      const l = U(name); if (!l) return;
      if (typeof v === 'number') gl.uniform1f(l, v);
      else if (v.length === 2) gl.uniform2f(l, v[0], v[1]);
      else if (v.length === 3) gl.uniform3f(l, v[0], v[1], v[2]);
      else gl.uniform4f(l, v[0], v[1], v[2], v[3]);
    }
    return {
      draw(u) {
        gl.viewport(0, 0, canvas.width, canvas.height);
        gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
        set('iResolution', [canvas.width, canvas.height]);
        for (const k in u) set(k, u[k]);
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      },
    };
  }
  const api = { LIFE, SURGE, MODES, W, dropMode, kickOf, drive, agslToGlsl, makeOrb };
  if (typeof module !== 'undefined') module.exports = api; else root.OrbLab = api;
})(typeof window !== 'undefined' ? window : globalThis);
