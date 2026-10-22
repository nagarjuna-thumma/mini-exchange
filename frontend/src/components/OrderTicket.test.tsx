import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrderTicket } from './OrderTicket';

const setup = (onSubmit = vi.fn().mockResolvedValue('Order is on the book.')) => {
  render(<OrderTicket symbol="LAKE" traderId="t-1" bestBid={73.2} bestAsk={73.3} onSubmit={onSubmit} />);
  return onSubmit;
};

describe('OrderTicket', () => {
  it('needs a valid price before a limit order can be sent', async () => {
    setup();
    const submit = screen.getByRole('button', { name: 'Buy LAKE' });
    expect(submit).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Price'), '73.255');
    expect(submit).toBeDisabled(); // only two decimals allowed

    await userEvent.clear(screen.getByLabelText('Price'));
    await userEvent.type(screen.getByLabelText('Price'), '73.25');
    expect(submit).toBeEnabled();
    expect(screen.getByTestId('estimate')).toHaveTextContent('7,325.00');
  });

  it('sends a limit order and shows the result', async () => {
    const onSubmit = setup();
    await userEvent.type(screen.getByLabelText('Price'), '73.10');
    await userEvent.click(screen.getByRole('button', { name: 'Buy LAKE' }));

    expect(onSubmit).toHaveBeenCalledWith({
      traderId: 't-1', symbol: 'LAKE', side: 'BUY', type: 'LIMIT', price: '73.10', quantity: 100,
    });
    expect(await screen.findByRole('status')).toHaveTextContent('Order is on the book.');
  });

  it('market sell uses the best bid for its estimate and sends no price', async () => {
    const onSubmit = setup();
    await userEvent.click(screen.getByRole('button', { name: 'Sell' }));
    await userEvent.click(screen.getByRole('button', { name: 'Market' }));

    expect(screen.queryByLabelText('Price')).not.toBeInTheDocument();
    expect(screen.getByTestId('estimate')).toHaveTextContent('7,320.00');

    await userEvent.click(screen.getByRole('button', { name: 'Sell LAKE' }));
    expect(onSubmit).toHaveBeenCalledWith({ traderId: 't-1', symbol: 'LAKE', side: 'SELL', type: 'MARKET', quantity: 100 });
  });
});
