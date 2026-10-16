// BROWSING LOAD TEST: many customers browsing the shop at the same time.
//
// Each simulated customer (virtual user) opens a product list, a product page and the
// category list, then "reads" for 0.5-1.5 s, again and again. Users ramp up to the peak,
// stay there for 2 minutes, then ramp down.
//
//   k6 run loadtest/k6/browse.js                      (peak 500 users)
//   k6 run -e USERS=2000 loadtest/k6/browse.js        (peak 2,000 users)
//
// Pass: p95 under 500 ms and under 1% failed requests.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE, ms } from './lib.js';

const USERS = Number(__ENV.USERS || 500);

export const options = {
  stages: [
    { duration: '30s', target: USERS },
    { duration: '2m', target: USERS },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const ids = http.get(`${BASE}/api/products?size=48`).json('content').map((p) => p.id);
  if (ids.length === 0) {
    throw new Error('No products found. Is the shop running and seeded?');
  }
  return { ids };
}

export default function (data) {
  const page = Math.floor(Math.random() * 2);
  const id = data.ids[Math.floor(Math.random() * data.ids.length)];

  const list = http.get(`${BASE}/api/products?page=${page}&size=12`, { tags: { name: 'product list' } });
  const product = http.get(`${BASE}/api/products/${id}`, { tags: { name: 'product page' } });
  const categories = http.get(`${BASE}/api/categories`, { tags: { name: 'categories' } });

  check(list, { 'product list 200': (r) => r.status === 200 });
  check(product, { 'product page 200': (r) => r.status === 200 });
  check(categories, { 'categories 200': (r) => r.status === 200 });

  sleep(0.5 + Math.random());
}

export function handleSummary(data) {
  const d = data.metrics.http_req_duration.values;
  const report = [
    '# Browse load test',
    '',
    `- Peak concurrent users: ${USERS}`,
    `- Requests: ${data.metrics.http_reqs.values.count} (${data.metrics.http_reqs.values.rate.toFixed(0)} per second on average)`,
    `- Latency: median ${ms(d.med)}, p95 ${ms(d['p(95)'])}, p99 ${ms(d['p(99)'])}, max ${ms(d.max)}`,
    `- Failed requests: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`,
    '',
  ].join('\n');
  return {
    stdout: `\n${report}\n`,
    [`loadtest/results/browse-${USERS}-users.md`]: report,
    [`loadtest/results/browse-${USERS}-users.json`]: JSON.stringify(data, null, 2),
  };
}
