// STEADY SHOPPING + CHAOS TEST: customers keep buying for a few minutes while you break things.
//
// Each customer: add 1 unit to the cart, check out, pay (demo payment), wait 2-3 s, repeat.
// While it runs, follow loadtest/CHAOS.md: stop Kafka, kill one back-end copy, make the
// payment provider fail. Afterwards loadtest/verify.sql proves nothing was lost or doubled.
//
//   k6 run loadtest/k6/checkout-steady.js                        (200 customers, 3 minutes)
//   k6 run -e USERS=300 -e DURATION=5m loadtest/k6/checkout-steady.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE, adminToken, auth, createProduct, ms, registerCustomers, stockOf } from './lib.js';

const USERS = Number(__ENV.USERS || 200);
const DURATION = __ENV.DURATION || '3m';
const START_STOCK = 1000000;

const placed = new Counter('orders_placed');
const paid = new Counter('orders_paid');
const failed = new Counter('checkout_failed');

export const options = {
  setupTimeout: '15m',
  scenarios: {
    shoppers: { executor: 'constant-vus', vus: USERS, duration: DURATION },
  },
  thresholds: {
    'http_req_duration{name:checkout}': ['p(95)<10000'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const admin = adminToken();
  const product = createProduct(admin, 'STEADY', START_STOCK);
  console.log(`Registering ${USERS} customers (not timed)...`);
  const tokens = registerCustomers(USERS, `steady-${product.sku.toLowerCase()}`);
  console.log(`Set-up done. Test product: "${product.name}" (id ${product.id}). Start breaking things now.`);
  return { admin, product, tokens };
}

export default function (data) {
  const token = data.tokens[__VU - 1];

  http.post(`${BASE}/api/cart/items`, JSON.stringify({ productId: data.product.id, quantity: 1 }), auth(token, 'add to cart'));

  const res = http.post(`${BASE}/api/checkout`,
    JSON.stringify({ shippingAddress: '12 MG Road, Bengaluru' }), auth(token, 'checkout'));

  if (res.status === 201) {
    placed.add(1);
    const orderNumber = res.json('order.orderNumber');
    const pay = http.post(`${BASE}/api/orders/${orderNumber}/payment/demo-confirm`, null, auth(token, 'pay'));
    if (check(pay, { 'payment confirmed': (r) => r.status === 200 })) {
      paid.add(1);
    }
  } else {
    failed.add(1);
  }

  sleep(2 + Math.random());
}

export function teardown(data) {
  console.log(`Product id for verify.sql: ${data.product.id}. Stock left now: ${stockOf(data.admin, data.product)}`);
}

export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  const d = data.metrics['http_req_duration{name:checkout}'].values;
  const report = [
    '# Steady shopping + chaos test',
    '',
    `- Concurrent customers: ${USERS} for ${DURATION}`,
    `- Requests: ${data.metrics.http_reqs.values.count} (${data.metrics.http_reqs.values.rate.toFixed(0)} per second on average)`,
    `- Orders placed: ${count('orders_placed')}, paid: ${count('orders_paid')}`,
    `- Checkouts that failed (expected while a copy is being killed or payments are failing): ${count('checkout_failed')}`,
    `- Checkout latency: median ${ms(d.med)}, p95 ${ms(d['p(95)'])}, p99 ${ms(d['p(99)'])}`,
    `- Failed requests overall: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`,
    '',
    'Now run loadtest/verify.sql (see loadtest/CHAOS.md) to check nothing was lost or doubled.',
    '',
  ].join('\n');
  return {
    stdout: `\n${report}\n`,
    'loadtest/results/chaos-steady.md': report,
    'loadtest/results/chaos-steady.json': JSON.stringify(data, null, 2),
  };
}
