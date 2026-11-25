import type { GlassType, IceType, MethodType, Recipe, RecipeCategory } from '../types';
import { RECIPES_CASSIS } from './recipes-cassis';
import { RECIPES_PART_1 } from './recipes-part1';
import { RECIPES_PART_2 } from './recipes-part2';
import { RECIPES_PART_3 } from './recipes-part3';
import { RECIPES_USER_1 } from './recipes-user1';
import { RECIPES_USER_2 } from './recipes-user2';

export const RECIPES: Recipe[] = [
  ...RECIPES_USER_1,
  ...RECIPES_USER_2,
  ...RECIPES_CASSIS,
  ...RECIPES_PART_3,
  ...RECIPES_PART_1,
  ...RECIPES_PART_2,
];

export const RECIPE_MAP = new Map<string, Recipe>(RECIPES.map((r) => [r.id, r]));

export const METHOD_LABELS: Record<MethodType, string> = {
  build: 'Билд в бокале',
  shake: 'Шейк со льдом',
  stir: 'Стир в стакане',
  muddle: 'Мадл + билд',
  layer: 'Слоями',
  warm: 'Тёплая подача',
};

export const GLASS_LABELS: Record<GlassType, string> = {
  coupe: 'Купе',
  martini: 'Коктейльная рюмка',
  rocks: 'Рокс / Олд фэшн',
  highball: 'Хайбол',
  wine: 'Винный бокал',
  shot: 'Шот-рюмка',
  flute: 'Флют',
  tiki: 'Тики / Харикейн',
  mug: 'Кружка',
  pitcher: 'Кувшин',
};

export const ICE_LABELS: Record<IceType, string> = {
  none: 'Без льда',
  cubes: 'Кубики льда',
  'big-cube': 'Большой куб',
  crushed: 'Колотый лёд',
};

export const CATEGORY_LABELS: Record<RecipeCategory, string> = {
  classic: 'Классика',
  modern: 'Современный',
  shot: 'Шот',
  pitcher: 'Кувшин',
  hot: 'Тёплый',
  zero: '0,0%',
  'beer-wine': 'Вино и пиво',
};
