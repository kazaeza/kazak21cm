import type { BottleId, MatchedRecipe, Recipe, VibeTag } from '../types';
import { renderRecipeCard } from '../components/RecipeCard';
import { icon } from '../components/icons';
import { escapeHtml, pluralize } from '../utils/html';

export interface ResultsSheetCallbacks {
  getShelf: () => Set<BottleId>;
  isShelfOnly: () => boolean;
  isSaved: (id: string) => boolean;
  onToggleShelfOnly: () => void;
  onToggleSave: (id: string) => void;
  onOpenRecipe: (recipe: Recipe) => void;
  onClearTags: () => void;
  onClose: () => void;
}

export class ResultsSheet {
  private root: HTMLElement;
  private cb: ResultsSheetCallbacks;
  private openState = false;
  private lastMatches: MatchedRecipe[] = [];
  private lastTags: VibeTag[] = [];

  constructor(root: HTMLElement, cb: ResultsSheetCallbacks) {
    this.root = root;
    this.cb = cb;

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;
      if (target === this.root) {
        this.close();
        return;
      }
      if (target.closest('[data-close-results]')) {
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
      const saveBtn = target.closest<HTMLElement>('[data-toggle-save]');
      if (saveBtn?.dataset.toggleSave) {
        e.stopPropagation();
        this.cb.onToggleSave(saveBtn.dataset.toggleSave);
        this.render();
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

  public open(matches: MatchedRecipe[], selectedTags: VibeTag[]): void {
    this.lastMatches = matches;
    this.lastTags = selectedTags;
    this.openState = true;
    this.render();
    this.root.classList.remove('hidden');
    requestAnimationFrame(() => {
      this.root.classList.add('open');
    });
  }

  public update(matches: MatchedRecipe[], selectedTags: VibeTag[]): void {
    this.lastMatches = matches;
    this.lastTags = selectedTags;
    if (this.openState) this.render();
  }

  public close(): void {
    if (!this.openState) return;
    this.openState = false;
    this.root.classList.remove('open');
    setTimeout(() => {
      if (!this.openState) this.root.classList.add('hidden');
    }, 220);
    this.cb.onClose();
  }

  private render(): void {
    const shelf = this.cb.getShelf();
    const shelfOnly = this.cb.isShelfOnly();
    const matches = this.lastMatches;
    const tagCount = this.lastTags.length;

    const titleText = matches.length
      ? `${matches.length} ${pluralize(matches.length, 'коктейль', 'коктейля', 'коктейлей')}`
      : 'Слишком редкое сочетание';

    const subtitleText = tagCount
      ? this.lastTags.map((t) => `${t.emoji} ${t.label}`).join(' · ')
      : shelfOnly && shelf.size > 0
        ? 'Доступно из твоей полки'
        : 'Вся коллекция туманности';

    const shelfSwitchHtml =
      shelf.size > 0
        ? `
          <button type="button" class="shelf-filter-chip ${shelfOnly ? 'active' : ''}" data-sheet-shelf-toggle>
            ${icon('wine', 'icon-xs')}
            <span>Только с полки</span>
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
              ? 'Попробуй отключить фильтр «Только с полки» или выбери другой вайб в туманности.'
              : 'Попробуй выбрать другой вайб или введи другой ингредиент.'
          }</p>
          <div class="empty-actions">
            ${
              shelfOnly && shelf.size > 0
                ? `<button type="button" class="btn-secondary" data-sheet-shelf-toggle>Показать всю базу</button>`
                : ''
            }
            ${tagCount > 0 ? `<button type="button" class="btn-primary" data-sheet-clear-tags>Выбрать другой вайб</button>` : ''}
          </div>
        </div>
      `;
    } else if (tagCount > 1) {
      const exact = matches.filter((m) => m.hits === tagCount);
      const partial = matches.filter((m) => m.hits < tagCount);

      if (exact.length > 0) {
        bodyHtml += `<div class="results-group-label">Точное попадание (${exact.length})</div>`;
        bodyHtml += exact
          .map((m) =>
            renderRecipeCard({
              recipe: m.recipe,
              shelf,
              isSaved: this.cb.isSaved(m.recipe.id),
              matchBadge: `${m.hits} из ${tagCount}`,
              isExactMatch: true,
            })
          )
          .join('');
      }
      if (partial.length > 0) {
        bodyHtml += `<div class="results-group-label">${exact.length > 0 ? 'Близко по вайбу' : 'Частичные совпадения'} (${partial.length})</div>`;
        bodyHtml += partial
          .map((m) =>
            renderRecipeCard({
              recipe: m.recipe,
              shelf,
              isSaved: this.cb.isSaved(m.recipe.id),
              matchBadge: `${m.hits} из ${tagCount}`,
              isExactMatch: false,
            })
          )
          .join('');
      }
    } else {
      bodyHtml = matches
        .map((m) =>
          renderRecipeCard({
            recipe: m.recipe,
            shelf,
            isSaved: this.cb.isSaved(m.recipe.id),
          })
        )
        .join('');
    }

    this.root.innerHTML = `
      <div class="results-panel glass-chrome">
        <div class="sheet-handle-bar"><span></span></div>
        <div class="results-header">
          <div class="results-header-text">
            <p class="results-eyebrow">Поймано из туманности</p>
            <h3 class="results-title">${escapeHtml(titleText)}</h3>
            <p class="results-subtitle">${escapeHtml(subtitleText)}</p>
          </div>
          <button type="button" class="icon-circle-btn" data-close-results aria-label="Закрыть">
            ${icon('x', 'icon-sm')}
          </button>
        </div>
        ${shelfSwitchHtml ? `<div class="results-toolbar">${shelfSwitchHtml}</div>` : ''}
        <div class="results-list">${bodyHtml}</div>
      </div>
    `;
  }
}
