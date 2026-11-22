import type { LensId, VibeTag } from '../types';
import { TAGS } from '../data/tags';

interface NebulaPuff {
  x: number;
  y: number;
  z: number;
  radius: number;
  color: [number, number, number];
  phase: number;
  alpha: number;
}

interface Star {
  x: number;
  y: number;
  z: number;
  size: number;
  twinkle: number;
  speed: number;
  sparkle: boolean;
}

interface TagNode {
  tag: VibeTag;
  el: HTMLButtonElement;
  bx: number;
  by: number;
  bz: number;
  width: number;
  height: number;
  curOpacity: number;
  blurBucket: number;
  screenX: number;
  screenY: number;
}

const NEBULA_COLORS: [number, number, number][] = [
  [188, 108, 255], // luminous violet
  [255, 118, 168], // nebula rose
  [104, 186, 255], // cosmic cyan-blue
  [255, 164, 122], // warm peach glow
  [108, 232, 198], // aurora mint
  [142, 114, 255], // deep periwinkle
];

function escapeHtml(s: string): string {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] || c));
}

function smoothstep(edge0: number, edge1: number, x: number): number {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

function applyScreenRotation(m: number[], dYaw: number, dPitch: number): void {
  if (dYaw) {
    const cy = Math.cos(dYaw);
    const sy = Math.sin(dYaw);
    for (let col = 0; col < 3; col++) {
      const x = m[col];
      const z = m[6 + col];
      m[col] = x * cy + z * sy;
      m[6 + col] = -x * sy + z * cy;
    }
  }
  if (dPitch) {
    const cx = Math.cos(dPitch);
    const sx = Math.sin(dPitch);
    for (let col = 0; col < 3; col++) {
      const y = m[3 + col];
      const z = m[6 + col];
      m[3 + col] = y * cx + z * sx;
      m[6 + col] = -y * sx + z * cx;
    }
  }
}

// Gram-Schmidt re-orthonormalization to prevent floating-point drift over long sessions
function orthonormalize(m: number[]): void {
  // Rows r0, r1, r2
  let r0x = m[0], r0y = m[1], r0z = m[2];
  const n0 = Math.hypot(r0x, r0y, r0z) || 1;
  r0x /= n0; r0y /= n0; r0z /= n0;

  let r1x = m[3], r1y = m[4], r1z = m[5];
  const dot01 = r0x * r1x + r0y * r1y + r0z * r1z;
  r1x -= dot01 * r0x;
  r1y -= dot01 * r0y;
  r1z -= dot01 * r0z;
  const n1 = Math.hypot(r1x, r1y, r1z) || 1;
  r1x /= n1; r1y /= n1; r1z /= n1;

  // r2 = r0 x r1
  const r2x = r0y * r1z - r0z * r1y;
  const r2y = r0z * r1x - r0x * r1z;
  const r2z = r0x * r1y - r0y * r1x;

  m[0] = r0x; m[1] = r0y; m[2] = r0z;
  m[3] = r1x; m[4] = r1y; m[5] = r1z;
  m[6] = r2x; m[7] = r2y; m[8] = r2z;
}

function projectMat(m: number[], x: number, y: number, z: number): [number, number, number] {
  return [
    m[0] * x + m[1] * y + m[2] * z,
    m[3] * x + m[4] * y + m[5] * z,
    m[6] * x + m[7] * y + m[8] * z,
  ];
}

function drawFourPointStar(ctx: CanvasRenderingContext2D, x: number, y: number, r: number, alpha: number): void {
  ctx.save();
  ctx.translate(x, y);
  ctx.fillStyle = `rgba(255, 240, 255, ${alpha.toFixed(2)})`;
  ctx.beginPath();
  ctx.moveTo(0, -r);
  ctx.quadraticCurveTo(r * 0.16, -r * 0.16, r, 0);
  ctx.quadraticCurveTo(r * 0.16, r * 0.16, 0, r);
  ctx.quadraticCurveTo(-r * 0.16, r * 0.16, -r, 0);
  ctx.quadraticCurveTo(-r * 0.16, -r * 0.16, 0, -r);
  ctx.fill();
  ctx.restore();
}

export interface NebulaOptions {
  appEl: HTMLElement;
  canvasEl: HTMLCanvasElement;
  tagLayerEl: HTMLElement;
  topObstacleEl: HTMLElement;
  bottomObstacleEl: HTMLElement;
  isSelected: (tagId: string) => boolean;
  onToggleTag: (tag: VibeTag) => void;
}

export class NebulaEngine {
  private opts: NebulaOptions;
  private ctx: CanvasRenderingContext2D;
  private rotM = [1, 0, 0, 0, 1, 0, 0, 0, 1];
  private starM = [1, 0, 0, 0, 1, 0, 0, 0, 1];
  private velX = 0;
  private velY = 0;
  private isDragging = false;
  private dragMoved = false;
  private dragOrigin: { id: number; x: number; y: number; startX: number; startY: number; t: number } | null = null;
  private hoveredTagId: string | null = null;

  private paused = false;
  private active = true;
  private rafId: number | null = null;
  private lastTime = 0;
  private frameCounter = 0;

  private viewportW = 390;
  private viewportH = 780;
  private safeTop = 124;
  private safeBottom = 560;

  private nebulaPuffs: NebulaPuff[];
  private stars: Star[];
  private tagNodes: TagNode[] = [];

  constructor(opts: NebulaOptions) {
    this.opts = opts;
    const ctx = opts.canvasEl.getContext('2d');
    if (!ctx) throw new Error('Could not acquire 2D context for nebula canvas');
    this.ctx = ctx;

    applyScreenRotation(this.rotM, 0.55, 0.12);

    this.nebulaPuffs = Array.from({ length: 20 }, (_, i) => {
      const y = 1 - (2 * (i + 0.5)) / 20;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const a = i * 2.399963;
      const dist = 0.35 + (i % 4) * 0.22;
      return {
        x: r * Math.cos(a) * dist,
        y: y * dist * 0.85,
        z: r * Math.sin(a) * dist,
        radius: 115 + (i % 5) * 34,
        color: NEBULA_COLORS[i % NEBULA_COLORS.length],
        phase: (i * 1.13) % (Math.PI * 2),
        alpha: 0.16 + (i % 3) * 0.05,
      };
    });

    // Deterministic seeded stars so layout looks balanced every launch
    let seed = 4219;
    const rand = () => {
      seed = (seed * 16807) % 2147483647;
      return (seed - 1) / 2147483646;
    };

    this.stars = Array.from({ length: 130 }, () => {
      const theta = rand() * Math.PI * 2;
      const phi = Math.acos(2 * rand() - 1);
      const rad = 0.9 + rand() * 1.1;
      return {
        x: rad * Math.sin(phi) * Math.cos(theta),
        y: rad * Math.cos(phi),
        z: rad * Math.sin(phi) * Math.sin(theta),
        size: 0.6 + rand() * 1.5,
        twinkle: rand() * Math.PI * 2,
        speed: 0.001 + rand() * 0.0022,
        sparkle: rand() < 0.09,
      };
    });

    this.bindDrag();
    this.bindResize();
  }

  public start(): void {
    this.measureBounds();
    this.active = true;
    this.scheduleFrame();
  }

  public setActive(active: boolean): void {
    this.active = active;
    if (active) {
      this.lastTime = performance.now();
      this.measureBounds();
      this.scheduleFrame();
    } else if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  public setPaused(paused: boolean): void {
    this.paused = paused;
  }

  public isPaused(): boolean {
    return this.paused;
  }

  public spinImpulse(vx: number, vy: number): void {
    this.velX = vx;
    this.velY = vy;
  }

  public nudgeRotation(dYaw: number, dPitch: number): void {
    applyScreenRotation(this.rotM, dYaw, dPitch);
    applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);
  }

  public syncSelectionStyles(): void {
    for (const node of this.tagNodes) {
      const isSel = this.opts.isSelected(node.tag.id);
      node.el.classList.toggle('selected', isSel);
      node.el.setAttribute('aria-pressed', isSel ? 'true' : 'false');
    }
  }

  public rebuildTags(lens: LensId): void {
    const layer = this.opts.tagLayerEl;
    layer.innerHTML = '';

    const visible = TAGS.filter((t) => lens === 'all' || t.lenses.includes(lens));
    const n = visible.length;

    const nodes: TagNode[] = visible.map((tag, i) => {
      const y = 1 - (2 * (i + 0.5)) / n;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const a = i * Math.PI * (3 - Math.sqrt(5));

      const btn = document.createElement('button');
      btn.type = 'button';
      const isSel = this.opts.isSelected(tag.id);
      btn.className = 'nebula-tag' + (isSel ? ' selected' : '');
      btn.style.setProperty('--glow', tag.glow);
      btn.setAttribute('aria-pressed', isSel ? 'true' : 'false');
      btn.innerHTML = `
        <span class="tag-main">
          <svg class="tag-spark" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true"><path d="M8 0L9.7 6.3L16 8L9.7 9.7L8 16L6.3 9.7L0 8L6.3 6.3L8 0Z"/></svg>
          <span>${escapeHtml(tag.label)}</span>
        </span>
        <span class="tag-sub">${escapeHtml(tag.sub)}</span>
      `;

      btn.addEventListener('mouseenter', () => {
        this.hoveredTagId = tag.id;
      });
      btn.addEventListener('mouseleave', () => {
        if (this.hoveredTagId === tag.id) this.hoveredTagId = null;
      });
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        if (this.dragMoved) return;
        this.opts.onToggleTag(tag);
      });

      layer.appendChild(btn);

      return {
        tag,
        el: btn,
        bx: r * Math.cos(a),
        by: y,
        bz: r * Math.sin(a),
        width: 130,
        height: 44,
        curOpacity: 0,
        blurBucket: -1,
        screenX: 0,
        screenY: 0,
      };
    });

    // Measure all tag widths/heights in a SINGLE batched layout read — never again inside rAF!
    for (const node of nodes) {
      node.width = node.el.offsetWidth || 130;
      node.height = node.el.offsetHeight || 44;
    }

    this.tagNodes = nodes;
    this.measureBounds();
  }

  public measureBounds(): void {
    const appRect = this.opts.appEl.getBoundingClientRect();
    this.viewportW = appRect.width || window.innerWidth || 390;
    this.viewportH = appRect.height || window.innerHeight || 780;

    const topRect = this.opts.topObstacleEl.getBoundingClientRect();
    const bottomRect = this.opts.bottomObstacleEl.getBoundingClientRect();

    const topLimit = Math.max(108, topRect.bottom - appRect.top + 24);
    const bottomLimit = Math.min(this.viewportH - 180, bottomRect.top - appRect.top - 26);

    this.safeTop = topLimit;
    this.safeBottom = Math.max(topLimit + 120, bottomLimit);
  }

  private bindResize(): void {
    const onResize = () => {
      this.measureBounds();
      for (const node of this.tagNodes) {
        node.width = node.el.offsetWidth || node.width;
        node.height = node.el.offsetHeight || node.height;
      }
    };
    window.addEventListener('resize', onResize, { passive: true });
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        if (this.rafId !== null) {
          cancelAnimationFrame(this.rafId);
          this.rafId = null;
        }
      } else if (this.active) {
        this.lastTime = performance.now();
        this.scheduleFrame();
      }
    });
  }

  private bindDrag(): void {
    const layer = this.opts.tagLayerEl;
    layer.addEventListener('pointerdown', (e) => {
      this.isDragging = true;
      this.dragMoved = false;
      this.dragOrigin = {
        id: e.pointerId,
        x: e.clientX,
        y: e.clientY,
        startX: e.clientX,
        startY: e.clientY,
        t: performance.now(),
      };
      this.velX = 0;
      this.velY = 0;
    });

    window.addEventListener('pointermove', (e) => {
      if (!this.isDragging || !this.dragOrigin || e.pointerId !== this.dragOrigin.id) return;
      const dist = Math.hypot(e.clientX - this.dragOrigin.startX, e.clientY - this.dragOrigin.startY);
      if (dist > 8) {
        this.dragMoved = true;
        layer.classList.add('dragging');
      }
      if (!this.dragMoved) return;

      const now = performance.now();
      const dt = Math.max(8, now - this.dragOrigin.t);
      const dx = e.clientX - this.dragOrigin.x;
      const dy = e.clientY - this.dragOrigin.y;
      const dYaw = dx * 0.0062;
      const dPitch = dy * 0.0062;

      applyScreenRotation(this.rotM, dYaw, dPitch);
      applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);

      this.velX = Math.max(-0.0075, Math.min(0.0075, dYaw / dt));
      this.velY = Math.max(-0.0075, Math.min(0.0075, dPitch / dt));

      this.dragOrigin.x = e.clientX;
      this.dragOrigin.y = e.clientY;
      this.dragOrigin.t = now;
    });

    const release = () => {
      if (!this.isDragging) return;
      this.isDragging = false;
      this.dragOrigin = null;
      layer.classList.remove('dragging');
      setTimeout(() => {
        this.dragMoved = false;
      }, 60);
    };
    window.addEventListener('pointerup', release);
    window.addEventListener('pointercancel', release);
  }

  private scheduleFrame(): void {
    if (this.rafId !== null || !this.active) return;
    this.rafId = requestAnimationFrame(this.frame);
  }

  private frame = (now: number): void => {
    this.rafId = null;
    if (!this.active) return;

    const dt = Math.min(34, now - (this.lastTime || now - 16));
    this.lastTime = now;

    const w = this.viewportW;
    const h = this.viewportH;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const canvas = this.opts.canvasEl;
    const targetW = Math.round(w * dpr);
    const targetH = Math.round(h * dpr);
    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width = targetW;
      canvas.height = targetH;
    }

    if (!this.isDragging && !this.paused) {
      const drift = this.hoveredTagId ? 0.000035 : 0.00014;
      const stepYaw = this.velX * dt + drift * dt;
      const stepPitch = this.velY * dt;
      applyScreenRotation(this.rotM, stepYaw, stepPitch);
      applyScreenRotation(this.starM, stepYaw * 0.35, stepPitch * 0.35);
      this.velX *= 0.94;
      this.velY *= 0.94;
    }

    if (++this.frameCounter % 120 === 0) {
      orthonormalize(this.rotM);
      orthonormalize(this.starM);
    }

    const centerX = w * 0.5;
    const centerY = (this.safeTop + this.safeBottom) * 0.5;
    const usableH = Math.max(200, this.safeBottom - this.safeTop);
    const radiusX = Math.min(w * 0.42, 305);
    const radiusY = Math.min(usableH * 0.47, 215);

    const ctx = this.ctx;
    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, w, h);

    // 1. Parallax 3D Starfield
    const starSpan = Math.max(w, h) * 0.46;
    for (const s of this.stars) {
      const [xx, yy] = projectMat(this.starM, s.x, s.y, s.z);
      const sxPos = centerX + xx * starSpan;
      const syPos = centerY + yy * starSpan * 0.95;
      const tw = 0.25 + 0.65 * (0.5 + 0.5 * Math.sin(now * s.speed + s.twinkle));
      if (s.sparkle) {
        drawFourPointStar(ctx, sxPos, syPos, s.size * 3.6, tw * 0.85);
      } else {
        ctx.beginPath();
        ctx.arc(sxPos, syPos, s.size, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(242, 236, 255, ${(tw * 0.72).toFixed(2)})`;
        ctx.fill();
      }
    }

    // 2. 3D Rotating Nebula Gas Clouds (Screen Blend)
    ctx.save();
    ctx.globalCompositeOperation = 'screen';
    const breath = now * 0.0005;
    const cloudScale = Math.min(w, h) < 480 ? 0.76 : 1.06;

    for (const p of this.nebulaPuffs) {
      const [xx, yy, depth] = projectMat(this.rotM, p.x, p.y, p.z);
      const px = centerX + xx * radiusX * 1.12;
      const py = centerY + yy * radiusY * 1.15;
      const depthNorm = (depth + 1) * 0.5;
      const r = p.radius * cloudScale * (0.86 + 0.24 * depthNorm) * (0.94 + 0.06 * Math.sin(breath + p.phase));
      const a = p.alpha * (0.55 + 0.45 * depthNorm);

      const grad = ctx.createRadialGradient(px, py, r * 0.05, px, py, r);
      grad.addColorStop(0, `rgba(${p.color[0]}, ${p.color[1]}, ${p.color[2]}, ${a.toFixed(3)})`);
      grad.addColorStop(0.5, `rgba(${p.color[0]}, ${p.color[1]}, ${p.color[2]}, ${(a * 0.38).toFixed(3)})`);
      grad.addColorStop(1, `rgba(${p.color[0]}, ${p.color[1]}, ${p.color[2]}, 0)`);

      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.arc(px, py, r, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();

    // 3. Project 3D Tags (Zero Layout Reflows!)
    const projected = this.tagNodes.map((node) => {
      const [xx, yy, depth] = projectMat(this.rotM, node.bx, node.by, node.bz);
      const depthNorm = (depth + 1) * 0.5;
      const persp = 0.80 + 0.26 * depthNorm;
      const scale = 0.72 + 0.34 * depthNorm;
      return {
        node,
        x: centerX + xx * radiusX * persp,
        y: centerY + yy * radiusY * persp,
        depth,
        scale,
      };
    });

    const frontOccupied: { x: number; y: number; w: number; h: number }[] = [];
    const selectedPoints: { x: number; y: number }[] = [];

    projected.sort((a, b) => b.depth - a.depth);

    for (const p of projected) {
      const node = p.node;
      const el = node.el;
      const isSel = this.opts.isSelected(node.tag.id);

      const ew = node.width * p.scale;
      const eh = node.height * p.scale;

      const px = Math.max(ew / 2 + 12, Math.min(w - ew / 2 - 12, p.x));
      const py = Math.max(this.safeTop, Math.min(this.safeBottom, p.y));

      let targetOpacity = smoothstep(-0.18, 0.52, p.depth);
      let targetBlur = (1 - smoothstep(-0.1, 0.55, p.depth)) * 2.4;

      const tooClose =
        !isSel &&
        frontOccupied.some(
          (q) => Math.abs(q.x - px) < (q.w + ew) * 0.44 && Math.abs(q.y - py) < (q.h + eh) * 0.56
        );
      if (tooClose) {
        targetOpacity *= 0.22;
        targetBlur = Math.max(targetBlur, 1.8);
      }

      if (isSel) {
        targetOpacity = Math.max(0.78, 0.68 + 0.32 * ((p.depth + 1) * 0.5));
        targetBlur = 0;
      }

      node.curOpacity += (targetOpacity - node.curOpacity) * 0.12;

      el.style.transform = `translate3d(${px.toFixed(1)}px, ${py.toFixed(1)}px, 0) translate(-50%, -50%) scale(${p.scale.toFixed(3)})`;
      el.style.opacity = node.curOpacity.toFixed(3);
      el.style.zIndex = String(isSel ? 220 : Math.round((p.depth + 1) * 90));
      el.style.pointerEvents = node.curOpacity > 0.32 ? 'auto' : 'none';

      // Quantized blur bucket (0 / 1 / 2) to keep Android WebView GPU 60fps without per-frame filter churn
      const bucket = targetBlur < 0.55 ? 0 : targetBlur < 1.55 ? 1 : 2;
      if (bucket !== node.blurBucket) {
        node.blurBucket = bucket;
        el.style.filter = bucket === 0 ? 'none' : bucket === 1 ? 'blur(1.1px)' : 'blur(2.2px)';
      }

      node.screenX = px;
      node.screenY = py;

      if (node.curOpacity > 0.38) frontOccupied.push({ x: px, y: py, w: ew, h: eh });
      if (isSel) selectedPoints.push({ x: px, y: py });
    }

    // 4. Luminous Constellation Line Connecting Selected Tags
    if (selectedPoints.length >= 2) {
      ctx.save();
      ctx.beginPath();
      for (let i = 0; i < selectedPoints.length - 1; i++) {
        ctx.moveTo(selectedPoints[i].x, selectedPoints[i].y);
        ctx.lineTo(selectedPoints[i + 1].x, selectedPoints[i + 1].y);
      }
      ctx.strokeStyle = 'rgba(232, 198, 255, 0.48)';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 5]);
      ctx.stroke();
      ctx.restore();
    }

    ctx.restore();
    this.scheduleFrame();
  };
}
