import type { TradeTick } from '../types';

interface Props {
  trades: TradeTick[];
  width?: number;
  height?: number;
}

/** Last trades as a simple line. Newest trade is on the right. */
export function PriceChart({ trades, width = 560, height = 160 }: Props) {
  const points = trades.slice(0, 100).reverse();
  if (points.length < 2) {
    return <div className="chart-empty">Waiting for trades…</div>;
  }
  const prices = points.map((t) => t.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;
  const pad = 8;
  const x = (i: number) => pad + (i / (points.length - 1)) * (width - pad * 2);
  const y = (p: number) => pad + (1 - (p - min) / range) * (height - pad * 2);
  const path = points.map((t, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(t.price).toFixed(1)}`).join(' ');
  const rising = prices[prices.length - 1] >= prices[0];

  return (
    <svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Recent trade prices">
      <path d={path} className={rising ? 'line up' : 'line down'} />
    </svg>
  );
}
