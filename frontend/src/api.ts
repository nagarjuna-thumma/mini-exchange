import type { Balance, Depth, Instrument, LedgerCheck, NewOrder, Order, TradeTick, Trader } from './types';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  });
  const body = await response.json().catch(() => null);
  if (!response.ok && response.status !== 422) {
    throw new Error(body?.detail ?? `Request failed (${response.status})`);
  }
  return body as T;
}

export const api = {
  instruments: () => request<Instrument[]>('/api/instruments'),
  traders: () => request<Trader[]>('/api/traders'),
  balances: (traderId: string) => request<Balance[]>(`/api/traders/${traderId}/balances`),
  orders: (traderId: string) => request<Order[]>(`/api/traders/${traderId}/orders`),
  depth: (symbol: string) => request<Depth>(`/api/market/${symbol}/depth`),
  trades: (symbol: string) => request<TradeTick[]>(`/api/market/${symbol}/trades`),
  ledgerCheck: () => request<LedgerCheck>('/api/ledger/check'),
  placeOrder: (order: NewOrder) =>
    request<{ order: Order; trades: TradeTick[] }>('/api/orders', { method: 'POST', body: JSON.stringify(order) }),
  cancelOrder: (order: Order, traderId: string) =>
    request<Order>(`/api/orders/${order.id}?symbol=${order.symbol}&traderId=${traderId}`, { method: 'DELETE' }),
};
