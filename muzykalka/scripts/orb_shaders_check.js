// O-38: compiles and links every shader of ui/.../orb/OrbShaders.kt in a browser's WebGL 2 (GLSL ES 3.00 is the same
// language as on the phone), runs each program once, and checks what the three bodies put on a transparent
// background (premultiplied light: the middle of the ball is the app's ink colour, the corner is clear).
//   node scripts/orb_shaders_check.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const file = path.join(__dirname, '..', 'ui/src/main/kotlin/com/kazaeza/muzykalka/ui/orb/OrbShaders.kt');
const src = fs.readFileSync(file, 'utf8');
const consts = {};
for (const m of src.matchAll(/const val (\w+) = """([\s\S]*?)"""/g)) consts[m[1]] = m[2];
for (const m of src.matchAll(/const val (\w+) = "(?!")((?:[^"\\\n]|\\.)*)"(?!")/g)) consts[m[1]] = JSON.parse('"' + m[2] + '"');
for (const k of ['PREFIX', 'VS_FULL', 'VS_LINE', 'FS_LINE', 'FS_KEEP', 'FS_OVER', 'FS_BALL', 'VS_SAND', 'FS_SAND', 'FS_PLATE', 'VS_SIM', 'FS_SIM_HEAD',
  'INK_ADVECT', 'INK_SPLAT', 'INK_BURST', 'INK_CURL', 'INK_VORT', 'INK_DIV', 'INK_PRESSURE', 'INK_GRAD', 'INK_SHOW']) {
  if (consts[k] === undefined) throw new Error('missing ' + k);
}
if (src.includes('$')) throw new Error('a dollar sign in OrbShaders.kt');

const programs = {
  ball: ['VS_FULL', 'FS_BALL', ['aPos']],
  keep: ['VS_FULL', 'FS_KEEP', ['aPos']],
  over: ['VS_FULL', 'FS_OVER', ['aPos']],
  line: ['VS_LINE', 'FS_LINE', ['aPos', 'aNorm', 'aInfo']],
  plate: ['VS_FULL', 'FS_PLATE', ['aPos']],
  sand: ['VS_SAND', 'FS_SAND', ['aPos', 'aHeat']],
};
for (const k of ['ADVECT', 'SPLAT', 'BURST', 'CURL', 'VORT', 'DIV', 'PRESSURE', 'GRAD', 'SHOW']) {
  programs['ink_' + k.toLowerCase()] = ['VS_SIM', ['FS_SIM_HEAD', 'INK_' + k], ['aPos']];
}

(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  const result = await page.evaluate(({ consts, programs }) => {
    const out = { errors: [], pixels: {}, uniforms: {} };
    const c = document.createElement('canvas');
    c.width = c.height = 64;
    const gl = c.getContext('webgl2', { alpha: true, premultipliedAlpha: true, antialias: false, preserveDrawingBuffer: true });
    if (!gl) { out.errors.push('no webgl2'); return out; }
    const build = (name, vs, fs, attribs) => {
      const sh = (type, text) => {
        const s = gl.createShader(type);
        gl.shaderSource(s, consts.PREFIX + text);
        gl.compileShader(s);
        if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) out.errors.push(name + ': ' + gl.getShaderInfoLog(s));
        return s;
      };
      const p = gl.createProgram();
      gl.attachShader(p, sh(gl.VERTEX_SHADER, vs));
      gl.attachShader(p, sh(gl.FRAGMENT_SHADER, fs));
      attribs.forEach((a, i) => gl.bindAttribLocation(p, i, a));
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) out.errors.push(name + ' link: ' + gl.getProgramInfoLog(p));
      const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
      out.uniforms[name] = [];
      for (let i = 0; i < n; i++) out.uniforms[name].push(gl.getActiveUniform(p, i).name);
      return p;
    };
    const built = {};
    for (const [name, [v, f, a]] of Object.entries(programs)) {
      const fsText = Array.isArray(f) ? f.map((k) => consts[k]).join('') : consts[f];
      built[name] = build(name, consts[v], fsText, a);
    }
    if (out.errors.length) return out;
    // one big triangle
    const vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
    const vbo = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    const u = (p, n) => gl.getUniformLocation(p, n);
    const paint = (name, set) => {
      gl.viewport(0, 0, 64, 64);
      gl.disable(gl.BLEND);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.useProgram(built[name]);
      set(built[name]);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      const px = new Uint8Array(4);
      const at = (x, y) => { gl.readPixels(x, y, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px); return Array.from(px); };
      out.pixels[name] = { centre: at(32, 32), corner: at(0, 0), rim: at(32 + 26, 32) };
      const e = gl.getError();
      if (e) out.errors.push(name + ' GL error ' + e);
    };
    const common = (p) => { gl.uniform3f(u(p, 'tint'), 0.91, 0.69, 0.1); gl.uniform1f(u(p, 'energy'), 0.3); gl.uniform1f(u(p, 'wall'), 0.42); gl.uniform2f(u(p, 'res'), 64, 64); };
    paint('ball', common);
    paint('plate', common);
    // the ink's picture of an empty fluid: a black dye texture
    const tex = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 4, 4, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(64));
    gl.generateMipmap(gl.TEXTURE_2D);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    paint('ink_show', (p) => { gl.uniform1i(u(p, 'uDye'), 0); gl.uniform1f(u(p, 'wall'), 0.42); gl.uniform1f(u(p, 'energy'), 0.3); gl.uniform2f(u(p, 'res'), 64, 64); gl.uniform2f(u(p, 'texel'), 1 / 64, 1 / 64); });
    return out;
  }, { consts, programs });
  await browser.close();
  console.log(JSON.stringify(result.pixels));
  console.log('uniforms', JSON.stringify(result.uniforms));
  if (result.errors.length) { console.error(result.errors.join('\n')); process.exit(1); }
  const ball = result.pixels.ball;
  const near = (a, b, t) => a.every((v, i) => Math.abs(v - b[i]) <= t);
  if (ball.corner[3] > 4) throw new Error('the corner of the ball is not clear: ' + ball.corner);
  if (ball.centre[3] < 250) throw new Error('the middle of the ball is not opaque: ' + ball.centre);
  if (!near(ball.centre.slice(0, 3), [10 + 4, 9 + 4, 9 + 6], 14)) throw new Error('the middle of the ball is not the ink colour: ' + ball.centre);
  console.log('shaders: ok');
})();
