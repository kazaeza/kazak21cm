import type { BottleId, Recipe } from '../types';
import { METHOD_LABELS } from '../data/recipes';
import { escapeHtml } from '../utils/html';
import { renderCocktailGlass } from './CocktailGlass';
import { icon } from './icons';

export interface RecipeCardOptions {
  recipe: Recipe;
  shelf?: Set<BottleId>;
  isSaved: boolean;
  matchBadge?: string;
  isExactMatch?: boolean;
  animIndex?: number;
}

export function renderRecipeCard(opts: RecipeCardOptions): string {
  const { recipe, isSaved, matchBadge, isExactMatch, animIndex } = opts;

  const statusBadge = matchBadge
    ? `<span class="badge ${isExactMatch ? 'badge-match-exact' : 'badge-match'}">${escapeHtml(matchBadge)}</span>`
    : '';

  const abvText = recipe.abv === 0 ? '0,0%' : `≈ ${recipe.abv}%`;
  const animClass = typeof animIndex === 'number' ? ' recipe-card-enter' : '';
  const delayMs = typeof animIndex === 'number' ? Math.min(animIndex, 7) * 34 : 0;

  return `
    <article class="recipe-card${animClass}" style="--drink-glow: ${recipe.visual.glowColor}; --card-delay: ${delayMs}ms" data-open-recipe="${escapeHtml(recipe.id)}" data-tactile role="button" tabindex="0">
      <div class="recipe-card-glass">
        ${renderCocktailGlass(recipe, 'mini')}
      </div>
      <div class="recipe-card-body">
        <div class="recipe-card-head">
          <h4 class="recipe-card-title">${escapeHtml(recipe.name)}</h4>
          <button type="button" class="fav-icon-btn ${isSaved ? 'saved' : ''}" data-toggle-save="${escapeHtml(recipe.id)}" aria-label="${isSaved ? 'Убрать из тайника' : 'Сохранить в тайник'}">
            ${icon('heart', 'icon-sm', isSaved)}
          </button>
        </div>
        <p class="recipe-card-why">${escapeHtml(recipe.why)}</p>
        <div class="recipe-card-meta">
          <span class="recipe-card-specs">
            <span class="spec-abv ${recipe.abv === 0 ? 'spec-abv-zero' : ''}">${abvText}</span>
            <span class="spec-sep" aria-hidden="true">·</span>
            <span class="spec-method">${escapeHtml(METHOD_LABELS[recipe.method])}</span>
          </span>
          ${statusBadge}
        </div>
      </div>
    </article>
  `;
}
