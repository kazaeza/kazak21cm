import type { BottleId } from '../types';
import { BOTTLE_GROUPS, BOTTLES, SHELF_PRESETS } from '../data/bottles';
import { countAvailableForShelf } from '../data/matcher';
import { RECIPES } from '../data/recipes';
import { icon } from '../components/icons';
import { escapeHtml, pluralize } from '../utils/html';

export interface ShelfViewCallbacks {
  getShelf: () => Set<BottleId>;
  onToggleBottle: (id: BottleId) => void;
  onApplyPreset: (ids: BottleId[]) => void;
  onClearShelf: () => void;
  onShowAvailable: () => void;
}

const bottleRecipeCount = new Map<BottleId, number>(
  BOTTLES.map((b) => [b.id, RECIPES.filter((r) => r.bottles.includes(b.id)).length])
);

export class ShelfView {
  private root: HTMLElement;
  private cb: ShelfViewCallbacks;

  constructor(root: HTMLElement, cb: ShelfViewCallbacks) {
    this.root = root;
    this.cb = cb;

    this.root.addEventListener('click', (e) => {
      const target = e.target as HTMLElement;

      const tile = target.closest<HTMLElement>('[data-bottle-id]');
      if (tile?.dataset.bottleId) {
        this.cb.onToggleBottle(tile.dataset.bottleId as BottleId);
        return;
      }

      const presetBtn = target.closest<HTMLElement>('[data-preset-id]');
      if (presetBtn?.dataset.presetId) {
        const preset = SHELF_PRESETS.find((p) => p.id === presetBtn.dataset.presetId);
        if (preset) this.cb.onApplyPreset(preset.bottles);
        return;
      }

      if (target.closest('[data-clear-shelf]')) {
        this.cb.onClearShelf();
        return;
      }

      if (target.closest('[data-show-available]')) {
        this.cb.onShowAvailable();
      }
    });
  }

  public render(): void {
    const shelf = this.cb.getShelf();
    const avail = countAvailableForShelf(shelf);
    const totalRecipes = RECIPES.length;
    const availPct = totalRecipes > 0 ? Math.min(100, Math.round((avail / totalRecipes) * 100)) : 0;
    const hasBottles = shelf.size > 0;

    const groupsHtml = BOTTLE_GROUPS.map((grp) => {
      const items = BOTTLES.filter((b) => b.group === grp.id);
      const selectedInGroup = items.filter((b) => shelf.has(b.id)).length;

      return `
        <section class="shelf-group">
          <div class="shelf-group-head">
            <div>
              <h3 class="shelf-group-title">${escapeHtml(grp.title)}</h3>
              <p class="shelf-group-sub">${escapeHtml(grp.subtitle)}</p>
            </div>
            ${selectedInGroup > 0 ? `<span class="group-count-pill">${selectedInGroup}/${items.length}</span>` : ''}
          </div>
          <div class="bottle-grid">
            ${items
              .map((b) => {
                const active = shelf.has(b.id);
                const cnt = bottleRecipeCount.get(b.id) || 0;
                return `
                  <button type="button" class="bottle-tile ${active ? 'active' : ''}" data-bottle-id="${escapeHtml(b.id)}" data-tactile style="--bottle-accent:${b.accent}" aria-pressed="${active ? 'true' : 'false'}">
                    <span class="bottle-accent-bar" aria-hidden="true"></span>
                    <div class="bottle-tile-top">
                      <span class="bottle-name">${escapeHtml(b.name)}</span>
                      <span class="bottle-check" aria-hidden="true">${active ? icon('check', 'icon-xs') : ''}</span>
                    </div>
                    <div class="bottle-sub">${escapeHtml(b.sub)}</div>
                    <div class="bottle-count">${cnt} ${pluralize(cnt, 'коктейль', 'коктейля', 'коктейлей')}</div>
                  </button>
                `;
              })
              .join('')}
          </div>
        </section>
      `;
    }).join('');

    this.root.innerHTML = `
      <div class="tab-scroll">
        <div class="tab-header">
          <div>
            <p class="tab-eyebrow">Домашний бар</p>
            <h2 class="tab-title">Моя полка</h2>
          </div>
          ${
            hasBottles
              ? `<button type="button" class="ghost-pill-btn" data-clear-shelf data-tactile>${icon('trash-2', 'icon-xs')} Сбросить</button>`
              : ''
          }
        </div>

        <div class="shelf-summary glass-card">
          <div class="shelf-summary-main">
            <div>
              <div class="shelf-summary-num">${
                hasBottles
                  ? `${shelf.size} ${pluralize(shelf.size, 'бутылка', 'бутылки', 'бутылок')}`
                  : 'Отметь, что есть дома'
              }</div>
              <p class="shelf-summary-sub">${
                hasBottles
                  ? `Можно смешать <strong>${avail}</strong> ${pluralize(avail, 'коктейль', 'коктейля', 'коктейлей')} прямо сейчас`
                  : 'Выбери пару базовых бутылок — и откроется фильтр «С моей полки».'
              }</p>
            </div>
            ${
              hasBottles
                ? `<button type="button" class="btn-primary btn-compact" data-show-available data-tactile>Открыть (${avail})</button>`
                : ''
            }
          </div>

          <div class="shelf-progress" aria-label="Покрытие барной карты">
            <div class="shelf-progress-track">
              <div class="shelf-progress-fill" style="width:${availPct}%"></div>
            </div>
            <span class="shelf-progress-meta">${avail} / ${totalRecipes}</span>
          </div>

          <div class="preset-row">
            <span class="preset-label">Быстрый старт</span>
            ${SHELF_PRESETS.map(
              (p) => `<button type="button" class="preset-chip" data-preset-id="${escapeHtml(p.id)}" data-tactile>${escapeHtml(p.label)}</button>`
            ).join('')}
          </div>
        </div>

        ${groupsHtml}

        <p class="shelf-footnote">
          ${icon('sparkles', 'icon-xs')} Безалкогольные коктейли 0,0% доступны всегда — отмечать их не нужно.
        </p>
        <div class="tab-bottom-spacer"></div>
      </div>
    `;
  }
}
