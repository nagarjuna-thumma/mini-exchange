import type { TradeTick } from '../types';
import { formatPrice, formatQuantity, formatTime } from '../format';

export function TradeTape({ trades }: { trades: TradeTick[] }) {
  return (
    <section className="panel" aria-label="Trades">
      <h2>Trades</h2>
      <div className="scroll">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th className="num">Price</th>
              <th className="num">Size</th>
            </tr>
          </thead>
          <tbody>
            {trades.slice(0, 40).map((t) => (
              <tr key={t.id} className={t.aggressorSide === 'BUY' ? 'bid' : 'ask'}>
                <td className="muted">{formatTime(t.executedAt)}</td>
                <td className="num price">{formatPrice(t.price)}</td>
                <td className="num">{formatQuantity(t.quantity)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
