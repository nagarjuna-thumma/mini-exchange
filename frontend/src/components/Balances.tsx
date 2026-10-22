import type { Balance } from '../types';
import { formatAmount } from '../format';

export function Balances({ balances }: { balances: Balance[] }) {
  return (
    <section className="panel" aria-label="Balances">
      <h2>Balances</h2>
      <table>
        <thead>
          <tr>
            <th>Asset</th>
            <th className="num">Total</th>
            <th className="num">Held</th>
            <th className="num">Available</th>
          </tr>
        </thead>
        <tbody>
          {balances.map((b) => (
            <tr key={b.asset}>
              <td>{b.asset}</td>
              <td className="num">{formatAmount(b.asset, b.balance)}</td>
              <td className="num muted">{formatAmount(b.asset, b.held)}</td>
              <td className="num">{formatAmount(b.asset, b.available)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
