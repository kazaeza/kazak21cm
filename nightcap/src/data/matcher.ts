import type { BottleId, MatchedRecipe, Recipe, VibeTag } from '../types';
import { BOTTLE_MAP } from './bottles';
import { RECIPES } from './recipes';
import { TAGS } from './tags';

const SYNONYM_TAGS: Record<string, string[]> = {
  '0%': ['zero'],
  '0,0': ['zero'],
  '0.0': ['zero'],
  'трезв': ['zero'],
  'безалк': ['zero'],
  'б/а': ['zero'],
  'виски': ['serious'],
  'бурбон': ['serious'],
  'скотч': ['smoke', 'serious'],
  'джин': ['gin-tonic'],
  'слоу': ['sloe-gin'],
  'сло': ['sloe-gin'],
  'sloe': ['sloe-gin'],
  'терн': ['sloe-gin'],
  'касис': ['cassis'],
  'кассис': ['cassis'],
  'cassis': ['cassis'],
  'смородин': ['cassis', 'berry'],
  'кир': ['cassis'],
  'джем': ['jam'],
  'варень': ['jam'],
  'конфитюр': ['jam'],
  'мармелад': ['jam'],
  'ром': ['tropics', 'vacation'],
  'текил': ['tequila'],
  'мескал': ['smoke'],
  'дым': ['smoke'],
  'смоки': ['smoke'],
  'игрист': ['sparkling', 'celebrate'],
  'просекко': ['sparkling', 'celebrate'],
  'шампан': ['sparkling', 'celebrate'],
  'кава': ['sparkling'],
  'шприц': ['sparkling', 'italy'],
  'аперол': ['italy', 'orange', 'sparkling'],
  'кампар': ['italy', 'serious'],
  'негрони': ['italy', 'serious'],
  'кисл': ['citrus'],
  'сауэр': ['citrus'],
  'цитрус': ['citrus'],
  'лайм': ['citrus'],
  'лимон': ['citrus'],
  'грейпфрут': ['grapefruit'],
  'апельсин': ['orange'],
  'мандарин': ['orange'],
  'ягер': ['jager'],
  'егер': ['jager'],
  'jager': ['jager'],
  'бальзам': ['jager', 'healing'],
  'рижск': ['jager'],
  'фернет': ['healing', 'alchemy'],
  'абсент': ['alchemy'],
  'absinth': ['alchemy'],
  'лимончел': ['citrus'],
  'адвокат': ['sweet', 'alchemy'],
  'чай': ['tea'],
  'бергамот': ['tea'],
  'эрл': ['tea'],
  'ромашк': ['tea'],
  'кол': ['cola'],
  'байкал': ['cola'],
  'батанга': ['cola', 'tequila'],
  'коньяк': ['cognac'],
  'бренди': ['cognac'],
  'алхим': ['alchemy'],
  'странн': ['alchemy'],
  'сладк': ['sweet'],
  'сланеньк': ['sweet'],
  'десерт': ['sweet'],
  'торт': ['sweet'],
  'сливк': ['sweet'],
  'кофе': ['coffee'],
  'коф': ['coffee'],
  'эспрессо': ['coffee'],
  'шот': ['shots'],
  'рюмк': ['shots'],
  'кувшин': ['celebrate'],
  'праздн': ['celebrate'],
  'компан': ['celebrate'],
  'остр': ['spicy'],
  'чили': ['spicy'],
  'табаско': ['spicy'],
  'имбир': ['spicy', 'healing'],
  'лечеб': ['healing'],
  'похмел': ['healing'],
  'мёд': ['healing'],
  'мед': ['healing'],
  'мят': ['summer'],
  'мохито': ['summer', 'vacation'],
  'лет': ['summer'],
  'огурц': ['cucumber'],
  'огурец': ['cucumber'],
  'оливк': ['olive'],
  'мартини': ['olive', 'gin-tonic'],
  'ягод': ['berry'],
  'малин': ['berry'],
  'клубник': ['berry'],
  'вишн': ['berry'],
  'фрукт': ['fruits'],
  'фрухт': ['fruits'],
  'персик': ['fruits'],
  'тропик': ['tropics', 'vacation'],
  'маракуй': ['tropics'],
  'ананас': ['tropics', 'fruits'],
  'кокос': ['tropics'],
  'отпуск': ['vacation'],
  'шоколад': ['choco-nut'],
  'орех': ['choco-nut'],
  'миндал': ['choco-nut'],
  'амаретто': ['choco-nut'],
  'крепк': ['serious', 'wasted'],
  'вдрызг': ['wasted', 'taxi'],
  'такси': ['taxi', 'wasted'],
  'прост': ['easy', 'pour-forget'],
  'быстр': ['easy', 'pour-forget'],
  'жарк': ['hot-day', 'summer'],
  'томат': ['bloody-mary'],
  'мэри': ['bloody-mary'],
  'мерри': ['bloody-mary'],
};

const recipeSearchHaystack = new Map<string, string>(
  RECIPES.map((r) => {
    const bottleNames = r.bottles
      .map((id) => {
        const b = BOTTLE_MAP.get(id);
        return b ? `${b.name} ${b.sub}` : '';
      })
      .join(' ');
    const ingNames = r.ingredients.map((i) => `${i.name} ${i.note || ''}`).join(' ');
    const tagNames = r.tags
      .map((tid) => {
        const t = TAGS.find((x) => x.id === tid);
        return t ? `${t.label} ${t.sub}` : tid;
      })
      .join(' ');
    const abvTerms =
      r.abv === 0
        ? '0% 0,0% 0.0% безалкогольный трезво'
        : r.abv >= 20
          ? 'крепкий покрепче'
          : 'лёгкий легкий полегче';
    const text = `${r.name} ${r.nameEn} ${r.why} ${r.garnish} ${bottleNames} ${ingNames} ${tagNames} ${abvTerms}`.toLowerCase();
    return [r.id, text];
  })
);

export function getShelfStatus(recipe: Recipe, shelf: Set<BottleId>): { onShelf: boolean; missingBottles: BottleId[] } {
  if (recipe.bottles.length === 0) {
    return { onShelf: true, missingBottles: [] };
  }
  const missingBottles = recipe.bottles.filter((b) => !shelf.has(b));
  return {
    onShelf: missingBottles.length === 0,
    missingBottles,
  };
}

function wordMatchesRecipe(recipe: Recipe, hay: string, word: string): boolean {
  if (/^0([.,]0)?%?$/.test(word)) {
    return recipe.abv === 0 || recipe.tags.includes('zero');
  }
  if (hay.includes(word)) return true;

  for (const [stem, mappedTags] of Object.entries(SYNONYM_TAGS)) {
    if ((word.length >= 3 && (word.includes(stem) || stem.startsWith(word))) || word === stem) {
      if (mappedTags.some((tid) => recipe.tags.includes(tid))) return true;
      if (mappedTags.includes('zero') && recipe.abv === 0) return true;
      if (mappedTags.includes('strong') && recipe.abv >= 20) return true;
    }
  }
  return false;
}

export function recipeMatchesQuery(recipe: Recipe, rawQuery: string): boolean {
  const q = rawQuery.trim().toLowerCase();
  if (!q) return true;

  if (/^0([.,]0)?%?$/.test(q)) {
    return recipe.abv === 0 || recipe.tags.includes('zero');
  }

  const hay = recipeSearchHaystack.get(recipe.id) || '';
  if (hay.includes(q)) return true;

  const words = q.split(/\s+/).filter((w) => w.length >= 2);
  if (words.length === 0) return hay.includes(q);
  return words.every((w) => wordMatchesRecipe(recipe, hay, w));
}

export function matchRecipes(
  selectedTags: VibeTag[],
  shelf: Set<BottleId>,
  shelfOnly: boolean
): MatchedRecipe[] {
  const results: MatchedRecipe[] = [];

  for (const recipe of RECIPES) {
    const { onShelf, missingBottles } = getShelfStatus(recipe, shelf);
    if (shelfOnly && shelf.size > 0 && !onShelf) continue;

    if (selectedTags.length === 0) {
      results.push({
        recipe,
        hits: 0,
        totalCriteria: 0,
        matchedTagIds: [],
        onShelf,
        missingBottles,
      });
      continue;
    }

    const matchedTagIds: string[] = [];
    for (const tag of selectedTags) {
      if (!tag.custom && recipe.tags.includes(tag.id)) {
        matchedTagIds.push(tag.id);
      } else if (tag.custom && recipeMatchesQuery(recipe, tag.label)) {
        matchedTagIds.push(tag.id);
      } else if (!tag.custom && recipeMatchesQuery(recipe, tag.label)) {
        matchedTagIds.push(tag.id);
      }
    }

    if (matchedTagIds.length > 0) {
      results.push({
        recipe,
        hits: matchedTagIds.length,
        totalCriteria: selectedTags.length,
        matchedTagIds,
        onShelf,
        missingBottles,
      });
    }
  }

  results.sort((a, b) => {
    // 1. Highest number of matched tags first
    if (b.hits !== a.hits) return b.hits - a.hits;
    // 2. If user set up their shelf, prioritize cocktails ready on shelf
    if (shelf.size > 0 && a.onShelf !== b.onShelf) return a.onShelf ? -1 : 1;
    // 3. Fewer missing bottles first
    if (shelf.size > 0 && a.missingBottles.length !== b.missingBottles.length) {
      return a.missingBottles.length - b.missingBottles.length;
    }
    // 4. Higher tag specificity (closer fit)
    return a.recipe.tags.length - b.recipe.tags.length;
  });

  return results;
}

export function getSimilarRecipes(recipe: Recipe, limit = 4): Recipe[] {
  const tagSet = new Set(recipe.tags);
  return RECIPES.filter((r) => r.id !== recipe.id)
    .map((r) => {
      let score = 0;
      for (const t of r.tags) {
        if (tagSet.has(t)) score += 2;
      }
      if (r.category === recipe.category) score += 1;
      if (r.abv === 0 && recipe.abv === 0) score += 2;
      if (r.bottles.some((b) => recipe.bottles.includes(b))) score += 1;
      return { r, score };
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((x) => x.r);
}

export function countAvailableForShelf(shelf: Set<BottleId>): number {
  if (shelf.size === 0) return RECIPES.length;
  let count = 0;
  for (const r of RECIPES) {
    if (getShelfStatus(r, shelf).onShelf) count++;
  }
  return count;
}
