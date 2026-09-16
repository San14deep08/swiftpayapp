-- Seeds 1,000 accounts with large balances specifically for the load test.
-- Money circulates between these accounts (sender debited, receiver
-- credited) so the total supply never runs out, regardless of how many
-- transactions run — only individual balances shift around.
--
-- Run this against the running Postgres BEFORE starting the load test:
--   docker compose exec -T postgres psql -U swiftpay -d swiftpay -f - < load-test/seed-load-test-accounts.sql
-- (or paste its contents into any Postgres client connected to the DB)

INSERT INTO accounts (user_id, balance, currency)
SELECT 'load-user-' || i, 1000000.0000, 'USD'
FROM generate_series(1, 1000) AS i
ON CONFLICT (user_id) DO UPDATE SET balance = EXCLUDED.balance;
