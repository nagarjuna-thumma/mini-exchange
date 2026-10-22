const money = new Intl.NumberFormat('en-CA', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const whole = new Intl.NumberFormat('en-CA', { maximumFractionDigits: 0 });

export const formatPrice = (value: number | null | undefined) => (value == null ? '-' : money.format(value));

export const formatQuantity = (value: number) => whole.format(value);

/** Cash shows cents; share counts are whole numbers. */
export const formatAmount = (asset: string, value: number) => (asset === 'CAD' ? money.format(value) : whole.format(value));

export const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString('en-CA', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });

/** Spread between best bid and best ask, or null if either side is empty. */
export function spread(bestBid?: number, bestAsk?: number): number | null {
  if (bestBid == null || bestAsk == null) return null;
  return Math.round((bestAsk - bestBid) * 100) / 100;
}
