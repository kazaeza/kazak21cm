import type { BottleId, LensId, Recipe, TabId, VibeTag } from './types';
import { LENSES, SURPRISES, TAGS, TAG_MAP } from './data/tags';
import { RECIPES } from './data/recipes';
import { matchRecipes } from './data/matcher';
import { NebulaEngine } from './engine/nebula';
import { icon } from './components/icons';
import { RecipeModal } from './views/RecipeModal';
import { ResultsSheet } from './views/ResultsSheet';
import { ShelfView } from './views/ShelfView';
import { MenuView } from './views/MenuView';
import { SavedView } from './views/SavedView';
import { applyNativeInsets, haptic, registerBackHandler } from './services/native';
import { loadSaved, loadShelf, loadShelfOnly, saveSaved, saveShelf, saveShelfOnly } from './services/storage';
import { escapeHtml } from './utils/html';

interface AppState {
  tab: TabId;
  lens: LensId;
  selected: Map<string, VibeTag>;
  shelf: Set<BottleId>;
  shelfOnly: boolean;
  saved: Set<string>;
  lastSurpriseIdx: number;
}

const state: AppState = {
  tab: 'nebula',
  lens: 'all',
  selected: new Map(),
  shelf: loadShelf(),
  shelfOnly: loadShelfOnly(),
  saved: loadSaved(),
  lastSurpriseIdx: -1,
};

let toastTimer: ReturnType<typeof setTimeout> | null = null;

function showToast(msg: string): void {
  const el = document.getElementById('toast');
  const txt = document.getElementById('toast-text');
  if (!el || !txt) return;
  txt.textContent = msg;
  el.classList.add('visible');
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    el.classList.remove('visible');
  }, 2200);
}

function initApp(): void {
  applyNativeInsets();

  const appEl = document.getElementById('cosmos-app')!;
  const canvasEl = document.getElementById('nebula-canvas') as HTMLCanvasElement;
  const tagLayerEl = document.getElementById('tag-layer')!;
  const topObstacleEl = document.getElementById('nebula-top-chrome')!;
  const bottomObstacleEl = document.getElementById('bottom-stack')!;

  const nebulaScreenEl = document.getElementById('screen-nebula')!;
  const shelfScreenEl = document.getElementById('screen-shelf')!;
  const menuScreenEl = document.getElementById('screen-menu')!;
  const savedScreenEl = document.getElementById('screen-saved')!;
  const resultsRoot = document.getElementById('results-sheet')!;
  const recipeModalRoot = document.getElementById('recipe-modal')!;

  const getCurrentMatches = () => matchRecipes([...state.selected.values()], state.shelf, state.shelfOnly);

  const syncNebulaActive = () => {
    const shouldRun = state.tab === 'nebula' && !recipeModal.isOpen();
    nebula.setActive(shouldRun);
  };

  const toggleSave = (recipeId: string) => {
    if (state.saved.has(recipeId)) {
      state.saved.delete(recipeId);
      haptic('light');
      showToast('Убрано из тайника');
    } else {
      state.saved.add(recipeId);
      haptic('success');
      showToast('Сохранено в тайник');
    }
    saveSaved(state.saved);
    updateDockBadges();
    if (state.tab === 'saved') savedView.render();
    if (state.tab === 'menu') menuView.render();
  };

  const recipeModal = new RecipeModal(recipeModalRoot, {
    isSaved: (id) => state.saved.has(id),
    getShelf: () => state.shelf,
    onToggleSave: toggleSave,
    onOpenRecipe: (recipe) => openRecipe(recipe),
    onPickTag: (tagId) => {
      const tag = TAG_MAP.get(tagId);
      if (!tag) return;
      resultsSheet.close();
      switchTab('nebula');
      state.lens = 'all';
      state.selected.clear();
      state.selected.set(tag.id, tag);
      renderLenses();
      nebula.rebuildTags('all');
      renderCaught();
      haptic('medium');
      showToast(`Вайб: ${tag.label}`);
    },
    onClose: () => {
      syncNebulaActive();
    },
  });

  const openRecipe = (recipe: Recipe) => {
    haptic('light');
    recipeModal.open(recipe);
    syncNebulaActive();
  };

  const resultsSheet = new ResultsSheet(resultsRoot, {
    getShelf: () => state.shelf,
    isShelfOnly: () => state.shelfOnly,
    isSaved: (id) => state.saved.has(id),
    onToggleShelfOnly: () => {
      state.shelfOnly = !state.shelfOnly;
      saveShelfOnly(state.shelfOnly);
      haptic('light');
      renderCaught();
      resultsSheet.update(getCurrentMatches(), [...state.selected.values()]);
    },
    onToggleSave: toggleSave,
    onOpenRecipe: openRecipe,
    onClearTags: () => {
      state.selected.clear();
      nebula.syncSelectionStyles();
      renderCaught();
    },
    onClose: () => {},
  });

  const shelfView = new ShelfView(shelfScreenEl, {
    getShelf: () => state.shelf,
    onToggleBottle: (id) => {
      if (state.shelf.has(id)) state.shelf.delete(id);
      else state.shelf.add(id);
      if (state.shelf.size === 0) {
        state.shelfOnly = false;
        saveShelfOnly(false);
      }
      saveShelf(state.shelf);
      haptic('light');
      shelfView.render();
      renderCaught();
      updateDockBadges();
    },
    onApplyPreset: (ids) => {
      state.shelf = new Set(ids);
      saveShelf(state.shelf);
      haptic('success');
      shelfView.render();
      renderCaught();
      updateDockBadges();
      showToast('Набор бутылок добавлен на полку');
    },
    onClearShelf: () => {
      state.shelf.clear();
      state.shelfOnly = false;
      saveShelf(state.shelf);
      saveShelfOnly(false);
      haptic('light');
      shelfView.render();
      renderCaught();
      updateDockBadges();
      showToast('Полка очищена');
    },
    onShowAvailable: () => {
      haptic('medium');
      menuView.setFilter('shelf');
      switchTab('menu');
    },
  });

  const menuView = new MenuView(menuScreenEl, {
    getShelf: () => state.shelf,
    isSaved: (id) => state.saved.has(id),
    onToggleSave: toggleSave,
    onOpenRecipe: openRecipe,
  });

  const savedView = new SavedView(savedScreenEl, {
    getSavedIds: () => state.saved,
    getShelf: () => state.shelf,
    onToggleSave: toggleSave,
    onOpenRecipe: openRecipe,
    onGoNebula: () => switchTab('nebula'),
    onGoMenu: () => switchTab('menu'),
  });

  const nebula = new NebulaEngine({
    appEl,
    canvasEl,
    tagLayerEl,
    topObstacleEl,
    bottomObstacleEl,
    isSelected: (tagId) => state.selected.has(tagId),
    onToggleTag: (tag) => toggleTag(tag),
  });

  function toggleTag(tag: VibeTag): void {
    if (state.selected.has(tag.id)) {
      state.selected.delete(tag.id);
      haptic('light');
    } else {
      state.selected.set(tag.id, tag);
      haptic('medium');
    }
    nebula.syncSelectionStyles();
    renderCaught();
  }

  function renderLenses(): void {
    const strip = document.getElementById('lens-strip')!;
    strip.innerHTML = LENSES.map(
      (l) =>
        `<button type="button" class="lens-pill ${state.lens === l.id ? 'active' : ''}" data-lens="${l.id}">${escapeHtml(l.label)}</button>`
    ).join('');
  }

  function renderCaught(): void {
    const row = document.getElementById('caught-row')!;
    const clearBtn = document.getElementById('clear-btn')!;
    const hint = document.getElementById('orbit-hint')!;
    const shelfChip = document.getElementById('console-shelf-chip')!;
    const mixCountEl = document.getElementById('mix-count')!;
    const orbLabelEl = document.getElementById('orb-count-label')!;

    const matches = getCurrentMatches();
    mixCountEl.textContent = String(matches.length);
    orbLabelEl.textContent =
      state.selected.size || (state.shelfOnly && state.shelf.size > 0) ? `${matches.length} шт.` : 'Микс';

    // Shelf toggle chip in bottom console (only visible when user added bottles to shelf)
    if (state.shelf.size > 0) {
      shelfChip.classList.remove('hidden');
      shelfChip.classList.toggle('active', state.shelfOnly);
      shelfChip.innerHTML = `${icon('wine', 'icon-xs')}<span>${state.shelfOnly ? 'С полки' : 'Полка'}</span>`;
    } else {
      shelfChip.classList.add('hidden');
    }

    if (!state.selected.size) {
      clearBtn.classList.add('hidden');
      hint.classList.remove('is-hidden');
      row.innerHTML = '<span class="caught-placeholder">Что хочется поймать из туманности?</span>';
    } else {
      clearBtn.classList.remove('hidden');
      hint.classList.add('is-hidden');
      row.innerHTML = [...state.selected.values()]
        .map(
          (tag) =>
            `<button type="button" class="caught-chip" data-remove-tag="${escapeHtml(tag.id)}" style="background:${tag.glow || 'linear-gradient(135deg,#fbc2eb,#a6c1ee)'}"><span>${escapeHtml(tag.label)}</span><span class="caught-x">×</span></button>`
        )
        .join('');
    }

    // Recalculate vertical bounds after console height changes
    requestAnimationFrame(() => nebula.measureBounds());
  }

  function updateDockBadges(): void {
    const shelfBadge = document.getElementById('nav-badge-shelf');
    const savedBadge = document.getElementById('nav-badge-saved');
    if (shelfBadge) {
      shelfBadge.textContent = state.shelf.size > 0 ? String(state.shelf.size) : '';
      shelfBadge.classList.toggle('hidden', state.shelf.size === 0);
    }
    if (savedBadge) {
      savedBadge.textContent = state.saved.size > 0 ? String(state.saved.size) : '';
      savedBadge.classList.toggle('hidden', state.saved.size === 0);
    }
  }

  function switchTab(next: TabId): void {
    state.tab = next;

    nebulaScreenEl.classList.toggle('hidden', next !== 'nebula');
    shelfScreenEl.classList.toggle('hidden', next !== 'shelf');
    menuScreenEl.classList.toggle('hidden', next !== 'menu');
    savedScreenEl.classList.toggle('hidden', next !== 'saved');

    document.getElementById('cosmos-console')!.classList.toggle('hidden', next !== 'nebula');
    document.getElementById('orbit-hint')!.classList.toggle('hidden', next !== 'nebula');

    document.querySelectorAll<HTMLElement>('.nav-item').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.nav === next);
    });

    if (next === 'shelf') shelfView.render();
    if (next === 'menu') menuView.render();
    if (next === 'saved') savedView.render();
    if (next === 'nebula') {
      renderCaught();
    }

    syncNebulaActive();
  }

  function openResultsFromOrb(): void {
    haptic('medium');
    if (state.tab !== 'nebula') {
      switchTab('nebula');
    }
    resultsSheet.open(getCurrentMatches(), [...state.selected.values()]);
  }

  // Hydrate static icon placeholders
  document.querySelectorAll<HTMLElement>('[data-icon]').forEach((el) => {
    const name = el.dataset.icon as Parameters<typeof icon>[0];
    const cls = el.dataset.iconClass || 'icon-sm';
    el.innerHTML = icon(name, cls);
  });

  // Populate datalist autocomplete
  const dl = document.getElementById('word-list');
  if (dl) {
    const suggestions = [...TAGS.map((t) => t.label), ...RECIPES.map((r) => r.name)];
    dl.innerHTML = suggestions.map((v) => `<option value="${escapeHtml(v)}"></option>`).join('');
  }

  // Lens clicks
  document.getElementById('lens-strip')!.addEventListener('click', (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>('[data-lens]');
    if (!btn?.dataset.lens) return;
    state.lens = btn.dataset.lens as LensId;
    haptic('light');
    nebula.nudgeRotation(0.55, 0.08);
    renderLenses();
    nebula.rebuildTags(state.lens);
  });

  // Caught chip removal
  document.getElementById('caught-row')!.addEventListener('click', (e) => {
    const chip = (e.target as HTMLElement).closest<HTMLElement>('[data-remove-tag]');
    if (!chip?.dataset.removeTag) return;
    const tag = state.selected.get(chip.dataset.removeTag);
    if (tag) toggleTag(tag);
  });

  // Console shelf-only toggle
  document.getElementById('console-shelf-chip')!.addEventListener('click', () => {
    state.shelfOnly = !state.shelfOnly;
    saveShelfOnly(state.shelfOnly);
    haptic('light');
    renderCaught();
    showToast(state.shelfOnly ? 'Показываем только с твоей полки' : 'Показываем весь космос');
  });

  // Clear caught tags
  document.getElementById('clear-btn')!.addEventListener('click', () => {
    state.selected.clear();
    haptic('light');
    nebula.syncSelectionStyles();
    renderCaught();
  });

  // Custom word input form
  const wordInput = document.getElementById('word-input') as HTMLInputElement;
  const dockEl = document.getElementById('cosmic-dock')!;

  wordInput.addEventListener('focus', () => {
    dockEl.classList.add('keyboard-compact');
  });
  wordInput.addEventListener('blur', () => {
    dockEl.classList.remove('keyboard-compact');
    requestAnimationFrame(() => nebula.measureBounds());
  });

  document.getElementById('word-form')!.addEventListener('submit', (e) => {
    e.preventDefault();
    const val = wordInput.value.trim();
    if (!val) return;
    const found = TAGS.find((t) => t.label.toLowerCase() === val.toLowerCase());
    const tag: VibeTag = found || {
      id: `custom:${val.toLowerCase()}`,
      label: val,
      sub: 'свой запрос',
      glow: 'linear-gradient(135deg,#8ec5fc,#e0c3fc)',
      lenses: ['all'],
      custom: true,
    };
    state.selected.set(tag.id, tag);
    wordInput.value = '';
    wordInput.blur();
    haptic('medium');
    nebula.syncSelectionStyles();
    renderCaught();
  });

  // Open results triggers
  document.getElementById('mix-btn')!.addEventListener('click', openResultsFromOrb);
  document.getElementById('center-orb-btn')!.addEventListener('click', openResultsFromOrb);

  // Surprise spin button (non-repeating!)
  document.getElementById('spin-surprise-btn')!.addEventListener('click', () => {
    state.lens = 'all';
    renderLenses();
    state.selected.clear();

    let nextIdx = Math.floor(Math.random() * SURPRISES.length);
    if (nextIdx === state.lastSurpriseIdx && SURPRISES.length > 1) {
      nextIdx = (nextIdx + 1) % SURPRISES.length;
    }
    state.lastSurpriseIdx = nextIdx;

    const pick = SURPRISES[nextIdx];
    for (const id of pick.ids) {
      const t = TAG_MAP.get(id);
      if (t) state.selected.set(t.id, t);
    }

    nebula.setPaused(false);
    const pauseEl = document.getElementById('pause-icon');
    if (pauseEl) pauseEl.innerHTML = icon('pause', 'icon-sm');

    nebula.spinImpulse(0.0058, 0.0014);
    nebula.rebuildTags('all');
    renderCaught();
    haptic('success');
    showToast(pick.text);
  });

  // Pause / resume rotation
  document.getElementById('pause-btn')!.addEventListener('click', () => {
    const nextPaused = !nebula.isPaused();
    nebula.setPaused(nextPaused);
    const pauseEl = document.getElementById('pause-icon');
    if (pauseEl) pauseEl.innerHTML = icon(nextPaused ? 'play' : 'pause', 'icon-sm');
    haptic('light');
    showToast(nextPaused ? 'Туманность на паузе' : 'Туманность снова плывёт');
  });

  // Navigation dock clicks
  document.querySelectorAll<HTMLElement>('.nav-item').forEach((btn) => {
    btn.addEventListener('click', () => {
      const nav = btn.dataset.nav as TabId | undefined;
      if (!nav || nav === state.tab) return;
      haptic('light');
      resultsSheet.close();
      switchTab(nav);
    });
  });

  // Android Hardware Back Button handler
  registerBackHandler(() => {
    if (recipeModal.isOpen()) {
      recipeModal.close();
      return true;
    }
    if (resultsSheet.isOpen()) {
      resultsSheet.close();
      return true;
    }
    if (state.tab !== 'nebula') {
      switchTab('nebula');
      return true;
    }
    return false;
  });

  // Escape key support for desktop preview
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (recipeModal.isOpen()) recipeModal.close();
      else if (resultsSheet.isOpen()) resultsSheet.close();
    }
  });

  // Initial render & boot
  renderLenses();
  nebula.rebuildTags('all');
  renderCaught();
  updateDockBadges();
  nebula.start();
}

window.addEventListener('DOMContentLoaded', initApp);
