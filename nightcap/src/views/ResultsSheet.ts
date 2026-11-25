import type { BottleId, MatchedRecipe, Recipe, VibeTag } from '../types';
import { GLASS_LABELS, METHOD_LABELS } from '../data/recipes';
import { renderCocktailGlass } from '../components/CocktailGlass';
import { icon } from '../components/icons';
import { escapeHtml, pluralize } from '../utils/html';
import { haptic } from '../services/native';

export interface ResultsSheetCallbacks {
  getShelf?: () => Set<BottleId>;
  isShelfOnly?: () => boolean;
  isSaved: (id: string) => boolean;
  onToggleShelfOnly?: () => void;
  onToggleSave: (id: string) => void;
  onOpenRecipe: (recipe: Recipe) => void;
  onHighlightRecipe?: (recipe: Recipe) => void;
  onClearTags: () => void;
  onClose: () => void;
}

export interface ResultsSheetOpenOptions {
  entrance?: 'warp' | 'standard';
}

const CLOSE_ANIMATION_MS = 280;

export class ResultsSheet {
  private root: HTMLElement;
  private cb: ResultsSheetCallbacks;
  private openState = false;
  private heroIndex = 0;
  private lastMatches: MatchedRecipe[] = [];
  private lastTags: VibeTag[] = [];
  private closeTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(root: HTMLElement, cb: ResultsSheetCallbacks) {
    this.root = root;
    this.cb = cb;

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;

      if (target.closest('[data-close-results]')) {
        haptic('light');
        this.close();
        return;
      }

      if (target.closest('[data-shuffle-results]')) {
        this.shuffleHero();
        return;
      }

      if (target.closest('[data-sheet-clear-tags]')) {
        haptic('light');
        this.cb.onClearTags();
        this.close();
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

    this.root.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter' && e.key !== ' ') return;
      const target = e.target as HTMLElement;
      if (target.matches('[data-open-recipe]') && target.dataset.openRecipe) {
        e.preventDefault();
        const hit = this.lastMatches.find((m) => m.recipe.id === target.dataset.openRecipe);
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
    if (this.closeTimer !== null) {
      clearTimeout(this.closeTimer);
      this.closeTimer = null;
    }

    this.lastMatches = matches;
    this.lastTags = selectedTags;
    this.heroIndex = 0;
    this.openState = true;

    this.root.classList.toggle('warp-entrance', opts?.entrance === 'warp');
    this.render({ animateCards: true, animateHero: true });
    this.root.classList.remove('hidden');

    if (matches[0]) {
      this.cb.onHighlightRecipe?.(matches[0].recipe);
    }

    requestAnimationFrame(() => {
      if (!this.openState) return;
      const scrollEl = this.root.querySelector<HTMLElement>('.results-page-scroll');
      if (scrollEl) scrollEl.scrollTop = 0;
      this.root.classList.add('open');
    });
  }

  public update(matches: MatchedRecipe[], selectedTags: VibeTag[]): void {
    this.lastMatches = matches;
    this.lastTags = selectedTags;
    this.heroIndex = 0;

    if (this.openState) {
      this.render({ animateCards: false, animateHero: false });
      if (matches[0]) {
        this.cb.onHighlightRecipe?.(matches[0].recipe);
      }
    }
  }

  public close(): void {
    if (!this.openState) return;
    this.openState = false;
    this.root.classList.remove('open');

    if (this.closeTimer !== null) {
      clearTimeout(this.closeTimer);
    }
    this.closeTimer = setTimeout(() => {
      this.closeTimer = null;
      if (!this.openState) {
        this.root.classList.add('hidden');
        this.root.classList.remove('warp-entrance');
      }
    }, CLOSE_ANIMATION_MS);

    this.cb.onClose();
  }

  private shuffleHero(): void {
    if (this.lastMatches.length <= 1) return;
    haptic('medium');
    this.heroIndex = (this.heroIndex + 1) % this.lastMatches.length;

    const nextHero = this.lastMatches[this.heroIndex];
    if (nextHero) {
      this.cb.onHighlightRecipe?.(nextHero.recipe);
    }

    this.render({ animateCards: false, animateHero: true });

    const scrollEl = this.root.querySelector<HTMLElement>('.results-page-scroll');
    if (scrollEl && scrollEl.scrollTop > 12) {
      scrollEl.scrollTo({ top: 0, behavior: 'smooth' });
    }
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

  private renderHeroCard(match: MatchedRecipe, animateHero: boolean): string {
    const hero = match.recipe;
    const saved = this.cb.isSaved(hero.id);
    const abvText = hero.abv === 0 ? '0,0%' : `≈ ${hero.abv}%`;
    const badgeLabel =
      this.heroIndex === 0 ? '✨ Идеально сейчас' : `✨ Выбор #${this.heroIndex + 1}`;
    const enterClass = animateHero ? ' results-hero-enter' : '';

    return `
      <section class="results-hero-section" aria-label="Главный выбор">
        <article class="results-hero-card${enterClass}" style="--drink-glow: ${hero.visual.glowColor}">
          <div class="results-hero-glass" data-open-recipe="${escapeHtml(hero.id)}" data-tactile role="button" tabindex="0" aria-label="Открыть рецепт ${escapeHtml(hero.name)}">
            <div class="results-hero-aura" aria-hidden="true"></div>
            <div class="results-hero-glass-inner">
              ${renderCocktailGlass(hero, 'hero')}
            </div>
          </div>

          <div class="results-hero-content">
            <div class="results-hero-top">
              <span class="results-hero-badge">${escapeHtml(badgeLabel)}</span>
              <button
                type="button"
                class="fav-icon-btn results-hero-fav ${saved ? 'saved' : ''}"
                data-toggle-save="${escapeHtml(hero.id)}"
                data-tactile
                aria-label="${saved ? 'Убрать из тайника' : 'Сохранить в тайник'}"
              >
                ${icon('heart', 'icon-sm', saved)}
              </button>
            </div>

            <h2 class="results-hero-name" data-open-recipe="${escapeHtml(hero.id)}">${escapeHtml(hero.name)}</h2>
            <p class="results-hero-why" data-open-recipe="${escapeHtml(hero.id)}">${escapeHtml(hero.why)}</p>

            <div class="results-hero-specs">
              <span class="spec-abv ${hero.abv === 0 ? 'spec-abv-zero' : ''}">${abvText}</span>
              <span class="spec-sep" aria-hidden="true">·</span>
              <span>${escapeHtml(METHOD_LABELS[hero.method])}</span>
              <span class="spec-sep" aria-hidden="true">·</span>
              <span>${escapeHtml(GLASS_LABELS[hero.glass])}</span>
            </div>

            <button
              type="button"
              class="results-hero-cta"
              data-open-recipe="${escapeHtml(hero.id)}"
              data-tactile
              style="--btn-glow: ${hero.visual.glowColor}"
            >
              <span>Рецепт</span>
              <span class="results-hero-cta-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </article>
      </section>
    `;
  }

  private renderBentoCard(match: MatchedRecipe, idx: number, animateCards: boolean): string {
    const r = match.recipe;
    const saved = this.cb.isSaved(r.id);
    const abvText = r.abv === 0 ? '0,0%' : `≈ ${r.abv}%`;
    const animClass = animateCards ? ' bento-card-enter' : '';
    const delayMs = animateCards ? Math.min(idx, 10) * 38 : 0;

    return `
      <article
        class="results-bento-card${animClass}"
        style="--drink-glow: ${r.visual.glowColor}; --card-delay: ${delayMs}ms"
        data-open-recipe="${escapeHtml(r.id)}"
        data-tactile
        role="button"
        tabindex="0"
      >
        <button
          type="button"
          class="fav-icon-btn bento-fav-btn ${saved ? 'saved' : ''}"
          data-toggle-save="${escapeHtml(r.id)}"
          data-tactile
          aria-label="${saved ? 'Убрать из тайника' : 'Сохранить в тайник'}"
        >
          ${icon('heart', 'icon-sm', saved)}
        </button>

        <div class="bento-glass-wrap">
          <div class="bento-glass-aura" aria-hidden="true"></div>
          ${renderCocktailGlass(r, 'mini')}
        </div>

        <div class="bento-body">
          <h4 class="bento-title">${escapeHtml(r.name)}</h4>
          <p class="bento-why">${escapeHtml(r.why)}</p>
          <div class="bento-specs">
            <span class="spec-abv ${r.abv === 0 ? 'spec-abv-zero' : ''}">${abvText}</span>
            <span class="spec-sep" aria-hidden="true">·</span>
            <span class="spec-method">${escapeHtml(METHOD_LABELS[r.method])}</span>
          </div>
        </div>
      </article>
    `;
  }

  private render(opts: { animateCards: boolean; animateHero: boolean }): void {
    const matches = this.lastMatches;
    const tagCount = this.lastTags.length;
    const safeHeroIdx = matches.length > 0 ? this.heroIndex % matches.length : 0;
    const heroMatch = matches[safeHeroIdx];
    const restMatches = matches.filter((_, idx) => idx !== safeHeroIdx);

    const primaryGlow =
      heroMatch?.recipe.visual.glowColor ||
      this.lastTags[0]?.glow.match(/#[0-9a-fA-F]{6}/)?.[0] ||
      '#c79bff';

    const vibeTitle =
      tagCount > 0
        ? this.lastTags.map((t) => `${t.emoji} ${t.label}`).join(' · ')
        : '✨ Космическая подборка';

    const countSubtitle =
      matches.length > 0
        ? `${matches.length} ${pluralize(matches.length, 'коктейль', 'коктейля', 'коктейлей')} под вайб`
        : 'Ничего не совпало';

    const canShuffle = matches.length > 1;

    let bodyHtml = '';
    if (!heroMatch) {
      bodyHtml = `
        <div class="empty-state results-empty-state">
          <div class="empty-orb">${icon('sparkles', 'icon-lg')}</div>
          <p class="empty-title">Слишком редкое сочетание</p>
          <p class="empty-sub">Попробуй выбрать другой вайб или введи другой ингредиент в туманности.</p>
          <div class="empty-actions">
            <button type="button" class="btn-primary" data-sheet-clear-tags data-tactile>Вернуться в космос</button>
          </div>
        </div>
      `;
    } else {
      const heroHtml = this.renderHeroCard(heroMatch, opts.animateHero);
      const gridHtml = restMatches.length
        ? `
          <section class="results-grid-section" aria-label="Ещё под этот вайб">
            <div class="results-section-head">
              <h3 class="results-section-title">Ещё под этот вайб</h3>
              <span class="results-section-count">${restMatches.length}</span>
            </div>
            <div class="results-bento-grid">
              ${restMatches.map((m, idx) => this.renderBentoCard(m, idx, opts.animateCards)).join('')}
            </div>
          </section>
        `
        : '';

      bodyHtml = `
        ${heroHtml}
        ${gridHtml}
        <div class="results-bottom-spacer"></div>
      `;
    }

    this.root.innerHTML = `
      <div class="results-page" style="--sheet-accent: ${primaryGlow}">
        <header class="results-topbar">
          <button type="button" class="results-back-btn" data-close-results data-tactile aria-label="Назад в космос">
            ${icon('arrow-left', 'icon-sm')}
            <span>Космос</span>
          </button>

          <div class="results-topbar-center">
            <h1 class="results-topbar-title">${escapeHtml(vibeTitle)}</h1>
            <p class="results-topbar-sub">${escapeHtml(countSubtitle)}</p>
          </div>

          <button
            type="button"
            class="icon-circle-btn results-shuffle-btn${canShuffle ? '' : ' is-disabled'}"
            data-shuffle-results
            data-tactile
            title="Перемешать главный выбор"
            aria-label="Другой главный коктейль"
            ${canShuffle ? '' : 'disabled'}
          >
            ${icon('refresh', 'icon-sm')}
          </button>
        </header>

        <div class="results-page-scroll">
          ${bodyHtml}
        </div>
      </div>
    `;
  }
}
