export type Side = 'BUY' | 'SELL';
export type OrderType = 'LIMIT' | 'MARKET';

export interface Instrument {
  symbol: string;
  name: string;
  currency: string;
  lastPrice: number | null;
}

export interface Trader {
  id: string;
  username: string;
}

export interface Level {
  price: number;
  quantity: number;
  orders: number;
}

export interface Depth {
  symbol: string;
  bids: Level[];
  asks: Level[];
}

export interface TradeTick {
  id: number;
  symbol: string;
  price: number;
  quantity: number;
  aggressorSide: Side;
  executedAt: string;
}

export interface Balance {
  asset: string;
  balance: number;
  held: number;
  available: number;
}

export interface Order {
  id: string;
  symbol: string;
  side: Side;
  type: OrderType;
  limitPrice: number | null;
  quantity: number;
  filledQuantity: number;
  averagePrice: number | null;
  status: 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED';
  rejectReason: string | null;
  createdAt: string;
}

export interface NewOrder {
  traderId: string;
  symbol: string;
  side: Side;
  type: OrderType;
  price?: string;
  quantity: number;
}

export interface LedgerCheck {
  healthy: boolean;
  totalsByAsset: { asset: string; total: number }[];
}
