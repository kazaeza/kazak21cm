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
  selectedTag: VibeTag | null;
  shelf: Set<BottleId>;
  shelfOnly: boolean;
  saved: Set<string>;
  lastSurpriseIdx: number;
}

const state: AppState = {
  tab: 'nebula',
  lens: 'all',
  selectedTag: null,
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

  const getSelectedList = (): VibeTag[] => (state.selectedTag ? [state.selectedTag] : []);
  const getCurrentMatches = () => matchRecipes(getSelectedList(), state.shelf, state.shelfOnly);

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
      state.selectedTag = tag;
      renderLenses();
      nebula.rebuildTags('all');
      nebula.focusTag(tag.id);
      renderConsole();
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
      renderConsole();
      resultsSheet.update(getCurrentMatches(), getSelectedList());
    },
    onToggleSave: toggleSave,
    onOpenRecipe: openRecipe,
    onClearTags: () => {
      state.selectedTag = null;
      nebula.syncSelectionStyles();
      renderConsole();
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
      renderConsole();
      updateDockBadges();
    },
    onApplyPreset: (ids) => {
      state.shelf = new Set(ids);
      saveShelf(state.shelf);
      haptic('success');
      shelfView.render();
      renderConsole();
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
      renderConsole();
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
    isSelected: (tagId) => state.selectedTag?.id === tagId,
    onToggleTag: (tag) => selectSingleTag(tag),
  });

  // Always single-tag selection: tapping a new tag immediately replaces the old one,
  // tapping the already selected tag clears selection.
  function selectSingleTag(tag: VibeTag): void {
    if (state.selectedTag?.id === tag.id) {
      state.selectedTag = null;
      haptic('light');
    } else {
      state.selectedTag = tag;
      haptic('medium');
    }
    nebula.syncSelectionStyles();
    renderConsole();
  }

  function renderLenses(): void {
    const strip = document.getElementById('lens-strip')!;
    strip.innerHTML = LENSES.map(
      (l) =>
        `<button type="button" class="lens-pill ${state.lens === l.id ? 'active' : ''}" data-lens="${l.id}">${escapeHtml(l.label)}</button>`
    ).join('');
  }

  function syncShelfChip(chipEl: HTMLElement): void {
    if (state.shelf.size > 0) {
      chipEl.classList.remove('hidden');
      chipEl.classList.toggle('active', state.shelfOnly);
      chipEl.innerHTML = `${icon('wine', 'icon-xs')}<span>${state.shelfOnly ? 'С полки' : 'Полка'}</span>`;
    } else {
      chipEl.classList.add('hidden');
    }
  }

  function renderConsole(): void {
    const idleEl = document.getElementById('console-idle')!;
    const confirmEl = document.getElementById('console-confirm')!;
    const hint = document.getElementById('orbit-hint')!;
    const idleShelfChip = document.getElementById('idle-shelf-chip')!;
    const confirmShelfChip = document.getElementById('confirm-shelf-chip')!;
    const mixCountEl = document.getElementById('mix-count')!;
    const orbLabelEl = document.getElementById('orb-count-label')!;

    const matches = getCurrentMatches();
    mixCountEl.textContent = String(matches.length);
    orbLabelEl.textContent = state.selectedTag || (state.shelfOnly && state.shelf.size > 0) ? `${matches.length} шт.` : 'Микс';

    syncShelfChip(idleShelfChip);
    syncShelfChip(confirmShelfChip);

    if (!state.selectedTag) {
      idleEl.classList.remove('hidden');
      confirmEl.classList.add('hidden');
      hint.classList.remove('is-hidden');
    } else {
      idleEl.classList.add('hidden');
      confirmEl.classList.remove('hidden');
      hint.classList.add('is-hidden');

      const dotEl = document.getElementById('selected-vibe-dot')!;
      const titleEl = document.getElementById('selected-vibe-title')!;
      const subEl = document.getElementById('selected-vibe-sub')!;
      dotEl.style.background = state.selectedTag.glow || 'linear-gradient(135deg,#f7b2d9,#87b8ff)';
      titleEl.textContent = state.selectedTag.label;
      subEl.textContent = state.selectedTag.sub || 'свой запрос';
    }

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
      renderConsole();
    }

    syncNebulaActive();
  }

  function openResults(): void {
    haptic('success');
    if (state.tab !== 'nebula') {
      switchTab('nebula');
    }
    resultsSheet.open(getCurrentMatches(), getSelectedList());
  }

  function toggleShelfFilter(): void {
    state.shelfOnly = !state.shelfOnly;
    saveShelfOnly(state.shelfOnly);
    haptic('light');
    renderConsole();
    showToast(state.shelfOnly ? 'Показываем только с твоей полки' : 'Показываем весь космос');
  }

  // Hydrate static icon placeholders
  document.querySelectorAll<HTMLElement>('[data-icon]').forEach((el) => {
    const name = el.dataset.icon as Parameters<typeof icon>[0];
    const cls = el.dataset.iconClass || 'icon-sm';
    el.innerHTML = icon(name, cls);
  });

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

  // Console shelf-only toggles (idle + confirm)
  document.getElementById('idle-shelf-chip')!.addEventListener('click', toggleShelfFilter);
  document.getElementById('confirm-shelf-chip')!.addEventListener('click', toggleShelfFilter);

  // Clear selected tag
  document.getElementById('clear-btn')!.addEventListener('click', () => {
    state.selectedTag = null;
    haptic('light');
    nebula.syncSelectionStyles();
    renderConsole();
  });

  // Custom word input form + live inline cosmic suggestions (no native Android datalist!)
  const wordInput = document.getElementById('word-input') as HTMLInputElement;
  const wordSuggestions = document.getElementById('word-suggestions')!;
  const dockEl = document.getElementById('cosmic-dock')!;

  function hideSuggestions(): void {
    wordSuggestions.classList.add('hidden');
    wordSuggestions.innerHTML = '';
  }

  function renderSuggestions(raw: string): void {
    const q = raw.trim().toLowerCase();
    if (!q) {
      hideSuggestions();
      return;
    }

    const tagHits = TAGS.filter(
      (t) => t.label.toLowerCase().includes(q) || t.sub.toLowerCase().includes(q)
    ).slice(0, 3);

    const recipeHits = RECIPES.filter(
      (r) => r.name.toLowerCase().includes(q) || r.nameEn.toLowerCase().includes(q)
    ).slice(0, Math.max(0, 5 - tagHits.length));

    if (!tagHits.length && !recipeHits.length) {
      wordSuggestions.innerHTML = `
        <button type="button" class="sugg-chip sugg-custom" data-sugg-custom="${escapeHtml(raw.trim())}">
          ${icon('search', 'icon-xs')}
          <span>Искать «${escapeHtml(raw.trim())}»</span>
        </button>
      `;
      wordSuggestions.classList.remove('hidden');
      return;
    }

    const parts: string[] = [];
    for (const t of tagHits) {
      parts.push(
        `<button type="button" class="sugg-chip sugg-tag" data-sugg-tag="${escapeHtml(t.id)}">${icon('sparkles', 'icon-xs')}<span>${escapeHtml(t.label)}</span></button>`
      );
    }
    for (const r of recipeHits) {
      parts.push(
        `<button type="button" class="sugg-chip sugg-recipe" data-sugg-recipe="${escapeHtml(r.id)}">${icon('martini', 'icon-xs')}<span>${escapeHtml(r.name)}</span></button>`
      );
    }

    wordSuggestions.innerHTML = parts.join('');
    wordSuggestions.classList.remove('hidden');
  }

  function submitCustomWord(raw: string): void {
    const val = raw.trim();
    if (!val) return;
    const found = TAGS.find((t) => t.label.toLowerCase() === val.toLowerCase());
    state.selectedTag = found || {
      id: `custom:${val.toLowerCase()}`,
      label: val,
      sub: 'свой запрос',
      glow: 'linear-gradient(135deg,#8ec5fc,#e0c3fc)',
      lenses: ['all'],
      custom: true,
    };
    wordInput.value = '';
    hideSuggestions();
    wordInput.blur();
    haptic('medium');
    nebula.syncSelectionStyles();
    if (found) nebula.focusTag(found.id);
    renderConsole();
  }

  // Handle suggestion taps on pointerdown so they trigger before input blur hides the tray
  wordSuggestions.addEventListener('pointerdown', (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>('.sugg-chip');
    if (!btn) return;
    e.preventDefault();
    if (btn.dataset.suggTag) {
      const tag = TAG_MAP.get(btn.dataset.suggTag);
      if (tag) {
        state.selectedTag = tag;
        wordInput.value = '';
        hideSuggestions();
        wordInput.blur();
        haptic('medium');
        nebula.syncSelectionStyles();
        nebula.focusTag(tag.id);
        renderConsole();
      }
      return;
    }
    if (btn.dataset.suggRecipe) {
      const recipe = RECIPES.find((r) => r.id === btn.dataset.suggRecipe);
      if (recipe) {
        wordInput.value = '';
        hideSuggestions();
        wordInput.blur();
        openRecipe(recipe);
      }
      return;
    }
    if (btn.dataset.suggCustom) {
      submitCustomWord(btn.dataset.suggCustom);
    }
  });

  wordInput.addEventListener('focus', () => {
    dockEl.classList.add('keyboard-compact');
    if (wordInput.value.trim()) renderSuggestions(wordInput.value);
  });

  wordInput.addEventListener('input', () => {
    renderSuggestions(wordInput.value);
  });

  wordInput.addEventListener('blur', () => {
    dockEl.classList.remove('keyboard-compact');
    setTimeout(() => {
      if (document.activeElement !== wordInput) hideSuggestions();
      nebula.measureBounds();
    }, 120);
  });

  document.getElementById('word-form')!.addEventListener('submit', (e) => {
    e.preventDefault();
    submitCustomWord(wordInput.value);
  });

  // Confirm "Заебенить" & Center Orb
  document.getElementById('mix-btn')!.addEventListener('click', openResults);
  document.getElementById('center-orb-btn')!.addEventListener('click', openResults);

  // Surprise button: picks ONE vibrant tag and smoothly rotates the nebula right to it!
  document.getElementById('spin-surprise-btn')!.addEventListener('click', () => {
    state.lens = 'all';
    renderLenses();

    let nextIdx = Math.floor(Math.random() * SURPRISES.length);
    if (nextIdx === state.lastSurpriseIdx && SURPRISES.length > 1) {
      nextIdx = (nextIdx + 1) % SURPRISES.length;
    }
    state.lastSurpriseIdx = nextIdx;

    const pick = SURPRISES[nextIdx];
    const tag = TAG_MAP.get(pick.ids[0]) || TAGS[0];
    state.selectedTag = tag;

    nebula.setPaused(false);
    const pauseEl = document.getElementById('pause-icon');
    if (pauseEl) pauseEl.innerHTML = icon('pause', 'icon-sm');

    nebula.rebuildTags('all');
    nebula.focusTag(tag.id);
    renderConsole();
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
    if (state.selectedTag) {
      state.selectedTag = null;
      nebula.syncSelectionStyles();
      renderConsole();
      return true;
    }
    return false;
  });

  // Escape key support
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (recipeModal.isOpen()) recipeModal.close();
      else if (resultsSheet.isOpen()) resultsSheet.close();
      else if (state.selectedTag) {
        state.selectedTag = null;
        nebula.syncSelectionStyles();
        renderConsole();
      }
    }
  });

  // Initial render & boot
  renderLenses();
  nebula.rebuildTags('all');
  renderConsole();
  updateDockBadges();
  nebula.start();
}

window.addEventListener('DOMContentLoaded', initApp);
