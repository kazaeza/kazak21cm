import type { LensId, VibeTag } from '../types';
import { TAGS } from '../data/tags';

interface NebulaCloud {
  x: number;
  y: number;
  z: number;
  rx: number;
  ry: number;
  angle: number;
  rotSpeed: number;
  spriteIdx: number;
  phase: number;
  alpha: number;
}

interface DustLane {
  x: number;
  y: number;
  z: number;
  rx: number;
  ry: number;
  angle: number;
  alpha: number;
}

interface Star {
  x: number;
  y: number;
  z: number;
  size: number;
  twinkle: number;
  speed: number;
  tint: string;
  sparkleIdx: number;
}

interface ShootingStar {
  x: number;
  y: number;
  vx: number;
  vy: number;
  len: number;
  life: number;
  maxLife: number;
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
  repelX: number;
  repelY: number;
  hidden: boolean;
  lastPointerAuto: boolean;
  lastZIndex: number;
  isFocused: boolean;
  screenX: number;
  screenY: number;
  depth: number;
}

// Rich cosmic neon emission palette: [innerCoreRGB, outerWispRGB]
const NEBULA_PALETTE: [[number, number, number], [number, number, number]][] = [
  [[232, 168, 255], [138, 72, 246]],  // nebula violet -> deep amethyst
  [[255, 166, 214], [224, 68, 152]],  // aurora rose -> cosmic magenta
  [[135, 224, 255], [56, 134, 248]],  // starlight cyan -> sapphire blue
  [[152, 248, 226], [36, 186, 178]],  // cosmic mint -> cerulean teal
  [[196, 182, 255], [96, 84, 238]],   // ultraviolet -> deep indigo
  [[255, 198, 180], [218, 96, 132]],  // warm stardust -> rosewood
];

const STAR_TINTS = ['#f9f7ff', '#d8f1ff', '#f3e5ff', '#cfe6ff', '#ffeaf6'];

function escapeHtml(s: string): string {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] || c));
}

function extractColors(glow: string): [string, string] {
  const matches = glow.match(/#[0-9a-fA-F]{6}/g);
  if (matches && matches.length >= 2) return [matches[0], matches[1]];
  if (matches && matches.length === 1) return [matches[0], '#7dd3fc'];
  return ['#c79bff', '#7dd3fc'];
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  if (h.length !== 6) return [199, 155, 255];
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16),
  ];
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

function createVolumetricCloudSprite(
  core: [number, number, number],
  outer: [number, number, number],
  variant: number
): HTMLCanvasElement {
  const sz = 192;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (!g) return c;

  const half = sz * 0.5;
  const lobes = [
    { dx: 0, dy: 0, r: 0.92, w: 0.62, inner: true },
    { dx: -0.22 * Math.cos(variant), dy: -0.14 * Math.sin(variant), r: 0.72, w: 0.44, inner: false },
    { dx: 0.24 * Math.sin(variant * 1.7), dy: 0.16 * Math.cos(variant * 1.3), r: 0.68, w: 0.40, inner: false },
    { dx: 0.08, dy: -0.19, r: 0.54, w: 0.34, inner: true },
  ];

  g.globalCompositeOperation = 'lighter';
  for (const l of lobes) {
    const cx = half + l.dx * half;
    const cy = half + l.dy * half;
    const rad = half * l.r;
    const col = l.inner ? core : outer;
    const grad = g.createRadialGradient(cx, cy, rad * 0.03, cx, cy, rad);
    grad.addColorStop(0, `rgba(${col[0]}, ${col[1]}, ${col[2]}, ${l.w})`);
    grad.addColorStop(0.38, `rgba(${outer[0]}, ${outer[1]}, ${outer[2]}, ${(l.w * 0.48).toFixed(3)})`);
    grad.addColorStop(0.74, `rgba(${outer[0]}, ${outer[1]}, ${outer[2]}, ${(l.w * 0.14).toFixed(3)})`);
    grad.addColorStop(1, `rgba(${outer[0]}, ${outer[1]}, ${outer[2]}, 0)`);
    g.fillStyle = grad;
    g.beginPath();
    g.arc(cx, cy, rad, 0, Math.PI * 2);
    g.fill();
  }
  return c;
}

function createDustSprite(): HTMLCanvasElement {
  const sz = 160;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (g) {
    const h = sz * 0.5;
    const grad = g.createRadialGradient(h, h, h * 0.08, h, h, h);
    grad.addColorStop(0, 'rgba(5, 5, 13, 0.88)');
    grad.addColorStop(0.52, 'rgba(5, 5, 13, 0.42)');
    grad.addColorStop(1, 'rgba(5, 5, 13, 0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, sz, sz);
  }
  return c;
}

function createDiffractionStarSprite(tint: [number, number, number]): HTMLCanvasElement {
  const sz = 48;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (g) {
    const h = sz * 0.5;
    const halo = g.createRadialGradient(h, h, 0.5, h, h, h * 0.82);
    halo.addColorStop(0, 'rgba(255, 255, 255, 1)');
    halo.addColorStop(0.22, `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0.72)`);
    halo.addColorStop(0.58, `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0.16)`);
    halo.addColorStop(1, `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0)`);
    g.fillStyle = halo;
    g.fillRect(0, 0, sz, sz);

    g.translate(h, h);
    const r = h * 0.92;
    g.fillStyle = 'rgba(255, 252, 255, 0.95)';
    g.beginPath();
    g.moveTo(0, -r);
    g.quadraticCurveTo(r * 0.08, -r * 0.08, r, 0);
    g.quadraticCurveTo(r * 0.08, r * 0.08, 0, r);
    g.quadraticCurveTo(-r * 0.08, r * 0.08, -r, 0);
    g.quadraticCurveTo(-r * 0.08, -r * 0.08, 0, -r);
    g.fill();

    g.rotate(Math.PI * 0.25);
    const r2 = h * 0.44;
    g.fillStyle = `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0.65)`;
    g.beginPath();
    g.moveTo(0, -r2);
    g.quadraticCurveTo(r2 * 0.1, -r2 * 0.1, r2, 0);
    g.quadraticCurveTo(r2 * 0.1, r2 * 0.1, 0, r2);
    g.quadraticCurveTo(-r2 * 0.1, r2 * 0.1, -r2, 0);
    g.quadraticCurveTo(-r2 * 0.1, -r2 * 0.1, 0, -r2);
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
  onFocusChange?: (tag: VibeTag | null) => void;
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
  private pointerPos: { x: number; y: number } | null = null;
  private dragOrigin: { id: number; x: number; y: number; startX: number; startY: number; t: number } | null = null;
  private hoveredTagId: string | null = null;
  private focusTargetId: string | null = null;
  private centeredTagId: string | null = null;
  private lastDetentTime = 0;
  private hasInteracted = false;

  private paused = false;
  private active = true;
  private rafId: number | null = null;
  private lastTime = 0;
  private frameCounter = 0;

  // Silky cloud dissolution (v4.0 "Заебенить" transition)
  private targetDisperse = 0;
  private disperseAmt = 0;

  // Dynamic reactive color tint from focused/selected tag or current podium cocktail
  private tintR = 178;
  private tintG = 140;
  private tintB = 255;
  private targetTintR = 178;
  private targetTintG = 140;
  private targetTintB = 255;
  private hasSelectionAura = false;
  private auraStrength = 0;
  private warpBoost = 0;

  private viewportW = 390;
  private viewportH = 780;
  private safeTop = 124;
  private safeBottom = 560;

  private cloudSprites: HTMLCanvasElement[];
  private dustSprite: HTMLCanvasElement;
  private sparkSprites: HTMLCanvasElement[];
  private clouds: NebulaCloud[];
  private dustLanes: DustLane[];
  private stars: Star[];
  private shootingStar: ShootingStar | null = null;
  private nextMeteorTime = 3800;
  private tagNodes: TagNode[] = [];

  constructor(opts: NebulaOptions) {
    this.opts = opts;
    const ctx = opts.canvasEl.getContext('2d', { alpha: true });
    if (!ctx) throw new Error('Could not acquire 2D context for nebula canvas');
    this.ctx = ctx;

    this.cloudSprites = NEBULA_PALETTE.map(([core, outer], idx) =>
      createVolumetricCloudSprite(core, outer, idx * 1.15 + 0.4)
    );
    this.dustSprite = createDustSprite();
    this.sparkSprites = [
      createDiffractionStarSprite([224, 185, 255]),
      createDiffractionStarSprite([145, 224, 255]),
      createDiffractionStarSprite([255, 192, 232]),
    ];

    applyScreenRotation(this.rotM, 0.52, 0.14);

    this.clouds = Array.from({ length: 15 }, (_, i) => {
      const y = (1 - (2 * (i + 0.5)) / 15) * 0.78;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const a = i * 2.399963;
      const dist = 0.28 + (i % 4) * 0.21;
      const baseR = 124 + (i % 5) * 28;
      return {
        x: r * Math.cos(a) * dist,
        y: y * dist,
        z: r * Math.sin(a) * dist,
        rx: baseR * (1.22 + (i % 3) * 0.18),
        ry: baseR * (0.72 + ((i + 1) % 3) * 0.12),
        angle: (i * 0.87) % (Math.PI * 2),
        rotSpeed: (i % 2 === 0 ? 1 : -1) * (0.00003 + (i % 3) * 0.000018),
        spriteIdx: i % NEBULA_PALETTE.length,
        phase: (i * 1.13) % (Math.PI * 2),
        alpha: 0.24 + (i % 3) * 0.06,
      };
    });

    this.dustLanes = Array.from({ length: 5 }, (_, i) => {
      const a = i * 1.256 + 0.4;
      const y = (i - 2) * 0.22;
      return {
        x: Math.cos(a) * 0.42,
        y,
        z: Math.sin(a) * 0.42,
        rx: 155 + (i % 2) * 35,
        ry: 68 + (i % 3) * 16,
        angle: -0.38 + i * 0.52,
        alpha: 0.42 + (i % 2) * 0.12,
      };
    });

    let seed = 4219;
    const rand = () => {
      seed = (seed * 16807) % 2147483647;
      return (seed - 1) / 2147483646;
    };

    this.stars = Array.from({ length: 118 }, (_, idx) => {
      const theta = rand() * Math.PI * 2;
      const phi = Math.acos(2 * rand() - 1);
      const rad = 0.85 + rand() * 1.15;
      const isSparkle = rand() < 0.13;
      return {
        x: rad * Math.sin(phi) * Math.cos(theta),
        y: rad * Math.cos(phi),
        z: rad * Math.sin(phi) * Math.sin(theta),
        size: isSparkle ? 0.95 + rand() * 1.35 : 0.65 + rand() * 1.3,
        twinkle: rand() * Math.PI * 2,
        speed: 0.0011 + rand() * 0.0024,
        tint: STAR_TINTS[idx % STAR_TINTS.length],
        sparkleIdx: isSparkle ? idx % 3 : -1,
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
    this.hasInteracted = true;
    this.focusTargetId = null;
    this.velX = vx;
    this.velY = vy;
  }

  /**
   * Silky v4.0 cloud dissolution: smoothly disperses star-nodes outward past the screen edges
   * while the volumetric nebula continues breathing behind the emerging Cocktail Podium.
   */
  public setDispersed(dispersed: boolean, immediate = false): void {
    this.targetDisperse = dispersed ? 1 : 0;
    if (immediate) {
      this.disperseAmt = this.targetDisperse;
    }
    this.opts.tagLayerEl.style.pointerEvents = dispersed ? 'none' : '';
  }

  /**
   * Gentle cosmic core pulse when opening the podium (zero harsh flicker).
   */
  public triggerWarpPulse(): void {
    this.warpBoost = 1;
  }

  /**
   * Smoothly morphs the nebula core glow to match a tag glow OR a cocktail's visual color.
   */
  public setAccentGlow(glow: string | null): void {
    if (!glow) {
      this.hasSelectionAura = false;
      this.targetTintR = 178;
      this.targetTintG = 140;
      this.targetTintB = 255;
      return;
    }
    const [c1] = extractColors(glow);
    const [r, g, b] = hexToRgb(c1);
    this.targetTintR = r;
    this.targetTintG = g;
    this.targetTintB = b;
    this.hasSelectionAura = true;
  }

  public getSelectedNodeScreenPos(): { x: number; y: number } | null {
    for (const node of this.tagNodes) {
      if (this.opts.isSelected(node.tag.id) && !node.hidden) {
        return { x: node.screenX, y: node.screenY };
      }
    }
    return null;
  }

  public focusTag(tagId: string): void {
    this.focusTargetId = tagId;
    this.velX = 0;
    this.velY = 0;
  }

  public nudgeRotation(dYaw: number, dPitch: number): void {
    this.hasInteracted = true;
    this.focusTargetId = null;
    applyScreenRotation(this.rotM, dYaw, dPitch);
    applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);
  }

  public syncSelectionStyles(): void {
    let selectedGlow: string | null = null;
    for (const node of this.tagNodes) {
      const isSel = this.opts.isSelected(node.tag.id);
      node.el.classList.toggle('selected', isSel);
      node.el.setAttribute('aria-pressed', isSel ? 'true' : 'false');
      if (isSel) selectedGlow = node.tag.glow;
    }
    this.setAccentGlow(selectedGlow);
  }

  public rebuildTags(lens: LensId): void {
    const layer = this.opts.tagLayerEl;
    layer.innerHTML = '';

    const visible = TAGS.filter((t) => lens === 'all' || t.lenses.includes(lens));
    const n = visible.length;

    let selectedGlow: string | null = null;

    const nodes: TagNode[] = visible.map((tag, i) => {
      const y = 1 - (2 * (i + 0.5)) / n;
      const r = Math.sqrt(Math.max(0, 1 - y * y));
      const a = i * Math.PI * (3 - Math.sqrt(5));

      const btn = document.createElement('button');
      btn.type = 'button';
      const isSel = this.opts.isSelected(tag.id);
      if (isSel) selectedGlow = tag.glow;

      const [c1, c2] = extractColors(tag.glow);
      btn.className = 'nebula-tag' + (isSel ? ' selected' : '');
      btn.style.setProperty('--glow-c', c1);
      btn.style.setProperty('--glow-c2', c2);
      btn.setAttribute('aria-pressed', isSel ? 'true' : 'false');
      btn.innerHTML = `
        <span class="tag-star-dot" aria-hidden="true"></span>
        <span class="tag-main">
          <span class="tag-emoji" aria-hidden="true">${escapeHtml(tag.emoji)}</span>
          <span class="tag-label-text">${escapeHtml(tag.label)}</span>
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
        this.hasInteracted = true;
        this.opts.onToggleTag(tag);
        // Smoothly pull a newly selected star into front optical focus!
        if (this.opts.isSelected(tag.id)) {
          this.focusTag(tag.id);
        }
      });

      layer.appendChild(btn);

      return {
        tag,
        el: btn,
        bx: r * Math.cos(a),
        by: y,
        bz: r * Math.sin(a),
        width: 118,
        height: 36,
        curOpacity: 0,
        repelX: 0,
        repelY: 0,
        hidden: false,
        lastPointerAuto: true,
        lastZIndex: 0,
        isFocused: false,
        screenX: 0,
        screenY: 0,
        depth: 0,
      };
    });

    for (const node of nodes) {
      node.width = node.el.offsetWidth || 118;
      node.height = node.el.offsetHeight || 36;
    }

    this.tagNodes = nodes;
    this.centeredTagId = null;
    this.setAccentGlow(selectedGlow);
    this.measureBounds();
  }

  public measureBounds(): void {
    const appRect = this.opts.appEl.getBoundingClientRect();
    this.viewportW = appRect.width || window.innerWidth || 390;
    this.viewportH = appRect.height || window.innerHeight || 780;

    const topRect = this.opts.topObstacleEl.getBoundingClientRect();
    const bottomRect = this.opts.bottomObstacleEl.getBoundingClientRect();

    const topLimit = Math.max(106, topRect.bottom - appRect.top + 20);
    const bottomLimit = Math.min(this.viewportH - 165, bottomRect.top - appRect.top - 22);

    this.safeTop = topLimit;
    this.safeBottom = Math.max(topLimit + 130, bottomLimit);
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
      if (this.targetDisperse > 0.5) return;
      this.isDragging = true;
      this.dragMoved = false;
      this.focusTargetId = null;
      this.hasInteracted = true;
      const appRect = this.opts.appEl.getBoundingClientRect();
      this.pointerPos = { x: e.clientX - appRect.left, y: e.clientY - appRect.top };
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
      const appRect = this.opts.appEl.getBoundingClientRect();
      this.pointerPos = { x: e.clientX - appRect.left, y: e.clientY - appRect.top };

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
      const dYaw = dx * 0.0064;
      const dPitch = dy * 0.0064;

      applyScreenRotation(this.rotM, dYaw, dPitch);
      applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);

      const instX = Math.max(-0.009, Math.min(0.009, dYaw / dt));
      const instY = Math.max(-0.009, Math.min(0.009, dPitch / dt));
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
      this.pointerPos = null;
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

  private updateShootingStar(now: number, dt: number, w: number, h: number): void {
    if (this.shootingStar) {
      this.shootingStar.x += this.shootingStar.vx * dt;
      this.shootingStar.y += this.shootingStar.vy * dt;
      this.shootingStar.life += dt;
      if (this.shootingStar.life >= this.shootingStar.maxLife) {
        this.shootingStar = null;
      }
      return;
    }
    if (now > this.nextMeteorTime && !this.paused) {
      this.nextMeteorTime = now + 4800 + Math.random() * 6200;
      const startX = w * (0.15 + Math.random() * 0.7);
      const startY = h * (0.08 + Math.random() * 0.28);
      const ang = (0.52 + Math.random() * 0.35) * (Math.random() < 0.5 ? 1 : Math.PI - 1);
      const speed = 0.55 + Math.random() * 0.25;
      this.shootingStar = {
        x: startX,
        y: startY,
        vx: Math.cos(ang) * speed,
        vy: Math.abs(Math.sin(ang)) * speed * 0.65 + 0.18,
        len: 68 + Math.random() * 44,
        life: 0,
        maxLife: 540 + Math.random() * 240,
      };
    }
  }

  private frame = (now: number): void => {
    this.rafId = null;
    if (!this.active) return;

    const dt = Math.min(34, Math.max(4, now - (this.lastTime || now - 16)));
    this.lastTime = now;

    const w = this.viewportW;
    const h = this.viewportH;
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const canvas = this.opts.canvasEl;
    const targetW = Math.round(w * dpr);
    const targetH = Math.round(h * dpr);
    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width = targetW;
      canvas.height = targetH;
    }

    // Smooth color, warp & disperse interpolation
    const colorLerp = Math.min(1, dt * 0.0065);
    this.tintR += (this.targetTintR - this.tintR) * colorLerp;
    this.tintG += (this.targetTintG - this.tintG) * colorLerp;
    this.tintB += (this.targetTintB - this.tintB) * colorLerp;

    const targetAura = this.hasSelectionAura ? 1 : 0;
    this.auraStrength += (targetAura - this.auraStrength) * colorLerp;

    const disperseLerp = Math.min(1, dt * (this.targetDisperse > 0.5 ? 0.011 : 0.0085));
    this.disperseAmt += (this.targetDisperse - this.disperseAmt) * disperseLerp;
    if (Math.abs(this.targetDisperse - this.disperseAmt) < 0.002) {
      this.disperseAmt = this.targetDisperse;
    }

    if (this.warpBoost > 0.001) {
      this.warpBoost *= Math.pow(0.91, dt / 16.667);
    } else {
      this.warpBoost = 0;
    }

    const centerX = w * 0.5;
    const centerY = (this.safeTop + this.safeBottom) * 0.5;
    const usableH = Math.max(210, this.safeBottom - this.safeTop);
    const radiusX = Math.min(w * 0.44, 320);
    const radiusY = Math.min(usableH * 0.48, 224);

    // Find current magnetic candidate near the front center before applying spring torque
    let magneticCandidate: { node: TagNode; tx: number; ty: number; tz: number; dist2: number } | null = null;
    for (let i = 0; i < this.tagNodes.length; i++) {
      const node = this.tagNodes[i];
      const [tx, ty, tz] = projectMat(this.rotM, node.bx, node.by, node.bz);
      if (tz <= 0.35) continue;
      const d2 = tx * tx + ty * ty;
      if (!magneticCandidate || d2 < magneticCandidate.dist2) {
        magneticCandidate = { node, tx, ty, tz, dist2: d2 };
      }
    }

    // Trackball Inertia + Watch Crown Magnetic Pull + Weightless Omnidirectional Sway
    if (!this.isDragging && this.targetDisperse < 0.5) {
      if (this.focusTargetId) {
        const targetNode = this.tagNodes.find((n) => n.tag.id === this.focusTargetId);
        if (targetNode) {
          const [tx, ty, tz] = projectMat(this.rotM, targetNode.bx, targetNode.by, targetNode.bz);
          const gain = Math.min(0.2, dt * 0.009);
          const dYaw = (tz < 0 ? (tx >= 0 ? -1 : 1) * 1.15 : -tx) * gain;
          const dPitch = -ty * gain;
          applyScreenRotation(this.rotM, dYaw, dPitch);
          applyScreenRotation(this.starM, dYaw * 0.35, dPitch * 0.35);
          if (tz > 0.97 && Math.abs(tx) < 0.045 && Math.abs(ty) < 0.045) {
            this.focusTargetId = null;
          }
        } else {
          this.focusTargetId = null;
        }
      } else if (!this.paused) {
        const speed = Math.hypot(this.velX, this.velY);
        if (speed > 0.00018) {
          const stepYaw = this.velX * dt;
          const stepPitch = this.velY * dt;
          applyScreenRotation(this.rotM, stepYaw, stepPitch);
          applyScreenRotation(this.starM, stepYaw * 0.35, stepPitch * 0.35);
          const decay = Math.pow(0.92, dt / 16.667);
          this.velX *= decay;
          this.velY *= decay;
        } else {
          this.velX = 0;
          this.velY = 0;
          if (magneticCandidate && magneticCandidate.dist2 < 0.22 && magneticCandidate.dist2 > 0.0012 && !this.hoveredTagId) {
            // Soft Watch-Crown magnetic lock onto the center star
            const magGain = Math.min(0.11, dt * 0.0046);
            const mYaw = -magneticCandidate.tx * magGain;
            const mPitch = -magneticCandidate.ty * magGain;
            applyScreenRotation(this.rotM, mYaw, mPitch);
            applyScreenRotation(this.starM, mYaw * 0.28, mPitch * 0.28);
          } else {
            // Weightless deep-space zero-gravity sway (never marches endlessly sideways!)
            const swayYaw = Math.sin(now * 0.00024) * 0.000034 * dt;
            const swayPitch = Math.cos(now * 0.00019) * 0.000024 * dt;
            applyScreenRotation(this.rotM, swayYaw, swayPitch);
            applyScreenRotation(this.starM, swayYaw * 0.35, swayPitch * 0.35);
          }
        }
      }
    }

    if (++this.frameCounter % 120 === 0) {
      orthonormalize(this.rotM);
      orthonormalize(this.starM);
    }

    const ctx = this.ctx;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    // 1. Deep-Space Parallax Starfield + JWST Diffraction Spikes
    const starSpan = Math.max(w, h) * 0.48;
    for (let i = 0; i < this.stars.length; i++) {
      const s = this.stars[i];
      const [xx, yy, szDepth] = projectMat(this.starM, s.x, s.y, s.z);
      const sxPos = centerX + xx * starSpan;
      const syPos = centerY + yy * starSpan * 0.95;
      if (sxPos < -20 || sxPos > w + 20 || syPos < -20 || syPos > h + 20) continue;

      const depthFactor = 0.55 + 0.45 * ((szDepth + 1) * 0.5);
      const tw = (0.28 + 0.72 * (0.5 + 0.5 * Math.sin(now * s.speed + s.twinkle))) * depthFactor;
      if (s.sparkleIdx >= 0) {
        const dim = s.size * (9.5 + this.warpBoost * 4);
        ctx.globalAlpha = Math.min(1, tw * 0.9);
        ctx.drawImage(this.sparkSprites[s.sparkleIdx], sxPos - dim * 0.5, syPos - dim * 0.5, dim, dim);
      } else {
        const d = s.size * 1.7;
        ctx.fillStyle = s.tint;
        ctx.globalAlpha = tw * 0.74;
        ctx.fillRect(sxPos - d * 0.5, syPos - d * 0.5, d, d);
      }
    }

    // 1b. Occasional shooting comet streak
    this.updateShootingStar(now, dt, w, h);
    if (this.shootingStar) {
      const st = this.shootingStar;
      const p = st.life / st.maxLife;
      const alpha = Math.sin(p * Math.PI) * 0.85;
      const mag = Math.hypot(st.vx, st.vy) || 1;
      const tx = st.x - (st.vx / mag) * st.len;
      const ty = st.y - (st.vy / mag) * st.len;
      ctx.save();
      ctx.globalAlpha = alpha;
      const grad = ctx.createLinearGradient(st.x, st.y, tx, ty);
      grad.addColorStop(0, 'rgba(255, 250, 255, 1)');
      grad.addColorStop(0.35, 'rgba(195, 175, 255, 0.55)');
      grad.addColorStop(1, 'rgba(135, 200, 255, 0)');
      ctx.strokeStyle = grad;
      ctx.lineWidth = 1.8;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(st.x, st.y);
      ctx.lineTo(tx, ty);
      ctx.stroke();
      ctx.restore();
    }

    // 2. Volumetric Anisotropic Nebula Clouds + Reactive Core + Dust Lanes
    const breath = now * 0.00048;
    const cloudScale = (Math.min(w, h) < 480 ? 0.82 : 1.08) * (1 + this.disperseAmt * 0.14 + this.warpBoost * 0.08);

    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    const coreRad = Math.min(w, h) * (0.44 + 0.1 * this.auraStrength + 0.12 * this.disperseAmt);
    const coreAlpha = 0.15 + 0.18 * this.auraStrength + 0.12 * this.disperseAmt + 0.16 * this.warpBoost;
    const coreGrad = ctx.createRadialGradient(centerX, centerY, coreRad * 0.04, centerX, centerY, coreRad);
    coreGrad.addColorStop(0, `rgba(${Math.round(this.tintR)}, ${Math.round(this.tintG)}, ${Math.round(this.tintB)}, ${coreAlpha.toFixed(3)})`);
    coreGrad.addColorStop(0.48, `rgba(${Math.round(this.tintR * 0.72)}, ${Math.round(this.tintG * 0.68)}, ${Math.round(this.tintB)}, ${(coreAlpha * 0.42).toFixed(3)})`);
    coreGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = coreGrad;
    ctx.beginPath();
    ctx.arc(centerX, centerY, coreRad, 0, Math.PI * 2);
    ctx.fill();

    for (let i = 0; i < this.clouds.length; i++) {
      const c = this.clouds[i];
      const [xx, yy, depth] = projectMat(this.rotM, c.x, c.y, c.z);
      const px = centerX + xx * radiusX * 1.14;
      const py = centerY + yy * radiusY * 1.16;
      const depthNorm = (depth + 1) * 0.5;
      const pulse = 0.94 + 0.06 * Math.sin(breath + c.phase);
      const rx = c.rx * cloudScale * (0.84 + 0.28 * depthNorm) * pulse;
      const ry = c.ry * cloudScale * (0.84 + 0.28 * depthNorm) * pulse;
      const rot = c.angle + now * c.rotSpeed + xx * 0.32;

      ctx.save();
      ctx.translate(px, py);
      ctx.rotate(rot);
      ctx.globalAlpha = Math.min(1, c.alpha * (0.48 + 0.52 * depthNorm) * (1 + this.disperseAmt * 0.22));
      ctx.drawImage(this.cloudSprites[c.spriteIdx], -rx, -ry, rx * 2, ry * 2);
      ctx.restore();
    }
    ctx.restore();

    ctx.save();
    ctx.globalCompositeOperation = 'source-over';
    for (let i = 0; i < this.dustLanes.length; i++) {
      const d = this.dustLanes[i];
      const [xx, yy, depth] = projectMat(this.rotM, d.x, d.y, d.z);
      if (depth < -0.25) continue;
      const px = centerX + xx * radiusX * 0.95;
      const py = centerY + yy * radiusY * 0.95;
      const rx = d.rx * cloudScale;
      const ry = d.ry * cloudScale;
      ctx.save();
      ctx.translate(px, py);
      ctx.rotate(d.angle + yy * 0.25);
      ctx.globalAlpha = d.alpha * smoothstep(-0.25, 0.65, depth);
      ctx.drawImage(this.dustSprite, -rx, -ry, rx * 2, ry * 2);
      ctx.restore();
    }
    ctx.restore();
    ctx.globalAlpha = 1;

    // 3. Project 3D Frameless Star-Nodes + Magnetic Watch-Crown Reticle + Neighbor Repulsion
    const projected = this.tagNodes.map((node) => {
      const [xx, yy, depth] = projectMat(this.rotM, node.bx, node.by, node.bz);
      const depthNorm = (depth + 1) * 0.5;
      const persp = 0.78 + 0.30 * depthNorm;
      const scale = 0.66 + 0.44 * depthNorm;
      const sx = centerX + xx * radiusX * persp;
      const sy = centerY + yy * radiusY * persp;
      const centerDist2 = (sx - centerX) * (sx - centerX) + (sy - centerY) * (sy - centerY);
      return {
        node,
        x: sx,
        y: sy,
        depth,
        scale,
        centerDist2,
      };
    });

    // Identify the center-focused star (Watch-Crown reticle)
    let centerFocusedNode: TagNode | null = null;
    let bestFocusDist2 = Infinity;
    for (let i = 0; i < projected.length; i++) {
      const p = projected[i];
      if (p.depth > 0.22 && p.centerDist2 < bestFocusDist2) {
        bestFocusDist2 = p.centerDist2;
        centerFocusedNode = p.node;
      }
    }

    const nextFocusedId = this.targetDisperse > 0.5 ? null : (centerFocusedNode?.tag.id ?? null);
    if (nextFocusedId !== this.centeredTagId) {
      this.centeredTagId = nextFocusedId;
      if (this.hasInteracted && nextFocusedId && now - this.lastDetentTime > 95) {
        this.lastDetentTime = now;
        this.opts.onFocusChange?.(centerFocusedNode?.tag ?? null);
      }
    }

    const primaryProj =
      projected.find((p) => this.opts.isSelected(p.node.tag.id)) ??
      (centerFocusedNode ? projected.find((p) => p.node === centerFocusedNode) : undefined) ??
      null;

    // If nothing is explicitly selected yet, subtly bias ambient nebula tint toward centered reticle star
    if (!this.opts.isSelected(this.centeredTagId || '') && centerFocusedNode && this.targetDisperse < 0.5) {
      const [c1] = extractColors(centerFocusedNode.tag.glow);
      const [r, g, b] = hexToRgb(c1);
      this.targetTintR = r;
      this.targetTintG = g;
      this.targetTintB = b;
      this.hasSelectionAura = true;
    }

    const frontOccupied: { x: number; y: number; w: number; h: number }[] = [];
    projected.sort((a, b) => b.depth - a.depth);

    const lerpK = Math.min(1, 1 - Math.pow(0.86, dt / 16.667));
    const disperseEase = this.disperseAmt * this.disperseAmt;
    let selectedProj: { x: number; y: number; node: TagNode } | null = null;
    const visibleFrontNodes: { x: number; y: number; node: TagNode; op: number }[] = [];

    for (let i = 0; i < projected.length; i++) {
      const p = projected[i];
      const node = p.node;
      const el = node.el;
      const isSel = this.opts.isSelected(node.tag.id);
      const isFocused = !this.targetDisperse && (isSel || node.tag.id === this.centeredTagId);

      // Update focused state for orbital reticle & smooth subtitle reveal
      if (isFocused !== node.isFocused) {
        node.isFocused = isFocused;
        el.classList.toggle('focused', isFocused);
      }

      // Neighbor repulsion ("соседи мягко расступаются") away from spotlight node & active thumb
      let targetRepelX = 0;
      let targetRepelY = 0;
      if (!isSel && !isFocused && primaryProj) {
        const dx = p.x - primaryProj.x;
        const dy = p.y - primaryProj.y;
        const d = Math.hypot(dx, dy);
        if (d > 1 && d < 165) {
          const push = (1 - d / 165) * 32;
          targetRepelX += (dx / d) * push;
          targetRepelY += (dy / d) * push * 0.85;
        }
      }
      if (this.isDragging && this.pointerPos && !isSel) {
        const dx = p.x - this.pointerPos.x;
        const dy = p.y - this.pointerPos.y;
        const d = Math.hypot(dx, dy);
        if (d > 1 && d < 128) {
          const push = (1 - d / 128) * 26;
          targetRepelX += (dx / d) * push;
          targetRepelY += (dy / d) * push;
        }
      }

      node.repelX += (targetRepelX - node.repelX) * lerpK;
      node.repelY += (targetRepelY - node.repelY) * lerpK;

      let baseX = p.x + node.repelX;
      let baseY = p.y + node.repelY;

      // Radial outward cloud dissolution when "Заебенить" is triggered
      if (disperseEase > 0.001) {
        const outDx = baseX - centerX;
        const outDy = baseY - centerY;
        const outDist = Math.hypot(outDx, outDy) || 1;
        const pushDist = (260 + outDist * 0.55) * disperseEase;
        baseX += (outDx / outDist) * pushDist;
        baseY += (outDy / outDist) * pushDist;
      }

      const ew = node.width * p.scale;
      const eh = node.height * p.scale;

      const px = this.disperseAmt > 0.01 ? baseX : Math.max(ew * 0.5 + 14, Math.min(w - ew * 0.5 - 14, baseX));
      const py = this.disperseAmt > 0.01 ? baseY : Math.max(this.safeTop, Math.min(this.safeBottom, baseY));

      node.screenX = px;
      node.screenY = py;
      node.depth = p.depth;

      let targetOpacity = smoothstep(-0.08, 0.56, p.depth);

      // Keep crisp breathing room between overlapping non-focused background stars
      const tooClose =
        !isSel &&
        !isFocused &&
        frontOccupied.some(
          (q) => Math.abs(q.x - px) < (q.w + ew) * 0.48 && Math.abs(q.y - py) < (q.h + eh) * 0.62
        );
      if (tooClose) {
        targetOpacity *= 0.14;
      }

      if (isFocused) {
        targetOpacity = Math.max(0.94, 0.86 + 0.14 * ((p.depth + 1) * 0.5));
      }

      targetOpacity *= 1 - disperseEase;
      node.curOpacity += (targetOpacity - node.curOpacity) * lerpK;

      if (node.curOpacity < 0.025 && (!isSel || this.disperseAmt > 0.85)) {
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

      const focusBoost = isSel ? 1.12 : isFocused ? 1.07 : 1;
      const finalScale = p.scale * focusBoost * (1 + disperseEase * 0.12);
      el.style.transform = `translate3d(${px.toFixed(1)}px, ${py.toFixed(1)}px, 0) translate(-50%, -50%) scale(${finalScale.toFixed(3)})`;
      el.style.opacity = node.curOpacity.toFixed(3);

      const nextZ = isSel ? 240 : isFocused ? 210 : Math.round((p.depth + 1) * 90);
      if (nextZ !== node.lastZIndex) {
        node.lastZIndex = nextZ;
        el.style.zIndex = String(nextZ);
      }

      const nextAuto = this.targetDisperse < 0.5 && node.curOpacity > 0.34;
      if (nextAuto !== node.lastPointerAuto) {
        node.lastPointerAuto = nextAuto;
        el.style.pointerEvents = nextAuto ? 'auto' : 'none';
      }

      if (node.curOpacity > 0.36) {
        frontOccupied.push({ x: px, y: py, w: ew, h: eh });
        if (!isSel && !isFocused) visibleFrontNodes.push({ x: px, y: py, node, op: node.curOpacity });
      }
      if (isSel || (!selectedProj && isFocused)) {
        selectedProj = { x: px, y: py, node };
      }
    }

    // 4. Delicate Constellation Links & Starlight Reticle Ring around Selected / Focused Star
    if (selectedProj && this.disperseAmt < 0.5) {
      const sx = selectedProj.x;
      const sy = selectedProj.y;
      const isSelectedNode = this.opts.isSelected(selectedProj.node.tag.id);
      const rCol = `${Math.round(this.tintR)}, ${Math.round(this.tintG)}, ${Math.round(this.tintB)}`;
      const reticleAlpha = (1 - this.disperseAmt * 2) * (isSelectedNode ? 0.58 : 0.32);

      ctx.save();
      ctx.globalCompositeOperation = 'lighter';

      visibleFrontNodes.sort((a, b) => Math.hypot(a.x - sx, a.y - sy) - Math.hypot(b.x - sx, b.y - sy));
      const neighbors = visibleFrontNodes.slice(0, 3);
      for (const nb of neighbors) {
        const dist = Math.hypot(nb.x - sx, nb.y - sy);
        if (dist > 235) continue;
        const lineGrad = ctx.createLinearGradient(sx, sy, nb.x, nb.y);
        lineGrad.addColorStop(0, `rgba(${rCol}, ${reticleAlpha.toFixed(2)})`);
        lineGrad.addColorStop(1, `rgba(${rCol}, 0.03)`);
        ctx.strokeStyle = lineGrad;
        ctx.lineWidth = 1.1;
        ctx.setLineDash([2, 6]);
        ctx.beginPath();
        ctx.moveTo(sx, sy);
        ctx.lineTo(nb.x, nb.y);
        ctx.stroke();
      }
      ctx.setLineDash([]);

      // Orbiting astrolabe focus halo + twin starlight satellites on selected star
      const orbitPulse = 1 + 0.04 * Math.sin(now * 0.004);
      const orx = (selectedProj.node.width * 0.56 + 18) * orbitPulse;
      const ory = (selectedProj.node.height * 0.62 + 12) * orbitPulse;
      ctx.strokeStyle = `rgba(${rCol}, ${(reticleAlpha * 0.72).toFixed(2)})`;
      ctx.lineWidth = isSelectedNode ? 1.25 : 0.9;
      ctx.beginPath();
      ctx.ellipse(sx, sy, orx, ory, 0, 0, Math.PI * 2);
      ctx.stroke();

      if (isSelectedNode) {
        for (let k = 0; k < 2; k++) {
          const oa = now * 0.0022 + k * Math.PI;
          const ox = sx + Math.cos(oa) * orx;
          const oy = sy + Math.sin(oa) * ory;
          ctx.drawImage(this.sparkSprites[0], ox - 9, oy - 9, 18, 18);
        }
      }

      ctx.restore();
    }

    this.scheduleFrame();
  };
}
