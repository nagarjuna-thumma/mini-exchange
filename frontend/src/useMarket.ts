import { useEffect, useState } from 'react';
import { api } from './api';
import type { Depth, TradeTick } from './types';

const MAX_TRADES = 120;

/**
 * Live order book and trade tape for one symbol. Loads recent trades once, then follows
 * the server's event stream. EventSource reconnects by itself if the connection drops.
 */
export function useMarket(symbol: string) {
  const [depth, setDepth] = useState<Depth>({ symbol, bids: [], asks: [] });
  const [trades, setTrades] = useState<TradeTick[]>([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDepth({ symbol, bids: [], asks: [] });
    setTrades([]);
    api.trades(symbol).then((recent) => !cancelled && setTrades(recent)).catch(() => undefined);

    const source = new EventSource(`/api/market/${symbol}/stream`);
    source.onopen = () => setConnected(true);
    source.onerror = () => setConnected(false);
    source.addEventListener('depth', (e) => setDepth(JSON.parse((e as MessageEvent).data)));
    source.addEventListener('trades', (e) => {
      const fresh: TradeTick[] = JSON.parse((e as MessageEvent).data);
      setTrades((current) => [...fresh.slice().reverse(), ...current].slice(0, MAX_TRADES));
    });

    return () => {
      cancelled = true;
      source.close();
    };
  }, [symbol]);

  return { depth, trades, connected };
}
