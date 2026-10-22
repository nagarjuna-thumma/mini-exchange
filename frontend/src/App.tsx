import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import type { Balance, Instrument, LedgerCheck, NewOrder, Order, Trader } from './types';
import { useMarket } from './useMarket';
import { formatPrice } from './format';
import { OrderBookView } from './components/OrderBookView';
import { PriceChart } from './components/PriceChart';
import { TradeTape } from './components/TradeTape';
import { OrderTicket } from './components/OrderTicket';
import { Balances } from './components/Balances';
import { MyOrders } from './components/MyOrders';

export default function App() {
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [traders, setTraders] = useState<Trader[]>([]);
  const [symbol, setSymbol] = useState('NRTH');
  const [traderId, setTraderId] = useState('');
  const [balances, setBalances] = useState<Balance[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [ledger, setLedger] = useState<LedgerCheck | null>(null);
  const [pickedPrice, setPickedPrice] = useState<number>();
  const { depth, trades, connected } = useMarket(symbol);

  useEffect(() => {
    api.instruments().then(setInstruments);
    api.traders().then((list) => {
      const humans = list.filter((t) => !t.username.startsWith('mm-'));
      setTraders(humans);
      if (humans.length) setTraderId((current) => current || humans[0].id);
    });
  }, []);

  const refreshAccount = useCallback(() => {
    if (!traderId) return;
    api.balances(traderId).then(setBalances);
    api.orders(traderId).then(setOrders);
  }, [traderId]);

  // Fills can happen any time another trader hits your order, so poll the account view.
  useEffect(() => {
    refreshAccount();
    const timer = setInterval(refreshAccount, 2000);
    return () => clearInterval(timer);
  }, [refreshAccount]);

  useEffect(() => {
    const check = () => api.ledgerCheck().then(setLedger).catch(() => setLedger(null));
    check();
    const timer = setInterval(check, 5000);
    return () => clearInterval(timer);
  }, []);

  async function place(order: NewOrder) {
    const { order: result, trades: fills } = await api.placeOrder(order);
    refreshAccount();
    if (result.status === 'REJECTED') return `Rejected: ${result.rejectReason}`;
    if (fills.length === 0) return 'Order is on the book.';
    const filled = fills.reduce((n, t) => n + t.quantity, 0);
    return `Filled ${filled} in ${fills.length} trade${fills.length > 1 ? 's' : ''}, avg ${formatPrice(result.averagePrice)}.`;
  }

  async function cancel(order: Order) {
    await api.cancelOrder(order, traderId).catch(() => undefined);
    refreshAccount();
  }

  const last = trades[0]?.price ?? instruments.find((i) => i.symbol === symbol)?.lastPrice ?? null;

  return (
    <div className="app">
      <header>
        <div className="brand">
          mini<span>exchange</span>
        </div>
        <nav aria-label="Instruments">
          {instruments.map((i) => (
            <button key={i.symbol} className={i.symbol === symbol ? 'tab on' : 'tab'} onClick={() => setSymbol(i.symbol)}>
              <strong>{i.symbol}</strong>
              <small>{i.name}</small>
            </button>
          ))}
        </nav>
        <label className="trader">
          Trading as
          <select value={traderId} onChange={(e) => setTraderId(e.target.value)}>
            {traders.map((t) => (
              <option key={t.id} value={t.id}>
                {t.username}
              </option>
            ))}
          </select>
        </label>
      </header>

      <main>
        <OrderBookView depth={depth} onPickPrice={setPickedPrice} />

        <div className="column">
          <section className="panel" aria-label="Price">
            <div className="quote">
              <span className="symbol">{symbol}</span>
              <span className="last">{formatPrice(last)}</span>
              <span className={connected ? 'live on' : 'live'}>{connected ? 'live' : 'reconnecting'}</span>
            </div>
            <PriceChart trades={trades} />
          </section>
          <TradeTape trades={trades} />
        </div>

        <div className="column">
          {traderId && (
            <OrderTicket
              symbol={symbol}
              traderId={traderId}
              bestBid={depth.bids[0]?.price}
              bestAsk={depth.asks[0]?.price}
              pickedPrice={pickedPrice}
              onSubmit={place}
            />
          )}
          <Balances balances={balances} />
        </div>

        <MyOrders orders={orders} onCancel={cancel} />
      </main>

      <footer>
        {ledger && (
          <span className={ledger.healthy ? 'ok' : 'bad'}>
            Ledger check: {ledger.healthy ? 'every asset balances to zero, all holds match open orders' : 'PROBLEM FOUND'}
          </span>
        )}
      </footer>
    </div>
  );
}
