import { haptic } from '../services/native';

// ==================== SHARED HELPERS & CONSTANTS ====================

export const MOTION_EASE = {
  vaul: 'cubic-bezier(0.32, 0.72, 0, 1)',
  outQuart: 'cubic-bezier(0.16, 1, 0.3, 1)',
  springSnappy: 'var(--ease-spring-snappy, cubic-bezier(0.22, 1.12, 0.36, 1))',
  springBounce: 'var(--ease-spring-bounce, cubic-bezier(0.34, 1.45, 0.64, 1))',
} as const;

export function clamp(value: number, min: number, max: number): number {
  return value < min ? min : value > max ? max : value;
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

// ==================== 1. VAUL BACKGROUND DEPTH MANAGER ====================

export type SheetId = 'results' | 'recipe' | (string & {});

export interface SheetDepthEntry {
  id: SheetId;
  getPanelEl: () => HTMLElement | null;
}

interface StagePose {
  scale: number;
  y: number;
  opacity: number;
  radius: number;
}

const STAGE_POSE_0: StagePose = { scale: 1, y: 0, opacity: 1, radius: 0 };
const STAGE_POSE_1: StagePose = { scale: 0.955, y: 8, opacity: 0.78, radius: 24 };
const STAGE_POSE_2: StagePose = { scale: 0.928, y: 4, opacity: 0.58, radius: 24 };

const PANEL_POSE_ACTIVE = { scale: 1, y: 0, opacity: 1 };
const PANEL_POSE_RECESSED = { scale: 0.95, y: -14, opacity: 0.65 };

export class SheetDepthManager {
  private stageEl: HTMLElement | null = null;
  private stack: SheetDepthEntry[] = [];
  private settleTimers = new WeakMap<HTMLElement, ReturnType<typeof setTimeout>>();

  constructor(stageEl?: HTMLElement | null) {
    if (stageEl) this.stageEl = stageEl;
  }

  public setStageEl(el: HTMLElement | null): void {
    this.stageEl = el;
    this.applyDepthState(true);
  }

  public getDepth(): number {
    return this.stack.length;
  }

  public push(entry: SheetDepthEntry): void {
    this.stack = this.stack.filter((item) => item.id !== entry.id);
    this.stack.push(entry);
    this.applyDepthState(false);
  }

  public pop(id: SheetId): void {
    const idx = this.stack.findIndex((item) => item.id === id);
    if (idx === -1) return;
    const [removed] = this.stack.splice(idx, 1);
    const removedPanel = removed.getPanelEl();
    if (removedPanel) {
      removedPanel.classList.remove('sheet-recessed');
    }
    this.applyDepthState(false);
  }

  /**
   * Live 120Hz interpolation while the top sheet is dragged downward.
   * progress: 0 = fully open, 1 = fully dismissed.
   */
  public setTopDragProgress(id: SheetId, progress: number): void {
    if (prefersReducedMotion()) return;
    const top = this.stack[this.stack.length - 1];
    if (!top || top.id !== id) return;

    const p = clamp(progress, 0, 1);
    const stage = this.resolveStage();

    if (this.stack.length === 1) {
      if (stage) {
        this.applyStagePose(
          stage,
          {
            scale: lerp(STAGE_POSE_1.scale, STAGE_POSE_0.scale, p),
            y: lerp(STAGE_POSE_1.y, STAGE_POSE_0.y, p),
            opacity: lerp(STAGE_POSE_1.opacity, STAGE_POSE_0.opacity, p),
            radius: lerp(STAGE_POSE_1.radius, STAGE_POSE_0.radius, p),
          },
          true
        );
      }
      return;
    }

    if (stage) {
      this.applyStagePose(
        stage,
        {
          scale: lerp(STAGE_POSE_2.scale, STAGE_POSE_1.scale, p),
          y: lerp(STAGE_POSE_2.y, STAGE_POSE_1.y, p),
          opacity: lerp(STAGE_POSE_2.opacity, STAGE_POSE_1.opacity, p),
          radius: STAGE_POSE_1.radius,
        },
        true
      );
    }

    const underlying = this.stack[this.stack.length - 2]?.getPanelEl();
    if (underlying) {
      this.clearSettleTimer(underlying);
      underlying.style.transition = 'none';
      underlying.style.willChange = 'transform, opacity';
      const s = lerp(PANEL_POSE_RECESSED.scale, PANEL_POSE_ACTIVE.scale, p);
      const y = lerp(PANEL_POSE_RECESSED.y, PANEL_POSE_ACTIVE.y, p);
      const o = lerp(PANEL_POSE_RECESSED.opacity, PANEL_POSE_ACTIVE.opacity, p);
      underlying.style.transform = `translate3d(0, ${y.toFixed(2)}px, 0) scale3d(${s.toFixed(4)}, ${s.toFixed(4)}, 1)`;
      underlying.style.opacity = o.toFixed(3);
    }
  }

  /**
   * Re-enable smooth Vaul transition and settle stage & underlying sheet to current stack depth.
   */
  public settleTopDrag(id: SheetId): void {
    const top = this.stack[this.stack.length - 1];
    if (!top || top.id !== id) return;
    this.applyDepthState(false);
  }

  private resolveStage(): HTMLElement | null {
    if (!this.stageEl && typeof document !== 'undefined') {
      this.stageEl = document.getElementById('app-stage');
    }
    return this.stageEl;
  }

  private applyDepthState(immediate: boolean): void {
    const stage = this.resolveStage();
    const depth = this.stack.length;

    if (stage) {
      stage.dataset.sheetDepth = String(Math.min(depth, 2));
      const pose = depth === 0 ? STAGE_POSE_0 : depth === 1 ? STAGE_POSE_1 : STAGE_POSE_2;
      this.applyStagePose(stage, pose, immediate || prefersReducedMotion());
    }

    // Update recessed state for all stacked sheets below the top sheet
    for (let i = 0; i < this.stack.length; i++) {
      const panel = this.stack[i].getPanelEl();
      if (!panel) continue;
      const isRecessed = i < this.stack.length - 1;
      panel.classList.toggle('sheet-recessed', isRecessed);
      this.clearSettleTimer(panel);

      if (prefersReducedMotion() || immediate) {
        panel.style.transition = 'none';
      } else {
        panel.style.transition = `transform 400ms ${MOTION_EASE.vaul}, opacity 340ms ${MOTION_EASE.vaul}`;
        panel.style.willChange = 'transform, opacity';
      }

      if (isRecessed) {
        panel.style.transform = `translate3d(0, ${PANEL_POSE_RECESSED.y}px, 0) scale3d(${PANEL_POSE_RECESSED.scale}, ${PANEL_POSE_RECESSED.scale}, 1)`;
        panel.style.opacity = String(PANEL_POSE_RECESSED.opacity);
      } else {
        panel.style.transform = '';
        panel.style.opacity = '';
      }

      this.scheduleCleanup(panel, 430, !isRecessed);
    }
  }

  private applyStagePose(stage: HTMLElement, pose: StagePose, immediate: boolean): void {
    this.clearSettleTimer(stage);

    if (immediate) {
      stage.style.transition = 'none';
    } else {
      stage.style.transition = `transform 420ms ${MOTION_EASE.vaul}, opacity 360ms ${MOTION_EASE.vaul}, border-radius 360ms ${MOTION_EASE.vaul}`;
      stage.style.willChange = 'transform, opacity';
    }

    if (pose.scale === 1 && pose.y === 0 && pose.opacity === 1 && pose.radius === 0) {
      stage.style.transform = 'translate3d(0, 0, 0) scale3d(1, 1, 1)';
      stage.style.opacity = '1';
      stage.style.borderRadius = '0px';
      this.scheduleCleanup(stage, immediate ? 0 : 440, true);
    } else {
      stage.style.transform = `translate3d(0, ${pose.y.toFixed(2)}px, 0) scale3d(${pose.scale.toFixed(4)}, ${pose.scale.toFixed(4)}, 1)`;
      stage.style.opacity = pose.opacity.toFixed(3);
      stage.style.borderRadius = `${pose.radius.toFixed(1)}px`;
      if (!immediate) {
        this.scheduleCleanup(stage, 440, false);
      }
    }
  }

  private clearSettleTimer(el: HTMLElement): void {
    const existing = this.settleTimers.get(el);
    if (existing) {
      clearTimeout(existing);
      this.settleTimers.delete(el);
    }
  }

  private scheduleCleanup(el: HTMLElement, delayMs: number, clearTransform: boolean): void {
    const run = () => {
      this.settleTimers.delete(el);
      el.style.willChange = '';
      el.style.transition = '';
      if (clearTransform) {
        el.style.transform = '';
        el.style.opacity = '';
        if (el === this.stageEl) {
          el.style.borderRadius = '';
        }
      }
    };

    if (delayMs <= 0) {
      run();
      return;
    }
    this.settleTimers.set(el, setTimeout(run, delayMs));
  }
}

export const sheetDepth = new SheetDepthManager();

// ==================== 2. VAUL-STYLE SHEET GESTURE CONTROLLER ====================

interface VelocitySample {
  y: number;
  t: number;
}

export interface SheetGestureOptions {
  id: SheetId;
  rootEl: HTMLElement;
  depthManager?: SheetDepthManager;
  getPanelEl: () => HTMLElement | null;
  getBackdropEl?: () => HTMLElement | null;
  getScrollEl?: () => HTMLElement | null;
  isDragHandle?: (target: HTMLElement) => boolean;
  onDismiss: () => void;
  maxUpwardStretch?: number;
  upwardResistance?: number;
  dismissVelocity?: number;
  dismissRatio?: number;
  projectionMs?: number;
  projectedRatio?: number;
}

const DRAG_SLOP_PX = 8;
const HANDLE_SLOP_PX = 6;
const VELOCITY_WINDOW_MS = 100;
const VELOCITY_BUFFER_SIZE = 6;

export class SheetGestureController {
  private opts: Required<
    Pick<
      SheetGestureOptions,
      | 'maxUpwardStretch'
      | 'upwardResistance'
      | 'dismissVelocity'
      | 'dismissRatio'
      | 'projectionMs'
      | 'projectedRatio'
    >
  > &
    SheetGestureOptions;

  private depth: SheetDepthManager;
  private pointerId: number | null = null;
  private tracking = false;
  private dragging = false;
  private startedInHandle = false;
  private startX = 0;
  private startY = 0;
  private currentDy = 0;
  private scrollTopAtStart = 0;
  private panelHeight = 400;
  private samples: VelocitySample[] = [];
  private closeTimer: ReturnType<typeof setTimeout> | null = null;
  private snapTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(opts: SheetGestureOptions) {
    this.opts = {
      maxUpwardStretch: 34,
      upwardResistance: 160,
      dismissVelocity: 0.55,
      dismissRatio: 0.3,
      projectionMs: 180,
      projectedRatio: 0.45,
      ...opts,
    };
    this.depth = opts.depthManager ?? sheetDepth;

    this.opts.rootEl.addEventListener('pointerdown', this.onPointerDown, { passive: true });
    this.opts.rootEl.addEventListener('pointermove', this.onPointerMove, { passive: false });
    this.opts.rootEl.addEventListener('pointerup', this.onPointerEnd);
    this.opts.rootEl.addEventListener('pointercancel', this.onPointerEnd);
  }

  public reset(): void {
    this.cancelTimers();
    this.resetDragState();
    const panel = this.opts.getPanelEl();
    const backdrop = this.opts.getBackdropEl?.() ?? null;
    if (panel) {
      panel.classList.remove('is-dragging');
      panel.style.transition = '';
      panel.style.transform = '';
      panel.style.willChange = '';
    }
    if (backdrop) {
      backdrop.style.transition = '';
      backdrop.style.opacity = '';
      backdrop.style.willChange = '';
    }
  }

  public animateClose(onComplete: () => void): void {
    this.cancelTimers();
    this.resetDragState();

    const panel = this.opts.getPanelEl();
    const backdrop = this.opts.getBackdropEl?.() ?? null;
    const dur = prefersReducedMotion() ? 110 : 280;

    this.depth.pop(this.opts.id);

    if (panel) {
      panel.classList.remove('is-dragging');
      panel.style.willChange = 'transform';
      panel.style.transition = `transform ${dur}ms ${MOTION_EASE.vaul}`;
      panel.style.transform = prefersReducedMotion()
        ? 'translate3d(0, 18px, 0)'
        : 'translate3d(0, 100%, 0)';
    }
    if (backdrop) {
      backdrop.style.willChange = 'opacity';
      backdrop.style.transition = `opacity ${Math.round(dur * 0.85)}ms ease-out`;
      backdrop.style.opacity = '0';
    }

    this.closeTimer = setTimeout(() => {
      this.closeTimer = null;
      if (panel) {
        panel.style.willChange = '';
        panel.style.transition = '';
        panel.style.transform = '';
      }
      if (backdrop) {
        backdrop.style.willChange = '';
        backdrop.style.transition = '';
        backdrop.style.opacity = '';
      }
      onComplete();
    }, dur + 16);
  }

  public destroy(): void {
    this.cancelTimers();
    this.opts.rootEl.removeEventListener('pointerdown', this.onPointerDown);
    this.opts.rootEl.removeEventListener('pointermove', this.onPointerMove);
    this.opts.rootEl.removeEventListener('pointerup', this.onPointerEnd);
    this.opts.rootEl.removeEventListener('pointercancel', this.onPointerEnd);
  }

  private onPointerDown = (e: PointerEvent): void => {
    if (e.button !== 0 || this.pointerId !== null || this.closeTimer !== null) return;
    const panel = this.opts.getPanelEl();
    if (!panel) return;

    const target = e.target as HTMLElement | null;
    if (!target || !panel.contains(target)) return;

    const inHandle = this.opts.isDragHandle ? this.opts.isDragHandle(target) : false;
    const scrollEl = this.opts.getScrollEl?.() ?? null;
    const inScroll = scrollEl ? scrollEl.contains(target) : false;

    if (!inHandle && !inScroll) return;

    this.pointerId = e.pointerId;
    this.tracking = true;
    this.dragging = false;
    this.startedInHandle = inHandle;
    this.startX = e.clientX;
    this.startY = e.clientY;
    this.currentDy = 0;
    this.scrollTopAtStart = scrollEl ? scrollEl.scrollTop : 0;
    this.panelHeight = Math.max(220, panel.getBoundingClientRect().height);
    this.samples = [{ y: e.clientY, t: performance.now() }];
  };

  private onPointerMove = (e: PointerEvent): void => {
    if (!this.tracking || e.pointerId !== this.pointerId) return;

    const dx = e.clientX - this.startX;
    const dy = e.clientY - this.startY;
    const absX = Math.abs(dx);
    const absY = Math.abs(dy);

    if (!this.dragging) {
      const slop = this.startedInHandle ? HANDLE_SLOP_PX : DRAG_SLOP_PX;
      if (absX < slop && absY < slop) return;

      // Yield horizontal gestures (e.g., similar-drinks carousel)
      if (absX > absY * (this.startedInHandle ? 1.35 : 1.0)) {
        this.resetDragState();
        return;
      }

      // In scrollable content: only hand off when already at top and pulling down
      if (!this.startedInHandle && (this.scrollTopAtStart > 1 || dy <= 0)) {
        this.resetDragState();
        return;
      }

      // Engage sheet drag
      this.dragging = true;
      this.cancelTimers();
      this.startY = e.clientY;
      this.currentDy = 0;
      this.samples = [{ y: e.clientY, t: performance.now() }];

      const panel = this.opts.getPanelEl();
      const backdrop = this.opts.getBackdropEl?.() ?? null;
      if (panel) {
        panel.classList.add('is-dragging');
        panel.style.transition = 'none';
        panel.style.willChange = 'transform';
      }
      if (backdrop) {
        backdrop.style.transition = 'none';
        backdrop.style.willChange = 'opacity';
      }

      try {
        this.opts.rootEl.setPointerCapture(e.pointerId);
      } catch {
        // Ignore if capture is unsupported or already released
      }
    }

    if (e.cancelable) e.preventDefault();

    const dragDy = e.clientY - this.startY;
    this.currentDy = dragDy;
    this.pushVelocitySample(e.clientY, performance.now());

    const visualY =
      dragDy < 0
        ? -(this.opts.maxUpwardStretch * Math.abs(dragDy)) /
          (Math.abs(dragDy) + this.opts.upwardResistance)
        : dragDy;

    const panel = this.opts.getPanelEl();
    if (panel) {
      panel.style.transform = `translate3d(0, ${visualY.toFixed(2)}px, 0)`;
    }

    const progress = clamp(dragDy / Math.max(180, this.panelHeight * 0.85), 0, 1);
    const backdrop = this.opts.getBackdropEl?.() ?? null;
    if (backdrop) {
      backdrop.style.opacity = (1 - progress * 0.94).toFixed(3);
    }
    this.depth.setTopDragProgress(this.opts.id, progress);
  };

  private onPointerEnd = (e: PointerEvent): void => {
    if (e.pointerId !== this.pointerId) return;

    if (this.opts.rootEl.hasPointerCapture?.(e.pointerId)) {
      try {
        this.opts.rootEl.releasePointerCapture(e.pointerId);
      } catch {
        // No-op
      }
    }

    if (!this.dragging) {
      this.resetDragState();
      return;
    }

    const dy = this.currentDy;
    const vy = this.computeVelocity();
    const projectedY = dy + vy * this.opts.projectionMs;
    const isCancel = e.type === 'pointercancel';

    const shouldDismiss =
      !isCancel &&
      dy > 0 &&
      (vy > this.opts.dismissVelocity ||
        dy > this.panelHeight * this.opts.dismissRatio ||
        projectedY > this.panelHeight * this.opts.projectedRatio);

    this.resetDragState();

    const panel = this.opts.getPanelEl();
    const backdrop = this.opts.getBackdropEl?.() ?? null;
    if (panel) panel.classList.remove('is-dragging');

    if (shouldDismiss) {
      haptic('light');
      const dur = prefersReducedMotion()
        ? 110
        : clamp(Math.round(300 - vy * 110), 150, 300);

      this.depth.pop(this.opts.id);

      if (panel) {
        panel.style.transition = `transform ${dur}ms ${MOTION_EASE.vaul}`;
        panel.style.transform = 'translate3d(0, 100%, 0)';
      }
      if (backdrop) {
        backdrop.style.transition = `opacity ${Math.round(dur * 0.85)}ms ease-out`;
        backdrop.style.opacity = '0';
      }

      this.closeTimer = setTimeout(() => {
        this.closeTimer = null;
        if (panel) {
          panel.style.willChange = '';
          panel.style.transition = '';
          panel.style.transform = '';
        }
        if (backdrop) {
          backdrop.style.willChange = '';
          backdrop.style.transition = '';
          backdrop.style.opacity = '';
        }
        this.opts.onDismiss();
      }, dur + 16);
      return;
    }

    // Spring-snap back to open resting state
    const snapDur = prefersReducedMotion() ? 100 : 380;
    if (panel) {
      panel.style.transition = `transform ${snapDur}ms ${MOTION_EASE.vaul}`;
      panel.style.transform = 'translate3d(0, 0, 0)';
    }
    if (backdrop) {
      backdrop.style.transition = `opacity ${Math.round(snapDur * 0.7)}ms ease-out`;
      backdrop.style.opacity = '1';
    }
    this.depth.settleTopDrag(this.opts.id);

    this.snapTimer = setTimeout(() => {
      this.snapTimer = null;
      if (panel) {
        panel.style.willChange = '';
        panel.style.transition = '';
        panel.style.transform = '';
      }
      if (backdrop) {
        backdrop.style.willChange = '';
        backdrop.style.transition = '';
        backdrop.style.opacity = '';
      }
    }, snapDur + 20);
  };

  private pushVelocitySample(y: number, t: number): void {
    this.samples.push({ y, t });
    while (this.samples.length > VELOCITY_BUFFER_SIZE) {
      this.samples.shift();
    }
  }

  private computeVelocity(): number {
    const now = performance.now();
    const recent = this.samples.filter((s) => now - s.t <= VELOCITY_WINDOW_MS);
    if (recent.length < 2) return 0;
    const first = recent[0];
    const last = recent[recent.length - 1];
    const dt = last.t - first.t;
    if (dt < 4) return 0;
    return (last.y - first.y) / dt; // px/ms
  }

  private resetDragState(): void {
    this.pointerId = null;
    this.tracking = false;
    this.dragging = false;
    this.startedInHandle = false;
    this.currentDy = 0;
    this.samples = [];
  }

  private cancelTimers(): void {
    if (this.closeTimer) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }
    if (this.snapTimer) {
      clearTimeout(this.snapTimer);
      this.snapTimer = null;
    }
  }
}

// ==================== 3. FLUID MORPHING PILL CONTROLLER ====================

export interface MorphingPillOptions {
  container: HTMLElement;
  itemSelector: string;
  /** Optional existing pill element (e.g. #dock-pill-indicator) */
  indicatorEl?: HTMLElement | null;
  /** Optional selector inside container to locate an existing pill element */
  indicatorSelector?: string;
  /** Class added when auto-creating a pill indicator */
  pillClass?: string;
  mode: 'fixed-width' | 'adaptive-width';
  autoScrollActive?: boolean;
}

export class MorphingPillController {
  private container: HTMLElement;
  private itemSelector: string;
  private mode: 'fixed-width' | 'adaptive-width';
  private autoScrollActive: boolean;
  private pillEl: HTMLElement;
  private ownsPill = false;
  private hasPlaced = false;
  private fixedWidthPx = 0;
  private fixedHeightPx = 0;
  private resizeObserver: ResizeObserver | null = null;
  private rafId: number | null = null;

  constructor(opts: MorphingPillOptions) {
    this.container = opts.container;
    this.itemSelector = opts.itemSelector;
    this.mode = opts.mode;
    this.autoScrollActive = Boolean(opts.autoScrollActive);

    const existing =
      opts.indicatorEl ??
      (opts.indicatorSelector
        ? this.container.querySelector<HTMLElement>(opts.indicatorSelector)
        : null);

    if (existing) {
      this.pillEl = existing;
    } else {
      const span = document.createElement('span');
      span.className = opts.pillClass ? `morph-pill ${opts.pillClass}` : 'morph-pill';
      span.setAttribute('aria-hidden', 'true');
      this.container.prepend(span);
      this.pillEl = span;
      this.ownsPill = true;
    }

    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => {
        this.fixedWidthPx = 0;
        this.fixedHeightPx = 0;
        this.sync(undefined, true);
      });
      this.resizeObserver.observe(this.container);
    }
  }

  /**
   * Move the shared pill to `targetEl` (or the container's current `.active` item).
   * Gracefully hides the pill if container/target is currently hidden (0×0) and
   * snaps into place cleanly once ResizeObserver or a subsequent sync() runs.
   */
  public sync(targetEl?: HTMLElement | null, immediate = false): void {
    const active =
      targetEl ??
      this.container.querySelector<HTMLElement>(`${this.itemSelector}.active`);

    if (!active) {
      this.pillEl.style.opacity = '0';
      return;
    }

    const rectW = active.offsetWidth;
    const rectH = active.offsetHeight;
    if (rectW <= 0 || rectH <= 0 || this.container.offsetWidth <= 0) {
      this.pillEl.style.opacity = '0';
      return;
    }

    const { x, y } = this.measureOffset(active);
    const shouldSnap = immediate || !this.hasPlaced || prefersReducedMotion();

    if (shouldSnap) {
      this.pillEl.style.transition = 'none';
    } else if (this.mode === 'fixed-width') {
      this.pillEl.style.transition = `transform 340ms ${MOTION_EASE.springSnappy}, opacity 180ms ease`;
    } else {
      this.pillEl.style.transition = `transform 340ms ${MOTION_EASE.springSnappy}, width 340ms ${MOTION_EASE.springSnappy}, opacity 180ms ease`;
    }

    if (this.mode === 'fixed-width') {
      if (this.fixedWidthPx !== rectW || this.fixedHeightPx !== rectH) {
        this.fixedWidthPx = rectW;
        this.fixedHeightPx = rectH;
        this.pillEl.style.width = `${rectW}px`;
        this.pillEl.style.height = `${rectH}px`;
      }
    } else {
      this.pillEl.style.width = `${rectW}px`;
      this.pillEl.style.height = `${rectH}px`;
    }

    this.pillEl.style.transform = `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0)`;
    this.pillEl.style.opacity = '1';
    this.hasPlaced = true;

    if (shouldSnap) {
      if (this.rafId !== null) cancelAnimationFrame(this.rafId);
      this.rafId = requestAnimationFrame(() => {
        this.rafId = null;
        this.pillEl.style.transition = '';
      });
    }

    if (this.autoScrollActive && !immediate) {
      try {
        active.scrollIntoView({
          behavior: prefersReducedMotion() ? 'auto' : 'smooth',
          inline: 'center',
          block: 'nearest',
        });
      } catch {
        // Fallback for older WebViews
      }
    }
  }

  public destroy(): void {
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    if (this.ownsPill && this.pillEl.parentElement === this.container) {
      this.pillEl.remove();
    }
  }

  private measureOffset(item: HTMLElement): { x: number; y: number } {
    if (item.offsetParent === this.container) {
      return { x: item.offsetLeft, y: item.offsetTop };
    }
    const cRect = this.container.getBoundingClientRect();
    const iRect = item.getBoundingClientRect();
    return {
      x: iRect.left - cRect.left + this.container.scrollLeft,
      y: iRect.top - cRect.top + this.container.scrollTop,
    };
  }
}

// ==================== 4. COORDINATED SCREEN CROSS-FLOW ====================

export interface TabTransitionOptions<T extends string> {
  initial: T;
  durationInMs?: number;
  durationOutMs?: number;
  travelInPx?: number;
  travelOutPx?: number;
  onLeaveStart?: (prev: T) => void;
  onEnterStart?: (next: T) => void;
}

export class TabTransitionController<T extends string> {
  private order: readonly T[];
  private screens: Record<T, HTMLElement>;
  private current: T;
  private durationInMs: number;
  private durationOutMs: number;
  private travelInPx: number;
  private travelOutPx: number;
  private onLeaveStart?: (prev: T) => void;
  private onEnterStart?: (next: T) => void;
  private seq = 0;
  private outTimer: ReturnType<typeof setTimeout> | null = null;
  private inTimer: ReturnType<typeof setTimeout> | null = null;
  private rafId: number | null = null;

  constructor(
    order: readonly T[],
    screens: Record<T, HTMLElement>,
    opts?: Partial<TabTransitionOptions<T>>
  ) {
    this.order = order;
    this.screens = screens;
    this.current = opts?.initial ?? order[0];
    this.durationInMs = opts?.durationInMs ?? 280;
    this.durationOutMs = opts?.durationOutMs ?? 180;
    this.travelInPx = opts?.travelInPx ?? 26;
    this.travelOutPx = opts?.travelOutPx ?? 20;
    this.onLeaveStart = opts?.onLeaveStart;
    this.onEnterStart = opts?.onEnterStart;
  }

  public getCurrent(): T {
    return this.current;
  }

  public switchTo(next: T, onSettled?: (active: T) => void): void {
    const prev = this.current;
    if (next === prev) {
      this.snapOnly(next);
      onSettled?.(next);
      return;
    }

    this.cancelPending();
    const mySeq = ++this.seq;
    this.current = next;

    const prevEl = this.screens[prev];
    const nextEl = this.screens[next];

    // Hide any third screen that may have been mid-flight during rapid tapping
    for (const key of this.order) {
      if (key !== prev && key !== next) {
        const el = this.screens[key];
        this.clearScreenStyles(el);
        el.classList.add('hidden');
      }
    }

    if (prefersReducedMotion()) {
      this.clearScreenStyles(prevEl);
      prevEl.classList.add('hidden');
      this.clearScreenStyles(nextEl);
      nextEl.classList.remove('hidden');
      this.onLeaveStart?.(prev);
      this.onEnterStart?.(next);
      onSettled?.(next);
      return;
    }

    const prevIdx = this.order.indexOf(prev);
    const nextIdx = this.order.indexOf(next);
    const dir = nextIdx >= prevIdx ? 1 : -1;

    // 1. Outgoing screen
    this.onLeaveStart?.(prev);
    prevEl.classList.add('tab-leaving');
    prevEl.style.pointerEvents = 'none';
    prevEl.style.zIndex = '1';
    prevEl.style.willChange = 'transform, opacity';
    prevEl.style.transition = `transform ${this.durationOutMs}ms ${MOTION_EASE.outQuart}, opacity ${this.durationOutMs}ms ease-out`;
    prevEl.style.transform = `translate3d(${(-dir * this.travelOutPx).toFixed(1)}px, 0, 0)`;
    prevEl.style.opacity = '0';

    // 2. Incoming screen — position off-axis then animate in on next frame
    this.onEnterStart?.(next);
    nextEl.classList.remove('hidden', 'tab-leaving');
    nextEl.classList.add('tab-entering');
    nextEl.style.pointerEvents = '';
    nextEl.style.zIndex = '2';
    nextEl.style.willChange = 'transform, opacity';
    nextEl.style.transition = 'none';
    nextEl.style.transform = `translate3d(${(dir * this.travelInPx).toFixed(1)}px, 0, 0)`;
    nextEl.style.opacity = '0';

    this.rafId = requestAnimationFrame(() => {
      if (mySeq !== this.seq) return;
      this.rafId = requestAnimationFrame(() => {
        this.rafId = null;
        if (mySeq !== this.seq) return;
        nextEl.style.transition = `transform ${this.durationInMs}ms ${MOTION_EASE.vaul}, opacity ${Math.round(this.durationInMs * 0.8)}ms ease-out`;
        nextEl.style.transform = 'translate3d(0, 0, 0)';
        nextEl.style.opacity = '1';
      });
    });

    this.outTimer = setTimeout(() => {
      this.outTimer = null;
      if (mySeq !== this.seq) return;
      this.clearScreenStyles(prevEl);
      prevEl.classList.add('hidden');
    }, this.durationOutMs + 24);

    this.inTimer = setTimeout(() => {
      this.inTimer = null;
      if (mySeq !== this.seq) return;
      this.clearScreenStyles(nextEl);
      onSettled?.(next);
    }, this.durationInMs + 32);
  }

  public destroy(): void {
    this.cancelPending();
  }

  private snapOnly(active: T): void {
    for (const key of this.order) {
      const el = this.screens[key];
      this.clearScreenStyles(el);
      el.classList.toggle('hidden', key !== active);
    }
  }

  private clearScreenStyles(el: HTMLElement): void {
    el.classList.remove('tab-leaving', 'tab-entering');
    el.style.pointerEvents = '';
    el.style.zIndex = '';
    el.style.willChange = '';
    el.style.transition = '';
    el.style.transform = '';
    el.style.opacity = '';
  }

  private cancelPending(): void {
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    if (this.outTimer) {
      clearTimeout(this.outTimer);
      this.outTimer = null;
    }
    if (this.inTimer) {
      clearTimeout(this.inTimer);
      this.inTimer = null;
    }
  }
}

// ==================== 5. SONNER-STYLE TOAST CONTROLLER ====================

export type ToastTone = 'default' | 'success' | 'info';

export interface ToastShowOptions {
  tone?: ToastTone;
  durationMs?: number;
}

export class ToastController {
  private rootEl: HTMLElement;
  private textEl: HTMLElement;
  private pillEl: HTMLElement;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;
  private bumpTimer: ReturnType<typeof setTimeout> | null = null;
  private isVisible = false;

  // Swipe-up-to-dismiss state
  private pointerId: number | null = null;
  private startY = 0;
  private lastDy = 0;

  constructor(rootEl: HTMLElement, textEl: HTMLElement) {
    this.rootEl = rootEl;
    this.textEl = textEl;
    this.pillEl = rootEl.querySelector<HTMLElement>('.toast-pill') ?? rootEl;

    this.rootEl.addEventListener('pointerdown', this.onPointerDown, { passive: true });
    this.rootEl.addEventListener('pointermove', this.onPointerMove, { passive: false });
    this.rootEl.addEventListener('pointerup', this.onPointerEnd);
    this.rootEl.addEventListener('pointercancel', this.onPointerEnd);
  }

  public show(message: string, opts?: ToastShowOptions): void {
    const tone = opts?.tone ?? 'default';
    const durationMs = opts?.durationMs ?? 1750;

    this.textEl.textContent = message;
    this.rootEl.dataset.tone = tone;
    this.pillEl.style.transition = '';
    this.pillEl.style.transform = '';

    if (this.isVisible && !prefersReducedMotion()) {
      this.pillEl.classList.remove('toast-bump');
      void this.pillEl.offsetWidth;
      this.pillEl.classList.add('toast-bump');
      if (this.bumpTimer) clearTimeout(this.bumpTimer);
      this.bumpTimer = setTimeout(() => {
        this.bumpTimer = null;
        this.pillEl.classList.remove('toast-bump');
      }, 240);
    } else {
      this.isVisible = true;
      this.rootEl.classList.add('visible');
    }

    if (this.hideTimer) clearTimeout(this.hideTimer);
    this.hideTimer = setTimeout(() => {
      this.dismiss();
    }, durationMs);
  }

  public dismiss(): void {
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
    this.isVisible = false;
    this.pointerId = null;
    this.rootEl.classList.remove('visible');
    this.pillEl.classList.remove('toast-bump');
    this.pillEl.style.transition = '';
    this.pillEl.style.transform = '';
  }

  public destroy(): void {
    this.dismiss();
    if (this.bumpTimer) {
      clearTimeout(this.bumpTimer);
      this.bumpTimer = null;
    }
    this.rootEl.removeEventListener('pointerdown', this.onPointerDown);
    this.rootEl.removeEventListener('pointermove', this.onPointerMove);
    this.rootEl.removeEventListener('pointerup', this.onPointerEnd);
    this.rootEl.removeEventListener('pointercancel', this.onPointerEnd);
  }

  private onPointerDown = (e: PointerEvent): void => {
    if (!this.isVisible || e.button !== 0) return;
    this.pointerId = e.pointerId;
    this.startY = e.clientY;
    this.lastDy = 0;
    this.pillEl.style.transition = 'none';
  };

  private onPointerMove = (e: PointerEvent): void => {
    if (this.pointerId !== e.pointerId || !this.isVisible) return;
    const dy = e.clientY - this.startY;
    this.lastDy = dy;
    if (dy < 0) {
      if (e.cancelable) e.preventDefault();
      this.pillEl.style.transform = `translate3d(0, ${dy.toFixed(1)}px, 0)`;
    } else {
      // Gentle downward rubber-band
      const damped = (10 * dy) / (dy + 40);
      this.pillEl.style.transform = `translate3d(0, ${damped.toFixed(1)}px, 0)`;
    }
  };

  private onPointerEnd = (e: PointerEvent): void => {
    if (this.pointerId !== e.pointerId) return;
    this.pointerId = null;

    if (this.lastDy < -14) {
      this.dismiss();
      return;
    }

    this.pillEl.style.transition = `transform 260ms ${MOTION_EASE.springSnappy}`;
    this.pillEl.style.transform = 'translate3d(0, 0, 0)';
  };
}

// ==================== 6. DELEGATED TACTILE PRESS ====================

const TACTILE_SELECTOR = [
  '[data-tactile]',
  '.recipe-card',
  '.bottle-tile',
  '.similar-card',
  '.nav-item',
  '.glass-pill',
  '.icon-circle-btn',
  '.btn-primary',
  '.btn-secondary',
  '.orb-cta',
  '.sugg-chip',
  '.confirm-cta-btn',
  '.preset-chip',
  '.lens-pill',
  '.filter-pill',
  '.mult-pill',
  '.recipe-tag-chip',
  '.mini-shelf-chip',
  '.shelf-filter-chip',
].join(', ');

const PRESS_CANCEL_PX = 8;

export function bindTactilePress(root: HTMLElement): () => void {
  let activeEl: HTMLElement | null = null;
  let activePointerId: number | null = null;
  let startX = 0;
  let startY = 0;

  const release = () => {
    if (activeEl) {
      activeEl.classList.remove('is-pressed');
      activeEl = null;
    }
    activePointerId = null;
  };

  const onPointerDown = (e: PointerEvent) => {
    if (e.button !== 0 || prefersReducedMotion()) return;
    const target = e.target as HTMLElement | null;
    const hit = target?.closest<HTMLElement>(TACTILE_SELECTOR);
    if (!hit) return;

    release();
    activeEl = hit;
    activePointerId = e.pointerId;
    startX = e.clientX;
    startY = e.clientY;
    hit.classList.add('is-pressed');
  };

  const onPointerMove = (e: PointerEvent) => {
    if (activePointerId !== e.pointerId || !activeEl) return;
    if (Math.hypot(e.clientX - startX, e.clientY - startY) > PRESS_CANCEL_PX) {
      release();
    }
  };

  const onPointerEnd = (e: PointerEvent) => {
    if (activePointerId !== e.pointerId) return;
    release();
  };

  root.addEventListener('pointerdown', onPointerDown, { passive: true });
  root.addEventListener('pointermove', onPointerMove, { passive: true });
  window.addEventListener('pointerup', onPointerEnd, { passive: true });
  window.addEventListener('pointercancel', onPointerEnd, { passive: true });

  return () => {
    release();
    root.removeEventListener('pointerdown', onPointerDown);
    root.removeEventListener('pointermove', onPointerMove);
    window.removeEventListener('pointerup', onPointerEnd);
    window.removeEventListener('pointercancel', onPointerEnd);
  };
}
