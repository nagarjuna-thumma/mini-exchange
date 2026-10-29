-- Test-only symbols, so each test trades in its own order book and tests can't interfere.
INSERT INTO instruments (symbol, name, currency) VALUES
    ('T1', 'Test One', 'CAD'), ('T2', 'Test Two', 'CAD'), ('T3', 'Test Three', 'CAD'),
    ('T4', 'Test Four', 'CAD'), ('T5', 'Test Five', 'CAD'), ('T6', 'Test Six', 'CAD'),
    ('C1', 'Crowd One', 'CAD'), ('C2', 'Crowd Two', 'CAD'), ('C3', 'Crowd Three', 'CAD'),
    ('R1', 'Risk One', 'CAD'), ('R2', 'Risk Two', 'CAD');

INSERT INTO accounts (id, owner_id, asset, kind)
SELECT gen_random_uuid(), NULL, symbol, 'ISSUER' FROM instruments WHERE symbol LIKE 'T%' OR symbol LIKE 'C%' OR symbol LIKE 'R%';
