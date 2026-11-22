import type { BottleId, Recipe } from '../types';
import { BOTTLE_MAP } from '../data/bottles';
import { getShelfStatus, getSimilarRecipes } from '../data/matcher';
import { GLASS_LABELS, ICE_LABELS, METHOD_LABELS } from '../data/recipes';
import { TAG_MAP } from '../data/tags';
import { renderCocktailGlass } from '../components/CocktailGlass';
import { icon } from '../components/icons';
import { escapeHtml, formatScaledAmount } from '../utils/html';

export interface RecipeModalCallbacks {
  isSaved: (id: string) => boolean;
  getShelf: () => Set<BottleId>;
  onToggleSave: (id: string) => void;
  onOpenRecipe: (recipe: Recipe) => void;
  onPickTag: (tagId: string) => void;
  onClose: () => void;
}

export class RecipeModal {
  private root: HTMLElement;
  private cb: RecipeModalCallbacks;
  private currentRecipe: Recipe | null = null;
  private multiplier = 1;

  constructor(root: HTMLElement, cb: RecipeModalCallbacks) {
    this.root = root;
    this.cb = cb;

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (target === this.root) {
        this.close();
        return;
      }

      const closeBtn = target.closest<HTMLElement>('[data-close-recipe]');
      if (closeBtn) {
        this.close();
        return;
      }

      const saveBtn = target.closest<HTMLElement>('[data-modal-save]');
      if (saveBtn && this.currentRecipe) {
        this.cb.onToggleSave(this.currentRecipe.id);
        this.render();
        return;
      }

      const multBtn = target.closest<HTMLElement>('[data-mult]');
      if (multBtn) {
        const next = Number(multBtn.dataset.mult || 1);
        if (next && next !== this.multiplier) {
          this.multiplier = next;
          this.renderIngredients();
        }
        return;
      }

      const tagBtn = target.closest<HTMLElement>('[data-jump-tag]');
      if (tagBtn && tagBtn.dataset.jumpTag) {
        const tid = tagBtn.dataset.jumpTag;
        this.close();
        this.cb.onPickTag(tid);
        return;
      }

      const simBtn = target.closest<HTMLElement>('[data-similar-id]');
      if (simBtn && simBtn.dataset.similarId) {
        const similar = getSimilarRecipes(this.currentRecipe!, 6).find((r) => r.id === simBtn.dataset.similarId);
        if (similar) {
          this.open(similar);
        }
      }
    });
  }

  public isOpen(): boolean {
    return this.currentRecipe !== null;
  }

  public open(recipe: Recipe): void {
    this.currentRecipe = recipe;
    this.multiplier = 1;
    this.render();
    this.root.classList.remove('hidden');
    requestAnimationFrame(() => {
      this.root.classList.add('open');
      const scroll = this.root.querySelector<HTMLElement>('.recipe-modal-scroll');
      if (scroll) scroll.scrollTop = 0;
    });
  }

  public close(): void {
    if (!this.currentRecipe) return;
    this.currentRecipe = null;
    this.root.classList.remove('open');
    setTimeout(() => {
      if (!this.currentRecipe) {
        this.root.classList.add('hidden');
      }
    }, 220);
    this.cb.onClose();
  }

  private renderIngredients(): void {
    const listEl = this.root.querySelector<HTMLElement>('#modal-ingredients-list');
    if (!listEl || !this.currentRecipe) return;
    const shelf = this.cb.getShelf();

    listEl.innerHTML = this.currentRecipe.ingredients
      .map((ing) => {
        const hasBottle = ing.bottleId && shelf.size > 0 ? shelf.has(ing.bottleId) : null;
        const statusDot =
          hasBottle === true
            ? `<span class="ing-dot ing-dot-ok" title="Есть на полке"></span>`
            : hasBottle === false
              ? `<span class="ing-dot ing-dot-miss" title="Нет на полке"></span>`
              : `<span class="ing-dot"></span>`;
        const scaled = formatScaledAmount(ing.amount, ing.ml, this.multiplier);
        return `
          <li class="ing-row">
            <div class="ing-left">
              ${statusDot}
              <div>
                <span class="ing-name">${escapeHtml(ing.name)}</span>
                ${ing.note ? `<span class="ing-note">${escapeHtml(ing.note)}</span>` : ''}
              </div>
            </div>
            <span class="ing-amount">${escapeHtml(scaled)}</span>
          </li>
        `;
      })
      .join('');

    this.root.querySelectorAll<HTMLElement>('[data-mult]').forEach((b) => {
      b.classList.toggle('active', Number(b.dataset.mult) === this.multiplier);
    });
  }

  private render(): void {
    const r = this.currentRecipe;
    if (!r) return;

    const saved = this.cb.isSaved(r.id);
    const shelf = this.cb.getShelf();
    const { onShelf, missingBottles } = getShelfStatus(r, shelf);
    const similar = getSimilarRecipes(r, 4);

    let shelfBanner = '';
    if (shelf.size > 0) {
      if (r.bottles.length === 0) {
        shelfBanner = `<div class="shelf-status-banner ready">${icon('sparkles', 'icon-sm')} Безалкогольный — можно сделать прямо сейчас</div>`;
      } else if (onShelf) {
        shelfBanner = `<div class="shelf-status-banner ready">${icon('check', 'icon-sm')} Все основные бутылки уже есть на твоей полке</div>`;
      } else {
        const missNames = missingBottles.map((id) => BOTTLE_MAP.get(id)?.name || id).join(', ');
        shelfBanner = `<div class="shelf-status-banner missing">${icon('wine', 'icon-sm')} На полке не отмечено: ${escapeHtml(missNames)}</div>`;
      }
    }

    const tagChips = r.tags
      .map((tid) => {
        const t = TAG_MAP.get(tid);
        if (!t) return '';
        return `<button type="button" class="recipe-tag-chip" data-jump-tag="${escapeHtml(t.id)}" style="--chip-glow:${t.glow}"><span>${escapeHtml(t.label)}</span></button>`;
      })
      .join('');

    const stepsHtml = r.steps
      .map(
        (step, idx) => `
          <li class="step-item">
            <span class="step-num">${idx + 1}</span>
            <p class="step-text">${escapeHtml(step)}</p>
          </li>
        `
      )
      .join('');

    const subsHtml = r.substitutes.length
      ? `
        <section class="recipe-section recipe-subs-card">
          <div class="section-heading">
            ${icon('lightbulb', 'icon-sm text-amber')}
            <h4>Чем заменить и лайфхаки</h4>
          </div>
          <ul class="subs-list">
            ${r.substitutes.map((s) => `<li>${escapeHtml(s)}</li>`).join('')}
          </ul>
        </section>
      `
      : '';

    const similarHtml = similar.length
      ? `
        <section class="recipe-section">
          <h4 class="section-label">Похожие по вайбу</h4>
          <div class="similar-rail">
            ${similar
              .map(
                (sr) => `
                <button type="button" class="similar-card" data-similar-id="${escapeHtml(sr.id)}" style="--drink-glow:${sr.visual.glowColor}">
                  <div class="similar-glass">${renderCocktailGlass(sr, 'mini')}</div>
                  <div class="similar-name">${escapeHtml(sr.name)}</div>
                  <div class="similar-abv">${sr.abv === 0 ? '0,0%' : `≈ ${sr.abv}%`}</div>
                </button>
              `
              )
              .join('')}
          </div>
        </section>
      `
      : '';

    this.root.innerHTML = `
      <div class="recipe-modal-panel" style="--drink-glow: ${r.visual.glowColor}">
        <div class="recipe-modal-topbar">
          <button type="button" class="icon-circle-btn" data-close-recipe aria-label="Назад">
            ${icon('arrow-left', 'icon-md')}
          </button>
          <div class="recipe-topbar-titles">
            <span class="recipe-topbar-en">${escapeHtml(r.nameEn)}</span>
          </div>
          <button type="button" class="icon-circle-btn fav-circle-btn ${saved ? 'saved' : ''}" data-modal-save aria-label="${saved ? 'Убрать из тайника' : 'В тайник'}">
            ${icon('heart', 'icon-md', saved)}
          </button>
        </div>

        <div class="recipe-modal-scroll">
          <!-- Hero Cocktail Glass Stage -->
          <div class="recipe-hero-stage">
            <div class="recipe-hero-aura"></div>
            <div class="recipe-hero-glass-wrap">
              ${renderCocktailGlass(r, 'hero')}
            </div>
            <h2 class="recipe-hero-title">${escapeHtml(r.name)}</h2>
            <p class="recipe-hero-why">${escapeHtml(r.why)}</p>

            <div class="recipe-spec-grid">
              <div class="spec-tile">
                <span class="spec-label">Крепость</span>
                <span class="spec-val">${r.abv === 0 ? '0,0%' : `≈ ${r.abv}%`}</span>
              </div>
              <div class="spec-tile">
                <span class="spec-label">Метод</span>
                <span class="spec-val">${escapeHtml(METHOD_LABELS[r.method])}</span>
              </div>
              <div class="spec-tile">
                <span class="spec-label">Бокал</span>
                <span class="spec-val">${escapeHtml(GLASS_LABELS[r.glass])}</span>
              </div>
              <div class="spec-tile">
                <span class="spec-label">Лёд</span>
                <span class="spec-val">${escapeHtml(ICE_LABELS[r.ice])}</span>
              </div>
            </div>
          </div>

          ${shelfBanner}

          <div class="recipe-tags-row">${tagChips}</div>

          <!-- Ingredients -->
          <section class="recipe-section glass-card">
            <div class="section-head-row">
              <h4 class="section-title">Ингредиенты</h4>
              <div class="mult-pills" role="group" aria-label="Порции">
                <button type="button" class="mult-pill active" data-mult="1">×1</button>
                <button type="button" class="mult-pill" data-mult="2">×2</button>
                <button type="button" class="mult-pill" data-mult="4">×4</button>
              </div>
            </div>
            <ul id="modal-ingredients-list" class="ing-list"></ul>
            <div class="garnish-callout">
              <span class="garnish-label">Украшение:</span>
              <span class="garnish-text">${escapeHtml(r.garnish)}</span>
            </div>
          </section>

          <!-- Steps -->
          <section class="recipe-section glass-card">
            <h4 class="section-title">Как сделать</h4>
            <ol class="steps-list">${stepsHtml}</ol>
          </section>

          ${subsHtml}
          ${similarHtml}

          <div class="recipe-bottom-spacer"></div>
        </div>
      </div>
    `;

    this.renderIngredients();
  }
}
