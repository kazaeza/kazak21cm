import type { BottleId, Recipe } from '../types';
import { RECIPES } from '../data/recipes';
import { renderRecipeCard } from '../components/RecipeCard';
import { icon } from '../components/icons';
import { pluralize } from '../utils/html';

export interface SavedViewCallbacks {
  getSavedIds: () => Set<string>;
  getShelf: () => Set<BottleId>;
  onToggleSave: (id: string) => void;
  onOpenRecipe: (recipe: Recipe) => void;
  onGoNebula: () => void;
  onGoMenu: () => void;
}

export class SavedView {
  private root: HTMLElement;
  private cb: SavedViewCallbacks;

  constructor(root: HTMLElement, cb: SavedViewCallbacks) {
    this.root = root;
    this.cb = cb;

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;

      if (target.closest('[data-go-nebula]')) {
        this.cb.onGoNebula();
        return;
      }
      if (target.closest('[data-go-menu]')) {
        this.cb.onGoMenu();
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
        const recipe = RECIPES.find((r) => r.id === card.dataset.openRecipe);
        if (recipe) this.cb.onOpenRecipe(recipe);
      }
    });
  }

  public render(): void {
    const savedIds = this.cb.getSavedIds();
    const shelf = this.cb.getShelf();
    const savedRecipes = RECIPES.filter((r) => savedIds.has(r.id));

    const contentHtml = savedRecipes.length
      ? `<div class="cards-stack">${savedRecipes
          .map((recipe) =>
            renderRecipeCard({
              recipe,
              shelf,
              isSaved: true,
            })
          )
          .join('')}</div>`
      : `
        <div class="empty-state empty-state-tall">
          <div class="empty-orb">${icon('heart', 'icon-lg')}</div>
          <p class="empty-title">Тайник пока пуст</p>
          <p class="empty-sub">Нажимай на сердечко в любом коктейле, чтобы сохранить любимые вечерние рецепты под рукой.</p>
          <div class="empty-actions">
            <button type="button" class="btn-primary" data-go-nebula>В туманность</button>
            <button type="button" class="btn-secondary" data-go-menu>Открыть карту</button>
          </div>
        </div>
      `;

    this.root.innerHTML = `
      <div class="tab-scroll">
        <div class="tab-header">
          <div>
            <p class="tab-eyebrow">Избранное</p>
            <h2 class="tab-title">Тайник</h2>
          </div>
          ${
            savedRecipes.length
              ? `<span class="group-count-pill">${savedRecipes.length} ${pluralize(savedRecipes.length, 'коктейль', 'коктейля', 'коктейлей')}</span>`
              : ''
          }
        </div>
        ${contentHtml}
        <div class="tab-bottom-spacer"></div>
      </div>
    `;
  }
}
