// O-38: three different bodies for the tonic sphere. One rule for all: nothing is drawn «on top» — a pluck and an
// answer are forces on the one material the body is made of (a fluid, a thread, sand), and the picture is whatever
// that material does. Each maker returns { step(dt), pluck(strength, x, y), answer(semitones), clear(), pointer(...) }.
(function (root) {
  'use strict';
  const RATIO = [[1, 1], [16, 15], [9, 8], [6, 5], [5, 4], [4, 3], [7, 5], [3, 2], [8, 5], [5, 3], [9, 5], [15, 8]];
  const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) / 255);
  const COLORS = ['#E8B11A', '#FD95BF', '#FDA166', '#D6A0FD', '#1FD1E9', '#1FE044', '#63C7FD', '#88BFFD', '#FD9B98', '#FD87F5', '#1FD7BE', '#B1C91B'].map(hex);
  const GOLD = COLORS[0];
  /** 0 = rest (the tonic), 1 = the most restless (the minor second): the simpler the ratio, the calmer. */
  const tension = s => (s === 0 ? 0 : Math.min(1, Math.log2(RATIO[s][0] * RATIO[s][1]) / Math.log2(240)));
  const ORB = 0.42; // radius of the body, in canvas widths

  // ---------------------------------------------------------------- GL helpers
  function compile(gl, type, src) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s) + '\n' + src.split('\n').map((l, i) => (i + 1) + ': ' + l).join('\n'));
    return s;
  }
  function program(gl, vs, fs) {
    const p = gl.createProgram();
    gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, vs)); gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, fs));
    gl.bindAttribLocation(p, 0, 'aPos'); gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    const u = {}; const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < n; i++) { const name = gl.getActiveUniform(p, i).name.replace('[0]', ''); u[name] = gl.getUniformLocation(p, name); }
    return { p, u };
  }
  function fullscreen(gl) {
    const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
    const b = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, b);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    return vao;
  }
  const HASH = `float hash(vec2 p){ vec3 p3=fract(vec3(p.xyx)*.1031); p3+=dot(p3,p3.yzx+33.33); return fract((p3.x+p3.y)*p3.z); }`;

  // ================================================================ 1. INK: a real fluid in a glass ball
  const VS_SIM = `#version 300 es
  in vec2 aPos; uniform vec2 texel; out vec2 vUv, vL, vR, vT, vB;
  void main(){ vUv=aPos*0.5+0.5; vL=vUv-vec2(texel.x,0.); vR=vUv+vec2(texel.x,0.); vT=vUv+vec2(0.,texel.y); vB=vUv-vec2(0.,texel.y); gl_Position=vec4(aPos,0.,1.); }`;
  const FS_HEAD = `#version 300 es
  precision highp float; precision highp sampler2D; in vec2 vUv, vL, vR, vT, vB; out vec4 o;`;
  const INK = {
    advect: `uniform sampler2D uVel, uSrc; uniform float dt, diss; uniform vec2 vtexel;
      void main(){ vec2 c=vUv-dt*texture(uVel,vUv).xy*vtexel; o=texture(uSrc,c)/(1.0+diss*dt); }`,
    splat: `uniform sampler2D uSrc; uniform vec2 point; uniform vec3 color; uniform float radius;
      void main(){ vec2 p=vUv-point; o=vec4(texture(uSrc,vUv).rgb+color*exp(-dot(p,p)/radius),1.); }`,
    burst: `uniform sampler2D uSrc; uniform vec2 point; uniform float power, r0, w, seed;
      void main(){ vec2 p=vUv-point; float r=length(p); vec2 n=p/max(r,1e-4); float a=atan(p.y,p.x);
        float f=power*exp(-pow((r-r0)/w,2.0))*(1.0+0.5*sin(5.0*a+seed)+0.35*sin(8.0*a-seed*1.7));
        o=vec4(texture(uSrc,vUv).xy+n*f,0.,1.); }`,
    curl: `uniform sampler2D uVel;
      void main(){ float L=texture(uVel,vL).y, R=texture(uVel,vR).y, T=texture(uVel,vT).x, B=texture(uVel,vB).x; o=vec4(0.5*(R-L-T+B),0.,0.,1.); }`,
    vort: `uniform sampler2D uVel, uCurl; uniform float curl, dt;
      void main(){ float L=texture(uCurl,vL).x, R=texture(uCurl,vR).x, T=texture(uCurl,vT).x, B=texture(uCurl,vB).x, C=texture(uCurl,vUv).x;
        vec2 f=0.5*vec2(abs(T)-abs(B),abs(R)-abs(L)); f/=length(f)+1e-4; f*=curl*C; f.y*=-1.0;
        o=vec4(texture(uVel,vUv).xy+f*dt,0.,1.); }`,
    div: `uniform sampler2D uVel;
      void main(){ float L=texture(uVel,vL).x, R=texture(uVel,vR).x, T=texture(uVel,vT).y, B=texture(uVel,vB).y; o=vec4(0.5*(R-L+T-B),0.,0.,1.); }`,
    pressure: `uniform sampler2D uP, uDiv;
      void main(){ float L=texture(uP,vL).x, R=texture(uP,vR).x, T=texture(uP,vT).x, B=texture(uP,vB).x; o=vec4((L+R+B+T-texture(uDiv,vUv).x)*0.25,0.,0.,1.); }`,
    // projection, and the wall of the ball: nothing flows through it, the flow slides along and curls back
    grad: `uniform sampler2D uP, uVel; uniform float wall;
      void main(){ float L=texture(uP,vL).x, R=texture(uP,vR).x, T=texture(uP,vT).x, B=texture(uP,vB).x;
        vec2 v=texture(uVel,vUv).xy-vec2(R-L,T-B); vec2 p=vUv-0.5; float r=length(p); vec2 n=p/max(r,1e-4);
        v-=n*max(dot(v,n),0.)*smoothstep(wall-0.07,wall-0.01,r); v*=1.0-smoothstep(wall-0.012,wall+0.004,r);
        o=vec4(v,0.,1.); }`,
    show: `uniform sampler2D uDye; uniform float wall, time, energy; uniform vec2 res; ${HASH}
      void main(){ vec2 p=(vUv-0.5)/wall; float r=length(p); float z=sqrt(max(0.,1.-r*r));
        vec2 uv=0.5+p*(0.80+0.20*r*r)*wall;
        vec3 dye=texture(uDye,uv).rgb, soft=textureLod(uDye,uv,3.5).rgb, wide=textureLod(uDye,uv,6.0).rgb;
        vec3 avg=textureLod(uDye,vec2(0.5),9.0).rgb*5.0;
        vec3 ink=(dye*1.1+soft*0.6+wide*0.4)*mix(0.5,1.0,z);
        float lum=max(ink.r,max(ink.g,ink.b)); vec3 col=ink*(1.0-exp(-lum*1.9))/max(lum,1e-4); col=mix(col,vec3(1.0),0.35*smoothstep(1.6,4.0,lum));
        float fres=pow(1.0-z,2.4);
        col+=(vec3(0.03,0.035,0.05)+0.6*(1.0-exp(-wide*2.4)))*fres;
        vec2 hl=p-vec2(-0.36,0.46); col+=0.09*exp(-dot(hl,hl)/0.016)+0.03*exp(-dot(hl,hl)/0.2);
        float e=1.5/(wall*res.x); float mask=1.0-smoothstep(1.0-e,1.0+e,r); float d=max(r-1.0,0.);
        vec3 halo=(1.0-exp(-avg*1.3))*(0.5*exp(-d*7.0)+0.2*exp(-d*2.2))*(0.6+0.8*energy);
        vec3 c=vec3(0.039,0.035,0.035)+mix(halo,col,mask);
        c+=(hash(gl_FragCoord.xy+fract(time)*61.0)-0.5)*0.03;
        o=vec4(c,1.); }`,
  };

  function makeInk(canvas) {
    const gl = canvas.getContext('webgl2', { alpha: false, antialias: false });
    if (!gl || !(gl.getExtension('EXT_color_buffer_float') || gl.getExtension('EXT_color_buffer_half_float'))) return null;
    const SIM = 192, DYE = 512;
    const vao = fullscreen(gl);
    const P = {}; for (const k in INK) P[k] = program(gl, VS_SIM, FS_HEAD + INK[k]);
    function target(n, mip) {
      const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, mip ? gl.LINEAR_MIPMAP_LINEAR : gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, n, n, 0, gl.RGBA, gl.HALF_FLOAT, null);
      const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb);
      gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
      gl.viewport(0, 0, n, n); gl.clearColor(0, 0, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT);
      if (mip) gl.generateMipmap(gl.TEXTURE_2D);
      return { t, fb, n };
    }
    const pair = (n, mip) => { let a = target(n, mip), b = target(n, mip); return { get read() { return a; }, get write() { return b; }, swap() { const x = a; a = b; b = x; } }; };
    const vel = pair(SIM), dye = pair(DYE, true), prs = pair(SIM), div = target(SIM), curl = target(SIM);
    let unit = 0;
    function use(pr, n) { gl.useProgram(pr.p); unit = 0; if (pr.u.texel) gl.uniform2f(pr.u.texel, 1 / n, 1 / n); }
    function bind(pr, name, tex) { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, tex.t); gl.uniform1i(pr.u[name], unit++); }
    function draw(t) { gl.bindFramebuffer(gl.FRAMEBUFFER, t ? t.fb : null); if (t) gl.viewport(0, 0, t.n, t.n); else gl.viewport(0, 0, canvas.width, canvas.height); gl.bindVertexArray(vao); gl.drawArrays(gl.TRIANGLES, 0, 3); }
    function splat(buf, x, y, c, radius) {
      use(P.splat, buf.read.n); bind(P.splat, 'uSrc', buf.read);
      gl.uniform2f(P.splat.u.point, x, y); gl.uniform3f(P.splat.u.color, c[0], c[1], c[2]); gl.uniform1f(P.splat.u.radius, radius);
      draw(buf.write); buf.swap();
    }
    let time = 0, energy = 0, semi = -1, held = 0, spin = 0, seed = 1.3; const bursts = [];
    const SMOKE = [GOLD, [1.0, 0.36, 0.22], [0.62, 0.3, 1.0], [0.12, 0.45, 1.0]];
    const at = (r, a) => [0.5 + ORB * r * Math.cos(a), 0.5 + ORB * r * Math.sin(a)];
    function step(dt) {
      dt = Math.min(dt, 1 / 30); time += dt; energy *= Math.exp(-dt * 1.6);
      use(P.advect, SIM); bind(P.advect, 'uVel', vel.read); bind(P.advect, 'uSrc', vel.read);
      gl.uniform1f(P.advect.u.dt, dt); gl.uniform1f(P.advect.u.diss, 0.25); gl.uniform2f(P.advect.u.vtexel, 1 / SIM, 1 / SIM); draw(vel.write); vel.swap();
      // the tonic keeps the ink moving: two slow sources circling the middle
      const calm = semi >= 0 ? 0.25 : 1; // while a degree stands in the ball, the tonic only tints it
      for (let i = 0; i < 4; i++) {
        const sgn = i % 2 ? -1 : 1, a = sgn * (0.17 + 0.05 * i) * time + i * 1.7, [x, y] = at(0.30 + 0.11 * i + 0.07 * Math.sin(0.37 * time + i * 2.0), a);
        splat(vel, x, y, [-Math.sin(a) * 210 * sgn * calm * dt, Math.cos(a) * 210 * sgn * calm * dt, 0], 0.0030);
        const sm = semi >= 0 ? (i === 0 ? GOLD.map(v => v * 0.45) : COLORS[semi].map((v, j) => Math.min(1, v * (0.55 + 0.25 * i) + (i === 3 ? 0.25 * (j === 2 ? 1 : 0) : 0)))) : SMOKE[i];
        splat(dye, x, y, sm.map(v => v * 2.4 * (semi >= 0 ? 0.55 : 1) * dt), 0.0042);
      }
      // a pluck is a push from one point outwards, and a puff of bright ink there
      for (const b of bursts.splice(0)) {
        use(P.burst, SIM); bind(P.burst, 'uSrc', vel.read);
        gl.uniform2f(P.burst.u.point, b.x, b.y); gl.uniform1f(P.burst.u.power, 430 * b.s); gl.uniform1f(P.burst.u.r0, 0.085); gl.uniform1f(P.burst.u.w, 0.06); gl.uniform1f(P.burst.u.seed, seed += 2.1);
        draw(vel.write); vel.swap();
        splat(dye, b.x, b.y, [1.0, 0.86, 0.6].map(v => v * 0.8 * b.s), 0.0060);
      }
      // the answered degree: p jets round the middle; a simple ratio stands still, a rough one creeps and flickers
      if (semi >= 0) {
        held += dt; const [p] = RATIO[semi], te = tension(semi), c = COLORS[semi];
        spin += dt * 0.45 * te; const power = (0.75 + 1.3 * Math.exp(-held / 0.6)) / Math.sqrt(p), size = 0.0032 / (1 + 0.10 * p), swirl = 0.04 + 0.6 * te * te;
        for (let k = 0; k < p; k++) {
          const a = spin + 1.5708 + 6.2832 * k / p, [x, y] = at(0.17, a), flick = 1 + te * 0.5 * Math.sin(time * 9 + k * 2.4);
          const f = 2500 * power * flick * dt, ca = Math.cos(a), sa = Math.sin(a);
          splat(vel, x, y, [(ca - sa * swirl) * f, (sa + ca * swirl) * f, 0], size);
          splat(dye, x, y, c.map(v => v * 6.0 * power * flick * dt), size);
        }
      }
      use(P.curl, SIM); bind(P.curl, 'uVel', vel.read); draw(curl);
      use(P.vort, SIM); bind(P.vort, 'uVel', vel.read); bind(P.vort, 'uCurl', curl); gl.uniform1f(P.vort.u.curl, 11); gl.uniform1f(P.vort.u.dt, dt); draw(vel.write); vel.swap();
      use(P.div, SIM); bind(P.div, 'uVel', vel.read); draw(div);
      for (let i = 0; i < 22; i++) { use(P.pressure, SIM); bind(P.pressure, 'uP', prs.read); bind(P.pressure, 'uDiv', div); draw(prs.write); prs.swap(); }
      use(P.grad, SIM); bind(P.grad, 'uP', prs.read); bind(P.grad, 'uVel', vel.read); gl.uniform1f(P.grad.u.wall, ORB); draw(vel.write); vel.swap();
      use(P.advect, DYE); bind(P.advect, 'uVel', vel.read); bind(P.advect, 'uSrc', dye.read);
      gl.uniform1f(P.advect.u.dt, dt); gl.uniform1f(P.advect.u.diss, 0.16 + (semi >= 0 ? 1.3 * Math.exp(-held / 0.7) : 0)); gl.uniform2f(P.advect.u.vtexel, 1 / SIM, 1 / SIM); draw(dye.write); dye.swap();
      gl.bindTexture(gl.TEXTURE_2D, dye.read.t); gl.generateMipmap(gl.TEXTURE_2D);
      use(P.show, DYE); bind(P.show, 'uDye', dye.read);
      gl.uniform1f(P.show.u.wall, ORB); gl.uniform1f(P.show.u.time, time); gl.uniform1f(P.show.u.energy, energy); gl.uniform2f(P.show.u.res, canvas.width, canvas.height);
      draw(null);
    }
    return {
      step,
      pluck(s = 1, x = 0, y = 0) { bursts.push({ x: 0.5 + ORB * x, y: 0.5 + ORB * y, s }); energy = Math.min(1.5, energy + s); },
      answer(s) { semi = s; held = 0; },
      clear() { semi = -1; },
      pointer(x, y, dx, dy) { splat(vel, 0.5 + ORB * x, 0.5 + ORB * y, [dx * 5200, dy * 5200, 0], 0.0018); splat(dye, 0.5 + ORB * x, 0.5 + ORB * y, [0.5, 0.42, 0.3], 0.001); },
    };
  }

  // ================================================================ 2. THREAD: one string wound into a ball
  const VS_LINE = `#version 300 es
  in vec2 aPos; in vec2 aNorm; in vec3 aInfo; uniform float width; out vec3 vInfo; out float vSide;
  void main(){ vInfo=aInfo; vSide=aNorm.x==0.&&aNorm.y==0. ? 0. : aInfo.z; gl_Position=vec4(aPos+aNorm*width*aInfo.z,0.,1.); }`;
  const FS_LINE = `#version 300 es
  precision highp float; in vec3 vInfo; in float vSide; out vec4 o;
  uniform vec3 cA, cB; uniform float gain, lock, waves, sharp;
  void main(){ float prof=exp(-vSide*vSide*sharp); float depth=vInfo.x;
    float w=smoothstep(-0.35,0.35,sin(vInfo.y*waves));
    vec3 c=mix(cA,mix(cA,cB,w),lock);
    o=vec4(c*prof*gain*mix(0.16,1.0,depth*depth),1.); }`;
  const FS_BALL = `#version 300 es
  precision highp float; in vec2 vUv; out vec4 o; uniform vec3 tint; uniform float energy, time, wall; uniform vec2 res; ${HASH}
  void main(){ vec2 p=(vUv-0.5)/wall; float r=length(p); float z=sqrt(max(0.,1.-r*r)); float d=max(r-1.,0.);
    float e=1.5/(wall*res.x); float mask=1.0-smoothstep(1.0-e,1.0+e,r);
    vec3 body=vec3(0.02,0.021,0.026)*(0.4+0.6*z)+tint*(0.05+0.10*energy)*pow(1.0-z,2.2);
    vec2 hl=p-vec2(-0.36,0.46); body+=0.05*exp(-dot(hl,hl)/0.02);
    vec3 halo=tint*(0.16*exp(-d*6.0)+0.07*exp(-d*2.0))*(0.5+1.2*energy);
    vec3 c=vec3(0.039,0.035,0.035)+mix(halo,body,mask); c+=(hash(gl_FragCoord.xy+fract(time)*61.0)-0.5)*0.025; o=vec4(c,1.); }`;
  const VS_FULL = `#version 300 es
  in vec2 aPos; out vec2 vUv; void main(){ vUv=aPos*0.5+0.5; gl_Position=vec4(aPos,0.,1.); }`;

  function makeThread(canvas) {
    const gl = canvas.getContext('webgl2', { alpha: false, antialias: true });
    if (!gl) return null;
    const N = 3200, vao0 = fullscreen(gl), ball = program(gl, VS_FULL, FS_BALL);
    const line = (() => {
      const p = gl.createProgram();
      gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, VS_LINE)); gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, FS_LINE));
      gl.bindAttribLocation(p, 0, 'aPos'); gl.bindAttribLocation(p, 1, 'aNorm'); gl.bindAttribLocation(p, 2, 'aInfo'); gl.linkProgram(p);
      const u = {}; for (const n of ['width', 'cA', 'cB', 'gain', 'lock', 'waves', 'sharp']) u[n] = gl.getUniformLocation(p, n);
      return { p, u };
    })();
    const data = new Float32Array((N + 1) * 2 * 7), vao = gl.createVertexArray(), buf = gl.createBuffer();
    gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, data.byteLength, gl.DYNAMIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 28, 0);
    gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 2, gl.FLOAT, false, 28, 8);
    gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 3, gl.FLOAT, false, 28, 16);
    const px = new Float32Array(N + 1), py = new Float32Array(N + 1), pd = new Float32Array(N + 1);
    const keep = program(gl, VS_FULL, `#version 300 es
  precision highp float; in vec2 vUv; out vec4 o; uniform sampler2D uTex; uniform float mul, sub;
  void main(){ o=vec4(max(texture(uTex,vUv).rgb*mul-sub,0.),1.); }`);
    let A = null, B = null, tw = 0;
    function layer() {
      const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, canvas.width, canvas.height, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
      const fb = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, fb); gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, t, 0);
      gl.clearColor(0, 0, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT);
      return { t, fb };
    }
    let time = 0, energy = 0, semi = -1, p = 1, q = 1, lock = 0, lockV = 0, goal = 0, spin = 0; const plucks = [];
    function step(dt) {
      dt = Math.min(dt, 1 / 30); time += dt; energy *= Math.exp(-dt * 1.6);
      // the winding opens like a spring let go: a little past, then back
      lockV += (140 * (goal - lock) - 15 * lockV) * dt; lock += lockV * dt;
      if (goal === 0 && lock < 0.02) { p = 1; q = 1; }
      const s = Math.max(0, Math.min(1.04, lock)) * 0.9, te = semi < 0 ? 0 : tension(semi);
      spin += dt * (0.05 + 0.5 * te * lock);
      const tilt = s * (0.62 + 0.06 * Math.sin(time * 0.31)), ct = Math.cos(tilt), st = Math.sin(tilt), cs = Math.cos(spin), ss = Math.sin(spin);
      for (let i = plucks.length - 1; i >= 0; i--) { plucks[i].t += dt; if (plucks[i].t > 5) plucks.splice(i, 1); }
      for (let i = 0; i <= N; i++) {
        const u = 6.283185307 * i / N;
        // a pluck sends two waves round the closed string, the high harmonics dying first (as on the strings of the Tir)
        let disp = 0;
        for (const pl of plucks) {
          for (let k = 1; k <= 9; k++) disp += pl.s * 0.12 / Math.pow(k, 1.35) * Math.exp(-pl.t * (0.8 + 0.45 * (k - 1))) * Math.cos(k * (u - pl.u)) * Math.cos(9.5 * k * pl.t);
        }
        disp += 0.006 * te * lock * Math.sin(37 * u + 7 * time);
        const z0 = s * Math.cos(q * u), rho = Math.sqrt(1 - z0 * z0) * (1 + disp), a = p * u + spin;
        const x = rho * Math.cos(a), y0 = rho * Math.sin(a), z = z0 * (1 + disp);
        // lean the pole towards the eye a little, so the winding reads as a ball and not as a flat rose
        const y = y0 * ct - z * st, zz = y0 * st + z * ct, k = 1 / (1 - 0.16 * zz);
        px[i] = x * k * ORB * 2; py[i] = y * k * ORB * 2; pd[i] = 0.5 + 0.5 * zz;
      }
      let o = 0;
      for (let i = 0; i <= N; i++) {
        const a = i === 0 ? N - 1 : i - 1, b = i === N ? 1 : i + 1; let tx = px[b] - px[a], ty = py[b] - py[a]; const l = Math.hypot(tx, ty) || 1; tx /= l; ty /= l;
        for (let side = -1; side <= 1; side += 2) { data[o++] = px[i]; data[o++] = py[i]; data[o++] = -ty; data[o++] = tx; data[o++] = pd[i]; data[o++] = 6.283185307 * i / N; data[o++] = side; }
      }
      // the string leaves an afterglow, as a trace on a phosphor screen: the picture so far fades, the string is added
      if (tw !== canvas.width) { tw = canvas.width; A = layer(); B = layer(); }
      const fade = Math.exp(-dt / 0.085), k = 1 - fade;
      gl.bindFramebuffer(gl.FRAMEBUFFER, B.fb); gl.viewport(0, 0, canvas.width, canvas.height); gl.disable(gl.BLEND);
      gl.useProgram(keep.p); gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, A.t); gl.uniform1i(keep.u.uTex, 0); gl.uniform1f(keep.u.mul, fade); gl.uniform1f(keep.u.sub, 0.004);
      gl.bindVertexArray(vao0); gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
      gl.useProgram(line.p); gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferSubData(gl.ARRAY_BUFFER, 0, data);
      gl.uniform3fv(line.u.cA, GOLD); gl.uniform3fv(line.u.cB, semi < 0 ? GOLD : COLORS[semi]); gl.uniform1f(line.u.lock, Math.min(1, Math.max(0, lock))); gl.uniform1f(line.u.waves, q);
      // at rest the string lies on itself p times: keep the light of one string
      const fold = 1 / (1 + (p - 1) * Math.pow(1 - Math.min(1, Math.max(0, lock)), 3)), dense = 1 / (1 + 0.035 * (p + q));
      const pass = (w, g, sh) => { gl.uniform1f(line.u.width, w); gl.uniform1f(line.u.gain, g * fold * k); gl.uniform1f(line.u.sharp, sh); gl.drawArrays(gl.TRIANGLE_STRIP, 0, (N + 1) * 2); };
      pass(0.10, (0.13 + 0.14 * energy) * dense, 3.2); pass(0.032, 0.40 * dense, 3.0); pass(0.0085, 1.2, 2.2);
      gl.disable(gl.BLEND);
      gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, canvas.width, canvas.height);
      const tint = semi < 0 ? GOLD : COLORS[semi].map((v, i) => 0.5 * (v + GOLD[i]));
      gl.useProgram(ball.p); gl.uniform3fv(ball.u.tint, tint); gl.uniform1f(ball.u.energy, energy); gl.uniform1f(ball.u.time, time); gl.uniform1f(ball.u.wall, ORB); gl.uniform2f(ball.u.res, canvas.width, canvas.height);
      gl.bindVertexArray(vao0); gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
      gl.useProgram(keep.p); gl.bindTexture(gl.TEXTURE_2D, B.t); gl.uniform1f(keep.u.mul, 1); gl.uniform1f(keep.u.sub, 0); gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.disable(gl.BLEND);
      const x = A; A = B; B = x;
    }
    return {
      step,
      pluck(s = 1, x = 0, y = 1) { plucks.push({ t: 0, s, u: Math.atan2(y, x) }); if (plucks.length > 4) plucks.shift(); energy = Math.min(1.5, energy + s); },
      answer(sm) { if (sm === 0) { semi = 0; goal = 0; energy = 1.2; return; } semi = sm; [p, q] = RATIO[sm]; lock = 0; lockV = 0; goal = 1; },
      clear() { goal = 0; semi = -1; },
      pointer() {},
    };
  }

  // ================================================================ 3. SAND: grains on a ringing plate (Chladni)
  /** Bessel J_m(x) from its integral: plain and exact enough for a picture. */
  function besselJ(m, x) { let s = 0; const n = 220; for (let i = 0; i < n; i++) { const t = Math.PI * (i + 0.5) / n; s += Math.cos(m * t - x * Math.sin(t)); } return s / n; }
  function besselZero(m, n) { let prev = besselJ(m, 0.4), found = 0; for (let x = 0.45; x < 140; x += 0.05) { const v = besselJ(m, x); if (prev * v < 0 && x > m * 0.9) { if (++found === n) return x - 0.05 * v / (v - prev); } prev = v; } return 10; }
  const VS_SAND = `#version 300 es
  in vec2 aPos; in float aHeat; uniform float size; out float vHeat; void main(){ vHeat=aHeat; gl_Position=vec4(aPos,0.,1.); gl_PointSize=size*(1.0+0.6*aHeat); }`;
  const FS_SAND = `#version 300 es
  precision highp float; in float vHeat; out vec4 o; uniform vec3 tint;
  void main(){ vec2 d=gl_PointCoord-0.5; float a=smoothstep(0.5,0.1,length(d)); vec3 c=mix(vec3(1.0,0.84,0.62),tint,0.55)*(0.17+0.8*vHeat); o=vec4(c*a,1.); }`;
  const FS_PLATE = `#version 300 es
  precision highp float; in vec2 vUv; out vec4 o; uniform vec3 tint; uniform float energy, time, wall; uniform vec2 res; ${HASH}
  void main(){ vec2 p=(vUv-0.5)/wall; float r=length(p); float d=max(r-1.,0.);
    float e=1.5/(wall*res.x); float mask=1.0-smoothstep(1.0-e,1.0+e,r);
    vec3 plate=vec3(0.028,0.027,0.03)*(1.0-0.45*r*r)+tint*0.035*energy;
    plate+=tint*0.5*exp(-pow((r-0.992)/0.006,2.0))*(0.35+0.65*energy);
    vec3 halo=tint*(0.14*exp(-d*7.0)+0.05*exp(-d*2.0))*(0.35+1.3*energy);
    vec3 c=vec3(0.039,0.035,0.035)+mix(halo,plate,mask); c+=(hash(gl_FragCoord.xy+fract(time)*61.0)-0.5)*0.02; o=vec4(c,1.); }`;

  function makeSand(canvas, count) {
    const gl = canvas.getContext('webgl2', { alpha: false, antialias: false });
    if (!gl) return null;
    const N = count || 30000, M = 700, vao0 = fullscreen(gl), plate = program(gl, VS_FULL, FS_PLATE);
    const prog = (() => {
      const p = gl.createProgram();
      gl.attachShader(p, compile(gl, gl.VERTEX_SHADER, VS_SAND)); gl.attachShader(p, compile(gl, gl.FRAGMENT_SHADER, FS_SAND));
      gl.bindAttribLocation(p, 0, 'aPos'); gl.bindAttribLocation(p, 1, 'aHeat'); gl.linkProgram(p);
      return { p, u: { size: gl.getUniformLocation(p, 'size'), tint: gl.getUniformLocation(p, 'tint') } };
    })();
    const X = new Float32Array(N), Y = new Float32Array(N), VX = new Float32Array(N), VY = new Float32Array(N), out = new Float32Array(N * 3);
    let rnd = 12345; const rand = () => ((rnd = (rnd * 1664525 + 1013904223) >>> 0) / 4294967296);
    for (let i = 0; i < N; i++) { const r = Math.sqrt(rand()) * 0.97, a = rand() * 6.2832; X[i] = r * Math.cos(a); Y[i] = r * Math.sin(a); }
    const vao = gl.createVertexArray(), buf = gl.createBuffer();
    gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, out.byteLength, gl.DYNAMIC_DRAW);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 12, 0);
    gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 1, gl.FLOAT, false, 12, 8);
    // the figure: m nodal diameters and n nodal circles of a round plate, u = J_m(k r) cos(m theta)
    const J = new Float32Array(M + 2); let m = 0, k = 1;
    function figure(mm, nn) { m = mm; k = besselZero(mm, nn); let peak = 0; for (let i = 0; i < M + 2; i++) { J[i] = besselJ(mm, k * i / M); peak = Math.max(peak, Math.abs(J[i])); } for (let i = 0; i < M + 2; i++) J[i] /= peak; }
    figure(0, 3);
    let time = 0, energy = 0, shake = 0.5, semi = -1, spin = 0, finger = null;
    function step(dt) {
      dt = Math.min(dt, 1 / 30); time += dt; energy *= Math.exp(-dt * 1.6); shake = 0.05 + (shake - 0.05) * Math.exp(-dt * 1.3);
      const te = semi < 0 ? 0 : tension(semi); spin += dt * 0.25 * te;
      const pull = 15 / Math.sqrt(k), jit = (0.9 * shake + 0.25 * te) * Math.sqrt(dt) * 0.55, damp = Math.exp(-dt * 9);
      for (let i = 0; i < N; i++) {
        let x = X[i], y = Y[i]; const r = Math.hypot(x, y) || 1e-4, th = Math.atan2(y, x) - spin;
        const f = Math.min(r, 0.999) * M, i0 = f | 0, fr = f - i0, j = J[i0] + (J[i0 + 1] - J[i0]) * fr, dj = (J[i0 + 1] - J[i0]) * M;
        const c = Math.cos(m * th), s = Math.sin(m * th), u = j * c;
        // grains leave where the plate moves and stay where it does not: down the slope of u*u, and shaken by |u|
        const gr = dj * c, gt = -m * j * s / Math.max(r, 0.04), ex = x / r, ey = y / r;
        let ax = -2 * u * (gr * ex - gt * ey) * pull, ay = -2 * u * (gr * ey + gt * ex) * pull;
        if (finger) { const dx = x - finger[0], dy = y - finger[1], d2 = dx * dx + dy * dy; if (d2 < 0.05) { const w = 9 * (1 - d2 / 0.05) / (Math.sqrt(d2) + 0.02); ax += dx * w; ay += dy * w; } }
        const a = Math.abs(u) * jit + 0.016;
        let vx = (VX[i] + ax * dt) * damp + (rand() - 0.5) * a * 9, vy = (VY[i] + ay * dt) * damp + (rand() - 0.5) * a * 9;
        x += vx * dt; y += vy * dt; const r2 = Math.hypot(x, y);
        if (r2 > 0.985) { x *= 0.985 / r2; y *= 0.985 / r2; vx *= -0.3; vy *= -0.3; }
        X[i] = x; Y[i] = y; VX[i] = vx; VY[i] = vy;
        out[i * 3] = x * ORB * 2; out[i * 3 + 1] = y * ORB * 2; out[i * 3 + 2] = Math.min(1, Math.hypot(vx, vy) * 1.6);
      }
      gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, canvas.width, canvas.height); gl.disable(gl.BLEND);
      const tint = semi < 0 ? GOLD : COLORS[semi];
      gl.useProgram(plate.p); gl.uniform3fv(plate.u.tint, tint); gl.uniform1f(plate.u.energy, energy); gl.uniform1f(plate.u.time, time); gl.uniform1f(plate.u.wall, ORB); gl.uniform2f(plate.u.res, canvas.width, canvas.height);
      gl.bindVertexArray(vao0); gl.drawArrays(gl.TRIANGLES, 0, 3);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE);
      gl.useProgram(prog.p); gl.uniform1f(prog.u.size, Math.max(1.5, canvas.width / 300)); gl.uniform3fv(prog.u.tint, tint);
      gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferSubData(gl.ARRAY_BUFFER, 0, out); gl.drawArrays(gl.POINTS, 0, N);
      gl.disable(gl.BLEND);
    }
    function jump(s) { for (let i = 0; i < N; i++) { const a = rand() * 6.2832, v = (0.25 + rand()) * 1.5 * s; VX[i] += Math.cos(a) * v; VY[i] += Math.sin(a) * v; } }
    return {
      step,
      pluck(s = 1) { jump(s); shake = Math.min(1.2, shake + 0.8 * s); energy = Math.min(1.5, energy + s); },
      answer(sm) { semi = sm; if (sm === 0) figure(0, 2); else figure(RATIO[sm][0], RATIO[sm][1]); shake = 1.1; jump(0.35); },
      clear() { semi = -1; figure(0, 3); shake = 0.9; },
      pointer(x, y, dx, dy, down) { finger = down === false ? null : [x, y]; },
    };
  }

  root.OrbLab2 = { RATIO, COLORS, ORB, tension, makers: { ink: makeInk, thread: makeThread, sand: makeSand } };
})(typeof window !== 'undefined' ? window : globalThis);
