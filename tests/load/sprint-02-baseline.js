import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { vu } from 'k6/execution';

const loginLatency = new Trend('login_latency', true);
const productListLatency = new Trend('product_list_latency', true);
const errorRate = new Rate('errors');

const MAX_VUS = 20;
// Access token TTL is 15 minutes; refresh 1 minute early to avoid expiry mid-test.
const TOKEN_TTL_MS = 14 * 60 * 1000;

export const options = {
    stages: [
        { duration: '30s', target: MAX_VUS },
        { duration: '2m', target: MAX_VUS },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        'login_latency': ['p(99)<500'],
        'product_list_latency': ['p(99)<200'],
        'errors': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
    const password = 'secret123';
    const users = [];
    for (let i = 1; i <= MAX_VUS; i++) {
        const email = `loadtest-vu${i}-${randomString(6)}@k6.local`;
        const res = http.post(
            `${BASE_URL}/v1/auth/register`,
            JSON.stringify({ email, password, fullName: `Load VU ${i}` }),
            { headers: { 'Content-Type': 'application/json' } },
        );
        check(res, { 'setup register 201': (r) => r.status === 201 });
        users.push({ email, password });
    }
    return users;
}

// Per-VU token cache — module-level vars persist across iterations within one VU.
let cachedToken = null;
let tokenExpiresAt = 0;

export default function (data) {
    const user = data[vu.idInTest - 1];
    const vuIp = `10.0.0.${vu.idInTest}`;
    const now = Date.now();

    // Login only on first iteration or when token is about to expire.
    if (!cachedToken || now >= tokenExpiresAt) {
        const loginRes = http.post(
            `${BASE_URL}/v1/auth/login`,
            JSON.stringify({ email: user.email, password: user.password }),
            { headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': vuIp } },
        );
        const loginOk = check(loginRes, { 'login 200': (r) => r.status === 200 });
        errorRate.add(!loginOk);
        if (!loginOk) { sleep(1); return; }

        loginLatency.add(loginRes.timings.duration);
        cachedToken = loginRes.json('accessToken');
        tokenExpiresAt = now + TOKEN_TTL_MS;
    }

    const listRes = http.get(`${BASE_URL}/v1/products?page=0&size=20`, {
        headers: { Authorization: `Bearer ${cachedToken}` },
    });
    productListLatency.add(listRes.timings.duration);
    errorRate.add(listRes.status !== 200);

    sleep(1);
}
