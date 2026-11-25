import type { BottleId, LensId, Recipe, TabId, VibeTag } from './types';
import { LENSES, SURPRISES, TAGS, TAG_MAP } from './data/tags';
import { RECIPES } from './data/recipes';
import { matchRecipes } from './data/matcher';
import { NebulaEngine } from './engine/nebula';
import {
  bindTactilePress,
  MorphingPillController,
  sheetDepth,
  TabTransitionController,
  ToastController,
  type ToastTone,
} from './engine/motion';
import { icon } from './components/icons';
import { RecipeModal } from './views/RecipeModal';
import { ResultsSheet } from './views/ResultsSheet';
import { MenuView } from './views/MenuView';
import { SavedView } from './views/SavedView';
import { applyNativeInsets, haptic, registerBackHandler } from './services/native';
import { loadSaved, saveSaved } from './services/storage';
import { escapeHtml, parseGlowColors } from './utils/html';

const EMPTY_SHELF = new Set<BottleId>();

interface AppState {
  tab: TabId;
  lens: LensId;
  selectedTag: VibeTag | null;
  saved: Set<string>;
  lastSurpriseIdx: number;
}

const state: AppState = {
  tab: 'nebula',
  lens: 'all',
  selectedTag: TAGS[0] ?? null,
  saved: loadSaved(),
  lastSurpriseIdx: -1,
};

let toastController: ToastController | null = null;

function showToast(msg: string, tone: ToastTone = 'default'): void {
  toastController?.show(msg, { tone });
}

function initApp(): void {
  applyNativeInsets();

  const appEl = document.getElementById('cosmos-app')!;
  const stageEl = document.getElementById('app-stage')!;
  sheetDepth.setStageEl(stageEl);
  bindTactilePress(appEl);

  const toastEl = document.getElementById('toast');
  const toastTextEl = document.getElementById('toast-text');
  if (toastEl && toastTextEl) {
    toastController = new ToastController(toastEl, toastTextEl);
  }

  const canvasEl = document.getElementById('nebula-canvas') as HTMLCanvasElement;
  const tagLayerEl = document.getElementById('tag-layer')!;
  const topObstacleEl = document.getElementById('nebula-top-chrome')!;
  const bottomObstacleEl = document.getElementById('bottom-stack')!;

  const nebulaScreenEl = document.getElementById('screen-nebula')!;
  const menuScreenEl = document.getElementById('screen-menu')!;
  const savedScreenEl = document.getElementById('screen-saved')!;
  const resultsRoot = document.getElementById('results-sheet')!;
  const recipeModalRoot = document.getElementById('recipe-modal')!;
  const mixRitualEl = document.getElementById('mix-ritual')!;
  const ritualVibeEl = document.getElementById('ritual-vibe')!;
  const ritualStatusEl = document.getElementById('ritual-status')!;

  let searchMode = false;
  let isMixing = false;
  let ritualTimers: ReturnType<typeof setTimeout>[] = [];
  let lastDetentMs = 0;

  const tabFlow = new TabTransitionController<TabId>(
    ['nebula', 'menu', 'saved'],
    {
      nebula: nebulaScreenEl,
      menu: menuScreenEl,
      saved: savedScreenEl,
    },
    { initial: 'nebula' }
  );

  const cosmicDockInnerEl = document.querySelector<HTMLElement>('.cosmic-dock')!;
  const dockPill = new MorphingPillController({
    container: cosmicDockInnerEl,
    itemSelector: '.nav-item',
    indicatorEl: document.getElementById('dock-pill-indicator'),
    mode: 'fixed-width',
  });

  let lensPill: MorphingPillController | null = null;

  const getSelectedList = (): VibeTag[] => (state.selectedTag ? [state.selectedTag] : []);
  const getCurrentMatches = () => matchRecipes(getSelectedList(), EMPTY_SHELF, false);

  const syncNebulaActive = () => {
    const shouldRun = state.tab === 'nebula' && !recipeModal.isOpen() && !resultsSheet.isOpen();
    nebula.setActive(shouldRun);
  };

  function clearRitualTimers(): void {
    for (const t of ritualTimers) {
      clearTimeout(t);
    }
    ritualTimers = [];
  }

  function cancelMixRitual(restoreNebula: boolean): void {
    clearRitualTimers();
    isMixing = false;
    mixRitualEl.classList.remove('active', 'finishing');
    mixRitualEl.classList.add('hidden');
    mixRitualEl.setAttribute('aria-hidden', 'true');
    if (restoreNebula) {
      bottomObstacleEl.classList.remove('is-mixing');
      topObstacleEl.classList.remove('is-mixing');
      nebula.setMixingMode(false);
      syncNebulaActive();
    }
  }

  function setRitualStatus(text: string): void {
    ritualStatusEl.classList.add('is-swapping');
    const swapTimer = setTimeout(() => {
      ritualStatusEl.textContent = text;
      ritualStatusEl.classList.remove('is-swapping');
    }, 95);
    ritualTimers.push(swapTimer);
  }

  const toggleSave = (recipeId: string) => {
    if (state.saved.has(recipeId)) {
      state.saved.delete(recipeId);
      haptic('light');
      showToast('Убрано из тайника');
    } else {
      state.saved.add(recipeId);
      haptic('success');
      showToast('Сохранено в тайник', 'success');
    }
    saveSaved(state.saved);
    updateDockBadges();
    if (state.tab === 'saved') savedView.render();
    if (state.tab === 'menu') menuView.render();
  };

  const recipeModal = new RecipeModal(recipeModalRoot, {
    isSaved: (id) => state.saved.has(id),
    onToggleSave: toggleSave,
    onOpenRecipe: (recipe) => openRecipe(recipe),
    onPickTag: (tagId) => {
      const tag = TAG_MAP.get(tagId);
      if (!tag) return;
      cancelMixRitual(true);
      resultsSheet.close();
      switchTab('nebula');
      searchMode = false;
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
    isSaved: (id) => state.saved.has(id),
    onToggleSave: toggleSave,
    onOpenRecipe: openRecipe,
    onHighlightRecipe: (recipe) => {
      nebula.setAccentGlow(recipe.visual.glowColor);
    },
    onClearTags: () => {
      searchMode = false;
      if (state.selectedTag?.custom) {
        state.selectedTag = nebula.getFocusedTag() || TAGS[0] || null;
      }
      nebula.syncSelectionStyles();
      renderConsole();
    },
    onClose: () => {
      cancelMixRitual(false);
      bottomObstacleEl.classList.remove('is-mixing');
      topObstacleEl.classList.remove('is-mixing');
      nebula.setMixingMode(false);
      nebula.syncSelectionStyles();
      syncNebulaActive();
    },
  });

  const menuView = new MenuView(menuScreenEl, {
    isSaved: (id) => state.saved.has(id),
    onToggleSave: toggleSave,
    onOpenRecipe: openRecipe,
  });

  const savedView = new SavedView(savedScreenEl, {
    getSavedIds: () => state.saved,
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
    onFocusChange: (tag, userInitiated) => {
      const isCustomSelected = Boolean(state.selectedTag?.custom);
      if (!searchMode && !isCustomSelected && tag) {
        state.selectedTag = tag;
        nebula.syncSelectionStyles();
        renderConsole();
        if (
          userInitiated &&
          state.tab === 'nebula' &&
          !resultsSheet.isOpen() &&
          !recipeModal.isOpen() &&
          !isMixing
        ) {
          const now = performance.now();
          if (now - lastDetentMs > 95) {
            lastDetentMs = now;
            haptic('detent');
          }
        }
      }
    },
  });

  function selectSingleTag(tag: VibeTag): void {
    state.selectedTag = tag;
    if (searchMode) {
      closeSearchMode(false);
    }
    haptic('medium');
    nebula.syncSelectionStyles();
    renderConsole();
  }

  function renderLenses(immediate = false): void {
    const strip = document.getElementById('lens-strip')!;
    if (!strip.querySelector('[data-lens]')) {
      const pillsHtml = LENSES.map(
        (l) =>
          `<button type="button" class="lens-pill ${state.lens === l.id ? 'active' : ''}" data-lens="${l.id}" role="tab" aria-selected="${state.lens === l.id ? 'true' : 'false'}">${escapeHtml(l.label)}</button>`
      ).join('');
      strip.innerHTML = `<span class="rail-glider" aria-hidden="true"></span>${pillsHtml}`;
      lensPill = new MorphingPillController({
        container: strip,
        itemSelector: '[data-lens]',
        indicatorSelector: '.rail-glider',
        mode: 'adaptive-width',
        autoScrollActive: true,
      });
      lensPill.sync(undefined, true);
      requestAnimationFrame(() => lensPill?.sync(undefined, true));
      return;
    }

    strip.querySelectorAll<HTMLElement>('[data-lens]').forEach((btn) => {
      const isActive = btn.dataset.lens === state.lens;
      btn.classList.toggle('active', isActive);
      btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });
    lensPill?.sync(undefined, immediate);
  }

  function renderConsole(): void {
    const confirmEl = document.getElementById('console-confirm')!;
    const searchEl = document.getElementById('console-search')!;
    const hint = document.getElementById('orbit-hint')!;
    const clearCustomBtn = document.getElementById('clear-custom-btn')!;

    if (searchMode) {
      confirmEl.classList.add('hidden');
      searchEl.classList.remove('hidden');
      hint.classList.add('is-hidden');
      requestAnimationFrame(() => nebula.measureBounds());
      return;
    }

    searchEl.classList.add('hidden');
    confirmEl.classList.remove('hidden');
    hint.classList.toggle('is-hidden', state.tab !== 'nebula');

    const activeTag = state.selectedTag || nebula.getFocusedTag() || TAGS[0];
    if (activeTag) {
      state.selectedTag = activeTag;
      const dotEl = document.getElementById('selected-vibe-dot')!;
      const titleEl = document.getElementById('selected-vibe-title')!;
      const subEl = document.getElementById('selected-vibe-sub')!;
      const mixBtnEl = document.getElementById('mix-btn')!;
      const [c1, c2] = parseGlowColors(activeTag.glow);

      dotEl.textContent = activeTag.emoji || '🍸';
      dotEl.style.background = activeTag.glow || 'linear-gradient(135deg,#c79bff,#7dd3fc)';
      dotEl.style.boxShadow = `0 0 18px ${c1}88`;
      mixBtnEl.style.setProperty('--cta-c1', c1);
      mixBtnEl.style.setProperty('--cta-c2', c2);

      titleEl.textContent = activeTag.label;
      subEl.textContent = activeTag.sub || 'свой запрос';
      clearCustomBtn.classList.toggle('hidden', !activeTag.custom);
      nebula.setAccentGlow(activeTag.glow);
    }

    requestAnimationFrame(() => nebula.measureBounds());
  }

  function updateDockBadges(): void {
    const savedBadge = document.getElementById('nav-badge-saved');
    if (savedBadge) {
      savedBadge.textContent = state.saved.size > 0 ? String(state.saved.size) : '';
      savedBadge.classList.toggle('hidden', state.saved.size === 0);
    }
  }

  function switchTab(next: TabId): void {
    if (isMixing && next !== 'nebula') {
      cancelMixRitual(true);
    }
    state.tab = next;

    if (next !== 'nebula' && searchMode) {
      closeSearchMode(false);
    }

    document.getElementById('cosmos-console')!.classList.toggle('hidden', next !== 'nebula');
    document.getElementById('orbit-hint')!.classList.toggle('hidden', next !== 'nebula');

    document.querySelectorAll<HTMLElement>('.nav-item').forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.nav === next);
    });
    dockPill.sync();

    if (next === 'menu') menuView.render();
    if (next === 'saved') savedView.render();
    if (next === 'nebula') {
      renderConsole();
      syncNebulaActive();
    }

    tabFlow.switchTo(next, () => {
      syncNebulaActive();
      if (next === 'nebula') {
        nebula.measureBounds();
        lensPill?.sync(undefined, true);
      }
    });
  }

  // ==================== 1-SECOND COSMIC SHAKER RITUAL ====================
  function launchMixRitual(): void {
    if (isMixing) return;
    if (state.tab !== 'nebula') {
      switchTab('nebula');
    }

    const activeTag = state.selectedTag || nebula.getFocusedTag() || TAGS[0];
    if (!activeTag) return;
    state.selectedTag = activeTag;

    isMixing = true;
    clearRitualTimers();

    const matches = getCurrentMatches();
    const [c1, c2] = parseGlowColors(activeTag.glow);

    // t = 0ms: tactile press, vortex swirl, fade chrome out, show cosmic cobbler shaker
    haptic('medium');
    nebula.setAccentGlow(activeTag.glow);
    nebula.setMixingMode(true);
    bottomObstacleEl.classList.add('is-mixing');
    topObstacleEl.classList.add('is-mixing');

    mixRitualEl.style.setProperty('--ritual-c1', c1);
    mixRitualEl.style.setProperty('--ritual-c2', c2);
    ritualVibeEl.textContent = `${activeTag.emoji} ${activeTag.label}`;
    ritualStatusEl.classList.remove('is-swapping');
    ritualStatusEl.textContent = 'Ловим вайб…';

    mixRitualEl.classList.remove('hidden', 'finishing', 'active');
    mixRitualEl.setAttribute('aria-hidden', 'false');
    requestAnimationFrame(() => {
      if (isMixing) {
        mixRitualEl.classList.add('active');
      }
    });

    // t = 240ms: Ice clink 1
    ritualTimers.push(
      setTimeout(() => {
        if (!isMixing) return;
        haptic('detent');
      }, 240)
    );

    // t = 420ms: Cross-fade caption + Ice clink 2
    ritualTimers.push(
      setTimeout(() => {
        if (!isMixing) return;
        setRitualStatus('Взбиваем со льдом…');
        haptic('detent');
      }, 420)
    );

    // t = 660ms: Ice clink 3
    ritualTimers.push(
      setTimeout(() => {
        if (!isMixing) return;
        haptic('detent');
      }, 660)
    );

    // t = 840ms: Final serve bloom + cross-fade directly into Full-Page Results!
    ritualTimers.push(
      setTimeout(() => {
        if (!isMixing) return;
        ritualStatusEl.classList.remove('is-swapping');
        ritualStatusEl.textContent = 'Подаём!';
        haptic('success');
        mixRitualEl.classList.add('finishing');
        resultsSheet.open(matches, getSelectedList(), { entrance: 'warp' });
      }, 840)
    );

    // t = 1140ms: Clean up ritual overlay and pause nebula canvas while full-page Results is open
    ritualTimers.push(
      setTimeout(() => {
        isMixing = false;
        ritualTimers = [];
        mixRitualEl.classList.remove('active', 'finishing');
        mixRitualEl.classList.add('hidden');
        mixRitualEl.setAttribute('aria-hidden', 'true');
        syncNebulaActive();
      }, 1140)
    );
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
    if (state.selectedTag?.custom) {
      state.selectedTag = null;
    }
    haptic('light');
    nebula.nudgeRotation(0.52, 0.08);
    renderLenses();
    nebula.rebuildTags(state.lens);
  });

  // Custom word search state + inline suggestions (no native Android datalist!)
  const wordInput = document.getElementById('word-input') as HTMLInputElement;
  const wordSuggestions = document.getElementById('word-suggestions')!;
  const dockEl = document.getElementById('cosmic-dock')!;

  function hideSuggestions(): void {
    wordSuggestions.classList.add('hidden');
    wordSuggestions.innerHTML = '';
  }

  function openSearchMode(): void {
    searchMode = true;
    haptic('light');
    renderConsole();
    if (state.selectedTag?.custom) {
      wordInput.value = state.selectedTag.label;
    }
    requestAnimationFrame(() => {
      wordInput.focus();
      if (wordInput.value.trim()) {
        renderSuggestions(wordInput.value);
      }
    });
  }

  function closeSearchMode(shouldRender = true): void {
    searchMode = false;
    wordInput.value = '';
    hideSuggestions();
    wordInput.blur();
    dockEl.classList.remove('keyboard-compact');
    if (shouldRender) {
      renderConsole();
    }
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
        `<button type="button" class="sugg-chip sugg-tag" data-sugg-tag="${escapeHtml(t.id)}"><span>${escapeHtml(t.emoji)}</span><span>${escapeHtml(t.label)}</span></button>`
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
    if (!val) {
      closeSearchMode();
      return;
    }
    const found = TAGS.find((t) => t.label.toLowerCase() === val.toLowerCase());
    state.selectedTag = found || {
      id: `custom:${val.toLowerCase()}`,
      emoji: '🍸',
      label: val,
      sub: 'свой запрос',
      glow: 'linear-gradient(135deg,#c79bff,#7dd3fc)',
      lenses: ['all'],
      custom: true,
    };
    closeSearchMode(false);
    haptic('medium');
    nebula.syncSelectionStyles();
    if (found) nebula.focusTag(found.id);
    renderConsole();
  }

  document.getElementById('open-search-btn')!.addEventListener('click', openSearchMode);

  document.getElementById('close-search-btn')!.addEventListener('click', () => {
    haptic('light');
    closeSearchMode();
  });

  document.getElementById('clear-custom-btn')!.addEventListener('click', () => {
    haptic('light');
    state.selectedTag = nebula.getFocusedTag() || TAGS[0] || null;
    nebula.syncSelectionStyles();
    renderConsole();
  });

  // Handle suggestion taps on pointerdown so they trigger before input blur hides the tray
  wordSuggestions.addEventListener('pointerdown', (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>('.sugg-chip');
    if (!btn) return;
    e.preventDefault();
    if (btn.dataset.suggTag) {
      const tag = TAG_MAP.get(btn.dataset.suggTag);
      if (tag) {
        state.selectedTag = tag;
        closeSearchMode(false);
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
        closeSearchMode(true);
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

  // Primary "Заебенить" CTA -> 1-second Cosmic Shaker Ritual
  document.getElementById('mix-btn')!.addEventListener('click', launchMixRitual);

  // Surprise button: picks ONE vibrant tag and smoothly rotates the nebula right to it!
  document.getElementById('spin-surprise-btn')!.addEventListener('click', () => {
    if (isMixing) cancelMixRitual(true);
    if (searchMode) closeSearchMode(false);
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
    nebula.setMixingMode(false);
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

  // Minimalist 3-Tab Navigation dock clicks
  document.querySelectorAll<HTMLElement>('.nav-item').forEach((btn) => {
    btn.addEventListener('click', () => {
      const nav = btn.dataset.nav as TabId | undefined;
      if (!nav || nav === state.tab) return;
      haptic('light');
      if (resultsSheet.isOpen()) resultsSheet.close();
      switchTab(nav);
    });
  });

  // Android Hardware Back Button handler
  registerBackHandler(() => {
    if (isMixing) {
      cancelMixRitual(true);
      return true;
    }
    if (recipeModal.isOpen()) {
      recipeModal.close();
      return true;
    }
    if (resultsSheet.isOpen()) {
      resultsSheet.close();
      return true;
    }
    if (searchMode) {
      closeSearchMode();
      return true;
    }
    if (state.tab !== 'nebula') {
      switchTab('nebula');
      return true;
    }
    if (state.selectedTag?.custom) {
      state.selectedTag = nebula.getFocusedTag() || TAGS[0] || null;
      nebula.syncSelectionStyles();
      renderConsole();
      return true;
    }
    return false;
  });

  // Escape key support
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (isMixing) cancelMixRitual(true);
      else if (recipeModal.isOpen()) recipeModal.close();
      else if (resultsSheet.isOpen()) resultsSheet.close();
      else if (searchMode) closeSearchMode();
      else if (state.selectedTag?.custom) {
        state.selectedTag = nebula.getFocusedTag() || TAGS[0] || null;
        nebula.syncSelectionStyles();
        renderConsole();
      }
    }
  });

  // Keep morphing pills aligned on viewport resize
  window.addEventListener('resize', () => {
    dockPill.sync(undefined, true);
    lensPill?.sync(undefined, true);
  });

  // Initial render & boot
  renderLenses(true);
  nebula.rebuildTags('all');
  renderConsole();
  updateDockBadges();
  dockPill.sync(undefined, true);
  requestAnimationFrame(() => dockPill.sync(undefined, true));
  nebula.start();
}

window.addEventListener('DOMContentLoaded', initApp);
