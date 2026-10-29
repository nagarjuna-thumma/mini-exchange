# mini-exchange

[![build](https://github.com/nagarjuna-thumma/mini-exchange/actions/workflows/build.yml/badge.svg)](https://github.com/nagarjuna-thumma/mini-exchange/actions/workflows/build.yml)

A small stock exchange: a price-time priority matching engine, a double-entry settlement ledger in Postgres, and a live trading screen in React.

![Trading screen](docs/screenshot.png)

I wanted something that shows the parts of financial systems that are easy to get subtly wrong: concurrent orders racing for the same money, keeping an in-memory engine and a database in agreement, and proving afterwards that not a cent went missing.

## How it fits together

```mermaid
flowchart LR
    ui[React trading screen] -- REST --> api[Spring Boot API]
    api --> w1[NRTH thread]
    api --> w2[BRCH thread]
    api --> w3[LAKE thread]
    w1 --> book1[(order book in memory)]
    w1 -- one transaction --> db[(Postgres: orders, trades, ledger)]
    w2 --> db
    w3 --> db
    w1 -. after commit .-> sse[market data stream]
    sse -- Server-Sent Events --> ui
```

Each symbol has its own order book and a single thread that owns it. Orders for the same stock are handled strictly one at a time in arrival order, so matching is deterministic and the engine needs no locks. Different stocks run in parallel.

## The matching engine

`OrderBook` is plain Java with no Spring and no database, so it's easy to test on its own.

- **Price-time priority.** Best price first. At the same price, whoever arrived first fills first.
- **Trade price is the resting order's price.** A buy at 10.50 that hits an ask at 10.00 trades at 10.00.
- **Limit and market orders.** Limit orders rest with any unfilled remainder. Market orders sweep the book and never rest: whatever can't fill is cancelled.
- **Partial fills** on both sides.
- **Self-trade prevention.** If an order would match one of the same trader's resting orders, the resting order is cancelled instead. Trading with yourself is wash trading.

## Pre-trade risk checks

Every order goes through `PreTradeRisk` before it reaches the book. A blocked order is saved as `REJECTED` with the rule and the numbers that stopped it, so there's a record of every attempt, and the API returns 422.

| Rule | Default | What it stops |
|---|---|---|
| `MAX_QUANTITY` | 50,000 shares | An extra zero on the quantity |
| `MAX_NOTIONAL` | 1,000,000 CAD | One order worth more than the desk should risk at once. Market orders are valued at the last trade price |
| `PRICE_BAND` | 10% from the last trade | A limit price typed as 4200 instead of 42.00. Not applied until a stock has traded |
| `ORDER_RATE` | 30 orders per 10 s per trader | A runaway script or a bot stuck in a loop |

The checks run cheapest first, and the rate check goes last so an order blocked for its size or price doesn't use up the trader's allowance. A trader can trade several stocks at once on different threads, so the sliding window for each trader has its own lock. The limits live under `exchange.risk` in `application.yml`, `GET /api/risk/limits` returns them, and `exchange_risk_rejections_total{rule=...}` counts blocks per rule in Prometheus.

## Settlement and the ledger

Every trader has one account per asset (CAD cash, and shares of each stock). Balances only ever change through ledger transactions made of postings, and each transaction must net to zero per asset. A trade is four postings: the buyer's cash goes down, the seller's cash goes up, the seller's shares go down and the buyer's shares go up.

The database enforces the important rules itself, so a bug in the Java code can't break them:

- A deferred constraint trigger rejects any transaction whose postings don't balance per asset at commit.
- Postings are append-only. A trigger blocks updates and deletes.
- A check constraint stops any trader's balance falling below what their open orders have reserved.

**Holds.** Placing an order reserves what it could spend: a buy limit holds `price × quantity` of cash, and a sell holds the shares. If a buy fills at a better price than its limit, the difference is released. Cancelling releases the rest. That's how you can't sell the same shares twice from two different screens.

**Locking.** Cash is shared across symbols, so two symbol threads can touch the same trader's CAD account at once. Account rows are always locked in id order, so those transactions queue up instead of deadlocking.

## Keeping memory and the database in step

The book in memory changes before the database transaction commits. If anything in that transaction fails, the book for that symbol is thrown away and rebuilt from the open orders in Postgres, so memory never gets ahead of what was stored. The same rebuild runs on startup: stop the app mid-session and every resting order is back in the same time order when it starts again.

## Proving nothing went missing

`GET /api/ledger/check` audits everything in one consistent snapshot:

- every asset sums to zero across all accounts
- every account balance equals the sum of its postings
- every account's held amount equals what its open orders reserve

The trading screen runs this every few seconds and shows the result in the footer.

**Scheduled reconciliation.** `ReconciliationJob` runs the same audit every hour and stores each run in `reconciliation_runs` with its time, duration and what failed. The database constraints should make a failure impossible, so a failed run means a bug or someone editing balances by hand. It is logged at ERROR, and the `exchange_ledger_healthy` gauge drops to 0 so an alert can fire. `ReconciliationTest` proves this by changing a balance directly with SQL and checking that the next run catches it and names the account.

## Running it

You need Java 21, Node 22 and Docker.

```bash
docker compose up -d                                   # Postgres on 5436

cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo    # API on 8080

cd frontend
npm install
npm run dev                                            # http://localhost:5173
```

The `demo` profile opens three traders (alice, bob, carol) with cash and shares, plus two market-making bots that quote around a drifting price so the book and the chart move. Pick a trader in the top right and trade against the bots.

## API

| Method | Path | |
|---|---|---|
| POST | `/api/orders` | Place an order. 201 if accepted, 422 with the reason if rejected |
| DELETE | `/api/orders/{id}?symbol=&traderId=` | Cancel an open order |
| GET | `/api/market/{symbol}/depth` | Aggregated order book |
| GET | `/api/market/{symbol}/trades` | Last 100 trades |
| GET | `/api/market/{symbol}/stream` | Live depth and trades (Server-Sent Events) |
| GET | `/api/traders/{id}/balances` | Balance, held and available per asset |
| GET | `/api/traders/{id}/orders` | Recent orders |
| GET | `/api/ledger/check` | Full ledger audit |
| GET | `/api/ledger/reconciliations` | Last 20 reconciliation runs |
| POST | `/api/ledger/reconciliations` | Run a reconciliation now |
| GET | `/api/risk/limits` | Current pre-trade limits |

There's no login here on purpose, to keep the focus on the exchange. Authentication with OAuth2 and JWTs is in my [expense-claims-api](https://github.com/nagarjuna-thumma/expense-claims-api) project.

## Tests

```bash
cd backend && mvn verify     # 38 tests, needs Docker for Testcontainers
cd frontend && npm test      # 8 tests
```

- `OrderBookTest` covers the matching rules one by one.
- `OrderBookRandomizedTest` throws 5,000 random orders and cancels at the book with several seeds, and after every step checks that the book is never crossed, no quantity is created or lost, and no fill breaks a limit price.
- `ExchangeIntegrationTest` runs against a real Postgres. It checks settlement and holds, rejection of unaffordable orders, cancels, that the database refuses unbalanced or edited postings, and that a rebuilt book matches the live one exactly. One test fires 600 orders from 12 threads across three stocks and then runs the full ledger audit.
- `PreTradeRiskTest` covers each risk rule with a fixed clock, including the sliding window. `RiskIntegrationTest` checks that a blocked order never touches the book or holds any cash, and that a script sending too fast gets a 422.
- `ReconciliationTest` edits a balance behind the ledger's back and checks the reconciliation catches it.
- Front-end tests cover the order ticket's validation and the order book layout.

## Stack

Java 21 · Spring Boot 3.5 · Spring Data JPA · Micrometer · PostgreSQL 16 · Flyway · Testcontainers · React 19 · TypeScript · Vite · Vitest · GitHub Actions

## What I'd do next

- Journal incoming orders to Kafka before matching, so the engine can be replayed and a hot standby can follow it
- Add stop and IOC/FOK order types
- Move holds and the ledger check into a separate risk service
- Add authentication
- Add post-trade surveillance, such as flagging traders who cancel almost every order they place
