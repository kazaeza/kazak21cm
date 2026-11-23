import type { Bottle, BottleGroupId, BottleId } from '../types';

export const BOTTLE_GROUPS: { id: BottleGroupId; title: string; subtitle: string }[] = [
  { id: 'spirits', title: 'Крепкая основа', subtitle: 'Главные бутылки бара' },
  { id: 'aperitifs', title: 'Аперитивы и вермуты', subtitle: 'Горчинка, травы и Италия' },
  { id: 'liqueurs', title: 'Ликёры', subtitle: 'Сладость, кофе и акценты' },
  { id: 'wine_beer', title: 'Вино, игристое и пиво', subtitle: 'Для шприцев, сангрии и лонгов' },
];

export const BOTTLES: Bottle[] = [
  // Крепкая основа
  { id: 'gin', name: 'Джин', sub: 'сухой лондонский', group: 'spirits', accent: '#66e0ff' },
  { id: 'sloe-gin', name: 'Слоу джин', sub: 'терновый ягодный джин', group: 'spirits', accent: '#c026d3' },
  { id: 'white-rum', name: 'Белый ром', sub: 'для мохито и дайкири', group: 'spirits', accent: '#6ee7b7' },
  { id: 'dark-rum', name: 'Тёмный / пряный ром', sub: 'май тай и шторм', group: 'spirits', accent: '#f59e0b' },
  { id: 'vodka', name: 'Водка', sub: 'чистая база', group: 'spirits', accent: '#bae6fd' },
  { id: 'whisky', name: 'Бурбон / виски', sub: 'сауэр, олд фэшн', group: 'spirits', accent: '#fb923c' },
  { id: 'scotch', name: 'Скотч / дымный виски', sub: 'пенициллин, роб рой', group: 'spirits', accent: '#d97706' },
  { id: 'tequila', name: 'Текила', sub: 'бланко / репосадо', group: 'spirits', accent: '#fde047' },
  { id: 'mezcal', name: 'Мескаль', sub: 'копчёный и дерзкий', group: 'spirits', accent: '#c084fc' },
  { id: 'brandy', name: 'Коньяк / бренди', sub: 'сайдакар, александр', group: 'spirits', accent: '#ea580c' },

  // Аперитивы и вермуты
  { id: 'aperol', name: 'Апероль', sub: 'шприц и закаты', group: 'aperitifs', accent: '#ff7849' },
  { id: 'campari', name: 'Кампари', sub: 'негрони и горчинка', group: 'aperitifs', accent: '#f43f5e' },
  { id: 'dry-vermouth', name: 'Сухой вермут', sub: 'мартини-классика', group: 'aperitifs', accent: '#a7f3d0' },
  { id: 'sweet-vermouth', name: 'Красный вермут', sub: 'негрони, манхэттен', group: 'aperitifs', accent: '#e11d48' },

  // Ликёры
  { id: 'triple-sec', name: 'Апельсиновый ликёр', sub: 'Куантро / Трипл-сек', group: 'liqueurs', accent: '#fdba74' },
  { id: 'coffee-liqueur', name: 'Кофейный ликёр', sub: 'Калуа / аналоги', group: 'liqueurs', accent: '#a8764c' },
  { id: 'amaretto', name: 'Амаретто', sub: 'миндаль и вишня', group: 'liqueurs', accent: '#d97706' },
  { id: 'cream-liqueur', name: 'Сливочный / Адвокат', sub: 'Бейлис, Айриш крим, Адвокат', group: 'liqueurs', accent: '#f5d0a9' },
  { id: 'herbal-liqueur', name: 'Травяной / Ягер / Фернет', sub: 'Ягер, Фернет, Бальзам, Шартрёз', group: 'liqueurs', accent: '#4ade80' },
  { id: 'limoncello', name: 'Лимончелло', sub: 'итальянский лимонный ликёр', group: 'liqueurs', accent: '#fde047' },
  { id: 'absinthe', name: 'Абсент', sub: 'полынь и анис', group: 'spirits', accent: '#22c55e' },

  // Вино, игристое и пиво
  { id: 'sparkling', name: 'Игристое / Просекко', sub: 'брюты и кава', group: 'wine_beer', accent: '#f9a8d4' },
  { id: 'wine', name: 'Вино', sub: 'красное или белое', group: 'wine_beer', accent: '#c084fc' },
  { id: 'beer', name: 'Пиво / стаут', sub: 'лагер, пшеничное', group: 'wine_beer', accent: '#fbbf24' },
];

export const BOTTLE_MAP = new Map<BottleId, Bottle>(BOTTLES.map((b) => [b.id, b]));

export const SHELF_PRESETS: { id: string; label: string; bottles: BottleId[] }[] = [
  {
    id: 'starter',
    label: 'Базовый набор',
    bottles: ['gin', 'white-rum', 'vodka', 'whisky', 'sparkling'],
  },
  {
    id: 'italy',
    label: 'Италия',
    bottles: ['gin', 'aperol', 'campari', 'sweet-vermouth', 'sparkling'],
  },
  {
    id: 'party',
    label: 'Вечеринка',
    bottles: ['tequila', 'white-rum', 'vodka', 'triple-sec', 'coffee-liqueur', 'cream-liqueur', 'sparkling'],
  },
];
