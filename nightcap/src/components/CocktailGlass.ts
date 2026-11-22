import type { GarnishType, GlassType, IceType, Recipe } from '../types';

let svgUidCounter = 0;

interface GlassGeometry {
  bowlOuter: string;
  liquidClip: string;
  extraGlass: string; // stem, base, handle, thick crystal bottom
  highlightPath: string;
  rimY: number;
  rimLeft: number;
  rimRight: number;
  liquidTop: number;
  liquidBottom: number;
  centerX: number;
  liquidWidth: number;
}

const GLASS_GEOMETRIES: Record<GlassType, GlassGeometry> = {
  coupe: {
    bowlOuter: 'M 42 78 C 42 128 158 128 158 78 Z',
    liquidClip: 'M 46 84 C 48 123 152 123 154 84 Z',
    extraGlass:
      '<path d="M 97 125 L 97 196 C 97 202 68 205 64 210 L 136 210 C 132 205 103 202 103 196 L 103 125 Z" fill="rgba(255,255,255,0.09)" stroke="rgba(255,255,255,0.34)" stroke-width="1.4"/>' +
      '<ellipse cx="100" cy="210" rx="36" ry="5.5" fill="rgba(255,255,255,0.12)" stroke="rgba(255,255,255,0.32)" stroke-width="1.3"/>',
    highlightPath: 'M 52 84 C 54 106 66 116 80 120',
    rimY: 78,
    rimLeft: 42,
    rimRight: 158,
    liquidTop: 88,
    liquidBottom: 124,
    centerX: 100,
    liquidWidth: 96,
  },
  martini: {
    bowlOuter: 'M 38 72 L 100 138 L 162 72 Z',
    liquidClip: 'M 45 81 L 100 133 L 155 81 Z',
    extraGlass:
      '<path d="M 97.2 136 L 97.2 198 C 97.2 203 68 206 64 210 L 136 210 C 132 206 102.8 203 102.8 198 L 102.8 136 Z" fill="rgba(255,255,255,0.09)" stroke="rgba(255,255,255,0.34)" stroke-width="1.4"/>' +
      '<ellipse cx="100" cy="210" rx="36" ry="5.5" fill="rgba(255,255,255,0.12)" stroke="rgba(255,255,255,0.32)" stroke-width="1.3"/>',
    highlightPath: 'M 49 78 L 88 122',
    rimY: 72,
    rimLeft: 38,
    rimRight: 162,
    liquidTop: 86,
    liquidBottom: 133,
    centerX: 100,
    liquidWidth: 94,
  },
  rocks: {
    bowlOuter: 'M 48 84 L 56 202 Q 100 212 144 202 L 152 84 Z',
    liquidClip: 'M 52 96 L 59 188 Q 100 195 141 188 L 148 96 Z',
    extraGlass:
      '<path d="M 55 184 Q 100 192 145 184 L 144 202 Q 100 212 56 202 Z" fill="rgba(255,255,255,0.15)" stroke="rgba(255,255,255,0.28)" stroke-width="1.2"/>',
    highlightPath: 'M 58 92 L 64 178',
    rimY: 84,
    rimLeft: 48,
    rimRight: 152,
    liquidTop: 102,
    liquidBottom: 192,
    centerX: 100,
    liquidWidth: 88,
  },
  highball: {
    bowlOuter: 'M 58 52 L 64 204 Q 100 213 136 204 L 142 52 Z',
    liquidClip: 'M 62 64 L 67 192 Q 100 199 133 192 L 138 64 Z',
    extraGlass:
      '<path d="M 64 189 Q 100 197 136 189 L 136 204 Q 100 213 64 204 Z" fill="rgba(255,255,255,0.16)" stroke="rgba(255,255,255,0.28)" stroke-width="1.2"/>',
    highlightPath: 'M 67 62 L 71 184',
    rimY: 52,
    rimLeft: 58,
    rimRight: 142,
    liquidTop: 72,
    liquidBottom: 196,
    centerX: 100,
    liquidWidth: 72,
  },
  wine: {
    bowlOuter: 'M 56 58 C 42 108 55 148 100 150 C 145 148 158 108 144 58 Z',
    liquidClip: 'M 56 76 C 48 112 60 144 100 146 C 140 144 152 112 144 76 Z',
    extraGlass:
      '<path d="M 97.2 149 L 97.2 199 C 97.2 204 69 207 66 211 L 134 211 C 131 207 102.8 204 102.8 199 L 102.8 149 Z" fill="rgba(255,255,255,0.09)" stroke="rgba(255,255,255,0.34)" stroke-width="1.4"/>' +
      '<ellipse cx="100" cy="211" rx="34" ry="5" fill="rgba(255,255,255,0.12)" stroke="rgba(255,255,255,0.32)" stroke-width="1.3"/>',
    highlightPath: 'M 61 68 C 54 100 60 124 76 138',
    rimY: 58,
    rimLeft: 56,
    rimRight: 144,
    liquidTop: 82,
    liquidBottom: 146,
    centerX: 100,
    liquidWidth: 84,
  },
  shot: {
    bowlOuter: 'M 64 96 L 74 200 Q 100 208 126 200 L 136 96 Z',
    liquidClip: 'M 68 106 L 76 180 Q 100 185 124 180 L 132 106 Z',
    extraGlass:
      '<path d="M 73 177 Q 100 183 127 177 L 126 200 Q 100 208 74 200 Z" fill="rgba(255,255,255,0.2)" stroke="rgba(255,255,255,0.32)" stroke-width="1.2"/>',
    highlightPath: 'M 73 104 L 80 172',
    rimY: 96,
    rimLeft: 64,
    rimRight: 136,
    liquidTop: 110,
    liquidBottom: 182,
    centerX: 100,
    liquidWidth: 58,
  },
  flute: {
    bowlOuter: 'M 75 44 L 78 134 C 78 148 92 154 100 154 C 108 154 122 148 122 134 L 125 44 Z',
    liquidClip: 'M 78 60 L 81 132 C 81 144 92 149 100 149 C 108 149 119 144 119 132 L 122 60 Z',
    extraGlass:
      '<path d="M 97.6 153 L 97.6 200 C 97.6 204 72 207 70 211 L 130 211 C 128 207 102.4 204 102.4 200 L 102.4 153 Z" fill="rgba(255,255,255,0.09)" stroke="rgba(255,255,255,0.34)" stroke-width="1.4"/>' +
      '<ellipse cx="100" cy="211" rx="30" ry="4.5" fill="rgba(255,255,255,0.12)" stroke="rgba(255,255,255,0.32)" stroke-width="1.3"/>',
    highlightPath: 'M 83 54 L 85 132',
    rimY: 44,
    rimLeft: 75,
    rimRight: 125,
    liquidTop: 64,
    liquidBottom: 149,
    centerX: 100,
    liquidWidth: 42,
  },
  tiki: {
    bowlOuter:
      'M 54 54 C 66 80 48 118 54 156 C 58 176 76 184 100 184 C 124 184 142 176 146 156 C 152 118 134 80 146 54 Z',
    liquidClip:
      'M 59 68 C 68 88 53 120 58 154 C 62 171 78 179 100 179 C 122 179 138 171 142 154 C 147 120 132 88 141 68 Z',
    extraGlass:
      '<path d="M 91 183 L 88 202 C 88 205 68 207 66 211 L 134 211 C 132 207 112 205 112 202 L 109 183 Z" fill="rgba(255,255,255,0.11)" stroke="rgba(255,255,255,0.34)" stroke-width="1.4"/>' +
      '<ellipse cx="100" cy="211" rx="34" ry="5" fill="rgba(255,255,255,0.13)" stroke="rgba(255,255,255,0.3)" stroke-width="1.3"/>',
    highlightPath: 'M 64 70 C 66 96 58 124 64 154',
    rimY: 54,
    rimLeft: 54,
    rimRight: 146,
    liquidTop: 72,
    liquidBottom: 179,
    centerX: 100,
    liquidWidth: 82,
  },
  mug: {
    bowlOuter: 'M 46 78 L 52 196 Q 96 208 140 196 L 146 78 Z',
    liquidClip: 'M 50 90 L 55 190 Q 96 200 137 190 L 142 90 Z',
    extraGlass:
      '<path d="M 145 96 C 178 96 178 170 141 170" fill="none" stroke="rgba(255,220,190,0.45)" stroke-width="7.5" stroke-linecap="round"/>' +
      '<path d="M 145 96 C 178 96 178 170 141 170" fill="none" stroke="rgba(255,255,255,0.28)" stroke-width="2" stroke-linecap="round"/>',
    highlightPath: 'M 56 88 L 60 182',
    rimY: 78,
    rimLeft: 46,
    rimRight: 146,
    liquidTop: 94,
    liquidBottom: 196,
    centerX: 96,
    liquidWidth: 86,
  },
  pitcher: {
    bowlOuter:
      'M 42 62 L 34 65 L 48 80 C 42 122 40 172 54 198 Q 96 210 138 198 C 150 172 150 122 142 78 L 146 62 Z',
    liquidClip:
      'M 49 82 C 45 122 44 170 57 193 Q 96 203 135 193 C 146 170 146 122 139 82 Z',
    extraGlass:
      '<path d="M 144 82 C 178 86 180 166 144 174" fill="none" stroke="rgba(255,255,255,0.34)" stroke-width="6.5" stroke-linecap="round"/>' +
      '<path d="M 144 82 C 178 86 180 166 144 174" fill="none" stroke="rgba(255,255,255,0.2)" stroke-width="2" stroke-linecap="round"/>',
    highlightPath: 'M 55 86 C 50 124 51 162 61 186',
    rimY: 62,
    rimLeft: 42,
    rimRight: 146,
    liquidTop: 86,
    liquidBottom: 198,
    centerX: 96,
    liquidWidth: 88,
  },
};

function renderIce(ice: IceType, geom: GlassGeometry, isMini: boolean): string {
  if (ice === 'none') return '';
  const cx = geom.centerX;
  const top = geom.liquidTop;
  const bot = geom.liquidBottom;
  const h = bot - top;

  if (ice === 'big-cube') {
    const size = Math.min(geom.liquidWidth * 0.62, h * 0.66, 56);
    const x = cx - size / 2;
    const y = top + h * 0.14;
    return `
      <g opacity="0.72">
        <rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${size.toFixed(1)}" height="${size.toFixed(1)}" rx="7"
          transform="rotate(-4 ${cx} ${(y + size / 2).toFixed(1)})"
          fill="rgba(255,255,255,0.16)" stroke="rgba(255,255,255,0.52)" stroke-width="${isMini ? 2 : 1.3}"/>
        <path d="M ${(x + 6).toFixed(1)} ${(y + 8).toFixed(1)} L ${(x + size * 0.55).toFixed(1)} ${(y + 8).toFixed(1)} M ${(x + 6).toFixed(1)} ${(y + 8).toFixed(1)} L ${(x + 6).toFixed(1)} ${(y + size * 0.45).toFixed(1)}"
          transform="rotate(-4 ${cx} ${(y + size / 2).toFixed(1)})"
          stroke="rgba(255,255,255,0.65)" stroke-width="${isMini ? 2 : 1.5}" stroke-linecap="round" fill="none"/>
      </g>
    `;
  }

  if (ice === 'cubes') {
    const count = h > 85 ? 4 : 3;
    const cubeSize = Math.min(26, geom.liquidWidth * 0.34);
    const offsets = [
      { dx: -14, dy: 0.12, rot: -12 },
      { dx: 12, dy: 0.22, rot: 14 },
      { dx: -9, dy: 0.48, rot: 9 },
      { dx: 11, dy: 0.64, rot: -16 },
    ];
    return offsets
      .slice(0, count)
      .map((o) => {
        const x = cx + o.dx - cubeSize / 2;
        const y = top + h * o.dy;
        return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${cubeSize.toFixed(1)}" height="${cubeSize.toFixed(1)}" rx="5"
          transform="rotate(${o.rot} ${(x + cubeSize / 2).toFixed(1)} ${(y + cubeSize / 2).toFixed(1)})"
          fill="rgba(255,255,255,0.17)" stroke="rgba(255,255,255,0.45)" stroke-width="${isMini ? 1.8 : 1.15}"/>`;
      })
      .join('');
  }

  // Crushed ice
  const shards: string[] = [];
  const rows = isMini ? 3 : 5;
  for (let r = 0; r < rows; r++) {
    const y = top - 2 + (r / rows) * (h * 0.65);
    const cols = isMini ? 3 : 5;
    for (let c = 0; c < cols; c++) {
      const frac = (c + 0.5) / cols - 0.5;
      const x = cx + frac * geom.liquidWidth * 0.8 + ((r * 7 + c * 11) % 7 - 3);
      const sz = isMini ? 9 : 11;
      const rot = ((r * 37 + c * 53) % 70) - 35;
      shards.push(
        `<polygon points="${x},${y - sz * 0.5} ${x + sz * 0.6},${y} ${x},${y + sz * 0.5} ${x - sz * 0.55},${y + 1}"
          transform="rotate(${rot} ${x} ${y})"
          fill="rgba(240,250,255,0.28)" stroke="rgba(255,255,255,0.52)" stroke-width="0.9"/>`
      );
    }
  }
  return `<g>${shards.join('')}</g>`;
}

function renderBubbles(geom: GlassGeometry, isMini: boolean): string {
  const cx = geom.centerX;
  const span = geom.liquidWidth * 0.62;
  const count = isMini ? 5 : 13;
  const items: string[] = [];
  for (let i = 0; i < count; i++) {
    const seed = (i * 29 + 13) % 100;
    const x = cx + (seed / 100 - 0.5) * span;
    const y = geom.liquidBottom - 10 - ((i * 19) % Math.max(24, Math.floor(geom.liquidBottom - geom.liquidTop - 14)));
    const r = isMini ? 1.8 : 1.2 + (i % 3) * 0.85;
    const delay = ((i * 0.37) % 2.6).toFixed(2);
    const dur = (2.1 + (i % 4) * 0.45).toFixed(2);
    items.push(
      `<circle class="${isMini ? '' : 'drink-bubble'}" cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r.toFixed(1)}" fill="rgba(255,255,255,0.68)" style="${isMini ? '' : `animation-delay:-${delay}s;animation-duration:${dur}s;`}"/>`
    );
  }
  return `<g>${items.join('')}</g>`;
}

function renderRim(rim: Recipe['visual']['rim'], geom: GlassGeometry): string {
  if (!rim) return '';
  const palette: Record<NonNullable<Recipe['visual']['rim']>, [string, string]> = {
    salt: ['#ffffff', '#e2e8f0'],
    sugar: ['#fffbeb', '#fde68a'],
    tajin: ['#dc2626', '#fb923c'],
    cocoa: ['#451a03', '#78350f'],
  };
  const [c1, c2] = palette[rim];
  const dots: string[] = [];
  const start = geom.rimLeft + 2;
  const end = geom.rimLeft + (geom.rimRight - geom.rimLeft) * 0.62;
  const steps = 20;
  for (let i = 0; i <= steps; i++) {
    const x = start + ((end - start) * i) / steps;
    const dy = ((i * 7) % 5) - 2;
    const r = 1.4 + (i % 3) * 0.55;
    dots.push(`<circle cx="${x.toFixed(1)}" cy="${(geom.rimY + dy).toFixed(1)}" r="${r.toFixed(1)}" fill="${i % 2 === 0 ? c1 : c2}" opacity="0.9"/>`);
  }
  return `<g>${dots.join('')}</g>`;
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
          ? { rind: '#15803d', pith: '#ecfccb', pulp: '#84cc16' }
          : type === 'orange-slice'
            ? { rind: '#ea580c', pith: '#ffedd5', pulp: '#fb923c' }
            : { rind: '#fb923c', pith: '#ffe4e6', pulp: '#fb7185' };
      const rad = isMini ? 16 : 19;
      return `
        <g transform="translate(${rx}, ${ry}) rotate(18)">
          <circle cx="0" cy="0" r="${rad}" fill="${colors.rind}"/>
          <circle cx="0" cy="0" r="${rad - 2.5}" fill="${colors.pith}"/>
          <circle cx="0" cy="0" r="${rad - 4.5}" fill="${colors.pulp}"/>
          <g stroke="${colors.pith}" stroke-width="1.2" opacity="0.85">
            <line x1="0" y1="${-(rad - 4)}" x2="0" y2="${rad - 4}"/>
            <line x1="${-(rad - 4)}" y1="0" x2="${rad - 4}" y2="0"/>
            <line x1="${-(rad - 6) * 0.72}" y1="${-(rad - 6) * 0.72}" x2="${(rad - 6) * 0.72}" y2="${(rad - 6) * 0.72}"/>
            <line x1="${(rad - 6) * 0.72}" y1="${-(rad - 6) * 0.72}" x2="${-(rad - 6) * 0.72}" y2="${(rad - 6) * 0.72}"/>
          </g>
        </g>
      `;
    }
    case 'lemon-twist':
      return `
        <g transform="translate(${rx - 6}, ${ry - 2})">
          <path d="M -8 10 C -4 -14 14 -16 12 0 C 10 12 22 14 24 -4" fill="none" stroke="#facc15" stroke-width="4.5" stroke-linecap="round"/>
          <path d="M -8 10 C -4 -14 14 -16 12 0 C 10 12 22 14 24 -4" fill="none" stroke="#fef9c3" stroke-width="1.4" stroke-linecap="round"/>
        </g>
      `;
    case 'olive-pick':
      return `
        <g>
          <line x1="${cx - 18}" y1="${ly + 24}" x2="${rx + 12}" y2="${ry - 18}" stroke="#e2e8f0" stroke-width="2" stroke-linecap="round"/>
          <circle cx="${rx + 12}" cy="${ry - 18}" r="3.2" fill="#f5d0fe"/>
          <g transform="translate(${cx - 4}, ${ly + 10}) rotate(-32)">
            <ellipse cx="0" cy="0" rx="10" ry="7.5" fill="#65a30d" stroke="#365314" stroke-width="1"/>
            <circle cx="-4" cy="0" r="2.6" fill="#dc2626"/>
            <ellipse cx="2" cy="-2.5" rx="3" ry="1.3" fill="rgba(255,255,255,0.35)"/>
          </g>
          <g transform="translate(${cx + 12}, ${ly - 2}) rotate(-32)">
            <ellipse cx="0" cy="0" rx="9.5" ry="7" fill="#84cc16" stroke="#3f6212" stroke-width="1"/>
            <circle cx="-3.5" cy="0" r="2.4" fill="#dc2626"/>
            <ellipse cx="2" cy="-2" rx="2.8" ry="1.2" fill="rgba(255,255,255,0.35)"/>
          </g>
        </g>
      `;
    case 'cherry-pick':
      return `
        <g>
          <line x1="${cx - 12}" y1="${ly + 24}" x2="${rx + 10}" y2="${ry - 18}" stroke="#e2e8f0" stroke-width="2" stroke-linecap="round"/>
          <circle cx="${rx + 10}" cy="${ry - 18}" r="3" fill="#fda4af"/>
          <circle cx="${cx + 2}" cy="${ly + 8}" r="9.5" fill="#9f1239" stroke="#4c0519" stroke-width="1"/>
          <circle cx="${cx - 1}" cy="${ly + 5}" r="2.8" fill="rgba(255,255,255,0.45)"/>
        </g>
      `;
    case 'mint-sprig':
      return `
        <g transform="translate(${cx + 14}, ${ry + 4})">
          <path d="M 0 12 Q 2 -12 8 -26" fill="none" stroke="#15803d" stroke-width="2" stroke-linecap="round"/>
          <ellipse cx="-6" cy="-4" rx="9" ry="5" transform="rotate(-35 -6 -4)" fill="#22c55e" stroke="#166534" stroke-width="0.9"/>
          <ellipse cx="10" cy="-10" rx="9.5" ry="5.2" transform="rotate(28 10 -10)" fill="#4ade80" stroke="#166534" stroke-width="0.9"/>
          <ellipse cx="0" cy="-20" rx="8.5" ry="4.8" transform="rotate(-18 0 -20)" fill="#34d399" stroke="#166534" stroke-width="0.9"/>
          <ellipse cx="13" cy="-22" rx="7" ry="4" transform="rotate(42 13 -22)" fill="#22c55e" stroke="#166534" stroke-width="0.9"/>
        </g>
      `;
    case 'rosemary':
      return `
        <g transform="translate(${cx + 12}, ${ry + 18}) rotate(12)">
          <line x1="0" y1="22" x2="0" y2="-34" stroke="#166534" stroke-width="2.2" stroke-linecap="round"/>
          ${[-28, -21, -14, -7, 0, 7]
            .map(
              (y) =>
                `<line x1="0" y1="${y}" x2="-10" y2="${y - 7}" stroke="#34d399" stroke-width="2" stroke-linecap="round"/>` +
                `<line x1="0" y1="${y + 2}" x2="10" y2="${y - 5}" stroke="#4ade80" stroke-width="2" stroke-linecap="round"/>`
            )
            .join('')}
        </g>
      `;
    case 'cucumber-ribbon':
      return `
        <g>
          <path d="M ${cx - 22} ${ly + 42} Q ${cx - 8} ${ly + 14} ${cx - 18} ${ry - 14}" fill="none" stroke="#15803d" stroke-width="9" stroke-linecap="round" opacity="0.88"/>
          <path d="M ${cx - 22} ${ly + 42} Q ${cx - 8} ${ly + 14} ${cx - 18} ${ry - 14}" fill="none" stroke="#bbf7d0" stroke-width="4.5" stroke-linecap="round" opacity="0.85"/>
        </g>
      `;
    case 'celery':
      return `
        <g transform="translate(${cx + 16}, ${ry + 16}) rotate(14)">
          <path d="M -4 48 L -5 -32 L 5 -32 L 4 48 Z" fill="#84cc16" stroke="#4d7c0f" stroke-width="1.2"/>
          <line x1="-1.5" y1="44" x2="-1.5" y2="-30" stroke="#bef264" stroke-width="1.4"/>
          <ellipse cx="-6" cy="-35" rx="7" ry="4.5" transform="rotate(-30 -6 -35)" fill="#65a30d"/>
          <ellipse cx="6" cy="-37" rx="7" ry="4.5" transform="rotate(28 6 -37)" fill="#84cc16"/>
          <ellipse cx="0" cy="-42" rx="6" ry="4" fill="#4d7c0f"/>
        </g>
      `;
    case 'coffee-beans':
      return `
        <g transform="translate(${cx}, ${ly - 1})">
          ${[
            { x: -10, y: 1, r: -18 },
            { x: 0, y: -2, r: 10 },
            { x: 10, y: 1, r: 28 },
          ]
            .map(
              (b) =>
                `<g transform="translate(${b.x}, ${b.y}) rotate(${b.r})">
                  <ellipse cx="0" cy="0" rx="5.2" ry="3.4" fill="#3b1d0a" stroke="#1c0d04" stroke-width="0.9"/>
                  <path d="M -4 0 Q 0 1.4 4 0" fill="none" stroke="#c89b72" stroke-width="0.9"/>
                </g>`
            )
            .join('')}
        </g>
      `;
    case 'cinnamon-stick':
      return `
        <g transform="translate(${cx + 22}, ${ry + 6}) rotate(22)">
          <rect x="-4.5" y="-32" width="9" height="62" rx="4" fill="#92400e" stroke="#451a03" stroke-width="1.2"/>
          <line x1="-1.5" y1="-28" x2="-1.5" y2="26" stroke="#d97706" stroke-width="1.5" opacity="0.7"/>
          <ellipse cx="0" cy="-32" rx="4.5" ry="2.2" fill="#78350f" stroke="#451a03" stroke-width="0.9"/>
        </g>
      `;
    case 'berry':
      return `
        <g transform="translate(${cx + 12}, ${ry + 4})">
          <line x1="-10" y1="16" x2="18" y2="-22" stroke="#e2e8f0" stroke-width="1.8" stroke-linecap="round"/>
          <g transform="translate(2, -2)">
            <circle cx="-3" cy="-2" r="4" fill="#be185d"/>
            <circle cx="3" cy="-2" r="4" fill="#ec4899"/>
            <circle cx="0" cy="3" r="4.2" fill="#db2777"/>
            <circle cx="-2" cy="1" r="3.6" fill="#f43f5e"/>
            <circle cx="2" cy="2" r="3.5" fill="#9d174d"/>
          </g>
        </g>
      `;
    case 'pineapple-wedge':
      return `
        <g transform="translate(${rx}, ${ry - 2}) rotate(20)">
          <path d="M -14 10 L 14 10 L 0 -18 Z" fill="#facc15" stroke="#ca8a04" stroke-width="1.2"/>
          <path d="M -14 10 L 14 10 L 12 15 L -12 15 Z" fill="#a16207"/>
          <path d="M -6 -12 L -14 -26 L -2 -18 L 2 -30 L 6 -16 L 14 -24 L 8 -10" fill="#16a34a" stroke="#14532d" stroke-width="0.9"/>
        </g>
      `;
    case 'chili':
      return `
        <g transform="translate(${rx - 4}, ${ry - 2}) rotate(14)">
          <path d="M -6 14 C -2 -2 8 -12 18 -14 C 14 -2 6 8 -2 18 Z" fill="#dc2626" stroke="#7f1d1d" stroke-width="1"/>
          <path d="M 16 -13 Q 22 -18 24 -12" fill="none" stroke="#16a34a" stroke-width="2.2" stroke-linecap="round"/>
          <path d="M 1 6 Q 6 0 11 -5" fill="none" stroke="rgba(255,255,255,0.45)" stroke-width="1.4" stroke-linecap="round"/>
        </g>
      `;
    case 'flower':
      return `
        <g transform="translate(${cx - 8}, ${ly - 2})">
          ${[0, 72, 144, 216, 288]
            .map(
              (deg) =>
                `<ellipse cx="0" cy="-7" rx="4.5" ry="6.5" transform="rotate(${deg})" fill="#f9a8d4" stroke="#f472b6" stroke-width="0.7" opacity="0.95"/>`
            )
            .join('')}
          <circle cx="0" cy="0" r="3.5" fill="#fde047"/>
        </g>
      `;
    case 'apple-slice':
      return `
        <g transform="translate(${rx - 2}, ${ry}) rotate(22)">
          <path d="M -15 8 C -10 -12 10 -12 15 8 Z" fill="#ecfccb" stroke="#65a30d" stroke-width="2.6"/>
          <path d="M -8 10 C -4 -6 12 -6 17 10 Z" fill="#fefce8" stroke="#dc2626" stroke-width="2.4" opacity="0.92"/>
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
    <g class="warm-steam" fill="none" stroke="rgba(255,245,235,0.34)" stroke-width="2.2" stroke-linecap="round">
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

  // Build vertical gradient (bottom to top: liquidStops[0] is bottom, last is top)
  const stops = vis.liquidStops.length ? vis.liquidStops : ['#c084fc', '#66e0ff'];
  const gradStops: string[] = [];

  if (vis.layered && stops.length >= 2) {
    const n = stops.length;
    for (let i = 0; i < n; i++) {
      const startPct = Math.round((i / n) * 100);
      const endPct = Math.round(((i + 1) / n) * 100);
      const blend = i === 0 ? startPct : Math.min(100, startPct + 4);
      const stopEnd = i === n - 1 ? 100 : Math.max(0, endPct - 4);
      gradStops.push(`<stop offset="${blend}%" stop-color="${stops[n - 1 - i]}"/>`);
      gradStops.push(`<stop offset="${stopEnd}%" stop-color="${stops[n - 1 - i]}"/>`);
    }
  } else {
    const n = stops.length;
    for (let i = 0; i < n; i++) {
      const pct = n === 1 ? 0 : Math.round((i / (n - 1)) * 100);
      // SVG y1=0 is top, y2=1 is bottom, so reverse stops so stops[0] is bottom
      gradStops.push(`<stop offset="${pct}%" stop-color="${stops[n - 1 - i]}"/>`);
    }
  }

  const liquidOpacity = vis.liquidOpacity ?? 0.85;
  const foamHeight = isMini ? 9 : 11;
  const foamSvg = vis.foam
    ? `<rect x="20" y="${geom.liquidTop - 6}" width="160" height="${foamHeight + 6}" fill="${vis.foam}" opacity="0.96"/>
       <ellipse cx="${geom.centerX}" cy="${geom.liquidTop - 3}" rx="${geom.liquidWidth * 0.54}" ry="4.5" fill="#ffffff" opacity="0.35"/>`
    : '';

  const liquidTopY = vis.foam ? geom.liquidTop + foamHeight - 4 : geom.liquidTop;

  const auraSvg = !isMini
    ? `<circle cx="${geom.centerX}" cy="${(geom.liquidTop + geom.liquidBottom) * 0.5}" r="76" fill="url(#${uid}-aura)" opacity="0.52"/>`
    : `<circle cx="${geom.centerX}" cy="${(geom.liquidTop + geom.liquidBottom) * 0.5}" r="66" fill="url(#${uid}-aura)" opacity="0.38"/>`;

  const steamSvg = !isMini && recipe.method === 'warm' ? renderWarmSteam(geom) : '';

  return `
    <svg class="cocktail-glass-svg ${isMini ? 'cocktail-glass-mini' : 'cocktail-glass-hero'}" viewBox="0 0 200 232" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <defs>
        <clipPath id="${uid}-clip">
          <path d="${geom.liquidClip}"/>
        </clipPath>
        <linearGradient id="${uid}-liq" x1="0" y1="${geom.liquidTop}" x2="0" y2="${geom.liquidBottom}" gradientUnits="userSpaceOnUse">
          ${gradStops.join('')}
        </linearGradient>
        <radialGradient id="${uid}-aura" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stop-color="${vis.glowColor}" stop-opacity="0.72"/>
          <stop offset="55%" stop-color="${vis.glowColor}" stop-opacity="0.18"/>
          <stop offset="100%" stop-color="${vis.glowColor}" stop-opacity="0"/>
        </radialGradient>
        <linearGradient id="${uid}-glass-sheen" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#ffffff" stop-opacity="0.22"/>
          <stop offset="28%" stop-color="#ffffff" stop-opacity="0.04"/>
          <stop offset="76%" stop-color="#ffffff" stop-opacity="0.02"/>
          <stop offset="100%" stop-color="#ffffff" stop-opacity="0.16"/>
        </linearGradient>
      </defs>

      ${auraSvg}
      ${steamSvg}

      <!-- Glass back wall & stem/handle -->
      ${geom.extraGlass}
      <path d="${geom.bowlOuter}" fill="url(#${uid}-glass-sheen)"/>

      <!-- Clipped liquid, ice, foam & carbonation bubbles -->
      <g clip-path="url(#${uid}-clip)">
        <rect x="20" y="${liquidTopY}" width="160" height="${geom.liquidBottom - liquidTopY + 14}" fill="url(#${uid}-liq)" opacity="${liquidOpacity}"/>
        ${!vis.foam ? `<ellipse cx="${geom.centerX}" cy="${liquidTopY}" rx="${geom.liquidWidth * 0.56}" ry="4.5" fill="rgba(255,255,255,0.28)"/>` : ''}
        ${renderIce(recipe.ice, geom, isMini)}
        ${vis.bubbles ? renderBubbles(geom, isMini) : ''}
        ${foamSvg}
      </g>

      <!-- Garnish -->
      ${renderGarnish(vis.garnishType, geom, isMini)}

      <!-- Crystal Glass Rim & Specular Highlights -->
      <path d="${geom.bowlOuter}" fill="none" stroke="rgba(255,255,255,${isMini ? '0.52' : '0.42'})" stroke-width="${isMini ? '2.4' : '1.7'}" stroke-linejoin="round"/>
      <path d="${geom.highlightPath}" fill="none" stroke="rgba(255,255,255,0.44)" stroke-width="${isMini ? '3' : '2.4'}" stroke-linecap="round"/>
      <ellipse cx="${geom.centerX}" cy="${geom.rimY}" rx="${(geom.rimRight - geom.rimLeft) / 2}" ry="3.2" fill="none" stroke="rgba(255,255,255,0.34)" stroke-width="1.1"/>

      <!-- Salt / Sugar / Chili / Cocoa Rim -->
      ${renderRim(vis.rim, geom)}
    </svg>
  `;
}
