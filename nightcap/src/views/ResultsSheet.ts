import type { BottleId, MatchedRecipe, Recipe, VibeTag } from '../types';
import { BOTTLE_MAP } from '../data/bottles';
import { getShelfStatus } from '../data/matcher';
import { GLASS_LABELS, ICE_LABELS, METHOD_LABELS } from '../data/recipes';
import { renderCocktailGlass } from '../components/CocktailGlass';
import { renderRecipeCard } from '../components/RecipeCard';
import { icon } from '../components/icons';
import { escapeHtml, pluralize } from '../utils/html';
import { haptic } from '../services/native';
import { SheetGestureController, sheetDepth } from '../engine/motion';

export interface ResultsSheetCallbacks {
  getShelf: () => Set<BottleId>;
  isShelfOnly: () => boolean;
  isSaved: (id: string) => boolean;
  onToggleShelfOnly: () => void;
  onToggleSave: (id: string) => void;
  onOpenRecipe: (recipe: Recipe) => void;
  onHighlightRecipe?: (recipe: Recipe) => void;
  onClearTags: () => void;
  onClose: () => void;
}

export interface ResultsSheetOpenOptions {
  entrance?: 'warp' | 'standard';
}

const MAX_PODIUM_CARDS = 7;

const PODIUM_ROLE_BADGES = [
  '✨ Идеально сейчас',
  '☄️ Вариант #2',
  '🌌 Хит #3',
  '💫 Выбор #4',
  '🪐 Вайб #5',
  '⚡ Выбор #6',
  '🌙 Резерв #7',
];

export class ResultsSheet {
  private root: HTMLElement;
  private cb: ResultsSheetCallbacks;
  private gesture: SheetGestureController;
  private openState = false;
  private viewMode: 'podium' | 'list' = 'podium';
  private activeSlide = 0;
  private lastMatches: MatchedRecipe[] = [];
  private lastTags: VibeTag[] = [];
  private scrollSyncTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(root: HTMLElement, cb: ResultsSheetCallbacks) {
    this.root = root;
    this.cb = cb;

    this.gesture = new SheetGestureController({
      id: 'results',
      rootEl: this.root,
      depthManager: sheetDepth,
      getPanelEl: () => this.root.querySelector<HTMLElement>('.results-panel'),
      getBackdropEl: () => this.root.querySelector<HTMLElement>('.sheet-backdrop'),
      getScrollEl: () =>
        this.root.querySelector<HTMLElement>(this.viewMode === 'list' ? '.results-list' : '.podium-track'),
      isDragHandle: (target) => Boolean(target.closest('.sheet-handle-bar, .results-header, .podium-footer')),
      onDismiss: () => this.finalizeClose(),
    });

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (target === this.root || target.closest('[data-close-results]')) {
        this.close();
        return;
      }
      if (target.closest('[data-sheet-shelf-toggle]')) {
        this.cb.onToggleShelfOnly();
        return;
      }
      if (target.closest('[data-sheet-clear-tags]')) {
        this.cb.onClearTags();
        this.close();
        return;
      }
      const modeBtn = target.closest<HTMLElement>('[data-results-mode]');
      if (modeBtn?.dataset.resultsMode) {
        const nextMode = modeBtn.dataset.resultsMode === 'list' ? 'list' : 'podium';
        if (nextMode !== this.viewMode) {
          haptic('light');
          this.viewMode = nextMode;
          this.render(false);
        }
        return;
      }
      const dotBtn = target.closest<HTMLElement>('[data-podium-dot]');
      if (dotBtn?.dataset.podiumDot !== undefined) {
        const idx = Number(dotBtn.dataset.podiumDot);
        this.scrollToSlide(idx);
        return;
      }
      const saveBtn = target.closest<HTMLElement>('[data-toggle-save]');
      if (saveBtn?.dataset.toggleSave) {
        e.stopPropagation();
        this.cb.onToggleSave(saveBtn.dataset.toggleSave);
        this.syncSaveIcons();
        return;
      }
      const card = target.closest<HTMLElement>('[data-open-recipe]');
      if (card?.dataset.openRecipe) {
        const hit = this.lastMatches.find((m) => m.recipe.id === card.dataset.openRecipe);
        if (hit) this.cb.onOpenRecipe(hit.recipe);
      }
    });
  }

  public isOpen(): boolean {
    return this.openState;
  }

  public open(
    matches: MatchedRecipe[],
    selectedTags: VibeTag[],
    opts?: ResultsSheetOpenOptions
  ): void {
    this.lastMatches = matches;
    this.lastTags = selectedTags;
    this.viewMode = 'podium';
    this.activeSlide = 0;
    this.openState = true;
    this.gesture.reset();
    this.root.classList.toggle('warp-entrance', opts?.entrance === 'warp');
    this.render(true);
    this.root.classList.remove('hidden');
    sheetDepth.push({
      id: 'results',
      getPanelEl: () => this.root.querySelector<HTMLElement>('.results-panel'),
    });
    if (matches[0]) {
      this.cb.onHighlightRecipe?.(matches[0].recipe);
    }
    requestAnimationFrame(() => {
      if (this.openState) this.root.classList.add('open');
    });
  }

  public update(matches: MatchedRecipe[], selectedTags: VibeTag[]): void {
    this.lastMatches = matches;
    this.lastTags = selectedTags;
    this.activeSlide = 0;
    if (this.openState) {
      this.render(false);
      if (matches[0]) {
        this.cb.onHighlightRecipe?.(matches[0].recipe);
      }
    }
  }

  public close(): void {
    if (!this.openState) return;
    this.openState = false;
    this.root.classList.remove('open');
    this.gesture.animateClose(() => {
      if (!this.openState) {
        this.root.classList.add('hidden');
        this.root.classList.remove('warp-entrance');
      }
    });
    sheetDepth.pop('results');
    this.cb.onClose();
  }

  private finalizeClose(): void {
    if (!this.openState) return;
    this.openState = false;
    this.root.classList.remove('open', 'warp-entrance');
    this.root.classList.add('hidden');
    sheetDepth.pop('results');
    this.cb.onClose();
  }

  private syncSaveIcons(): void {
    this.root.querySelectorAll<HTMLElement>('[data-toggle-save]').forEach((btn) => {
      const id = btn.dataset.toggleSave;
      if (!id) return;
      const saved = this.cb.isSaved(id);
      btn.classList.toggle('saved', saved);
      btn.setAttribute('aria-label', saved ? 'Убрать из тайника' : 'Сохранить в тайник');
      btn.innerHTML = icon('heart', 'icon-sm', saved);
    });
  }

  private scrollToSlide(idx: number): void {
    const track = this.root.querySelector<HTMLElement>('.podium-track');
    const slide = this.root.querySelectorAll<HTMLElement>('.podium-card')[idx];
    if (!track || !slide) return;
    const targetLeft = slide.offsetLeft - Math.max(0, (track.clientWidth - slide.offsetWidth) * 0.5);
    track.scrollTo({ left: targetLeft, behavior: 'smooth' });
  }

  private bindPodiumScroll(featured: MatchedRecipe[]): void {
    const track = this.root.querySelector<HTMLElement>('.podium-track');
    if (!track || featured.length <= 1) return;

    const handleScrollUpdate = () => {
      const slides = track.querySelectorAll<HTMLElement>('.podium-card');
      if (!slides.length) return;
      const viewCenter = track.scrollLeft + track.clientWidth * 0.5;
      let nearestIdx = 0;
      let bestDist = Infinity;
      slides.forEach((s, i) => {
        const sCenter = s.offsetLeft + s.offsetWidth * 0.5;
        const d = Math.abs(sCenter - viewCenter);
        if (d < bestDist) {
          bestDist = d;
          nearestIdx = i;
        }
      });

      if (nearestIdx !== this.activeSlide && featured[nearestIdx]) {
        this.activeSlide = nearestIdx;
        haptic('detent');
        this.cb.onHighlightRecipe?.(featured[nearestIdx].recipe);

        this.root.querySelectorAll<HTMLElement>('[data-podium-dot]').forEach((dot, i) => {
          dot.classList.toggle('active', i === nearestIdx);
        });
        const counterEl = this.root.querySelector<HTMLElement>('#podium-counter');
        if (counterEl) {
          counterEl.textContent = `${nearestIdx + 1} / ${featured.length}`;
        }
      }
    };

    track.addEventListener(
      'scroll',
      () => {
        if (this.scrollSyncTimer) clearTimeout(this.scrollSyncTimer);
        this.scrollSyncTimer = setTimeout(handleScrollUpdate, 55);
      },
      { passive: true }
    );
  }

  private renderPodiumSlide(m: MatchedRecipe, idx: number, shelf: Set<BottleId>, tagCount: number): string {
    const r = m.recipe;
    const saved = this.cb.isSaved(r.id);
    const { onShelf, missingBottles } = getShelfStatus(r, shelf);

    const roleLabel =
      tagCount > 1
        ? `✨ ${m.hits} из ${tagCount}`
        : PODIUM_ROLE_BADGES[idx] || `✨ Выбор #${idx + 1}`;

    let shelfPillHtml = '';
    if (shelf.size > 0) {
      if (r.bottles.length === 0) {
        shelfPillHtml = `<span class="badge badge-zero">0,0% · готов</span>`;
      } else if (onShelf) {
        shelfPillHtml = `<span class="badge badge-ready">${icon('check', 'icon-xs')} Всё на полке</span>`;
      } else {
        const miss = missingBottles
          .slice(0, 2)
          .map((id) => BOTTLE_MAP.get(id)?.name || id)
          .join(', ');
        shelfPillHtml = `<span class="badge badge-missing">Докупить: ${escapeHtml(miss)}</span>`;
      }
    }

    const previewIngs = r.ingredients
      .slice(0, 3)
      .map((ing) => ing.name)
      .join(' · ');

    return `
      <article class="podium-card" style="--drink-glow: ${r.visual.glowColor}">
        <div class="podium-card-top">
          <div class="podium-badges">
            <span class="podium-role-pill">${escapeHtml(roleLabel)}</span>
            ${shelfPillHtml}
          </div>
          <button type="button" class="icon-circle-btn fav-circle-btn ${saved ? 'saved' : ''}" data-toggle-save="${escapeHtml(r.id)}" data-tactile aria-label="${saved ? 'Убрать из тайника' : 'В тайник'}">
            ${icon('heart', 'icon-sm', saved)}
          </button>
        </div>

        <div class="podium-hero-stage" data-open-recipe="${escapeHtml(r.id)}">
          <div class="podium-glass-aura"></div>
          <div class="podium-glass-wrap">
            ${renderCocktailGlass(r, 'hero')}
          </div>
        </div>

        <div class="podium-copy" data-open-recipe="${escapeHtml(r.id)}">
          <h3 class="podium-title">${escapeHtml(r.name)}</h3>
          <p class="podium-why">${escapeHtml(r.why)}</p>
        </div>

        <div class="podium-sensory-row">
          <span>${r.abv === 0 ? '0,0%' : `≈ ${r.abv}%`}</span>
          <span class="sensory-dot">·</span>
          <span>${escapeHtml(METHOD_LABELS[r.method])}</span>
          <span class="sensory-dot">·</span>
          <span>${escapeHtml(GLASS_LABELS[r.glass])}</span>
          ${r.ice !== 'none' ? `<span class="sensory-dot">·</span><span>${escapeHtml(ICE_LABELS[r.ice])}</span>` : ''}
        </div>

        <p class="podium-ings-teaser">${escapeHtml(previewIngs)}${r.ingredients.length > 3 ? ` +${r.ingredients.length - 3}` : ''}</p>

        <button type="button" class="podium-mix-btn" data-open-recipe="${escapeHtml(r.id)}" data-tactile style="--btn-glow: ${r.visual.glowColor}">
          ${icon('zap', 'icon-sm')}
          <span>Смешать коктейль</span>
          <span class="podium-mix-meta">${r.ingredients.length} ингр.</span>
        </button>
      </article>
    `;
  }

  private render(animateCards = false): void {
    const shelf = this.cb.getShelf();
    const shelfOnly = this.cb.isShelfOnly();
    const matches = this.lastMatches;
    const tagCount = this.lastTags.length;

    const primaryGlow = matches[this.activeSlide]?.recipe.visual.glowColor
      || this.lastTags[0]?.glow.match(/#[0-9a-fA-F]{6}/)?.[0]
      || '#c79bff';

    const subtitleText = tagCount
      ? this.lastTags.map((t) => `${t.emoji} ${t.label}`).join(' · ')
      : shelfOnly && shelf.size > 0
        ? 'Доступно с твоей полки'
        : 'Космическая подборка';

    const featured = matches.slice(0, MAX_PODIUM_CARDS);

    const titleText = matches.length === 0
      ? 'Слишком редкое сочетание'
      : this.viewMode === 'podium'
        ? matches.length > 1
          ? `Топ ${featured.length} из ${matches.length}`
          : featured[0].recipe.name
        : `${matches.length} ${pluralize(matches.length, 'коктейль', 'коктейля', 'коктейлей')}`;

    const shelfSwitchHtml =
      shelf.size > 0
        ? `
          <button type="button" class="shelf-filter-chip ${shelfOnly ? 'active' : ''}" data-sheet-shelf-toggle data-tactile>
            ${icon('wine', 'icon-xs')}
            <span>С полки</span>
          </button>
        `
        : '';

    const modeSwitchHtml =
      matches.length > 1
        ? `
          <button type="button" class="shelf-filter-chip ${this.viewMode === 'list' ? 'active' : ''}" data-results-mode="${this.viewMode === 'podium' ? 'list' : 'podium'}" data-tactile>
            ${icon(this.viewMode === 'podium' ? 'book-open' : 'sparkles', 'icon-xs')}
            <span>${this.viewMode === 'podium' ? `Весь список (${matches.length})` : 'Подиум'}</span>
          </button>
        `
        : '';

    let bodyHtml = '';
    if (!matches.length) {
      bodyHtml = `
        <div class="empty-state">
          <div class="empty-orb">${icon('sparkles', 'icon-lg')}</div>
          <p class="empty-title">Ничего не совпало</p>
          <p class="empty-sub">${
            shelfOnly && shelf.size > 0
              ? 'Попробуй отключить фильтр «С полки» или выбери другой вайб в туманности.'
              : 'Попробуй выбрать другой вайб или введи другой ингредиент.'
          }</p>
          <div class="empty-actions">
            ${
              shelfOnly && shelf.size > 0
                ? `<button type="button" class="btn-secondary" data-sheet-shelf-toggle data-tactile>Показать всю базу</button>`
                : ''
            }
            ${tagCount > 0 ? `<button type="button" class="btn-primary" data-sheet-clear-tags data-tactile>Выбрать другой вайб</button>` : ''}
          </div>
        </div>
      `;
    } else if (this.viewMode === 'podium') {
      const dotsHtml =
        featured.length > 1
          ? `
            <div class="podium-footer">
              <div class="podium-dots" role="tablist" aria-label="Слайды подборки">
                ${featured
                  .map(
                    (_, i) =>
                      `<button type="button" class="podium-dot ${i === this.activeSlide ? 'active' : ''}" data-podium-dot="${i}" aria-label="Коктейль ${i + 1}"></button>`
                  )
                  .join('')}
              </div>
              <span id="podium-counter" class="podium-counter">свайпай · ${this.activeSlide + 1} / ${featured.length}</span>
            </div>
          `
          : '';

      bodyHtml = `
        <div class="podium-shell">
          <div class="podium-track">
            ${featured.map((m, idx) => this.renderPodiumSlide(m, idx, shelf, tagCount)).join('')}
          </div>
          ${dotsHtml}
        </div>
      `;
    } else {
      const listHtml = matches
        .map((m, idx) =>
          renderRecipeCard({
            recipe: m.recipe,
            shelf,
            isSaved: this.cb.isSaved(m.recipe.id),
            matchBadge: tagCount > 1 ? `${m.hits} из ${tagCount}` : undefined,
            isExactMatch: tagCount > 1 ? m.hits === tagCount : undefined,
            animIndex: animateCards ? idx : undefined,
          })
        )
        .join('');
      bodyHtml = `<div class="results-list">${listHtml}</div>`;
    }

    this.root.innerHTML = `
      <div class="sheet-backdrop" data-close-results></div>
      <div class="results-panel glass-chrome" style="--sheet-accent: ${primaryGlow}">
        <div class="sheet-handle-bar"><span></span></div>
        <div class="results-header">
          <div class="results-header-text">
            <p class="results-eyebrow">${escapeHtml(subtitleText)}</p>
            <h3 class="results-title">${escapeHtml(titleText)}</h3>
          </div>
          <div class="results-header-actions">
            ${modeSwitchHtml}
            ${shelfSwitchHtml}
            <button type="button" class="icon-circle-btn" data-close-results data-tactile aria-label="Закрыть">
              ${icon('x', 'icon-sm')}
            </button>
          </div>
        </div>
        ${bodyHtml}
      </div>
    `;

    if (this.viewMode === 'podium' && featured.length > 1) {
      this.bindPodiumScroll(featured);
    }
  }
}
