# SwiftPay Load Test

Target: **250 TPS sustained for 1,000,000 total transactions**, with a PCAP trace of the run, per
the hackathon spec.

**Honest status: this tooling has been written but not executed.** It needs to run against your
local `docker compose` stack, which I can't reach from this sandboxed environment. Everything
below is ready to run — treat the first smoke test as the real verification step, the same way
every other piece of this repo got verified.

## Why the default seeded accounts won't work for this

`user-1`/`user-2`/`user-3` from `db/init.sql` have balances in the hundreds. At any real
throughput they'd be drained by insufficient-funds rejections within seconds — the test would end
up measuring "how fast can we reject payments," not "how fast can we move money." Step 1 below
seeds 1,000 accounts with $1,000,000 each specifically for this test; money circulates between
them (sender debited, receiver credited) so the supply never runs out no matter how many
transactions run.

## Prerequisites

- `docker compose up -d` already running (gateway, ledger, Postgres, Redis, Kafka)
- [k6](https://k6.io) installed locally (`choco install k6` on Windows, or download from
  k6.io/docs/get-started/installation)
- Docker available for the PCAP sidecar container (already true if compose is running)

## Step 1 — Seed the load-test accounts

```powershell
docker compose exec -T postgres psql -U swiftpay -d swiftpay -f - < load-test/seed-load-test-accounts.sql
```

## Step 2 — Smoke test FIRST (do not skip to the full run)

Prove the script, the seeded accounts, and the pipeline all actually work before committing to a
~67-minute run:

```powershell
k6 run -e RATE=10 -e DURATION=10s -e PRE_VUS=5 -e MAX_VUS=20 load-test/swiftpay-load-test.js
```

Check the summary k6 prints: `http_req_failed` should be near 0%, and status codes should be a mix
of 202 (accepted) with essentially no unexpected errors. If this fails, fix it here — a failure at
10 TPS for 10 seconds is much cheaper to diagnose than one 40 minutes into the real run.

## Step 3 — Start the PCAP capture (separate terminal, runs in the background)

This runs `tcpdump` inside a sidecar container that shares `gateway-service`'s network namespace,
so it captures exactly the traffic in and out of that container — no Windows network-adapter
guessing required:

```powershell
mkdir load-test\output -Force
docker run --rm --net container:swiftpay-gateway-service-1 -v ${PWD}/load-test/output:/capture nicolaka/netshoot tcpdump -i eth0 -w /capture/loadtest.pcap port 8081
```

Leave this running in its own terminal window for the duration of the load test. Stop it with
Ctrl+C once the k6 run below finishes — the `.pcap` file will be in `load-test/output/`.

## Step 4 — The real submission run

```powershell
k6 run load-test/swiftpay-load-test.js
```

Defaults are already set to 250 TPS for 4000 seconds (250 × 4000 = 1,000,000 iterations) — **this
takes about 67 minutes**. Let it run to completion; don't background it or close the terminal.

### Alternative: a shorter representative run

Sustaining 250 TPS for a shorter window demonstrates the same throughput capability as sustaining
it for the full 67 minutes — the difference is total transaction count, not whether the target
rate was hit. If time is limited, a 5-minute run at the same 250 TPS is a legitimate way to prove
the rate is sustainable:

```powershell
k6 run -e DURATION=300s load-test/swiftpay-load-test.js
```

250 TPS × 300s = **75,000 transactions**. **State this explicitly in your submission** — "sustained
250 TPS demonstrated over a 5-minute / 75,000-transaction run; full 1,000,000-transaction duration
not executed due to time constraints" — rather than presenting a shorter run as if it satisfied the
spec's exact 1,000,000-transaction figure. The k6 summary and the resulting PCAP are equally valid
evidence of the achieved rate either way; only the total volume differs.

## Step 5 — Stop the PCAP capture

Ctrl+C in the terminal from Step 3 once the k6 run (Step 4) finishes — whether that's the full
67-minute run or the 5-minute representative run. Confirm `load-test/output/loadtest.pcap` exists
and has a non-trivial size (for the 5-minute/75,000-transaction run, expect a meaningfully sized
file reflecting ~75,000 HTTP request/response pairs — if it's a few KB, something didn't capture
correctly and this needs re-running before you can submit it).

## Interpreting the results — this IS the "identify a bottleneck" deliverable

k6 prints a summary at the end: request rate actually achieved, `http_req_duration` percentiles
(p95/p99), and `http_req_failed` rate. Compare the achieved rate against the target 250 TPS —
if it falls short, that's the headline finding.

**One likely bottleneck worth checking, and stating explicitly if you find it:** `payment.initiated`
is a Kafka topic that auto-creates with a single partition in this dev setup (see
`docker-compose.yml`'s `KAFKA_AUTO_CREATE_TOPICS_ENABLE`). A single partition means exactly one
`ledger-service` consumer thread processes every payment sequentially, no matter how much CPU is
available — that's a real, structural throughput ceiling, not a tuning problem. If the achieved
rate plateaus well below 250 TPS while gateway-service stays healthy, check Kafka consumer lag on
that topic; growing lag points straight at this. The fix (increasing the topic's partition count)
is a real tradeoff against the row-locking design in `LedgerTransactionService`: more partitions
allow more parallel consumers, but the "lock both accounts in a fixed order" logic still only
protects against races within a single JVM's row-locking — multiple consumer instances processing
different partitions concurrently would need the DB-level lock (which they'd still get, since it's
enforced by Postgres, not application code) to actually prevent a genuine cross-partition
double-spend on the same account. Worth stating as a design note in your submission rather than
silently changing it without re-verifying the concurrency test still holds.
