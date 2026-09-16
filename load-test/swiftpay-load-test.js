// SwiftPay load test — POST /v1/payments at a sustained rate.
//
// Run a SMOKE TEST first to prove the script and pipeline work before
// committing to the full ~67-minute submission run:
//   k6 run -e RATE=10 -e DURATION=10s -e PRE_VUS=5 -e MAX_VUS=20 load-test/swiftpay-load-test.js
//
// Then the REAL submission run (250 TPS, 1,000,000 total transactions):
//   k6 run load-test/swiftpay-load-test.js
// (defaults below are already set to 250 TPS / 4000s = 1,000,000 iterations)
//
// PREREQUISITE: run seed-load-test-accounts.sql against Postgres first —
// see that file's header comment. Without it, every request 404s on an
// unknown sender_id.

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const NUM_ACCOUNTS = parseInt(__ENV.NUM_ACCOUNTS || '1000', 10);
const RATE = parseInt(__ENV.RATE || '250', 10);
const DURATION = __ENV.DURATION || '4000s'; // 250 TPS * 4000s = 1,000,000 iterations
const PRE_VUS = parseInt(__ENV.PRE_VUS || '50', 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || '300', 10);

export const options = {
    scenarios: {
        sustained_payments: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: PRE_VUS,
            maxVUs: MAX_VUS,
        },
    },
    // Loose thresholds — the point of this run is to OBSERVE where things
    // degrade, not to pass/fail against an assumed SLA we haven't measured
    // yet. Tighten these once a real baseline exists.
    thresholds: {
        http_req_failed: ['rate<0.05'],
    },
};

function randomAccountId() {
    const n = Math.floor(Math.random() * NUM_ACCOUNTS) + 1;
    return 'load-user-' + n;
}

function uniqueTransactionId() {
    // __VU and __ITER are k6 globals identifying the virtual user and
    // iteration number — combined with Date.now() this is unique across
    // the whole run without needing an external UUID library.
    return `loadtest-${__VU}-${__ITER}-${Date.now()}`;
}

export default function () {
    let sender = randomAccountId();
    let receiver = randomAccountId();
    while (receiver === sender) {
        receiver = randomAccountId();
    }

    const payload = JSON.stringify({
        transaction_id: uniqueTransactionId(),
        sender_id: sender,
        receiver_id: receiver,
        amount: (Math.random() * 9 + 1).toFixed(2), // 1.00–10.00
        currency: 'USD',
    });

    const res = http.post(`${BASE_URL}/v1/payments`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, {
        'status is 202 or 422': (r) => r.status === 202 || r.status === 422,
    });
}
