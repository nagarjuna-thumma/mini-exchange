import type { Order } from '../types';
import { formatPrice, formatQuantity, formatTime } from '../format';

interface Props {
  orders: Order[];
  onCancel: (order: Order) => void;
}

const isOpen = (o: Order) => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED';

export function MyOrders({ orders, onCancel }: Props) {
  return (
    <section className="panel wide" aria-label="My orders">
      <h2>My orders</h2>
      <div className="scroll">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Symbol</th>
              <th>Side</th>
              <th>Type</th>
              <th className="num">Price</th>
              <th className="num">Filled</th>
              <th className="num">Avg</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.id}>
                <td className="muted">{formatTime(o.createdAt)}</td>
                <td>{o.symbol}</td>
                <td className={o.side === 'BUY' ? 'bid' : 'ask'}>{o.side}</td>
                <td>{o.type}</td>
                <td className="num">{formatPrice(o.limitPrice)}</td>
                <td className="num">
                  {formatQuantity(o.filledQuantity)} / {formatQuantity(o.quantity)}
                </td>
                <td className="num">{formatPrice(o.averagePrice)}</td>
                <td title={o.rejectReason ?? undefined} className={`status ${o.status.toLowerCase()}`}>
                  {o.status.replace('_', ' ').toLowerCase()}
                </td>
                <td>{isOpen(o) && <button className="link" onClick={() => onCancel(o)}>Cancel</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
