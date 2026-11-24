import type { GarnishType, GlassType, IceType, Recipe } from '../types';

let svgUidCounter = 0;

interface GlassGeometry {
  bowlOuter: string;
  liquidClip: string;
  extraGlass: string;       // stem, footplate, handle, or thick optical sham base
  shamRefraction?: string;  // colored caustic light trapped inside heavy crystal base
  highlightPath: string;
  rightHighlightPath: string;
  rimY: number;
  rimLeft: number;
  rimRight: number;
  liquidTop: number;
  liquidBottom: number;
  centerX: number;
  liquidWidth: number;
  baseY: number;
  baseRx: number;
}

const GLASS_GEOMETRIES: Record<GlassType, GlassGeometry> = {
  coupe: {
    bowlOuter: 'M 40 76 C 40 129 160 129 160 76 Z',
    liquidClip: 'M 45 83 C 47 123 153 123 155 83 Z',
    extraGlass:
      '<path d="M 96.8 124 L 96.8 196 C 96.8 202 67 205 62 210 L 138 210 C 133 205 103.2 202 103.2 196 L 103.2 124 Z" fill="rgba(255,255,255,0.10)" stroke="rgba(255,255,255,0.38)" stroke-width="1.35"/>' +
      '<line x1="99.2" y1="128" x2="99.2" y2="196" stroke="rgba(255,255,255,0.52)" stroke-width="1.3" stroke-linecap="round"/>' +
      '<ellipse cx="100" cy="210" rx="38" ry="5.8" fill="rgba(255,255,255,0.14)" stroke="rgba(255,255,255,0.38)" stroke-width="1.3"/>' +
      '<ellipse cx="100" cy="209.5" rx="22" ry="2.6" fill="none" stroke="rgba(255,255,255,0.28)" stroke-width="0.9"/>',
    shamRefraction: '<ellipse cx="100" cy="123" rx="14" ry="3.2" fill="var(--caustic-c)" opacity="0.55"/>',
    highlightPath: 'M 50 83 C 52 107 66 117 83 121',
    rightHighlightPath: 'M 150 84 C 148 103 139 113 127 118',
    rimY: 76,
    rimLeft: 40,
    rimRight: 160,
    liquidTop: 87,
    liquidBottom: 124,
    centerX: 100,
    liquidWidth: 102,
    baseY: 211,
    baseRx: 42,
  },
  martini: {
    bowlOuter: 'M 36 70 L 100 139 L 164 70 Z',
    liquidClip: 'M 44 80 L 100 134 L 156 80 Z',
    extraGlass:
      '<path d="M 97 136 L 97 197 C 97 203 67 206 63 210 L 137 210 C 133 206 103 203 103 197 L 103 136 Z" fill="rgba(255,255,255,0.10)" stroke="rgba(255,255,255,0.38)" stroke-width="1.35"/>' +
      '<line x1="99.2" y1="140" x2="99.2" y2="197" stroke="rgba(255,255,255,0.52)" stroke-width="1.3" stroke-linecap="round"/>' +
      '<ellipse cx="100" cy="210" rx="37" ry="5.6" fill="rgba(255,255,255,0.14)" stroke="rgba(255,255,255,0.38)" stroke-width="1.3"/>' +
      '<ellipse cx="100" cy="209.5" rx="21" ry="2.5" fill="none" stroke="rgba(255,255,255,0.28)" stroke-width="0.9"/>',
    shamRefraction: '<polygon points="92,131 100,138 108,131" fill="var(--caustic-c)" opacity="0.6"/>',
    highlightPath: 'M 47 77 L 89 123',
    rightHighlightPath: 'M 152 78 L 116 117',
    rimY: 70,
    rimLeft: 36,
    rimRight: 164,
    liquidTop: 85,
    liquidBottom: 134,
    centerX: 100,
    liquidWidth: 96,
    baseY: 211,
    baseRx: 41,
  },
  rocks: {
    bowlOuter: 'M 47 82 L 55 202 Q 100 213 145 202 L 153 82 Z',
    liquidClip: 'M 51.5 95 L 58 184 Q 100 191 142 184 L 148.5 95 Z',
    extraGlass:
      '<path d="M 54.5 182 Q 100 191 145.5 182 L 145 202 Q 100 213 55 202 Z" fill="rgba(255,255,255,0.16)" stroke="rgba(255,255,255,0.34)" stroke-width="1.25"/>' +
      '<path d="M 62 195 Q 100 203 138 195" fill="none" stroke="rgba(255,255,255,0.48)" stroke-width="1.8" stroke-linecap="round"/>',
    shamRefraction:
      '<path d="M 60 187 Q 100 195 140 187 Q 100 201 60 187 Z" fill="var(--caustic-c)" opacity="0.52"/>',
    highlightPath: 'M 56 91 L 62 178',
    rightHighlightPath: 'M 145 92 L 139 176',
    rimY: 82,
    rimLeft: 47,
    rimRight: 153,
    liquidTop: 100,
    liquidBottom: 189,
    centerX: 100,
    liquidWidth: 90,
    baseY: 207,
    baseRx: 49,
  },
  highball: {
    bowlOuter: 'M 57 50 L 63 204 Q 100 214 137 204 L 143 50 Z',
    liquidClip: 'M 61.5 63 L 66.5 188 Q 100 195 133.5 188 L 138.5 63 Z',
    extraGlass:
      '<path d="M 63 186 Q 100 194 137 186 L 137 204 Q 100 214 63 204 Z" fill="rgba(255,255,255,0.17)" stroke="rgba(255,255,255,0.34)" stroke-width="1.25"/>' +
      '<path d="M 69 197 Q 100 204 131 197" fill="none" stroke="rgba(255,255,255,0.48)" stroke-width="1.7" stroke-linecap="round"/>',
    shamRefraction:
      '<path d="M 67 190 Q 100 197 133 190 Q 100 202 67 190 Z" fill="var(--caustic-c)" opacity="0.52"/>',
    highlightPath: 'M 66 60 L 70 182',
    rightHighlightPath: 'M 135 62 L 131 180',
    rimY: 50,
    rimLeft: 57,
    rimRight: 143,
    liquidTop: 70,
    liquidBottom: 193,
    centerX: 100,
    liquidWidth: 74,
    baseY: 208,
    baseRx: 41,
  },
  wine: {
    bowlOuter: 'M 55 56 C 40 108 54 149 100 151 C 146 149 160 108 145 56 Z',
    liquidClip: 'M 55 75 C 47 112 59 145 100 147 C 141 145 153 112 145 75 Z',
    extraGlass:
      '<path d="M 97 150 L 97 199 C 97 204 68 207 65 211 L 135 211 C 132 207 103 204 103 199 L 103 150 Z" fill="rgba(255,255,255,0.10)" stroke="rgba(255,255,255,0.38)" stroke-width="1.35"/>' +
      '<line x1="99.2" y1="153" x2="99.2" y2="199" stroke="rgba(255,255,255,0.5)" stroke-width="1.2" stroke-linecap="round"/>' +
      '<ellipse cx="100" cy="211" rx="35" ry="5.2" fill="rgba(255,255,255,0.14)" stroke="rgba(255,255,255,0.36)" stroke-width="1.3"/>',
    shamRefraction: '<ellipse cx="100" cy="147" rx="16" ry="3.5" fill="var(--caustic-c)" opacity="0.5"/>',
    highlightPath: 'M 60 66 C 52 100 59 126 76 140',
    rightHighlightPath: 'M 140 68 C 146 98 141 122 126 137',
    rimY: 56,
    rimLeft: 55,
    rimRight: 145,
    liquidTop: 81,
    liquidBottom: 147,
    centerX: 100,
    liquidWidth: 86,
    baseY: 212,
    baseRx: 39,
  },
  shot: {
    bowlOuter: 'M 63 94 L 73 200 Q 100 209 127 200 L 137 94 Z',
    liquidClip: 'M 67.5 105 L 75.5 176 Q 100 182 124.5 176 L 132.5 105 Z',
    extraGlass:
      '<path d="M 72.5 174 Q 100 181 127.5 174 L 127 200 Q 100 209 73 200 Z" fill="rgba(255,255,255,0.22)" stroke="rgba(255,255,255,0.38)" stroke-width="1.25"/>' +
      '<path d="M 78 192 Q 100 198 122 192" fill="none" stroke="rgba(255,255,255,0.55)" stroke-width="1.8" stroke-linecap="round"/>',
    shamRefraction:
      '<path d="M 76 181 Q 100 188 124 181 Q 100 194 76 181 Z" fill="var(--caustic-c)" opacity="0.58"/>',
    highlightPath: 'M 72 102 L 79 170',
    rightHighlightPath: 'M 129 103 L 122 169',
    rimY: 94,
    rimLeft: 63,
    rimRight: 137,
    liquidTop: 109,
    liquidBottom: 180,
    centerX: 100,
    liquidWidth: 60,
    baseY: 205,
    baseRx: 32,
  },
  flute: {
    bowlOuter: 'M 74 42 L 77 134 C 77 149 91 155 100 155 C 109 155 123 149 123 134 L 126 42 Z',
    liquidClip: 'M 77.5 58 L 80.5 132 C 80.5 145 92 150 100 150 C 108 150 119.5 145 119.5 132 L 122.5 58 Z',
    extraGlass:
      '<path d="M 97.4 154 L 97.4 200 C 97.4 204 71 207 69 211 L 131 211 C 129 207 102.6 204 102.6 200 L 102.6 154 Z" fill="rgba(255,255,255,0.10)" stroke="rgba(255,255,255,0.38)" stroke-width="1.35"/>' +
      '<line x1="99.3" y1="156" x2="99.3" y2="200" stroke="rgba(255,255,255,0.52)" stroke-width="1.2" stroke-linecap="round"/>' +
      '<ellipse cx="100" cy="211" rx="31" ry="4.8" fill="rgba(255,255,255,0.14)" stroke="rgba(255,255,255,0.36)" stroke-width="1.3"/>',
    shamRefraction: '<ellipse cx="100" cy="149" rx="10" ry="3" fill="var(--caustic-c)" opacity="0.55"/>',
    highlightPath: 'M 82 52 L 84 132',
    rightHighlightPath: 'M 119 54 L 117 130',
    rimY: 42,
    rimLeft: 74,
    rimRight: 126,
    liquidTop: 62,
    liquidBottom: 150,
    centerX: 100,
    liquidWidth: 43,
    baseY: 212,
    baseRx: 35,
  },
  tiki: {
    bowlOuter:
      'M 53 54 C 65 80 47 118 53 156 C 57 177 76 185 100 185 C 124 185 143 177 147 156 C 153 118 135 80 147 54 Z',
    liquidClip:
      'M 58 67 C 67 88 52 120 57 154 C 61 172 78 180 100 180 C 122 180 139 172 143 154 C 148 120 133 88 142 67 Z',
    extraGlass:
      '<path d="M 90 184 L 87 202 C 87 205 67 207 65 211 L 135 211 C 133 207 113 205 113 202 L 110 184 Z" fill="rgba(255,255,255,0.13)" stroke="rgba(255,255,255,0.36)" stroke-width="1.35"/>' +
      '<ellipse cx="100" cy="211" rx="35" ry="5.2" fill="rgba(255,255,255,0.15)" stroke="rgba(255,255,255,0.34)" stroke-width="1.3"/>' +
      // Subtle tiki brow/carved relief arcs
      '<path d="M 68 106 Q 84 100 94 108 M 106 108 Q 116 100 132 106 M 78 144 Q 100 152 122 144" fill="none" stroke="rgba(255,255,255,0.15)" stroke-width="1.5" stroke-linecap="round"/>',
    highlightPath: 'M 63 69 C 65 96 57 124 63 155',
    rightHighlightPath: 'M 137 70 C 135 96 143 124 137 153',
    rimY: 54,
    rimLeft: 53,
    rimRight: 147,
    liquidTop: 71,
    liquidBottom: 180,
    centerX: 100,
    liquidWidth: 84,
    baseY: 212,
    baseRx: 39,
  },
  mug: {
    bowlOuter: 'M 46 76 L 51 196 Q 96 209 141 196 L 146 76 Z',
    liquidClip: 'M 50 88 L 54.5 190 Q 96 200 137.5 190 L 142 88 Z',
    extraGlass:
      '<path d="M 145 95 C 179 95 179 171 141 171" fill="none" stroke="rgba(235,176,132,0.62)" stroke-width="8" stroke-linecap="round"/>' +
      '<path d="M 145 95 C 179 95 179 171 141 171" fill="none" stroke="rgba(255,235,215,0.48)" stroke-width="2.2" stroke-linecap="round"/>' +
      '<path d="M 51 190 Q 96 202 141 190 L 141 196 Q 96 209 51 196 Z" fill="rgba(235,176,132,0.22)"/>',
    highlightPath: 'M 56 86 L 60 183',
    rightHighlightPath: 'M 137 87 L 133 181',
    rimY: 76,
    rimLeft: 46,
    rimRight: 146,
    liquidTop: 92,
    liquidBottom: 196,
    centerX: 96,
    liquidWidth: 88,
    baseY: 205,
    baseRx: 48,
  },
  pitcher: {
    bowlOuter:
      'M 42 60 L 33 64 L 47 79 C 41 122 39 172 54 198 Q 96 211 138 198 C 150 172 150 122 142 77 L 146 60 Z',
    liquidClip:
      'M 48.5 81 C 44.5 122 43.5 170 57 193 Q 96 203 135 193 C 146 170 146 122 139.5 81 Z',
    extraGlass:
      '<path d="M 144 81 C 179 85 181 167 144 175" fill="none" stroke="rgba(255,255,255,0.38)" stroke-width="7" stroke-linecap="round"/>' +
      '<path d="M 144 81 C 179 85 181 167 144 175" fill="none" stroke="rgba(255,255,255,0.52)" stroke-width="2" stroke-linecap="round"/>',
    highlightPath: 'M 55 85 C 49 124 50 162 61 186',
    rightHighlightPath: 'M 135 86 C 141 124 140 160 131 184',
    rimY: 60,
    rimLeft: 42,
    rimRight: 146,
    liquidTop: 85,
    liquidBottom: 198,
    centerX: 96,
    liquidWidth: 90,
    baseY: 206,
    baseRx: 46,
  },
};

function renderCrystalCube(cx: number, cy: number, size: number, rot: number, isMini: boolean): string {
  const r = size * 0.5;
  const sw = isMini ? 1.5 : 1.15;
  // 3D bevelled crystal block: outer rounded square + bright top-left bevel face + deep right bevel + internal refraction glint
  return `
    <g transform="translate(${cx.toFixed(1)}, ${cy.toFixed(1)}) rotate(${rot})">
      <rect x="${(-r).toFixed(1)}" y="${(-r).toFixed(1)}" width="${size.toFixed(1)}" height="${size.toFixed(1)}" rx="${(size * 0.16).toFixed(1)}"
        fill="rgba(232, 248, 255, 0.18)" stroke="rgba(255, 255, 255, 0.58)" stroke-width="${sw}"/>
      <!-- Top/Left luminous crystal facet -->
      <polygon points="${(-r * 0.78).toFixed(1)},${(-r * 0.78).toFixed(1)} ${(r * 0.76).toFixed(1)},${(-r * 0.78).toFixed(1)} ${(r * 0.34).toFixed(1)},${(-r * 0.32).toFixed(1)} ${(-r * 0.32).toFixed(1)},${(r * 0.34).toFixed(1)} ${(-r * 0.78).toFixed(1)},${(r * 0.76).toFixed(1)}"
        fill="rgba(255, 255, 255, 0.22)"/>
      <!-- Inner crystal core refraction -->
      <rect x="${(-r * 0.42).toFixed(1)}" y="${(-r * 0.42).toFixed(1)}" width="${(size * 0.42).toFixed(1)}" height="${(size * 0.42).toFixed(1)}" rx="2.5"
        fill="rgba(255, 255, 255, 0.12)" stroke="rgba(255, 255, 255, 0.32)" stroke-width="0.8"/>
      <!-- Crisp specular edge highlight -->
      <path d="M ${(-r * 0.68).toFixed(1)} ${(r * 0.25).toFixed(1)} L ${(-r * 0.68).toFixed(1)} ${(-r * 0.68).toFixed(1)} L ${(r * 0.38).toFixed(1)} ${(-r * 0.68).toFixed(1)}"
        fill="none" stroke="rgba(255, 255, 255, 0.78)" stroke-width="${isMini ? 1.7 : 1.45}" stroke-linecap="round" stroke-linejoin="round"/>
    </g>
  `;
}

function renderIce(ice: IceType, geom: GlassGeometry, isMini: boolean): string {
  if (ice === 'none') return '';
  const cx = geom.centerX;
  const top = geom.liquidTop;
  const bot = geom.liquidBottom;
  const h = bot - top;

  if (ice === 'big-cube') {
    const size = Math.min(geom.liquidWidth * 0.63, h * 0.68, 56);
    const cy = top + h * 0.13 + size * 0.5;
    return `<g opacity="0.86">${renderCrystalCube(cx, cy, size, -5, isMini)}</g>`;
  }

  if (ice === 'cubes') {
    const count = h > 85 ? 4 : 3;
    const cubeSize = Math.min(26, geom.liquidWidth * 0.35);
    const offsets = [
      { dx: -13, dy: 0.13, rot: -13 },
      { dx: 12, dy: 0.23, rot: 15 },
      { dx: -9, dy: 0.49, rot: 10 },
      { dx: 11, dy: 0.65, rot: -16 },
    ];
    return `<g opacity="0.84">${offsets
      .slice(0, count)
      .map((o) => renderCrystalCube(cx + o.dx, top + h * o.dy + cubeSize * 0.5, cubeSize, o.rot, isMini))
      .join('')}</g>`;
  }

  // Crushed ice: sparkling multi-tone crystal pebbles + frosty crown
  const shards: string[] = [];
  const rows = isMini ? 3 : 5;
  const cols = isMini ? 3 : 5;
  for (let r = 0; r < rows; r++) {
    const y = top - 2 + (r / rows) * (h * 0.66);
    for (let c = 0; c < cols; c++) {
      const frac = (c + 0.5) / cols - 0.5;
      const x = cx + frac * geom.liquidWidth * 0.82 + (((r * 7 + c * 11) % 7) - 3);
      const sz = (isMini ? 9.5 : 11.5) * (0.88 + ((r + c) % 3) * 0.12);
      const rot = ((r * 37 + c * 53) % 76) - 38;
      const fillAlpha = (0.22 + ((r * 3 + c * 5) % 4) * 0.06).toFixed(2);
      shards.push(
        `<polygon points="${x.toFixed(1)},${(y - sz * 0.52).toFixed(1)} ${(x + sz * 0.62).toFixed(1)},${(y - sz * 0.08).toFixed(1)} ${(x + sz * 0.22).toFixed(1)},${(y + sz * 0.52).toFixed(1)} ${(x - sz * 0.56).toFixed(1)},${(y + sz * 0.18).toFixed(1)}"
          transform="rotate(${rot} ${x.toFixed(1)} ${y.toFixed(1)})"
          fill="rgba(238,250,255,${fillAlpha})" stroke="rgba(255,255,255,0.62)" stroke-width="0.9"/>`
      );
    }
  }
  return `<g>${shards.join('')}</g>`;
}

function renderBubbles(geom: GlassGeometry, isMini: boolean): string {
  const cx = geom.centerX;
  const span = geom.liquidWidth * 0.64;
  const count = isMini ? 6 : 15;
  const items: string[] = [];
  for (let i = 0; i < count; i++) {
    const seed = (i * 29 + 13) % 100;
    const x = cx + (seed / 100 - 0.5) * span;
    const y =
      geom.liquidBottom -
      10 -
      ((i * 19) % Math.max(24, Math.floor(geom.liquidBottom - geom.liquidTop - 14)));
    const r = isMini ? 1.7 : 1.15 + (i % 3) * 0.85;
    const delay = ((i * 0.37) % 2.6).toFixed(2);
    const dur = (2.0 + (i % 4) * 0.45).toFixed(2);
    items.push(
      `<circle class="${isMini ? '' : 'drink-bubble'}" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r.toFixed(1)}" fill="rgba(255,255,255,0.72)" style="${isMini ? '' : `animation-delay:-${delay}s;animation-duration:${dur}s;`}"/>`
    );
  }
  return `<g>${items.join('')}</g>`;
}

function renderCondensation(geom: GlassGeometry): string {
  // Subtle chilled condensation micro-beads on the exterior bowl in hero view
  const cx = geom.centerX;
  const top = geom.liquidTop + 6;
  const spanY = Math.max(18, (geom.liquidBottom - top) * 0.78);
  const spanX = geom.liquidWidth * 0.38;
  const seeds = [
    [-0.72, 0.16, 1.3],
    [-0.42, 0.38, 1.7],
    [-0.15, 0.68, 1.15],
    [0.24, 0.26, 1.5],
    [0.58, 0.52, 1.35],
    [0.74, 0.22, 1.1],
    [-0.55, 0.76, 1.25],
    [0.38, 0.82, 1.4],
  ];
  return `<g opacity="0.52">${seeds
    .map(([fx, fy, r]) => {
      const x = cx + fx * spanX;
      const y = top + fy * spanY;
      return (
        `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r}" fill="rgba(255,255,255,0.28)" stroke="rgba(255,255,255,0.45)" stroke-width="0.45"/>` +
        `<circle cx="${(x - r * 0.3).toFixed(1)}" cy="${(y - r * 0.3).toFixed(1)}" r="${(r * 0.35).toFixed(2)}" fill="#ffffff"/>`
      );
    })
    .join('')}</g>`;
}

function renderRim(rim: Recipe['visual']['rim'], geom: GlassGeometry): string {
  if (!rim) return '';
  const palette: Record<NonNullable<Recipe['visual']['rim']>, [string, string, string]> = {
    salt: ['#ffffff', '#e2e8f0', '#cbd5e1'],
    sugar: ['#fffbeb', '#fef3c7', '#fde68a'],
    tajin: ['#ef4444', '#ea580c', '#fca5a5'],
    cocoa: ['#451a03', '#78350f', '#a16207'],
  };
  const [c1, c2, c3] = palette[rim];
  const crystals: string[] = [];
  const start = geom.rimLeft + 2;
  const end = geom.rimLeft + (geom.rimRight - geom.rimLeft) * 0.66;
  const rx = (geom.rimRight - geom.rimLeft) * 0.5;
  const steps = 26;
  for (let i = 0; i <= steps; i++) {
    const x = start + ((end - start) * i) / steps;
    const normX = Math.max(-1, Math.min(1, (x - geom.centerX) / rx));
    const arcY = Math.sqrt(Math.max(0, 1 - normX * normX)) * 3.4;
    const jitterY = ((i * 7) % 5) - 2;
    const y = geom.rimY + arcY + jitterY * 0.75;
    const r = 1.25 + (i % 3) * 0.6;
    const col = i % 3 === 0 ? c1 : i % 3 === 1 ? c2 : c3;
    crystals.push(
      `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r.toFixed(1)}" fill="${col}" opacity="0.94"/>`
    );
  }
  return `<g>${crystals.join('')}</g>`;
}

function renderGarnish(type: GarnishType, geom: GlassGeometry, isMini: boolean): string {
  if (type === 'none') return '';
  const rx = geom.rimRight - 4;
  const ry = geom.rimY;
  const cx = geom.centerX;
  const ly = geom.liquidTop;

  switch (type) {
    case 'lime-wheel':
    case 'orange-slice':
    case 'grapefruit-wedge': {
      const colors =
        type === 'lime-wheel'
          ? { rind: '#15803d', rindHi: '#22c55e', pith: '#f7fee7', pulp: '#84cc16', pulpHi: '#bef264' }
          : type === 'orange-slice'
            ? { rind: '#c2410c', rindHi: '#fb923c', pith: '#fff7ed', pulp: '#f97316', pulpHi: '#fdba74' }
            : { rind: '#ea580c', rindHi: '#fda4af', pith: '#ffe4e6', pulp: '#f43f5e', pulpHi: '#fda4af' };
      const rad = isMini ? 16.5 : 20;
      const segs = Array.from({ length: 8 }, (_, k) => {
        const a1 = (k * Math.PI) / 4 + 0.08;
        const a2 = ((k + 1) * Math.PI) / 4 - 0.08;
        const rIn = 2.4;
        const rOut = rad - 4.4;
        const x1 = Math.cos(a1) * rOut;
        const y1 = Math.sin(a1) * rOut;
        const x2 = Math.cos(a2) * rOut;
        const y2 = Math.sin(a2) * rOut;
        const xi = Math.cos((a1 + a2) * 0.5) * rIn;
        const yi = Math.sin((a1 + a2) * 0.5) * rIn;
        return `<path d="M ${xi.toFixed(1)} ${yi.toFixed(1)} L ${x1.toFixed(1)} ${y1.toFixed(1)} A ${rOut.toFixed(1)} ${rOut.toFixed(1)} 0 0 1 ${x2.toFixed(1)} ${y2.toFixed(1)} Z" fill="${k % 2 === 0 ? colors.pulp : colors.pulpHi}" opacity="0.95"/>`;
      }).join('');
      return `
        <g transform="translate(${rx}, ${ry}) rotate(18)">
          <circle cx="0" cy="0" r="${rad}" fill="${colors.rind}" stroke="rgba(0,0,0,0.22)" stroke-width="0.8"/>
          <path d="M ${(-rad * 0.7).toFixed(1)} ${(-rad * 0.65).toFixed(1)} A ${rad} ${rad} 0 0 1 ${(rad * 0.85).toFixed(1)} ${(-rad * 0.4).toFixed(1)}" fill="none" stroke="${colors.rindHi}" stroke-width="1.6" stroke-linecap="round"/>
          <circle cx="0" cy="0" r="${(rad - 2.4).toFixed(1)}" fill="${colors.pith}"/>
          ${segs}
          <circle cx="0" cy="0" r="2.3" fill="${colors.pith}"/>
        </g>
      `;
    }
    case 'lemon-twist':
      return `
        <g transform="translate(${rx - 6}, ${ry - 2})">
          <path d="M -9 11 C -5 -15 14 -17 12 0 C 10 13 23 14 25 -5" fill="none" stroke="#ca8a04" stroke-width="5.8" stroke-linecap="round"/>
          <path d="M -9 11 C -5 -15 14 -17 12 0 C 10 13 23 14 25 -5" fill="none" stroke="#fde047" stroke-width="4.2" stroke-linecap="round"/>
          <path d="M -8 9 C -4 -14 13 -15 11 0" fill="none" stroke="#fefce8" stroke-width="1.3" stroke-linecap="round"/>
        </g>
      `;
    case 'olive-pick':
      return `
        <g>
          <line x1="${cx - 19}" y1="${ly + 25}" x2="${rx + 13}" y2="${ry - 19}" stroke="#e2e8f0" stroke-width="2.1" stroke-linecap="round"/>
          <circle cx="${rx + 13}" cy="${ry - 19}" r="3.6" fill="#f5d0fe" stroke="#ffffff" stroke-width="0.8"/>
          <g transform="translate(${cx - 4}, ${ly + 10}) rotate(-32)">
            <ellipse cx="0" cy="0" rx="10.4" ry="7.8" fill="#65a30d" stroke="#365314" stroke-width="1"/>
            <ellipse cx="-4.2" cy="0" rx="2.7" ry="3.2" fill="#dc2626"/>
            <path d="M -3 -4.5 Q 3 -5.5 6 -2" fill="none" stroke="rgba(255,255,255,0.52)" stroke-width="1.5" stroke-linecap="round"/>
          </g>
          <g transform="translate(${cx + 12}, ${ly - 2}) rotate(-32)">
            <ellipse cx="0" cy="0" rx="9.8" ry="7.3" fill="#84cc16" stroke="#3f6212" stroke-width="1"/>
            <ellipse cx="-3.8" cy="0" rx="2.5" ry="3" fill="#dc2626"/>
            <path d="M -2.5 -4.2 Q 3 -5 5.5 -1.8" fill="none" stroke="rgba(255,255,255,0.52)" stroke-width="1.4" stroke-linecap="round"/>
          </g>
        </g>
      `;
    case 'cherry-pick':
      return `
        <g>
          <line x1="${cx - 13}" y1="${ly + 25}" x2="${rx + 11}" y2="${ry - 19}" stroke="#e2e8f0" stroke-width="2" stroke-linecap="round"/>
          <circle cx="${rx + 11}" cy="${ry - 19}" r="3.3" fill="#fda4af" stroke="#ffffff" stroke-width="0.7"/>
          <circle cx="${cx + 2}" cy="${ly + 8}" r="10" fill="#881337" stroke="#4c0519" stroke-width="1.1"/>
          <circle cx="${cx + 1}" cy="${ly + 7}" r="7.5" fill="#be123c" opacity="0.65"/>
          <path d="M ${cx - 3.5} ${ly + 3.5} A 5.5 5.5 0 0 1 ${cx + 2.5} ${ly + 1.5}" fill="none" stroke="rgba(255,255,255,0.68)" stroke-width="1.8" stroke-linecap="round"/>
        </g>
      `;
    case 'mint-sprig':
      return `
        <g transform="translate(${cx + 14}, ${ry + 4})">
          <path d="M 0 13 Q 2 -12 8 -27" fill="none" stroke="#15803d" stroke-width="2.1" stroke-linecap="round"/>
          <g transform="translate(-6, -4) rotate(-35)">
            <ellipse cx="0" cy="0" rx="9.5" ry="5.2" fill="#16a34a" stroke="#14532d" stroke-width="0.9"/>
            <line x1="-7" y1="0" x2="7" y2="0" stroke="#86efac" stroke-width="0.8"/>
          </g>
          <g transform="translate(10, -10) rotate(28)">
            <ellipse cx="0" cy="0" rx="10" ry="5.4" fill="#22c55e" stroke="#14532d" stroke-width="0.9"/>
            <line x1="-7.5" y1="0" x2="7.5" y2="0" stroke="#bbf7d0" stroke-width="0.8"/>
          </g>
          <g transform="translate(0, -20) rotate(-18)">
            <ellipse cx="0" cy="0" rx="8.8" ry="4.8" fill="#4ade80" stroke="#166534" stroke-width="0.9"/>
            <line x1="-6.5" y1="0" x2="6.5" y2="0" stroke="#dcfce7" stroke-width="0.8"/>
          </g>
          <ellipse cx="13" cy="-23" rx="7.2" ry="4" transform="rotate(42 13 -23)" fill="#22c55e" stroke="#14532d" stroke-width="0.9"/>
        </g>
      `;
    case 'rosemary':
      return `
        <g transform="translate(${cx + 12}, ${ry + 18}) rotate(12)">
          <line x1="0" y1="22" x2="0" y2="-35" stroke="#78350f" stroke-width="2.3" stroke-linecap="round"/>
          ${[-29, -22, -15, -8, -1, 6]
            .map(
              (y) =>
                `<line x1="0" y1="${y}" x2="-10.5" y2="${y - 7.5}" stroke="#34d399" stroke-width="2.1" stroke-linecap="round"/>` +
                `<line x1="0" y1="${y + 2}" x2="10.5" y2="${y - 5.5}" stroke="#4ade80" stroke-width="2.1" stroke-linecap="round"/>`
            )
            .join('')}
        </g>
      `;
    case 'cucumber-ribbon':
      return `
        <g>
          <path d="M ${cx - 22} ${ly + 42} Q ${cx - 7} ${ly + 14} ${cx - 18} ${ry - 15}" fill="none" stroke="#15803d" stroke-width="9.5" stroke-linecap="round" opacity="0.9"/>
          <path d="M ${cx - 22} ${ly + 42} Q ${cx - 7} ${ly + 14} ${cx - 18} ${ry - 15}" fill="none" stroke="#dcfce7" stroke-width="5" stroke-linecap="round" opacity="0.88"/>
        </g>
      `;
    case 'celery':
      return `
        <g transform="translate(${cx + 16}, ${ry + 16}) rotate(14)">
          <path d="M -4 48 L -5 -32 L 5 -32 L 4 48 Z" fill="#84cc16" stroke="#4d7c0f" stroke-width="1.2"/>
          <line x1="-1.6" y1="44" x2="-1.6" y2="-30" stroke="#d9f99d" stroke-width="1.4"/>
          <line x1="1.6" y1="42" x2="1.6" y2="-28" stroke="#65a30d" stroke-width="1"/>
          <ellipse cx="-6" cy="-35" rx="7.5" ry="4.5" transform="rotate(-30 -6 -35)" fill="#65a30d"/>
          <ellipse cx="6" cy="-37" rx="7.5" ry="4.5" transform="rotate(28 6 -37)" fill="#84cc16"/>
          <ellipse cx="0" cy="-42" rx="6.2" ry="4" fill="#4d7c0f"/>
        </g>
      `;
    case 'coffee-beans':
      return `
        <g transform="translate(${cx}, ${ly - 1.5})">
          <ellipse cx="0" cy="1.5" rx="18" ry="3.8" fill="rgba(42,18,6,0.28)"/>
          ${[
            { x: -10, y: 1, r: -20 },
            { x: 0, y: -2, r: 12 },
            { x: 10, y: 1, r: 30 },
          ]
            .map(
              (b) =>
                `<g transform="translate(${b.x}, ${b.y}) rotate(${b.r})">
                  <ellipse cx="0" cy="0" rx="5.5" ry="3.6" fill="#3b1d0a" stroke="#1c0d04" stroke-width="0.9"/>
                  <path d="M -4.2 0 Q 0 1.5 4.2 0" fill="none" stroke="#d6a87c" stroke-width="0.95"/>
                  <ellipse cx="-1.5" cy="-1.2" rx="2" ry="0.7" fill="rgba(255,255,255,0.22)"/>
                </g>`
            )
            .join('')}
        </g>
      `;
    case 'cinnamon-stick':
      return `
        <g transform="translate(${cx + 22}, ${ry + 6}) rotate(22)">
          <rect x="-4.8" y="-33" width="9.6" height="64" rx="4.2" fill="#92400e" stroke="#451a03" stroke-width="1.2"/>
          <line x1="-1.6" y1="-29" x2="-1.6" y2="27" stroke="#f59e0b" stroke-width="1.5" opacity="0.65"/>
          <ellipse cx="0" cy="-33" rx="4.8" ry="2.4" fill="#78350f" stroke="#451a03" stroke-width="0.9"/>
        </g>
      `;
    case 'berry':
      return `
        <g transform="translate(${cx + 12}, ${ry + 4})">
          <line x1="-10" y1="16" x2="18" y2="-22" stroke="#e2e8f0" stroke-width="1.9" stroke-linecap="round"/>
          <g transform="translate(2, -2)">
            <circle cx="-3.2" cy="-2.2" r="4.2" fill="#be185d"/>
            <circle cx="3.2" cy="-2.2" r="4.2" fill="#ec4899"/>
            <circle cx="0" cy="3.2" r="4.4" fill="#db2777"/>
            <circle cx="-2.2" cy="1.2" r="3.8" fill="#f43f5e"/>
            <circle cx="2.2" cy="2.2" r="3.7" fill="#9d174d"/>
            <circle cx="-4" cy="-3.2" r="1.2" fill="rgba(255,255,255,0.55)"/>
            <circle cx="2.2" cy="-3.2" r="1.1" fill="rgba(255,255,255,0.5)"/>
          </g>
        </g>
      `;
    case 'pineapple-wedge':
      return `
        <g transform="translate(${rx}, ${ry - 2}) rotate(20)">
          <path d="M -14.5 10.5 L 14.5 10.5 L 0 -19 Z" fill="#facc15" stroke="#ca8a04" stroke-width="1.2"/>
          <line x1="0" y1="-15" x2="0" y2="9" stroke="#fef08a" stroke-width="1.3"/>
          <path d="M -14.5 10.5 L 14.5 10.5 L 12.5 15.5 L -12.5 15.5 Z" fill="#a16207"/>
          <path d="M -6 -12 L -15 -27 L -2 -18 L 2 -31 L 6 -16 L 15 -25 L 8 -10" fill="#16a34a" stroke="#14532d" stroke-width="0.9"/>
        </g>
      `;
    case 'chili':
      return `
        <g transform="translate(${rx - 4}, ${ry - 2}) rotate(14)">
          <path d="M -6 14 C -2 -2 8 -12 18 -14 C 14 -2 6 8 -2 18 Z" fill="#dc2626" stroke="#7f1d1d" stroke-width="1"/>
          <path d="M 16 -13 Q 22 -18 24 -12" fill="none" stroke="#16a34a" stroke-width="2.3" stroke-linecap="round"/>
          <path d="M 0 7 Q 6 0 12 -6" fill="none" stroke="rgba(255,255,255,0.55)" stroke-width="1.5" stroke-linecap="round"/>
        </g>
      `;
    case 'flower':
      return `
        <g transform="translate(${cx - 8}, ${ly - 2})">
          ${[0, 72, 144, 216, 288]
            .map(
              (deg) =>
                `<ellipse cx="0" cy="-7.2" rx="4.7" ry="6.8" transform="rotate(${deg})" fill="#f9a8d4" stroke="#f472b6" stroke-width="0.7" opacity="0.96"/>`
            )
            .join('')}
          <circle cx="0" cy="0" r="3.6" fill="#fde047" stroke="#eab308" stroke-width="0.6"/>
        </g>
      `;
    case 'apple-slice':
      return `
        <g transform="translate(${rx - 2}, ${ry}) rotate(22)">
          <path d="M -15 8 C -10 -12 10 -12 15 8 Z" fill="#ecfccb" stroke="#65a30d" stroke-width="2.6"/>
          <path d="M -8 10 C -4 -6 12 -6 17 10 Z" fill="#fefce8" stroke="#dc2626" stroke-width="2.4" opacity="0.94"/>
        </g>
      `;
    default:
      return '';
  }
}

function renderWarmSteam(geom: GlassGeometry): string {
  const cx = geom.centerX;
  const y = geom.rimY - 12;
  return `
    <g class="warm-steam" fill="none" stroke="rgba(255,245,235,0.38)" stroke-width="2.2" stroke-linecap="round">
      <path d="M ${cx - 16} ${y} Q ${cx - 22} ${y - 14} ${cx - 14} ${y - 28}"/>
      <path d="M ${cx} ${y - 4} Q ${cx + 8} ${y - 18} ${cx} ${y - 34}"/>
      <path d="M ${cx + 16} ${y} Q ${cx + 10} ${y - 14} ${cx + 18} ${y - 26}"/>
    </g>
  `;
}

export function renderCocktailGlass(recipe: Recipe, size: 'hero' | 'mini' = 'hero'): string {
  const uid = `cg${++svgUidCounter}`;
  const geom = GLASS_GEOMETRIES[recipe.glass];
  const vis = recipe.visual;
  const isMini = size === 'mini';

  // Build vertical liquid gradient (bottom = stops[0], top = stops[n-1])
  const stops = vis.liquidStops.length ? vis.liquidStops : ['#c084fc', '#66e0ff'];
  const bottomCausticColor = stops[0];
  const gradStops: string[] = [];

  if (vis.layered && stops.length >= 2) {
    const n = stops.length;
    for (let i = 0; i < n; i++) {
      const startPct = Math.round((i / n) * 100);
      const endPct = Math.round(((i + 1) / n) * 100);
      const blend = i === 0 ? startPct : Math.min(100, startPct + 3);
      const stopEnd = i === n - 1 ? 100 : Math.max(0, endPct - 3);
      gradStops.push(`<stop offset="${blend}%" stop-color="${stops[n - 1 - i]}"/>`);
      gradStops.push(`<stop offset="${stopEnd}%" stop-color="${stops[n - 1 - i]}"/>`);
    }
  } else {
    const n = stops.length;
    for (let i = 0; i < n; i++) {
      const pct = n === 1 ? 0 : Math.round((i / (n - 1)) * 100);
      gradStops.push(`<stop offset="${pct}%" stop-color="${stops[n - 1 - i]}"/>`);
    }
  }

  const liquidOpacity = vis.liquidOpacity ?? 0.88;
  const foamHeight = isMini ? 9.5 : 12;
  const liquidTopY = vis.foam ? geom.liquidTop + foamHeight - 4 : geom.liquidTop;

  // 3D inter-layer elliptical menisci for layered cocktails/shots (B-52, Tequila Sunrise, etc.)
  let layerMenisciSvg = '';
  if (vis.layered && stops.length >= 2) {
    const n = stops.length;
    const totalH = geom.liquidBottom - liquidTopY;
    for (let i = 1; i < n; i++) {
      const ly = liquidTopY + (i / n) * totalH;
      layerMenisciSvg += `<ellipse cx="${geom.centerX}" cy="${ly.toFixed(1)}" rx="${(geom.liquidWidth * 0.58).toFixed(1)}" ry="${isMini ? '2.4' : '3.4'}" fill="rgba(255,255,255,0.18)" stroke="rgba(255,255,255,0.34)" stroke-width="0.65"/>`;
    }
  }

  // Velvety sculpted foam / crema head
  const foamSvg = vis.foam
    ? `<g>
         <rect x="20" y="${(geom.liquidTop - 6).toFixed(1)}" width="160" height="${(foamHeight + 6).toFixed(1)}" fill="${vis.foam}" opacity="0.97"/>
         <rect x="20" y="${(geom.liquidTop + foamHeight - 3.5).toFixed(1)}" width="160" height="3.5" fill="rgba(40,18,8,0.18)"/>
         <ellipse cx="${geom.centerX}" cy="${(geom.liquidTop - 2.5).toFixed(1)}" rx="${(geom.liquidWidth * 0.56).toFixed(1)}" ry="4.8" fill="#ffffff" opacity="0.44"/>
       </g>`
    : '';

  // 3D top liquid meniscus surface + luminous front lip
  const topMeniscusSvg = !vis.foam
    ? `<g>
         <ellipse cx="${geom.centerX}" cy="${liquidTopY.toFixed(1)}" rx="${(geom.liquidWidth * 0.56).toFixed(1)}" ry="${isMini ? '3.5' : '4.8'}" fill="url(#${uid}-meniscus)"/>
         <path d="M ${(geom.centerX - geom.liquidWidth * 0.48).toFixed(1)} ${(liquidTopY + 0.8).toFixed(1)} Q ${geom.centerX} ${(liquidTopY + (isMini ? 3.8 : 5.2)).toFixed(1)} ${(geom.centerX + geom.liquidWidth * 0.48).toFixed(1)} ${(liquidTopY + 0.8).toFixed(1)}"
           fill="none" stroke="rgba(255,255,255,0.52)" stroke-width="${isMini ? '1.1' : '1.3'}" stroke-linecap="round"/>
       </g>`
    : '';

  const auraSvg = !isMini
    ? `<circle cx="${geom.centerX}" cy="${((geom.liquidTop + geom.liquidBottom) * 0.5).toFixed(1)}" r="80" fill="url(#${uid}-aura)" opacity="0.56"/>`
    : `<circle cx="${geom.centerX}" cy="${((geom.liquidTop + geom.liquidBottom) * 0.5).toFixed(1)}" r="68" fill="url(#${uid}-aura)" opacity="0.42"/>`;

  // Grounding tabletop shadow + colored optical caustic pool beneath the glass foot/base
  const tableCausticsSvg = `
    <ellipse cx="${geom.centerX}" cy="${(geom.baseY + 4.5).toFixed(1)}" rx="${(geom.baseRx * 1.24).toFixed(1)}" ry="${isMini ? '5.5' : '7.2'}" fill="rgba(2, 2, 8, 0.62)"/>
    <ellipse cx="${geom.centerX}" cy="${(geom.baseY + 3.2).toFixed(1)}" rx="${(geom.baseRx * 0.92).toFixed(1)}" ry="${isMini ? '3.8' : '4.8'}" fill="url(#${uid}-caustic)" opacity="${isMini ? '0.68' : '0.84'}"/>
  `;

  const steamSvg = !isMini && recipe.method === 'warm' ? renderWarmSteam(geom) : '';
  const frostSvg = !isMini && recipe.ice !== 'none' && recipe.method !== 'warm' ? renderCondensation(geom) : '';
  const rimRx = (geom.rimRight - geom.rimLeft) / 2;

  return `
    <svg class="cocktail-glass-svg ${isMini ? 'cocktail-glass-mini' : 'cocktail-glass-hero'}" style="--caustic-c:${bottomCausticColor}" viewBox="0 0 200 232" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <clipPath id="${uid}-clip">
          <path d="${geom.liquidClip}"/>
        </clipPath>
        <linearGradient id="${uid}-liq" x1="0" y1="${geom.liquidTop}" x2="0" y2="${geom.liquidBottom}" gradientUnits="userSpaceOnUse">
          ${gradStops.join('')}
        </linearGradient>
        <!-- 3D cylindrical volume shading across the liquid width -->
        <linearGradient id="${uid}-cyl" x1="${geom.centerX - geom.liquidWidth * 0.55}" y1="0" x2="${geom.centerX + geom.liquidWidth * 0.55}" y2="0" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="#05030d" stop-opacity="0.28"/>
          <stop offset="18%" stop-color="#ffffff" stop-opacity="0.16"/>
          <stop offset="52%" stop-color="#ffffff" stop-opacity="0.03"/>
          <stop offset="80%" stop-color="#ffffff" stop-opacity="0.19"/>
          <stop offset="100%" stop-color="#05030d" stop-opacity="0.32"/>
        </linearGradient>
        <linearGradient id="${uid}-meniscus" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="#ffffff" stop-opacity="0.14"/>
          <stop offset="100%" stop-color="#ffffff" stop-opacity="0.38"/>
        </linearGradient>
        <radialGradient id="${uid}-aura" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="${vis.glowColor}" stop-opacity="0.76"/>
          <stop offset="55%" stop-color="${vis.glowColor}" stop-opacity="0.20"/>
          <stop offset="100%" stop-color="${vis.glowColor}" stop-opacity="0"/>
        </radialGradient>
        <radialGradient id="${uid}-caustic" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="${bottomCausticColor}" stop-opacity="0.75"/>
          <stop offset="55%" stop-color="${vis.glowColor}" stop-opacity="0.32"/>
          <stop offset="100%" stop-color="${vis.glowColor}" stop-opacity="0"/>
        </radialGradient>
        <linearGradient id="${uid}-glass-sheen" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#ffffff" stop-opacity="0.26"/>
          <stop offset="22%" stop-color="#ffffff" stop-opacity="0.06"/>
          <stop offset="55%" stop-color="#ffffff" stop-opacity="0.01"/>
          <stop offset="84%" stop-color="#ffffff" stop-opacity="0.07"/>
          <stop offset="100%" stop-color="#ffffff" stop-opacity="0.22"/>
        </linearGradient>
      </defs>

      ${auraSvg}
      ${tableCausticsSvg}
      ${steamSvg}

      <!-- Back rim arc (behind garnish & liquid top) -->
      <path d="M ${geom.rimLeft} ${geom.rimY} A ${rimRx.toFixed(1)} 3.4 0 0 1 ${geom.rimRight} ${geom.rimY}" fill="none" stroke="rgba(255,255,255,0.24)" stroke-width="1.05"/>

      <!-- Crystal stem, footplate, handle, or heavy sham base + internal caustic refraction -->
      ${geom.extraGlass}
      ${geom.shamRefraction || ''}
      <path d="${geom.bowlOuter}" fill="url(#${uid}-glass-sheen)"/>

      <!-- Clipped 3D liquid, cylindrical shading, faceted ice, carbonation & sculpted foam -->
      <g clip-path="url(#${uid}-clip)">
        <rect x="20" y="${liquidTopY.toFixed(1)}" width="160" height="${(geom.liquidBottom - liquidTopY + 14).toFixed(1)}" fill="url(#${uid}-liq)" opacity="${liquidOpacity}"/>
        <rect x="20" y="${liquidTopY.toFixed(1)}" width="160" height="${(geom.liquidBottom - liquidTopY + 14).toFixed(1)}" fill="url(#${uid}-cyl)"/>
        ${layerMenisciSvg}
        ${renderIce(recipe.ice, geom, isMini)}
        ${vis.bubbles ? renderBubbles(geom, isMini) : ''}
        ${topMeniscusSvg}
        ${foamSvg}
      </g>

      <!-- Botanical / Fruit / Pick Garnish -->
      ${renderGarnish(vis.garnishType, geom, isMini)}

      <!-- Crystal Glass Bowl Contour, Dual Specular Ribs, Condensation & Front Rim Lip -->
      <path d="${geom.bowlOuter}" fill="none" stroke="rgba(255,255,255,${isMini ? '0.58' : '0.48'})" stroke-width="${isMini ? '2.3' : '1.75'}" stroke-linejoin="round"/>
      <path d="${geom.highlightPath}" fill="none" stroke="rgba(255,255,255,0.52)" stroke-width="${isMini ? '3.1' : '2.5'}" stroke-linecap="round"/>
      <path d="${geom.rightHighlightPath}" fill="none" stroke="rgba(255,255,255,0.22)" stroke-width="${isMini ? '1.6' : '1.35'}" stroke-linecap="round"/>
      ${frostSvg}
      <path d="M ${geom.rimLeft} ${geom.rimY} A ${rimRx.toFixed(1)} 3.4 0 0 0 ${geom.rimRight} ${geom.rimY}" fill="none" stroke="rgba(255,255,255,0.52)" stroke-width="${isMini ? '1.5' : '1.3'}"/>

      <!-- Crystalline Salt / Sugar / Tajin / Cocoa Rim -->
      ${renderRim(vis.rim, geom)}
    </svg>
  `;
}
