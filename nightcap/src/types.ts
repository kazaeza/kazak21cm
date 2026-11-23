export type LensId = 'all' | 'strong' | 'light' | 'sour' | 'sweet' | 'fun' | 'party' | 'zero';

export interface Lens {
  id: LensId;
  label: string;
}

export interface VibeTag {
  id: string;
  emoji: string;
  label: string;
  sub: string;
  glow: string;
  lenses: LensId[];
  custom?: boolean;
}

export interface SurpriseCombo {
  ids: string[];
  text: string;
}

export type BottleGroupId = 'spirits' | 'aperitifs' | 'liqueurs' | 'wine_beer';

export type BottleId =
  | 'gin'
  | 'sloe-gin'
  | 'white-rum'
  | 'dark-rum'
  | 'vodka'
  | 'whisky'
  | 'scotch'
  | 'tequila'
  | 'mezcal'
  | 'brandy'
  | 'aperol'
  | 'campari'
  | 'dry-vermouth'
  | 'sweet-vermouth'
  | 'triple-sec'
  | 'coffee-liqueur'
  | 'amaretto'
  | 'cream-liqueur'
  | 'herbal-liqueur'
  | 'limoncello'
  | 'absinthe'
  | 'sparkling'
  | 'wine'
  | 'beer';

export interface Bottle {
  id: BottleId;
  name: string;
  sub: string;
  group: BottleGroupId;
  accent: string;
}

export type GlassType =
  | 'coupe'
  | 'martini'
  | 'rocks'
  | 'highball'
  | 'wine'
  | 'shot'
  | 'flute'
  | 'tiki'
  | 'mug'
  | 'pitcher';

export type IceType = 'none' | 'cubes' | 'big-cube' | 'crushed';

export type MethodType = 'build' | 'shake' | 'stir' | 'muddle' | 'layer' | 'warm';

export type RecipeCategory = 'classic' | 'modern' | 'shot' | 'pitcher' | 'hot' | 'zero' | 'beer-wine';

export type GarnishType =
  | 'lime-wheel'
  | 'lemon-twist'
  | 'orange-slice'
  | 'grapefruit-wedge'
  | 'olive-pick'
  | 'cherry-pick'
  | 'mint-sprig'
  | 'rosemary'
  | 'cucumber-ribbon'
  | 'celery'
  | 'coffee-beans'
  | 'cinnamon-stick'
  | 'berry'
  | 'pineapple-wedge'
  | 'chili'
  | 'flower'
  | 'apple-slice'
  | 'none';

export interface CocktailVisual {
  liquidStops: string[];
  liquidOpacity?: number;
  layered?: boolean;
  foam?: string;
  bubbles?: boolean;
  rim?: 'salt' | 'sugar' | 'tajin' | 'cocoa';
  garnishType: GarnishType;
  glowColor: string;
}

export interface Ingredient {
  name: string;
  amount: string;
  ml?: number;
  unit?: string;
  bottleId?: BottleId;
  note?: string;
}

export interface Recipe {
  id: string;
  name: string;
  nameEn: string;
  abv: number;
  category: RecipeCategory;
  tags: string[];
  bottles: BottleId[];
  why: string;
  method: MethodType;
  glass: GlassType;
  ice: IceType;
  ingredients: Ingredient[];
  steps: string[];
  garnish: string;
  substitutes: string[];
  visual: CocktailVisual;
}

export interface MatchedRecipe {
  recipe: Recipe;
  hits: number;
  totalCriteria: number;
  matchedTagIds: string[];
  onShelf: boolean;
  missingBottles: BottleId[];
}

export type TabId = 'nebula' | 'shelf' | 'menu' | 'saved';
