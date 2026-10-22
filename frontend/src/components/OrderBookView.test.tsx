import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrderBookView } from './OrderBookView';

const depth = {
  symbol: 'NRTH',
  bids: [
    { price: 41.99, quantity: 300, orders: 2 },
    { price: 41.95, quantity: 100, orders: 1 },
  ],
  asks: [
    { price: 42.01, quantity: 50, orders: 1 },
    { price: 42.05, quantity: 500, orders: 3 },
  ],
};

describe('OrderBookView', () => {
  it('puts the best ask just above the spread and the best bid just below it', () => {
    render(<OrderBookView depth={depth} />);

    const askPrices = within(screen.getByTestId('asks')).getAllByRole('row').map((r) => r.firstChild?.textContent);
    const bidPrices = within(screen.getByTestId('bids')).getAllByRole('row').map((r) => r.firstChild?.textContent);

    expect(askPrices).toEqual(['42.05', '42.01']);
    expect(bidPrices).toEqual(['41.99', '41.95']);
    expect(screen.getByText('Spread 0.02')).toBeInTheDocument();
  });

  it('passes the clicked level price to the ticket', async () => {
    const onPick = vi.fn();
    render(<OrderBookView depth={depth} onPickPrice={onPick} />);

    await userEvent.click(screen.getByText('42.01'));

    expect(onPick).toHaveBeenCalledWith(42.01);
  });
});
