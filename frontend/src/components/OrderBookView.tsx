import type { Depth } from '../types';
import { formatPrice, formatQuantity, spread } from '../format';

interface Props {
  depth: Depth;
  onPickPrice?: (price: number) => void;
}

/** Depth ladder: asks on top (best ask nearest the middle), bids below. Bars show size. */
export function OrderBookView({ depth, onPickPrice }: Props) {
  const asks = depth.asks.slice().reverse();
  const largest = Math.max(1, ...depth.bids.map((l) => l.quantity), ...depth.asks.map((l) => l.quantity));
  const gap = spread(depth.bids[0]?.price, depth.asks[0]?.price);

  const row = (side: 'bid' | 'ask') => (level: Depth['bids'][number]) => (
    <tr key={`${side}-${level.price}`} className={side} onClick={() => onPickPrice?.(level.price)}>
      <td className="price">{formatPrice(level.price)}</td>
      <td className="num">{formatQuantity(level.quantity)}</td>
      <td className="num muted">{level.orders}</td>
      <td className="bar-cell">
        <span className="bar" style={{ width: `${(level.quantity / largest) * 100}%` }} />
      </td>
    </tr>
  );

  return (
    <section className="panel" aria-label="Order book">
      <h2>Order book</h2>
      <table className="book">
        <thead>
          <tr>
            <th>Price</th>
            <th className="num">Size</th>
            <th className="num">Orders</th>
            <th />
          </tr>
        </thead>
        <tbody data-testid="asks">{asks.map(row('ask'))}</tbody>
        <tbody>
          <tr className="spread">
            <td colSpan={4}>{gap == null ? 'No spread yet' : `Spread ${formatPrice(gap)}`}</td>
          </tr>
        </tbody>
        <tbody data-testid="bids">{depth.bids.map(row('bid'))}</tbody>
      </table>
      {onPickPrice && <p className="hint">Click a level to use its price.</p>}
    </section>
  );
}
