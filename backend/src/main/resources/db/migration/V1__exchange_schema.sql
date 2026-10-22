CREATE TABLE instruments (
    symbol      VARCHAR(8) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    currency    VARCHAR(3)   NOT NULL
);

CREATE TABLE traders (
    id          UUID PRIMARY KEY,
    username    VARCHAR(50) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL
);

-- One ledger account per trader per asset (CAD cash, or shares of one symbol),
-- plus the exchange's own EXTERNAL and ISSUER accounts on the other side of deposits.
CREATE TABLE accounts (
    id        UUID PRIMARY KEY,
    owner_id  UUID REFERENCES traders (id),
    asset     VARCHAR(8)     NOT NULL,
    kind      VARCHAR(16)    NOT NULL,   -- TRADER, EXTERNAL, ISSUER
    balance   NUMERIC(19, 2) NOT NULL DEFAULT 0,
    held      NUMERIC(19, 2) NOT NULL DEFAULT 0,   -- reserved by open orders
    version   BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT trader_owned CHECK (kind <> 'TRADER' OR owner_id IS NOT NULL),
    -- A trader can never spend money or shares they don't have, or that open orders already reserve.
    CONSTRAINT trader_solvent CHECK (kind <> 'TRADER' OR (held >= 0 AND balance >= held)),
    CONSTRAINT uq_owner_asset UNIQUE (owner_id, asset)
);

CREATE TABLE ledger_transactions (
    id           UUID PRIMARY KEY,
    type         VARCHAR(16)  NOT NULL,   -- DEPOSIT, TRADE
    description  VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

-- Positive amount = into the account, negative = out of it.
CREATE TABLE postings (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID           NOT NULL REFERENCES ledger_transactions (id),
    account_id      UUID           NOT NULL REFERENCES accounts (id),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount <> 0),
    balance_after   NUMERIC(19, 2) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_postings_account ON postings (account_id, id);
CREATE INDEX idx_postings_transaction ON postings (transaction_id);

-- Double entry, checked by the database at commit: within one transaction, the postings
-- for each asset must sum to zero. Cash can't turn into shares out of thin air.
CREATE FUNCTION check_transaction_balanced() RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM postings p JOIN accounts a ON a.id = p.account_id
        WHERE p.transaction_id = NEW.transaction_id
        GROUP BY a.asset
        HAVING SUM(p.amount) <> 0
    ) THEN
        RAISE EXCEPTION 'Ledger transaction % does not balance', NEW.transaction_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER postings_must_balance
    AFTER INSERT ON postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_transaction_balanced();

CREATE FUNCTION reject_posting_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Postings are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER postings_append_only
    BEFORE UPDATE OR DELETE ON postings
    FOR EACH ROW EXECUTE FUNCTION reject_posting_change();

CREATE SEQUENCE order_seq;

CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    seq              BIGINT,                     -- time priority; null for rejected orders
    trader_id        UUID           NOT NULL REFERENCES traders (id),
    symbol           VARCHAR(8)     NOT NULL REFERENCES instruments (symbol),
    side             VARCHAR(4)     NOT NULL,    -- BUY, SELL
    type             VARCHAR(8)     NOT NULL,    -- LIMIT, MARKET
    limit_price      NUMERIC(19, 2),
    quantity         INT            NOT NULL CHECK (quantity > 0),
    filled_quantity  INT            NOT NULL DEFAULT 0,
    filled_notional  NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status           VARCHAR(20)    NOT NULL,
    reject_reason    VARCHAR(200),
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT limit_has_price CHECK (type <> 'LIMIT' OR limit_price > 0),
    CONSTRAINT not_overfilled CHECK (filled_quantity <= quantity)
);

-- Used on startup to rebuild each order book from the open orders, in time order.
CREATE INDEX idx_orders_open ON orders (symbol, seq) WHERE status IN ('NEW', 'PARTIALLY_FILLED');
CREATE INDEX idx_orders_trader ON orders (trader_id, created_at DESC);

CREATE TABLE trades (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(8)     NOT NULL REFERENCES instruments (symbol),
    price           NUMERIC(19, 2) NOT NULL,
    quantity        INT            NOT NULL CHECK (quantity > 0),
    buy_order_id    UUID           NOT NULL REFERENCES orders (id),
    sell_order_id   UUID           NOT NULL REFERENCES orders (id),
    aggressor_side  VARCHAR(4)     NOT NULL,
    transaction_id  UUID           NOT NULL REFERENCES ledger_transactions (id),
    executed_at     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_trades_symbol ON trades (symbol, id DESC);

-- Fictional companies.
INSERT INTO instruments (symbol, name, currency) VALUES
    ('NRTH', 'Northwind Energy', 'CAD'),
    ('BRCH', 'Birch Financial', 'CAD'),
    ('LAKE', 'Lakeshore Rail', 'CAD');

INSERT INTO accounts (id, owner_id, asset, kind) VALUES
    (gen_random_uuid(), NULL, 'CAD', 'EXTERNAL'),
    (gen_random_uuid(), NULL, 'NRTH', 'ISSUER'),
    (gen_random_uuid(), NULL, 'BRCH', 'ISSUER'),
    (gen_random_uuid(), NULL, 'LAKE', 'ISSUER');
