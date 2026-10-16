// FLASH SALE TEST: thousands of customers try to buy a few units at the same instant.
//
// Set-up (not timed): creates a product with STOCK units, registers USERS customers and
// puts 1 unit in each cart. Then every customer presses "checkout" at the same moment.
//
//   k6 run loadtest/k6/flashsale.js                              (1,000 customers, 100 units)
//   k6 run -e USERS=2000 -e STOCK=100 loadtest/k6/flashsale.js
//
// Correct result: exactly STOCK orders, everyone else told "sold out" (409),
// stock ends at exactly 0, and no other errors.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Gauge } from 'k6/metrics';
import { BASE, adminToken, auth, createProduct, fillCarts, ms, registerCustomers, stockOf } from './lib.js';

const USERS = Number(__ENV.USERS || 1000);
const STOCK = Number(__ENV.STOCK || 100);

const placed = new Counter('orders_placed');
const soldOut = new Counter('told_sold_out');
const unexpected = new Counter('unexpected_responses');
const stockLeft = new Gauge('stock_left_after_sale');

export const options = {
  setupTimeout: '15m',
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '3m' },
  },
  thresholds: {
    unexpected_responses: ['count==0'],
    // Listed so the summary has checkout-only latency (the limit itself is generous)
    'http_req_duration{name:checkout}': ['p(95)<10000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const admin = adminToken();
  const product = createProduct(admin, 'FLASH', STOCK);
  console.log(`Registering ${USERS} customers and filling their carts (not timed)...`);
  const tokens = registerCustomers(USERS, `flash-${product.sku.toLowerCase()}`);
  fillCarts(tokens, product.id, 1);
  console.log('Set-up done. Everyone checks out now.');
  return { admin, product, tokens };
}

export default function (data) {
  const res = http.post(`${BASE}/api/checkout`,
    JSON.stringify({ shippingAddress: '12 MG Road, Bengaluru' }), auth(data.tokens[__VU - 1], 'checkout'));

  if (res.status === 201) {
    placed.add(1);
  } else if (res.status === 409) {
    soldOut.add(1);
  } else {
    unexpected.add(1);
    console.warn(`Unexpected ${res.status}: ${res.body}`);
  }
}

export function teardown(data) {
  const left = stockOf(data.admin, data.product);
  stockLeft.add(left);
  check(left, { 'stock never goes below zero': (s) => s >= 0 });
}

export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  const d = data.metrics['http_req_duration{name:checkout}']
    ? data.metrics['http_req_duration{name:checkout}'].values
    : data.metrics.http_req_duration.values;
  const left = data.metrics.stock_left_after_sale ? data.metrics.stock_left_after_sale.values.value : 'n/a';
  const sold = count('orders_placed');
  const expected = Math.min(USERS, STOCK);
  const correct = sold === expected && left === STOCK - expected && count('unexpected_responses') === 0;

  const report = [
    '# Flash sale test',
    '',
    `- Customers checking out at the same moment: ${USERS}`,
    `- Units on sale: ${STOCK}`,
    `- Orders placed: ${sold} (expected ${expected})`,
    `- Told "sold out": ${count('told_sold_out')}`,
    `- Unexpected responses: ${count('unexpected_responses')}`,
    `- Stock left: ${left} (expected ${STOCK - expected})`,
    `- Checkout latency: median ${ms(d.med)}, p95 ${ms(d['p(95)'])}, p99 ${ms(d['p(99)'])}, max ${ms(d.max)}`,
    `- Result: ${correct ? 'PASS - no overselling, no lost orders' : 'FAIL - check the numbers above'}`,
    '',
  ].join('\n');
  return {
    stdout: `\n${report}\n`,
    [`loadtest/results/flashsale-${USERS}-users.md`]: report,
    [`loadtest/results/flashsale-${USERS}-users.json`]: JSON.stringify(data, null, 2),
  };
}
