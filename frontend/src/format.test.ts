import { formatAmount, formatPrice, spread } from './format';

describe('format', () => {
  it('shows prices with two decimals', () => {
    expect(formatPrice(42)).toBe('42.00');
    expect(formatPrice(1234.5)).toBe('1,234.50');
    expect(formatPrice(null)).toBe('-');
  });

  it('shows cash with cents and shares as whole numbers', () => {
    expect(formatAmount('CAD', 100)).toBe('100.00');
    expect(formatAmount('NRTH', 1500)).toBe('1,500');
  });

  it('computes the spread without floating point noise', () => {
    expect(spread(10.1, 10.3)).toBe(0.2);
    expect(spread(undefined, 10)).toBeNull();
  });
});
