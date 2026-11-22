import type { BottleId } from '../types';
import { BOTTLE_MAP } from '../data/bottles';
import { RECIPE_MAP } from '../data/recipes';

const KEY_SHELF = 'nightcap_shelf_v1';
const KEY_SAVED = 'nightcap_saved_v1';
const KEY_SHELF_ONLY = 'nightcap_shelf_only_v1';

export function loadShelf(): Set<BottleId> {
  try {
    const raw = localStorage.getItem(KEY_SHELF);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((id): id is BottleId => typeof id === 'string' && BOTTLE_MAP.has(id as BottleId)));
  } catch {
    return new Set();
  }
}

export function saveShelf(shelf: Set<BottleId>): void {
  try {
    localStorage.setItem(KEY_SHELF, JSON.stringify([...shelf]));
  } catch {
    // Ignore storage quota / private mode errors
  }
}

export function loadSaved(): Set<string> {
  try {
    const raw = localStorage.getItem(KEY_SAVED);
    if (!raw) return new Set();
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((id): id is string => typeof id === 'string' && RECIPE_MAP.has(id)));
  } catch {
    return new Set();
  }
}

export function saveSaved(saved: Set<string>): void {
  try {
    localStorage.setItem(KEY_SAVED, JSON.stringify([...saved]));
  } catch {
    // Ignore storage errors
  }
}

export function loadShelfOnly(): boolean {
  try {
    return localStorage.getItem(KEY_SHELF_ONLY) === '1';
  } catch {
    return false;
  }
}

export function saveShelfOnly(val: boolean): void {
  try {
    localStorage.setItem(KEY_SHELF_ONLY, val ? '1' : '0');
  } catch {
    // Ignore storage errors
  }
}
