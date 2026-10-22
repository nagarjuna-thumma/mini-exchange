import { useEffect, useState, type FormEvent } from 'react';
import type { NewOrder, OrderType, Side } from '../types';
import { formatPrice } from '../format';

interface Props {
  symbol: string;
  traderId: string;
  bestBid?: number;
  bestAsk?: number;
  pickedPrice?: number;
  onSubmit: (order: NewOrder) => Promise<string>;
}

const PRICE_PATTERN = /^\d+(\.\d{1,2})?$/;

export function OrderTicket({ symbol, traderId, bestBid, bestAsk, pickedPrice, onSubmit }: Props) {
  const [side, setSide] = useState<Side>('BUY');
  const [type, setType] = useState<OrderType>('LIMIT');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('100');
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (pickedPrice != null) setPrice(pickedPrice.toFixed(2));
  }, [pickedPrice]);

  const qty = Number(quantity);
  const qtyValid = Number.isInteger(qty) && qty > 0;
  const priceValid = type === 'MARKET' || (PRICE_PATTERN.test(price) && Number(price) > 0);
  const referencePrice = type === 'LIMIT' ? Number(price) : side === 'BUY' ? bestAsk : bestBid;
  const estimate = qtyValid && priceValid && referencePrice ? referencePrice * qty : null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!qtyValid || !priceValid || busy) return;
    setBusy(true);
    try {
      setMessage(await onSubmit({ traderId, symbol, side, type, quantity: qty, ...(type === 'LIMIT' ? { price } : {}) }));
    } catch (err) {
      setMessage((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" aria-label="Order ticket">
      <h2>New order · {symbol}</h2>
      <form onSubmit={submit} className="ticket">
        <div className="toggle" role="group" aria-label="Side">
          {(['BUY', 'SELL'] as Side[]).map((s) => (
            <button type="button" key={s} className={side === s ? `on ${s.toLowerCase()}` : ''} onClick={() => setSide(s)}>
              {s === 'BUY' ? 'Buy' : 'Sell'}
            </button>
          ))}
        </div>
        <div className="toggle" role="group" aria-label="Order type">
          {(['LIMIT', 'MARKET'] as OrderType[]).map((t) => (
            <button type="button" key={t} className={type === t ? 'on' : ''} onClick={() => setType(t)}>
              {t === 'LIMIT' ? 'Limit' : 'Market'}
            </button>
          ))}
        </div>
        {type === 'LIMIT' && (
          <label>
            Price
            <input value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" placeholder="0.00" />
          </label>
        )}
        <label>
          Quantity
          <input value={quantity} onChange={(e) => setQuantity(e.target.value)} inputMode="numeric" />
        </label>
        <p className="estimate" data-testid="estimate">
          {estimate == null ? 'Enter a price and quantity' : `≈ ${formatPrice(estimate)} CAD`}
        </p>
        <button type="submit" className={`submit ${side.toLowerCase()}`} disabled={!qtyValid || !priceValid || busy}>
          {side === 'BUY' ? 'Buy' : 'Sell'} {symbol}
        </button>
        {message && <p className="message" role="status">{message}</p>}
      </form>
    </section>
  );
}
