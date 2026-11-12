package com.kazaeza.muzykalka.ui.orb

/**
 * O-38 (ADR-040): the shaders of the three bodies, GLSL ES 3.00. They are the text of the lab page
 * (docs/design/lab2/orbs.js) with one change: the page painted an opaque canvas of its own background colour, the
 * app draws on its own screen, so the output is premultiplied light — `rgb` without the background and an alpha of
 * `max(mask of the ball, brightest channel)` — and the interior of the ball takes the background colour
 * (`Juicy.Ink`, #0A0909, written as vec3(0.039, 0.035, 0.035)) itself; the film grain of the page is left out (the screen has its own).
 * [PREFIX] goes in front of every source. Do not put a dollar sign in these strings (Kotlin).
 * `scripts/orb_shaders_check.js` compiles them in a browser's WebGL 2, which is the same language.
 */
object OrbShaders {
    const val PREFIX = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n"

    /** One triangle that covers the box. */
    const val VS_FULL = """
in vec2 aPos; out vec2 vUv;
void main(){ vUv=aPos*0.5+0.5; gl_Position=vec4(aPos,0.,1.); }"""

    // ================================================================ THREAD
    const val VS_LINE = """
in vec2 aPos; in vec2 aNorm; in vec3 aInfo; uniform float width; out vec3 vInfo; out float vSide;
void main(){ vInfo=aInfo; vSide=aNorm.x==0.&&aNorm.y==0. ? 0. : aInfo.z; gl_Position=vec4(aPos+aNorm*width*aInfo.z,0.,1.); }"""

    const val FS_LINE = """
in vec3 vInfo; in float vSide; out vec4 o;
uniform vec3 cA, cB; uniform float gain, lock, waves, sharp;
void main(){ float prof=exp(-vSide*vSide*sharp); float depth=vInfo.x;
  float w=smoothstep(-0.35,0.35,sin(vInfo.y*waves));
  vec3 c=mix(cA,mix(cA,cB,w),lock);
  o=vec4(c*prof*gain*mix(0.16,1.0,depth*depth),1.); }"""

    /** The picture so far, a little dimmer: the afterglow of a phosphor screen. */
    const val FS_KEEP = """
in vec2 vUv; out vec4 o; uniform sampler2D uTex; uniform float mul, sub;
void main(){ o=vec4(max(texture(uTex,vUv).rgb*mul-sub,0.),1.); }"""

    /** The light of the afterglow layer, laid over the ball as premultiplied light. */
    const val FS_OVER = """
in vec2 vUv; out vec4 o; uniform sampler2D uTex;
void main(){ vec3 c=texture(uTex,vUv).rgb; o=vec4(c,max(c.r,max(c.g,c.b))); }"""

    const val FS_BALL = """
in vec2 vUv; out vec4 o; uniform vec3 tint; uniform float energy, wall; uniform vec2 res;
void main(){ vec2 p=(vUv-0.5)/wall; float r=length(p); float z=sqrt(max(0.,1.-r*r)); float d=max(r-1.,0.);
  float e=1.5/(wall*res.x); float mask=1.0-smoothstep(1.0-e,1.0+e,r);
  vec3 body=vec3(0.02,0.021,0.026)*(0.4+0.6*z)+tint*(0.05+0.10*energy)*pow(1.0-z,2.2);
  vec2 hl=p-vec2(-0.36,0.46); body+=0.05*exp(-dot(hl,hl)/0.02);
  vec3 halo=tint*(0.16*exp(-d*6.0)+0.07*exp(-d*2.0))*(0.5+1.2*energy);
  vec3 c=mix(halo,body+vec3(0.039,0.035,0.035),mask);
  o=vec4(c,max(mask,max(c.r,max(c.g,c.b)))); }"""

    // ================================================================ SAND
    const val VS_SAND = """
in vec2 aPos; in float aHeat; uniform float size; out float vHeat;
void main(){ vHeat=aHeat; gl_Position=vec4(aPos,0.,1.); gl_PointSize=size*(1.0+0.6*aHeat); }"""

    const val FS_SAND = """
in float vHeat; out vec4 o; uniform vec3 tint;
void main(){ vec2 d=gl_PointCoord-0.5; float a=smoothstep(0.5,0.1,length(d)); vec3 c=mix(vec3(1.0,0.84,0.62),tint,0.55)*(0.17+0.8*vHeat); o=vec4(c*a,1.); }"""

    const val FS_PLATE = """
in vec2 vUv; out vec4 o; uniform vec3 tint; uniform float energy, wall; uniform vec2 res;
void main(){ vec2 p=(vUv-0.5)/wall; float r=length(p); float d=max(r-1.,0.);
  float e=1.5/(wall*res.x); float mask=1.0-smoothstep(1.0-e,1.0+e,r);
  vec3 plate=vec3(0.028,0.027,0.03)*(1.0-0.45*r*r)+tint*0.035*energy;
  plate+=tint*0.5*exp(-pow((r-0.992)/0.006,2.0))*(0.35+0.65*energy);
  vec3 halo=tint*(0.14*exp(-d*7.0)+0.05*exp(-d*2.0))*(0.35+1.3*energy);
  vec3 c=mix(halo,plate+vec3(0.039,0.035,0.035),mask);
  o=vec4(c,max(mask,max(c.r,max(c.g,c.b)))); }"""

    // ================================================================ INK
    const val VS_SIM = """
in vec2 aPos; uniform vec2 texel; out vec2 vUv, vL, vR, vT, vB;
void main(){ vUv=aPos*0.5+0.5; vL=vUv-vec2(texel.x,0.); vR=vUv+vec2(texel.x,0.); vT=vUv+vec2(0.,texel.y); vB=vUv-vec2(0.,texel.y); gl_Position=vec4(aPos,0.,1.); }"""

    const val FS_SIM_HEAD = "in vec2 vUv, vL, vR, vT, vB; out vec4 o;\n"

    const val INK_ADVECT = """
uniform sampler2D uVel, uSrc; uniform float dt, diss; uniform vec2 vtexel;
void main(){ vec2 c=vUv-dt*texture(uVel,vUv).xy*vtexel; o=texture(uSrc,c)/(1.0+diss*dt); }"""

    const val INK_SPLAT = """
uniform sampler2D uSrc; uniform vec2 point; uniform vec3 color; uniform float radius;
void main(){ vec2 p=vUv-point; o=vec4(texture(uSrc,vUv).rgb+color*exp(-dot(p,p)/radius),1.); }"""

    const val INK_BURST = """
uniform sampler2D uSrc; uniform vec2 point; uniform float power, r0, w, seed;
void main(){ vec2 p=vUv-point; float r=length(p); vec2 n=p/max(r,1e-4); float a=atan(p.y,p.x);
  float f=power*exp(-pow((r-r0)/w,2.0))*(1.0+0.5*sin(5.0*a+seed)+0.35*sin(8.0*a-seed*1.7));
  o=vec4(texture(uSrc,vUv).xy+n*f,0.,1.); }"""

    const val INK_CURL = """
uniform sampler2D uVel;
void main(){ float L=texture(uVel,vL).y, R=texture(uVel,vR).y, T=texture(uVel,vT).x, B=texture(uVel,vB).x; o=vec4(0.5*(R-L-T+B),0.,0.,1.); }"""

    const val INK_VORT = """
uniform sampler2D uVel, uCurl; uniform float curl, dt;
void main(){ float L=texture(uCurl,vL).x, R=texture(uCurl,vR).x, T=texture(uCurl,vT).x, B=texture(uCurl,vB).x, C=texture(uCurl,vUv).x;
  vec2 f=0.5*vec2(abs(T)-abs(B),abs(R)-abs(L)); f/=length(f)+1e-4; f*=curl*C; f.y*=-1.0;
  o=vec4(texture(uVel,vUv).xy+f*dt,0.,1.); }"""

    const val INK_DIV = """
uniform sampler2D uVel;
void main(){ float L=texture(uVel,vL).x, R=texture(uVel,vR).x, T=texture(uVel,vT).y, B=texture(uVel,vB).y; o=vec4(0.5*(R-L+T-B),0.,0.,1.); }"""

    const val INK_PRESSURE = """
uniform sampler2D uP, uDiv;
void main(){ float L=texture(uP,vL).x, R=texture(uP,vR).x, T=texture(uP,vT).x, B=texture(uP,vB).x; o=vec4((L+R+B+T-texture(uDiv,vUv).x)*0.25,0.,0.,1.); }"""

    /** Projection, and the wall of the ball: nothing flows through it, the flow slides along and curls back. */
    const val INK_GRAD = """
uniform sampler2D uP, uVel; uniform float wall;
void main(){ float L=texture(uP,vL).x, R=texture(uP,vR).x, T=texture(uP,vT).x, B=texture(uP,vB).x;
  vec2 v=texture(uVel,vUv).xy-vec2(R-L,T-B); vec2 p=vUv-0.5; float r=length(p); vec2 n=p/max(r,1e-4);
  v-=n*max(dot(v,n),0.)*smoothstep(wall-0.07,wall-0.01,r); v*=1.0-smoothstep(wall-0.012,wall+0.004,r);
  o=vec4(v,0.,1.); }"""

    const val INK_SHOW = """
uniform sampler2D uDye; uniform float wall, energy; uniform vec2 res;
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
  vec3 c=mix(halo,col+vec3(0.039,0.035,0.035),mask);
  o=vec4(c,max(mask,max(c.r,max(c.g,c.b)))); }"""
}
