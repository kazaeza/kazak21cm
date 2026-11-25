import type { BottleId, Recipe, RecipeCategory } from '../types';
import { recipeMatchesQuery } from '../data/matcher';
import { CATEGORY_LABELS, RECIPES } from '../data/recipes';
import { renderRecipeCard } from '../components/RecipeCard';
import { icon } from '../components/icons';
import { escapeHtml, pluralize } from '../utils/html';
import { MorphingPillController } from '../engine/motion';

export type MenuFilterId = 'all' | RecipeCategory | 'light' | 'strong';

export interface MenuViewCallbacks {
  getShelf?: () => Set<BottleId>;
  isSaved: (id: string) => boolean;
  onToggleSave: (id: string) => void;
  onOpenRecipe: (recipe: Recipe) => void;
}

const MENU_FILTERS: { id: MenuFilterId; label: string }[] = [
  { id: 'all', label: 'Все' },
  { id: 'classic', label: CATEGORY_LABELS.classic },
  { id: 'modern', label: 'Современные' },
  { id: 'light', label: 'Полегче' },
  { id: 'strong', label: 'Покрепче' },
  { id: 'shot', label: 'Шоты' },
  { id: 'pitcher', label: 'Кувшины' },
  { id: 'beer-wine', label: 'Вино и пиво' },
  { id: 'hot', label: 'Тёплые' },
  { id: 'zero', label: '0,0%' },
];

export class MenuView {
  private root: HTMLElement;
  private cb: MenuViewCallbacks;
  private query = '';
  private filter: MenuFilterId = 'all';
  private initialized = false;
  private railPill: MorphingPillController | null = null;

  constructor(root: HTMLElement, cb: MenuViewCallbacks) {
    this.root = root;
    this.cb = cb;

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;

      const filterBtn = target.closest<HTMLElement>('[data-menu-filter]');
      if (filterBtn?.dataset.menuFilter) {
        this.filter = filterBtn.dataset.menuFilter as MenuFilterId;
        this.updateFiltersAndList();
        return;
      }

      if (target.closest('[data-clear-menu-search]')) {
        this.query = '';
        const inp = this.root.querySelector<HTMLInputElement>('#menu-search-input');
        if (inp) inp.value = '';
        this.updateFiltersAndList();
        return;
      }

      const saveBtn = target.closest<HTMLElement>('[data-toggle-save]');
      if (saveBtn?.dataset.toggleSave) {
        e.stopPropagation();
        this.cb.onToggleSave(saveBtn.dataset.toggleSave);
        this.updateList();
        return;
      }

      const card = target.closest<HTMLElement>('[data-open-recipe]');
      if (card?.dataset.openRecipe) {
        const recipe = RECIPES.find((r) => r.id === card.dataset.openRecipe);
        if (recipe) this.cb.onOpenRecipe(recipe);
      }
    });

    this.root.addEventListener('input', (e) => {
      const target = e.target as HTMLElement;
      if (target.id === 'menu-search-input') {
        this.query = (target as HTMLInputElement).value;
        this.updateList();
      }
    });
  }

  public setFilter(filter: MenuFilterId): void {
    this.filter = filter;
    this.render();
  }

  public render(): void {
    if (!this.initialized) {
      this.initialized = true;
      this.root.innerHTML = `
        <div class="tab-scroll">
          <div class="tab-header">
            <div>
              <p class="tab-eyebrow">Полная коллекция</p>
              <h2 class="tab-title">Барная карта</h2>
            </div>
            <span id="menu-count-pill" class="group-count-pill"></span>
          </div>

          <div class="menu-sticky-bar">
            <div class="menu-search-wrap">
              <div class="search-field glass-card">
                ${icon('search', 'icon-sm text-muted')}
                <input id="menu-search-input" type="search" autocomplete="off" placeholder="Поиск: негрони, джин, маракуйя, 0%…" />
                <button type="button" id="menu-search-clear" class="search-clear-btn hidden" data-clear-menu-search aria-label="Очистить">
                  ${icon('x', 'icon-xs')}
                </button>
              </div>
            </div>

            <div id="menu-filter-rail" class="pill-rail" role="tablist" aria-label="Категории коктейлей"></div>
          </div>

          <div id="menu-list" class="cards-stack"></div>
          <div class="tab-bottom-spacer"></div>
        </div>
      `;
    }
    this.updateFiltersAndList();
  }

  private getFiltered(): Recipe[] {
    const q = this.query.trim().toLowerCase();
    return RECIPES.filter((r) => {
      if (q && !recipeMatchesQuery(r, q)) return false;

      switch (this.filter) {
        case 'all':
          return true;
        case 'light':
          return r.abv > 0 && r.abv <= 14;
        case 'strong':
          return r.abv >= 20;
        default:
          return r.category === this.filter;
      }
    }).sort((a, b) => {
      if (q) {
        const aName = a.name.toLowerCase().includes(q) || a.nameEn.toLowerCase().includes(q);
        const bName = b.name.toLowerCase().includes(q) || b.nameEn.toLowerCase().includes(q);
        if (aName !== bName) return aName ? -1 : 1;
      }
      return a.name.localeCompare(b.name, 'ru');
    });
  }

  private updateFiltersAndList(): void {
    const rail = this.root.querySelector<HTMLElement>('#menu-filter-rail');
    if (rail) {
      if (!rail.querySelector('[data-menu-filter]')) {
        this.railPill?.destroy();
        const pillsHtml = MENU_FILTERS.map(
          (f) =>
            `<button type="button" class="filter-pill ${this.filter === f.id ? 'active' : ''}" data-menu-filter="${f.id}" role="tab" aria-selected="${this.filter === f.id ? 'true' : 'false'}">${escapeHtml(f.label)}</button>`
        ).join('');
        rail.innerHTML = `<span class="rail-glider" aria-hidden="true"></span>${pillsHtml}`;
        this.railPill = new MorphingPillController({
          container: rail,
          itemSelector: '[data-menu-filter]',
          indicatorSelector: '.rail-glider',
          mode: 'adaptive-width',
          autoScrollActive: true,
        });
        this.railPill.sync(undefined, true);
        requestAnimationFrame(() => this.railPill?.sync(undefined, true));
      } else {
        rail.querySelectorAll<HTMLElement>('[data-menu-filter]').forEach((btn) => {
          const isActive = btn.dataset.menuFilter === this.filter;
          btn.classList.toggle('active', isActive);
          btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
        });
        this.railPill?.sync();
      }
    }
    this.updateList();
  }

  private updateList(): void {
    const list = this.getFiltered();

    const clearBtn = this.root.querySelector<HTMLElement>('#menu-search-clear');
    if (clearBtn) clearBtn.classList.toggle('hidden', !this.query.trim());

    const countEl = this.root.querySelector<HTMLElement>('#menu-count-pill');
    if (countEl) {
      countEl.textContent = `${list.length} ${pluralize(list.length, 'рецепт', 'рецепта', 'рецептов')}`;
    }

    const listEl = this.root.querySelector<HTMLElement>('#menu-list');
    if (!listEl) return;

    if (!list.length) {
      listEl.innerHTML = `
        <div class="empty-state">
          <div class="empty-orb">${icon('search', 'icon-lg')}</div>
          <p class="empty-title">В карте такого нет</p>
          <p class="empty-sub">Попробуй другое слово (например «джин», «кислый», «мята») или переключи категорию.</p>
          <button type="button" class="btn-secondary" data-clear-menu-search data-tactile>Сбросить поиск</button>
        </div>
      `;
      return;
    }

    listEl.innerHTML = list
      .map((recipe) =>
        renderRecipeCard({
          recipe,
          isSaved: this.cb.isSaved(recipe.id),
        })
      )
      .join('');
  }
}
