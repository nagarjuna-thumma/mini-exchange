import { expect, test, type Page } from '@playwright/test';

/** Opens the screen as one of the demo traders on one stock, and waits for the stock to have traded. */
async function open(page: Page, trader: string, symbol: string): Promise<number> {
  await page.goto('/');
  await page.getByLabel('Trading as').selectOption({ label: trader });
  await page.getByRole('navigation', { name: 'Instruments' }).getByRole('button', { name: new RegExp(symbol) }).click();
  await expect(page.locator('.quote .live')).toHaveText('live');
  // The demo bots trade every few hundred milliseconds, so a last price shows up quickly.
  const last = page.locator('.quote .last');
  await expect(last).toHaveText(/\d+\.\d{2}/, { timeout: 20_000 });
  return Number((await last.innerText()).replace(/,/g, ''));
}

function ticket(page: Page) {
  return page.getByRole('region', { name: 'Order ticket' });
}

function myOrders(page: Page) {
  return page.locator('table').filter({ has: page.getByRole('columnheader', { name: 'Filled' }) });
}

test('a limit order below the market rests on the book and can be cancelled', async ({ page }) => {
  const last = await open(page, 'alice', 'LAKE');
  const price = (Math.floor(last * 0.95 * 100) / 100).toFixed(2); // inside the 10% band, below the bots

  await ticket(page).getByRole('button', { name: 'Buy', exact: true }).click();
  await ticket(page).getByLabel('Price').fill(price);
  await ticket(page).getByLabel('Quantity').fill('3');
  await ticket(page).getByRole('button', { name: 'Buy LAKE' }).click();
  await expect(ticket(page).getByRole('status')).toHaveText('Order is on the book.');

  const row = myOrders(page).locator('tbody tr').filter({ hasText: 'LAKE' }).filter({ hasText: price }).first();
  await expect(row).toContainText('new');
  await row.getByRole('button', { name: 'Cancel' }).click();
  await expect(row).toContainText('cancelled');
});

test('a market buy fills against the market makers', async ({ page }) => {
  await open(page, 'bob', 'NRTH');

  await ticket(page).getByRole('button', { name: 'Buy', exact: true }).click();
  await ticket(page).getByRole('button', { name: 'Market' }).click();
  await ticket(page).getByLabel('Quantity').fill('5');
  await ticket(page).getByRole('button', { name: 'Buy NRTH' }).click();

  await expect(ticket(page).getByRole('status')).toHaveText(/^Filled 5 in \d+ trades?, avg /);
  await expect(myOrders(page).locator('tbody tr').first()).toContainText('filled');
});

test('a fat-finger price is stopped by the risk checks', async ({ page }) => {
  const last = await open(page, 'carol', 'BRCH');

  await ticket(page).getByRole('button', { name: 'Buy', exact: true }).click();
  await ticket(page).getByLabel('Price').fill((last * 10).toFixed(2)); // 4200 instead of 420
  await ticket(page).getByLabel('Quantity').fill('1');
  await ticket(page).getByRole('button', { name: 'Buy BRCH' }).click();

  await expect(ticket(page).getByRole('status')).toContainText('Rejected: Risk check PRICE_BAND');
  await expect(myOrders(page).locator('tbody tr').first()).toContainText('rejected');
});

test('the ledger still balances after all that trading', async ({ page }) => {
  await open(page, 'alice', 'NRTH');
  await expect(page.locator('footer')).toContainText('every asset balances to zero, all holds match open orders');
});
