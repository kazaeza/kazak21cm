import type { LensId, VibeTag } from '../types';
import { TAGS } from '../data/tags';

interface NebulaPuff {
  x: number;
  y: number;
  z: number;
  radius: number;
  spriteIdx: number;
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
  hidden: boolean;
  lastPointerAuto: boolean;
  lastZIndex: number;
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

function extractPrimaryColor(glow: string): string {
  const m = glow.match(/#[0-9a-fA-F]{6}/);
  return m ? m[0] : '#c79bff';
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

function orthonormalize(m: number[]): void {
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

// Pre-render soft radial nebula puff sprites ONCE so the rAF loop uses fast GPU drawImage
function createPuffSprite(color: [number, number, number]): HTMLCanvasElement {
  const sz = 128;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (g) {
    const half = sz * 0.5;
    const grad = g.createRadialGradient(half, half, half * 0.04, half, half, half);
    grad.addColorStop(0, `rgba(${color[0]}, ${color[1]}, ${color[2]}, 1)`);
    grad.addColorStop(0.46, `rgba(${color[0]}, ${color[1]}, ${color[2]}, 0.38)`);
    grad.addColorStop(1, `rgba(${color[0]}, ${color[1]}, ${color[2]}, 0)`);
    g.fillStyle = grad;
    g.fillRect(0, 0, sz, sz);
  }
  return c;
}

// Pre-render 4-point star sparkle sprite ONCE
function createSparkSprite(): HTMLCanvasElement {
  const sz = 32;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (g) {
    const h = sz * 0.5;
    const r = sz * 0.44;
    g.translate(h, h);
    g.fillStyle = 'rgba(255, 240, 255, 1)';
    g.beginPath();
    g.moveTo(0, -r);
    g.quadraticCurveTo(r * 0.16, -r * 0.16, r, 0);
    g.quadraticCurveTo(r * 0.16, r * 0.16, 0, r);
    g.quadraticCurveTo(-r * 0.16, r * 0.16, -r, 0);
    g.quadraticCurveTo(-r * 0.16, -r * 0.16, 0, -r);
    g.fill();
  }
  return c;
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
  private focusTargetId: string | null = null;

  private paused = false;
  private active = true;
  private rafId: number | null = null;
  private lastTime = 0;
  private frameCounter = 0;

  private viewportW = 390;
  private viewportH = 780;
  private safeTop = 124;
  private safeBottom = 560;

  private puffSprites: HTMLCanvasElement[];
  private sparkSprite: HTMLCanvasElement;
  private nebulaPuffs: NebulaPuff[];
  private stars: Star[];
  private tagNodes: TagNode[] = [];

  constructor(opts: NebulaOptions) {
    this.opts = opts;
    const ctx = opts.canvasEl.getContext('2d', { alpha: true });
    if (!ctx) throw new Error('Could not acquire 2D context for nebula canvas');
    this.ctx = ctx;

    this.puffSprites = NEBULA_COLORS.map((col) => createPuffSprite(col));
    this.sparkSprite = createSparkSprite();

    applyScreenRotation(this.rotM, 0.55, 0.12);

    // 14 lush pre-rendered nebula puffs — visually identical depth, half the overdraw
    this.nebulaPuffs = Array.from({ length: 14 }, (_, i) => {
      const y = 1 - (2 * (i + 0.5)) / 14;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const a = i * 2.399963;
      const dist = 0.34 + (i % 4) * 0.21;
      return {
        x: r * Math.cos(a) * dist,
        y: y * dist * 0.85,
        z: r * Math.sin(a) * dist,
        radius: 122 + (i % 5) * 32,
        spriteIdx: i % NEBULA_COLORS.length,
        phase: (i * 1.13) % (Math.PI * 2),
        alpha: 0.19 + (i % 3) * 0.05,
      };
    });

    // Deterministic starfield (90 crisp stars + sparkles)
    let seed = 4219;
    const rand = () => {
      seed = (seed * 16807) % 2147483647;
      return (seed - 1) / 2147483646;
    };

    this.stars = Array.from({ length: 90 }, () => {
      const theta = rand() * Math.PI * 2;
      const phi = Math.acos(2 * rand() - 1);
      const rad = 0.9 + rand() * 1.1;
      return {
        x: rad * Math.sin(phi) * Math.cos(theta),
        y: rad * Math.cos(phi),
        z: rad * Math.sin(phi) * Math.sin(theta),
        size: 0.7 + rand() * 1.4,
        twinkle: rand() * Math.PI * 2,
        speed: 0.001 + rand() * 0.0022,
        sparkle: rand() < 0.11,
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
    this.focusTargetId = null;
    this.velX = vx;
    this.velY = vy;
  }

  // Smoothly rotates the sphere to bring a specific tag straight to the front center
  public focusTag(tagId: string): void {
    this.focusTargetId = tagId;
    this.velX = 0;
    this.velY = 0;
  }

  public nudgeRotation(dYaw: number, dPitch: number): void {
    this.focusTargetId = null;
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
      btn.style.setProperty('--glow-c', extractPrimaryColor(tag.glow));
      btn.setAttribute('aria-pressed', isSel ? 'true' : 'false');
      btn.innerHTML = `
        <span class="tag-main">
          <span class="tag-emoji" aria-hidden="true">${escapeHtml(tag.emoji)}</span>
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
        hidden: false,
        lastPointerAuto: true,
        lastZIndex: 0,
      };
    });

    // Single batched read — never read offsetWidth/offsetHeight inside rAF
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

    const topLimit = Math.max(108, topRect.bottom - appRect.top + 22);
    const bottomLimit = Math.min(this.viewportH - 180, bottomRect.top - appRect.top - 24);

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
      this.focusTargetId = null;
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
      const dt = Math.max(4, now - this.dragOrigin.t);
      const dx = e.clientX - this.dragOrigin.x;
      const dy = e.clientY - this.dragOrigin.y;
      const dYaw = dx * 0.0062;
      const dPitch = dy * 0.0062;

      applyScreenRotation(this.rotM, dYaw, dPitch);
      applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);

      // Exponential smoothing on velocity so 120Hz touch samples feel silky
      const instX = Math.max(-0.0085, Math.min(0.0085, dYaw / dt));
      const instY = Math.max(-0.0085, Math.min(0.0085, dPitch / dt));
      this.velX = this.velX * 0.35 + instX * 0.65;
      this.velY = this.velY * 0.35 + instY * 0.65;

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

    const dt = Math.min(34, Math.max(4, now - (this.lastTime || now - 16)));
    this.lastTime = now;

    const w = this.viewportW;
    const h = this.viewportH;
    // Cap canvas DPR at 1.5 — visually indistinguishable for soft nebula/stars, 2x faster on QHD 120Hz phones
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const canvas = this.opts.canvasEl;
    const targetW = Math.round(w * dpr);
    const targetH = Math.round(h * dpr);
    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width = targetW;
      canvas.height = targetH;
    }

    // Frame-rate independent trackball physics (identical silky feel on 60Hz, 90Hz & 120Hz!)
    if (!this.isDragging) {
      if (this.focusTargetId) {
        const targetNode = this.tagNodes.find((n) => n.tag.id === this.focusTargetId);
        if (targetNode) {
          const [tx, ty, tz] = projectMat(this.rotM, targetNode.bx, targetNode.by, targetNode.bz);
          // Steer target toward (0, 0, +1)
          const gain = Math.min(0.18, dt * 0.0085);
          const dYaw = (tz < 0 ? (tx >= 0 ? -1 : 1) * 1.15 : -tx) * gain;
          const dPitch = -ty * gain;
          applyScreenRotation(this.rotM, dYaw, dPitch);
          applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);
          if (tz > 0.96 && Math.abs(tx) < 0.05 && Math.abs(ty) < 0.05) {
            this.focusTargetId = null;
          }
        } else {
          this.focusTargetId = null;
        }
      } else if (!this.paused) {
        const drift = this.hoveredTagId ? 0.000035 : 0.00014;
        const stepYaw = this.velX * dt + drift * dt;
        const stepPitch = this.velY * dt;
        applyScreenRotation(this.rotM, stepYaw, stepPitch);
        applyScreenRotation(this.starM, stepYaw * 0.35, stepPitch * 0.35);
        const decay = Math.pow(0.935, dt / 16.667);
        this.velX *= decay;
        this.velY *= decay;
      }
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
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    // 1. Parallax 3D Starfield (using pre-rendered sparkle sprite & fast fillRect dots)
    const starSpan = Math.max(w, h) * 0.46;
    ctx.fillStyle = '#f2ecff';
    for (let i = 0; i < this.stars.length; i++) {
      const s = this.stars[i];
      const [xx, yy] = projectMat(this.starM, s.x, s.y, s.z);
      const sxPos = centerX + xx * starSpan;
      const syPos = centerY + yy * starSpan * 0.95;
      if (sxPos < -12 || sxPos > w + 12 || syPos < -12 || syPos > h + 12) continue;

      const tw = 0.25 + 0.65 * (0.5 + 0.5 * Math.sin(now * s.speed + s.twinkle));
      if (s.sparkle) {
        const dim = s.size * 7.2;
        ctx.globalAlpha = tw * 0.85;
        ctx.drawImage(this.sparkSprite, sxPos - dim * 0.5, syPos - dim * 0.5, dim, dim);
      } else {
        const d = s.size * 1.8;
        ctx.globalAlpha = tw * 0.72;
        ctx.fillRect(sxPos - d * 0.5, syPos - d * 0.5, d, d);
      }
    }

    // 2. 3D Rotating Nebula Gas Clouds (Pre-rendered GPU sprites + 'lighter' additive blend)
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    const breath = now * 0.0005;
    const cloudScale = Math.min(w, h) < 480 ? 0.76 : 1.06;

    for (let i = 0; i < this.nebulaPuffs.length; i++) {
      const p = this.nebulaPuffs[i];
      const [xx, yy, depth] = projectMat(this.rotM, p.x, p.y, p.z);
      const px = centerX + xx * radiusX * 1.12;
      const py = centerY + yy * radiusY * 1.15;
      const depthNorm = (depth + 1) * 0.5;
      const r = p.radius * cloudScale * (0.86 + 0.24 * depthNorm) * (0.94 + 0.06 * Math.sin(breath + p.phase));
      ctx.globalAlpha = p.alpha * (0.52 + 0.48 * depthNorm);
      ctx.drawImage(this.puffSprites[p.spriteIdx], px - r, py - r, r * 2, r * 2);
    }
    ctx.restore();
    ctx.globalAlpha = 1;

    // 3. Project 3D Tags — Pure GPU Transform + Opacity (ZERO blur filter / ZERO backdrop-filter!)
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
    projected.sort((a, b) => b.depth - a.depth);

    const lerpK = Math.min(1, 1 - Math.pow(0.88, dt / 16.667));

    for (let i = 0; i < projected.length; i++) {
      const p = projected[i];
      const node = p.node;
      const el = node.el;
      const isSel = this.opts.isSelected(node.tag.id);

      const ew = node.width * p.scale;
      const eh = node.height * p.scale;

      const px = Math.max(ew * 0.5 + 12, Math.min(w - ew * 0.5 - 12, p.x));
      const py = Math.max(this.safeTop, Math.min(this.safeBottom, p.y));

      let targetOpacity = smoothstep(-0.14, 0.52, p.depth);

      const tooClose =
        !isSel &&
        frontOccupied.some(
          (q) => Math.abs(q.x - px) < (q.w + ew) * 0.44 && Math.abs(q.y - py) < (q.h + eh) * 0.56
        );
      if (tooClose) {
        targetOpacity *= 0.16;
      }

      if (isSel) {
        targetOpacity = Math.max(0.84, 0.74 + 0.26 * ((p.depth + 1) * 0.5));
      }

      node.curOpacity += (targetOpacity - node.curOpacity) * lerpK;

      if (node.curOpacity < 0.025 && !isSel) {
        if (!node.hidden) {
          node.hidden = true;
          el.style.visibility = 'hidden';
          el.style.pointerEvents = 'none';
          node.lastPointerAuto = false;
        }
        continue;
      }

      if (node.hidden) {
        node.hidden = false;
        el.style.visibility = 'visible';
      }

      el.style.transform = `translate3d(${px.toFixed(1)}px, ${py.toFixed(1)}px, 0) translate(-50%, -50%) scale(${p.scale.toFixed(3)})`;
      el.style.opacity = node.curOpacity.toFixed(3);

      const nextZ = isSel ? 220 : Math.round((p.depth + 1) * 90);
      if (nextZ !== node.lastZIndex) {
        node.lastZIndex = nextZ;
        el.style.zIndex = String(nextZ);
      }

      const nextAuto = node.curOpacity > 0.32;
      if (nextAuto !== node.lastPointerAuto) {
        node.lastPointerAuto = nextAuto;
        el.style.pointerEvents = nextAuto ? 'auto' : 'none';
      }

      if (node.curOpacity > 0.38) frontOccupied.push({ x: px, y: py, w: ew, h: eh });
    }

    this.scheduleFrame();
  };
}
