export interface BurstTriggerOptions {
  originX: number;
  originY: number;
  sourceX?: number | null;
  sourceY?: number | null;
  glow?: string | null;
  emoji?: string | null;
  intensity?: 'full' | 'mini';
}

interface SparkParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  gravity: number;
  drag: number;
  size: number;
  color: string;
  life: number;
  maxLife: number;
  kind: 'streak' | 'droplet' | 'diamond';
  rot: number;
  vRot: number;
}

interface EmojiParticle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  rot: number;
  vRot: number;
  scale: number;
  char: string;
  life: number;
  maxLife: number;
}

interface ShockRing {
  x: number;
  y: number;
  maxR: number;
  color: string;
  width: number;
  delay: number;
  life: number;
  maxLife: number;
}

interface PlasmaArc {
  sx: number;
  sy: number;
  tx: number;
  ty: number;
  cx: number;
  cy: number;
  color1: string;
  color2: string;
  life: number;
  maxLife: number;
}

export function parseGlowColors(glow?: string | null): [string, string] {
  if (!glow) return ['#f7b2d9', '#87b8ff'];
  const hits = glow.match(/#[0-9a-fA-F]{6}/g);
  if (hits && hits.length >= 2) return [hits[0], hits[1]];
  if (hits && hits.length === 1) return [hits[0], '#87b8ff'];
  return ['#f7b2d9', '#87b8ff'];
}

export class WarpBurstEngine {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private sparks: SparkParticle[] = [];
  private emojis: EmojiParticle[] = [];
  private rings: ShockRing[] = [];
  private arcs: PlasmaArc[] = [];
  private flashAlpha = 0;
  private flashX = 0;
  private flashY = 0;
  private flashColor = '#f7b2d9';
  private rafId: number | null = null;
  private lastTime = 0;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    const ctx = canvas.getContext('2d', { alpha: true });
    if (!ctx) throw new Error('Could not initialize 2D context for fx-canvas');
    this.ctx = ctx;
  }

  public trigger(opts: BurstTriggerOptions): void {
    const rect = this.canvas.getBoundingClientRect();
    const w = rect.width || window.innerWidth || 390;
    const h = rect.height || window.innerHeight || 780;
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const targetW = Math.round(w * dpr);
    const targetH = Math.round(h * dpr);
    if (this.canvas.width !== targetW || this.canvas.height !== targetH) {
      this.canvas.width = targetW;
      this.canvas.height = targetH;
    }

    const ox = opts.originX;
    const oy = opts.originY;
    const [c1, c2] = parseGlowColors(opts.glow);
    const isFull = (opts.intensity || 'full') === 'full';

    this.flashAlpha = isFull ? 0.95 : 0.55;
    this.flashX = ox;
    this.flashY = oy;
    this.flashColor = c1;

    const maxDiag = Math.hypot(w, h) * (isFull ? 0.88 : 0.45);

    // 1. Dual expanding chromatic shockwave rings
    this.rings.push({
      x: ox,
      y: oy,
      maxR: maxDiag,
      color: c1,
      width: isFull ? 5.5 : 3.5,
      delay: 0,
      life: 0,
      maxLife: isFull ? 560 : 400,
    });
    if (isFull) {
      this.rings.push({
        x: ox,
        y: oy,
        maxR: maxDiag * 0.72,
        color: c2,
        width: 3.8,
        delay: 55,
        life: 0,
        maxLife: 540,
      });
    }

    // 2. Converging plasma tether from the selected 3D nebula tag straight into the button!
    if (
      isFull &&
      typeof opts.sourceX === 'number' &&
      typeof opts.sourceY === 'number' &&
      Math.hypot(opts.sourceX - ox, opts.sourceY - oy) > 40
    ) {
      const midX = (opts.sourceX + ox) * 0.5 + (opts.sourceX > w * 0.5 ? 38 : -38);
      const midY = (opts.sourceY + oy) * 0.5 - 32;
      this.arcs.push({
        sx: opts.sourceX,
        sy: opts.sourceY,
        tx: ox,
        ty: oy,
        cx: midX,
        cy: midY,
        color1: c1,
        color2: c2,
        life: 0,
        maxLife: 280,
      });
    }

    // 3. Radial hyperdrive warp-streaks shooting across the screen
    const streakCount = isFull ? 32 : 14;
    for (let i = 0; i < streakCount; i++) {
      // Bias slightly upward (-PI..0) since the button sits near the bottom console
      const baseAngle = -Math.PI + (i / streakCount) * Math.PI * 2;
      const angle = baseAngle + (Math.random() - 0.5) * 0.18;
      const speed = (isFull ? 0.72 : 0.48) + Math.random() * 0.78;
      this.sparks.push({
        x: ox + Math.cos(angle) * 10,
        y: oy + Math.sin(angle) * 6,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed - (isFull ? 0.16 : 0.06),
        gravity: 0,
        drag: 0.985,
        size: 1.6 + Math.random() * 1.8,
        color: i % 3 === 0 ? '#ffffff' : i % 2 === 0 ? c1 : c2,
        life: 0,
        maxLife: 340 + Math.random() * 240,
        kind: 'streak',
        rot: 0,
        vRot: 0,
      });
    }

    // 4. Cocktail splash droplets & crystal ice diamonds erupting upward
    const splashCount = isFull ? 38 : 16;
    for (let i = 0; i < splashCount; i++) {
      const spread = -Math.PI * 0.5 + (Math.random() - 0.5) * Math.PI * 0.88;
      const speed = (isFull ? 0.42 : 0.28) + Math.random() * (isFull ? 0.68 : 0.36);
      const isDiamond = i % 3 === 0;
      this.sparks.push({
        x: ox + (Math.random() - 0.5) * 68,
        y: oy + (Math.random() - 0.5) * 12,
        vx: Math.cos(spread) * speed,
        vy: Math.sin(spread) * speed - 0.14,
        gravity: 0.00105,
        drag: 0.991,
        size: isDiamond ? 3.8 + Math.random() * 3.2 : 2.2 + Math.random() * 3.2,
        color: isDiamond ? '#e8f7ff' : i % 2 === 0 ? c1 : c2,
        life: 0,
        maxLife: 460 + Math.random() * 290,
        kind: isDiamond ? 'diamond' : 'droplet',
        rot: Math.random() * Math.PI * 2,
        vRot: (Math.random() - 0.5) * 0.014,
      });
    }

    // 5. Flying celebratory vibe emojis
    if (isFull) {
      const mainEmoji = opts.emoji || '🍸';
      const pool = [mainEmoji, '🍸', mainEmoji, '✨', mainEmoji, '🧊'];
      for (let i = 0; i < pool.length; i++) {
        const frac = (i / (pool.length - 1)) - 0.5;
        const ang = -Math.PI * 0.5 + frac * 1.15 + (Math.random() - 0.5) * 0.12;
        const spd = 0.52 + Math.random() * 0.34;
        this.emojis.push({
          x: ox + frac * 54,
          y: oy - 6,
          vx: Math.cos(ang) * spd,
          vy: Math.sin(ang) * spd - 0.12,
          rot: (Math.random() - 0.5) * 0.35,
          vRot: (Math.random() - 0.5) * 0.0045,
          scale: 0.85 + Math.random() * 0.35,
          char: pool[i],
          life: 0,
          maxLife: 640 + Math.random() * 140,
        });
      }
    }

    if (this.rafId === null) {
      this.lastTime = performance.now();
      this.rafId = requestAnimationFrame(this.frame);
    }
  }

  private frame = (now: number): void => {
    this.rafId = null;
    const dt = Math.min(34, Math.max(4, now - (this.lastTime || now - 16)));
    this.lastTime = now;

    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const w = this.canvas.width / dpr;
    const h = this.canvas.height / dpr;
    const ctx = this.ctx;

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    // 1. Central Ignition Flash
    if (this.flashAlpha > 0.01) {
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      const rad = Math.min(w, h) * (0.58 - this.flashAlpha * 0.18);
      const grad = ctx.createRadialGradient(this.flashX, this.flashY, 2, this.flashX, this.flashY, rad);
      grad.addColorStop(0, `rgba(255, 255, 255, ${(this.flashAlpha * 0.72).toFixed(3)})`);
      grad.addColorStop(0.32, `${this.flashColor}${Math.round(this.flashAlpha * 115).toString(16).padStart(2, '0')}`);
      grad.addColorStop(1, 'rgba(0, 0, 0, 0)');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);
      ctx.restore();
      this.flashAlpha *= Math.pow(0.82, dt / 16.667);
    } else {
      this.flashAlpha = 0;
    }

    // 2. Plasma Tether Arcs (Tag -> Button)
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    for (let i = this.arcs.length - 1; i >= 0; i--) {
      const a = this.arcs[i];
      a.life += dt;
      if (a.life >= a.maxLife) {
        this.arcs.splice(i, 1);
        continue;
      }
      const p = a.life / a.maxLife;
      const alpha = Math.sin(p * Math.PI);
      const grad = ctx.createLinearGradient(a.sx, a.sy, a.tx, a.ty);
      grad.addColorStop(0, a.color1);
      grad.addColorStop(0.5, '#ffffff');
      grad.addColorStop(1, a.color2);

      ctx.globalAlpha = alpha * 0.88;
      ctx.strokeStyle = grad;
      ctx.lineWidth = (1 - p * 0.5) * 4.2;
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(a.sx, a.sy);
      ctx.quadraticCurveTo(a.cx, a.cy, a.tx, a.ty);
      ctx.stroke();

      // Travelling energy pulse bead along quadratic Bezier
      const u = Math.min(1, p * 1.35);
      const inv = 1 - u;
      const bx = inv * inv * a.sx + 2 * inv * u * a.cx + u * u * a.tx;
      const by = inv * inv * a.sy + 2 * inv * u * a.cy + u * u * a.ty;
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.arc(bx, by, 5.5 * (1 - p * 0.4), 0, Math.PI * 2);
      ctx.fill();
    }

    // 3. Expanding Chromatic Shockwave Rings
    for (let i = this.rings.length - 1; i >= 0; i--) {
      const r = this.rings[i];
      r.life += dt;
      if (r.life < r.delay) continue;
      const activeLife = r.life - r.delay;
      if (activeLife >= r.maxLife) {
        this.rings.splice(i, 1);
        continue;
      }
      const p = activeLife / r.maxLife;
      const easeOut = 1 - Math.pow(1 - p, 3);
      const rad = Math.max(8, r.maxR * easeOut);
      const alpha = Math.pow(1 - p, 1.65);

      ctx.globalAlpha = alpha * 0.82;
      ctx.strokeStyle = r.color;
      ctx.lineWidth = Math.max(0.8, r.width * (1 - p * 0.72));
      ctx.beginPath();
      ctx.arc(r.x, r.y, rad, 0, Math.PI * 2);
      ctx.stroke();

      // Crisp inner white rim on primary shockwave
      if (i === 0 && p < 0.65) {
        ctx.globalAlpha = alpha * 0.55;
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1.3;
        ctx.beginPath();
        ctx.arc(r.x, r.y, Math.max(4, rad - 3), 0, Math.PI * 2);
        ctx.stroke();
      }
    }

    // 4. Hyperdrive Streaks, Liquid Droplets & Ice Diamonds
    const dragStep = dt / 16.667;
    for (let i = this.sparks.length - 1; i >= 0; i--) {
      const s = this.sparks[i];
      s.life += dt;
      if (s.life >= s.maxLife) {
        this.sparks.splice(i, 1);
        continue;
      }
      const damp = Math.pow(s.drag, dragStep);
      s.vx *= damp;
      s.vy = s.vy * damp + s.gravity * dt;
      s.x += s.vx * dt;
      s.y += s.vy * dt;
      s.rot += s.vRot * dt;

      const p = s.life / s.maxLife;
      const alpha = p < 0.12 ? p / 0.12 : Math.pow(1 - p, 1.35);
      ctx.globalAlpha = alpha;

      if (s.kind === 'streak') {
        const tailLen = (26 + Math.hypot(s.vx, s.vy) * 28) * (1 - p * 0.45);
        const mag = Math.hypot(s.vx, s.vy) || 1;
        const tx = s.x - (s.vx / mag) * tailLen;
        const ty = s.y - (s.vy / mag) * tailLen;
        ctx.strokeStyle = s.color;
        ctx.lineWidth = s.size * (1 - p * 0.4);
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(s.x, s.y);
        ctx.lineTo(tx, ty);
        ctx.stroke();
      } else if (s.kind === 'diamond') {
        const sz = s.size * (1 - p * 0.3);
        ctx.save();
        ctx.translate(s.x, s.y);
        ctx.rotate(s.rot);
        ctx.fillStyle = s.color;
        ctx.beginPath();
        ctx.moveTo(0, -sz);
        ctx.lineTo(sz * 0.72, 0);
        ctx.lineTo(0, sz);
        ctx.lineTo(-sz * 0.72, 0);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      } else {
        const r = s.size * (1 - p * 0.35);
        ctx.fillStyle = s.color;
        ctx.beginPath();
        ctx.arc(s.x, s.y, r, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.restore();

    // 5. Celebratory Floating Vibe Emojis
    if (this.emojis.length > 0) {
      ctx.save();
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      for (let i = this.emojis.length - 1; i >= 0; i--) {
        const em = this.emojis[i];
        em.life += dt;
        if (em.life >= em.maxLife) {
          this.emojis.splice(i, 1);
          continue;
        }
        const damp = Math.pow(0.986, dragStep);
        em.vx *= damp;
        em.vy = em.vy * damp + 0.00085 * dt;
        em.x += em.vx * dt;
        em.y += em.vy * dt;
        em.rot += em.vRot * dt;

        const p = em.life / em.maxLife;
        const popScale = p < 0.16 ? (p / 0.16) * 1.15 : 1.15 - p * 0.45;
        const alpha = p < 0.12 ? p / 0.12 : Math.pow(1 - p, 1.5);

        ctx.save();
        ctx.globalAlpha = Math.max(0, Math.min(1, alpha));
        ctx.translate(em.x, em.y);
        ctx.rotate(em.rot);
        ctx.font = `${Math.round(21 * em.scale * popScale)}px sans-serif`;
        ctx.fillText(em.char, 0, 0);
        ctx.restore();
      }
      ctx.restore();
    }

    const stillActive =
      this.flashAlpha > 0.01 ||
      this.arcs.length > 0 ||
      this.rings.length > 0 ||
      this.sparks.length > 0 ||
      this.emojis.length > 0;

    if (stillActive) {
      this.rafId = requestAnimationFrame(this.frame);
    } else {
      ctx.clearRect(0, 0, w, h);
    }
  };
}
