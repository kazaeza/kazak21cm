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
  sparkleIdx: number; // -1 = dot, 0..2 = colored diffraction star
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
  hidden: boolean;
  lastPointerAuto: boolean;
  lastZIndex: number;
  screenX: number;
  screenY: number;
  depth: number;
}

// Rich multi-hue JWST / Hubble emission nebula palette: [innerCoreRGB, outerWispRGB]
const NEBULA_PALETTE: [[number, number, number], [number, number, number]][] = [
  [[232, 165, 255], [142, 68, 245]],  // electric violet -> deep amethyst
  [[255, 168, 206], [236, 62, 138]],  // luminous rose -> magenta crimson
  [[146, 228, 255], [48, 128, 246]],  // icy cyan -> sapphire blue
  [[255, 204, 146], [244, 108, 68]],  // golden amber -> warm coral
  [[142, 248, 214], [28, 178, 148]],  // aurora mint -> deep teal
  [[192, 178, 255], [88, 76, 232]],   // starlight lilac -> cosmic indigo
];

const STAR_TINTS = ['#fff8ff', '#e4f0ff', '#ffe9d6', '#f2e2ff', '#daf7ff'];

function escapeHtml(s: string): string {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] || c));
}

function extractColors(glow: string): [string, string] {
  const matches = glow.match(/#[0-9a-fA-F]{6}/g);
  if (matches && matches.length >= 2) return [matches[0], matches[1]];
  if (matches && matches.length === 1) return [matches[0], '#87b8ff'];
  return ['#c79bff', '#87b8ff'];
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

// Pre-render multi-lobe organic volumetric cloud sprite ONCE (non-spherical realistic gas wisps!)
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
  // Sub-lobes create organic asymmetric wisps instead of plain flat circles
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

// Pre-render dark cosmic dust lane sprite ONCE to carve dramatic Hubble-like depth contrast
function createDustSprite(): HTMLCanvasElement {
  const sz = 160;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (g) {
    const h = sz * 0.5;
    const grad = g.createRadialGradient(h, h, h * 0.08, h, h, h);
    grad.addColorStop(0, 'rgba(5, 4, 12, 0.88)');
    grad.addColorStop(0.52, 'rgba(5, 4, 12, 0.42)');
    grad.addColorStop(1, 'rgba(5, 4, 12, 0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, sz, sz);
  }
  return c;
}

// Pre-render crisp JWST/Hubble diffraction star sprite with glowing halo
function createDiffractionStarSprite(tint: [number, number, number]): HTMLCanvasElement {
  const sz = 48;
  const c = document.createElement('canvas');
  c.width = sz;
  c.height = sz;
  const g = c.getContext('2d');
  if (g) {
    const h = sz * 0.5;
    // Soft halo
    const halo = g.createRadialGradient(h, h, 0.5, h, h, h * 0.82);
    halo.addColorStop(0, 'rgba(255, 255, 255, 1)');
    halo.addColorStop(0.22, `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0.72)`);
    halo.addColorStop(0.58, `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0.16)`);
    halo.addColorStop(1, `rgba(${tint[0]}, ${tint[1]}, ${tint[2]}, 0)`);
    g.fillStyle = halo;
    g.fillRect(0, 0, sz, sz);

    // Crisp 4-point diffraction spikes + delicate diagonal micro-rays
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

    // Subtle 45deg secondary spikes
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

  // Dynamic reactive color tint from currently selected tag
  private tintR = 192;
  private tintG = 140;
  private tintB = 255;
  private targetTintR = 192;
  private targetTintG = 140;
  private targetTintB = 255;
  private hasSelectionAura = false;
  private auraStrength = 0;
  private warpBoost = 0; // 0..1 hyperdrive surge when "Заебенить" is pressed!

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
  private nextMeteorTime = 3500;
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
      createDiffractionStarSprite([235, 195, 255]),
      createDiffractionStarSprite([180, 225, 255]),
      createDiffractionStarSprite([255, 210, 185]),
    ];

    applyScreenRotation(this.rotM, 0.55, 0.12);

    // 15 anisotropic multi-lobe volumetric clouds arranged along a galactic spiral band
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
        rotSpeed: (i % 2 === 0 ? 1 : -1) * (0.00004 + (i % 3) * 0.00002),
        spriteIdx: i % NEBULA_PALETTE.length,
        phase: (i * 1.13) % (Math.PI * 2),
        alpha: 0.22 + (i % 3) * 0.06,
      };
    });

    // 5 dark cosmic dust lanes that carve rich shadow pockets through the glowing gas
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

    // Deterministic multi-depth cosmic starfield (115 crisp stars + 14 diffraction stars)
    let seed = 4219;
    const rand = () => {
      seed = (seed * 16807) % 2147483647;
      return (seed - 1) / 2147483646;
    };

    this.stars = Array.from({ length: 115 }, (_, idx) => {
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
    this.focusTargetId = null;
    this.velX = vx;
    this.velY = vy;
  }

  // Triggered when user slams "Заебенить": surges the nebula core & spins into hyperdrive!
  public triggerWarpPulse(): void {
    this.warpBoost = 1;
    this.velX = (this.velX >= 0 ? 1 : -1) * 0.0072;
    this.velY = -0.0018;
  }

  // Updates reactive nebula core color to match the selected vibe tag
  public setAccentGlow(glow: string | null): void {
    if (!glow) {
      this.hasSelectionAura = false;
      this.targetTintR = 192;
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

  // Returns screen coordinates of the currently selected tag (if visible) for FX targeting
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
        screenX: 0,
        screenY: 0,
        depth: 0,
      };
    });

    // Single batched layout read — zero layout thrashing inside rAF
    for (const node of nodes) {
      node.width = node.el.offsetWidth || 130;
      node.height = node.el.offsetHeight || 44;
    }

    this.tagNodes = nodes;
    this.setAccentGlow(selectedGlow);
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

    // Smooth color & warp interpolation
    const colorLerp = Math.min(1, dt * 0.006);
    this.tintR += (this.targetTintR - this.tintR) * colorLerp;
    this.tintG += (this.targetTintG - this.tintG) * colorLerp;
    this.tintB += (this.targetTintB - this.tintB) * colorLerp;

    const targetAura = this.hasSelectionAura ? 1 : 0;
    this.auraStrength += (targetAura - this.auraStrength) * colorLerp;
    if (this.warpBoost > 0.001) {
      this.warpBoost *= Math.pow(0.89, dt / 16.667);
    } else {
      this.warpBoost = 0;
    }

    // Frame-rate independent 120Hz trackball rotation
    if (!this.isDragging) {
      if (this.focusTargetId) {
        const targetNode = this.tagNodes.find((n) => n.tag.id === this.focusTargetId);
        if (targetNode) {
          const [tx, ty, tz] = projectMat(this.rotM, targetNode.bx, targetNode.by, targetNode.bz);
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
        const dim = s.size * (9.5 + this.warpBoost * 6);
        ctx.globalAlpha = Math.min(1, tw * 0.92);
        ctx.drawImage(this.sparkSprites[s.sparkleIdx], sxPos - dim * 0.5, syPos - dim * 0.5, dim, dim);
      } else {
        const d = s.size * 1.75;
        ctx.fillStyle = s.tint;
        ctx.globalAlpha = tw * 0.76;
        ctx.fillRect(sxPos - d * 0.5, syPos - d * 0.5, d, d);
      }
    }

    // 1b. Occasional shooting comet streak across deep space
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
      grad.addColorStop(0.35, 'rgba(210, 180, 255, 0.55)');
      grad.addColorStop(1, 'rgba(210, 180, 255, 0)');
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
    const cloudScale = (Math.min(w, h) < 480 ? 0.78 : 1.06) * (1 + this.warpBoost * 0.16);

    // 2a. Reactive central core glow (pulses in the color of the selected vibe!)
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    const coreRad = Math.min(w, h) * (0.42 + 0.08 * this.auraStrength + 0.18 * this.warpBoost);
    const coreAlpha = 0.14 + 0.16 * this.auraStrength + 0.28 * this.warpBoost;
    const coreGrad = ctx.createRadialGradient(centerX, centerY, coreRad * 0.04, centerX, centerY, coreRad);
    coreGrad.addColorStop(0, `rgba(${Math.round(this.tintR)}, ${Math.round(this.tintG)}, ${Math.round(this.tintB)}, ${coreAlpha.toFixed(3)})`);
    coreGrad.addColorStop(0.5, `rgba(${Math.round(this.tintR * 0.7)}, ${Math.round(this.tintG * 0.65)}, ${Math.round(this.tintB)}, ${(coreAlpha * 0.38).toFixed(3)})`);
    coreGrad.addColorStop(1, 'rgba(0, 0, 0, 0)');
    ctx.fillStyle = coreGrad;
    ctx.beginPath();
    ctx.arc(centerX, centerY, coreRad, 0, Math.PI * 2);
    ctx.fill();

    // 2b. Stretched & rotated volumetric gas wisps
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
      ctx.globalAlpha = Math.min(1, c.alpha * (0.48 + 0.52 * depthNorm) * (1 + this.warpBoost * 0.45));
      ctx.drawImage(this.cloudSprites[c.spriteIdx], -rx, -ry, rx * 2, ry * 2);
      ctx.restore();
    }
    ctx.restore();

    // 2c. Subtle dark interstellar dust lanes (carves deep 3D relief into the bright nebula)
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

    // 3. Project 3D Tags + Constellation Resonance Filaments when a tag is selected
    const projected = this.tagNodes.map((node) => {
      const [xx, yy, depth] = projectMat(this.rotM, node.bx, node.by, node.bz);
      const depthNorm = (depth + 1) * 0.5;
      const persp = 0.80 + 0.26 * depthNorm;
      const scale = 0.72 + 0.34 * depthNorm;
      const sx = centerX + xx * radiusX * persp;
      const sy = centerY + yy * radiusY * persp;
      return {
        node,
        x: sx,
        y: sy,
        depth,
        scale,
      };
    });

    const frontOccupied: { x: number; y: number; w: number; h: number }[] = [];
    projected.sort((a, b) => b.depth - a.depth);

    const lerpK = Math.min(1, 1 - Math.pow(0.88, dt / 16.667));
    let selectedProj: { x: number; y: number; node: TagNode } | null = null;
    const visibleFrontNodes: { x: number; y: number; node: TagNode; op: number }[] = [];

    for (let i = 0; i < projected.length; i++) {
      const p = projected[i];
      const node = p.node;
      const el = node.el;
      const isSel = this.opts.isSelected(node.tag.id);

      const ew = node.width * p.scale;
      const eh = node.height * p.scale;

      const px = Math.max(ew * 0.5 + 12, Math.min(w - ew * 0.5 - 12, p.x));
      const py = Math.max(this.safeTop, Math.min(this.safeBottom, p.y));

      node.screenX = px;
      node.screenY = py;
      node.depth = p.depth;

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
        targetOpacity = Math.max(0.92, 0.82 + 0.18 * ((p.depth + 1) * 0.5));
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

      const selScale = isSel ? p.scale * 1.07 : p.scale;
      el.style.transform = `translate3d(${px.toFixed(1)}px, ${py.toFixed(1)}px, 0) translate(-50%, -50%) scale(${selScale.toFixed(3)})`;
      el.style.opacity = node.curOpacity.toFixed(3);

      const nextZ = isSel ? 230 : Math.round((p.depth + 1) * 90);
      if (nextZ !== node.lastZIndex) {
        node.lastZIndex = nextZ;
        el.style.zIndex = String(nextZ);
      }

      const nextAuto = node.curOpacity > 0.32;
      if (nextAuto !== node.lastPointerAuto) {
        node.lastPointerAuto = nextAuto;
        el.style.pointerEvents = nextAuto ? 'auto' : 'none';
      }

      if (node.curOpacity > 0.38) {
        frontOccupied.push({ x: px, y: py, w: ew, h: eh });
        if (!isSel) visibleFrontNodes.push({ x: px, y: py, node, op: node.curOpacity });
      }
      if (isSel) selectedProj = { x: px, y: py, node };
    }

    // 4. Luminous Constellation Ring & Star-Filaments around the Selected Tag!
    if (selectedProj) {
      const sx = selectedProj.x;
      const sy = selectedProj.y;
      const rCol = `${Math.round(this.tintR)}, ${Math.round(this.tintG)}, ${Math.round(this.tintB)}`;

      ctx.save();
      ctx.globalCompositeOperation = 'lighter';

      // Connect selected tag to 3 closest visible neighbor tags with delicate constellation filaments
      visibleFrontNodes.sort((a, b) => Math.hypot(a.x - sx, a.y - sy) - Math.hypot(b.x - sx, b.y - sy));
      const neighbors = visibleFrontNodes.slice(0, 3);
      for (const nb of neighbors) {
        const dist = Math.hypot(nb.x - sx, nb.y - sy);
        if (dist > 230) continue;
        const lineGrad = ctx.createLinearGradient(sx, sy, nb.x, nb.y);
        lineGrad.addColorStop(0, `rgba(${rCol}, 0.52)`);
        lineGrad.addColorStop(1, `rgba(${rCol}, 0.04)`);
        ctx.strokeStyle = lineGrad;
        ctx.lineWidth = 1.25;
        ctx.setLineDash([3, 5]);
        ctx.beginPath();
        ctx.moveTo(sx, sy);
        ctx.lineTo(nb.x, nb.y);
        ctx.stroke();
      }
      ctx.setLineDash([]);

      // Rotating orbital astrolabe halo behind the selected pill
      const orbitPulse = 1 + 0.05 * Math.sin(now * 0.004);
      const orx = (selectedProj.node.width * 0.56 + 16) * orbitPulse;
      const ory = (selectedProj.node.height * 0.58 + 12) * orbitPulse;
      ctx.strokeStyle = `rgba(${rCol}, 0.42)`;
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.ellipse(sx, sy, orx, ory, 0, 0, Math.PI * 2);
      ctx.stroke();

      // Two orbiting starlight satellites on the selected pill's ring
      for (let k = 0; k < 2; k++) {
        const oa = now * 0.0022 + k * Math.PI;
        const ox = sx + Math.cos(oa) * orx;
        const oy = sy + Math.sin(oa) * ory;
        ctx.drawImage(this.sparkSprites[0], ox - 9, oy - 9, 18, 18);
      }

      ctx.restore();
    }

    this.scheduleFrame();
  };
}
