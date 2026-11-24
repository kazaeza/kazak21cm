import { describe, expect, test } from 'bun:test';
import { RECIPES } from '../src/data/recipes';
import { TAGS, TAG_MAP, SURPRISES } from '../src/data/tags';
import { BOTTLES, BOTTLE_MAP } from '../src/data/bottles';
import { countAvailableForShelf, getShelfStatus, getSimilarRecipes, matchRecipes, recipeMatchesQuery } from '../src/data/matcher';
import { renderCocktailGlass } from '../src/components/CocktailGlass';
import { parseGlowColors } from '../src/engine/warpBurst';
import type { BottleId } from '../src/types';

describe('Nightcap data integrity', () => {
  test('has a rich balanced catalog with unique IDs', () => {
    expect(RECIPES.length).toBeGreaterThanOrEqual(75);
    const ids = new Set(RECIPES.map((r) => r.id));
    expect(ids.size).toBe(RECIPES.length);
  });

  test('every recipe has valid tags, valid bottles, steps, and visual spec', () => {
    for (const r of RECIPES) {
      expect(r.name.length).toBeGreaterThan(1);
      expect(r.nameEn.length).toBeGreaterThan(1);
      expect(r.why.length).toBeGreaterThan(10);
      expect(r.ingredients.length).toBeGreaterThanOrEqual(2);
      expect(r.steps.length).toBeGreaterThanOrEqual(2);
      expect(r.visual.liquidStops.length).toBeGreaterThanOrEqual(2);
      for (const tid of r.tags) expect(TAG_MAP.has(tid)).toBe(true);
      for (const bid of r.bottles) expect(BOTTLE_MAP.has(bid)).toBe(true);
    }
  });

  test('all 36 vibe tags have emojis and strictly 5–12 curated recipes each (no bloat)', () => {
    expect(TAGS.length).toBe(36);
    for (const tag of TAGS) {
      expect(tag.emoji.length).toBeGreaterThan(0);
      const count = RECIPES.filter((r) => r.tags.includes(tag.id)).length;
      expect(count).toBeGreaterThanOrEqual(5);
      expect(count).toBeLessThanOrEqual(12);
    }
    for (const r of RECIPES) {
      expect(r.tags.length).toBeGreaterThanOrEqual(1);
    }
  });

  test('similar vibe tag pairs are cleanly separated with zero duplicate overlap', () => {
    const pairs: [string, string][] = [
      ['wasted', 'taxi'],
      ['easy', 'pour-forget'],
      ['summer', 'hot-day'],
      ['vacation', 'tropics'],
    ];
    for (const [a, b] of pairs) {
      const aSet = new Set(RECIPES.filter((r) => r.tags.includes(a)).map((r) => r.id));
      const shared = RECIPES.filter((r) => r.tags.includes(b) && aSet.has(r.id));
      expect(shared.length).toBe(0);
    }
  });

  test('all surprises reference valid tags and yield matches', () => {
    for (const s of SURPRISES) {
      const tags = s.ids.map((id) => TAG_MAP.get(id)!);
      expect(tags.every(Boolean)).toBe(true);
      const hits = matchRecipes(tags, new Set(), false);
      expect(hits.length).toBeGreaterThan(0);
      expect(hits[0].hits).toBe(tags.length);
    }
  });

  test('all shelf bottles are used in at least 2 recipes', () => {
    for (const b of BOTTLES) {
      const count = RECIPES.filter((r) => r.bottles.includes(b.id)).length;
      expect(count).toBeGreaterThanOrEqual(2);
    }
  });
});

describe('Smart matcher & shelf filtering', () => {
  test('matches Russian synonyms like 0%, виски, слоу, джем, ягер, грейпфрут, and English names', () => {
    const zeroHits = RECIPES.filter((r) => recipeMatchesQuery(r, '0%'));
    expect(zeroHits.length).toBeGreaterThanOrEqual(10);
    expect(zeroHits.every((r) => r.abv === 0)).toBe(true);

    const whiskyHits = RECIPES.filter((r) => recipeMatchesQuery(r, 'виски'));
    expect(whiskyHits.some((r) => r.id === 'gold-rush')).toBe(true);

    const sloeHits = RECIPES.filter((r) => recipeMatchesQuery(r, 'слоу'));
    expect(sloeHits.length).toBeGreaterThanOrEqual(5);

    const jamHits = RECIPES.filter((r) => recipeMatchesQuery(r, 'джем'));
    expect(jamHits.length).toBeGreaterThanOrEqual(5);

    const jagerHits = RECIPES.filter((r) => recipeMatchesQuery(r, 'ягер'));
    expect(jagerHits.length).toBeGreaterThanOrEqual(6);

    const negroniHits = RECIPES.filter((r) => recipeMatchesQuery(r, 'negroni'));
    expect(negroniHits.some((r) => r.id === 'negroni')).toBe(true);

    const espressoMartiniHits = RECIPES.filter((r) => recipeMatchesQuery(r, 'эспрессо мартини'));
    expect(espressoMartiniHits.some((r) => r.id === 'espresso-martini')).toBe(true);
    expect(espressoMartiniHits.length).toBeLessThanOrEqual(3);
  });

  test('ranks exact multi-tag matches ahead of partial matches', () => {
    const jagerTag = TAG_MAP.get('jager')!;
    const coffeeTag = TAG_MAP.get('coffee')!;
    const matches = matchRecipes([jagerTag, coffeeTag], new Set(), false);
    expect(matches.length).toBeGreaterThan(0);
    expect(matches[0].hits).toBe(2);
    expect(matches[0].recipe.id).toBe('grizzly-bear');
  });

  test('shelf filtering marks 0,0% ready and filters alcohol drinks by bottles', () => {
    const shelf = new Set<BottleId>(['gin']);
    const gt = RECIPES.find((r) => r.id === 'gin-tonic')!;
    const negroni = RECIPES.find((r) => r.id === 'negroni')!;
    const zero = RECIPES.find((r) => r.id === 'espresso-tonic')!;

    expect(getShelfStatus(gt, shelf).onShelf).toBe(true);
    expect(getShelfStatus(zero, shelf).onShelf).toBe(true);
    expect(getShelfStatus(negroni, shelf).onShelf).toBe(false);
    expect(getShelfStatus(negroni, shelf).missingBottles).toEqual(['campari', 'sweet-vermouth']);

    const avail = countAvailableForShelf(shelf);
    expect(avail).toBeGreaterThan(15);
    expect(avail).toBeLessThan(RECIPES.length);

    const filtered = matchRecipes([], shelf, true);
    expect(filtered.length).toBe(avail);
    expect(filtered.every((m) => m.onShelf)).toBe(true);
  });

  test('similar recipes never include the source recipe itself', () => {
    const negroni = RECIPES.find((r) => r.id === 'negroni')!;
    const sim = getSimilarRecipes(negroni, 4);
    expect(sim.length).toBe(4);
    expect(sim.some((r) => r.id === negroni.id)).toBe(false);
  });
});

describe('CocktailGlass 2.0 & WarpBurst utilities', () => {
  test('renders valid Crystal 2.0 SVG for every recipe in both hero and mini sizes', () => {
    for (const r of RECIPES) {
      const hero = renderCocktailGlass(r, 'hero');
      const mini = renderCocktailGlass(r, 'mini');
      expect(hero).toContain('<svg');
      expect(hero).toContain('</svg>');
      expect(hero).toContain('cocktail-glass-hero');
      expect(hero).toContain('-caustic');
      expect(hero).toContain('-cyl');
      expect(mini).toContain('cocktail-glass-mini');
      expect(mini).toContain('-caustic');
    }
  });

  test('parses dual hex glow colors from tag gradients', () => {
    for (const tag of TAGS) {
      const [c1, c2] = parseGlowColors(tag.glow);
      expect(c1).toMatch(/^#[0-9a-fA-F]{6}$/);
      expect(c2).toMatch(/^#[0-9a-fA-F]{6}$/);
    }
  });
});
