export function escapeHtml(s: string): string {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] || c));
}

export function pluralize(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n) % 100;
  const last = abs % 10;
  if (abs > 10 && abs < 20) return many;
  if (last > 1 && last < 5) return few;
  if (last === 1) return one;
  return many;
}

export function formatScaledAmount(amount: string, ml: number | undefined, mult: number): string {
  if (mult === 1) return amount;
  if (typeof ml === 'number' && ml > 0) {
    return `${ml * mult} мл`;
  }
  // Scale leading numbers like "2 дольки" or "0.5 шт."
  return amount.replace(/^(\d+(?:[.,]\d+)?)/, (match) => {
    const num = Number(match.replace(',', '.'));
    if (Number.isNaN(num)) return match;
    const scaled = num * mult;
    return Number.isInteger(scaled) ? String(scaled) : scaled.toFixed(1).replace('.', ',');
  });
}
