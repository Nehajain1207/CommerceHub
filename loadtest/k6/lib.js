// Shared helpers for the k6 load tests.
import http from 'k6/http';
import { sleep } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';
export const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function auth(token, name) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, tags: { name } };
}

export function adminToken() {
  const res = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ email: 'admin@commercehub.local', password: 'admin123' }), { headers: JSON_HEADERS });
  if (res.status !== 200) {
    throw new Error(`Admin login failed (${res.status}). Is the shop running at ${BASE}?`);
  }
  return res.json('token');
}

/** Creates a product just for this test run and returns its id and SKU. */
export function createProduct(admin, label, stock) {
  const categoryId = http.get(`${BASE}/api/categories`).json()[0].id;
  const run = Date.now().toString(36).toUpperCase();
  const sku = `${label}-${run}`;
  const name = `${label} test item ${run}`;
  const res = http.post(`${BASE}/api/admin/products`, JSON.stringify({
    sku, name, description: 'Created by a k6 load test',
    price: 499, stock, categoryId, active: true,
  }), auth(admin, 'setup'));
  if (res.status !== 201 && res.status !== 200) {
    throw new Error(`Could not create the test product (${res.status}): ${res.body}`);
  }
  return { id: res.json('id'), sku, name };
}

/** Registers `count` customers, 50 at a time, and returns their login tokens. */
export function registerCustomers(count, prefix) {
  const tokens = [];
  for (let start = 0; start < count; start += 50) {
    const batch = [];
    for (let i = start; i < Math.min(start + 50, count); i++) {
      batch.push(['POST', `${BASE}/api/auth/register`, JSON.stringify({
        name: `Shopper ${i}`, email: `${prefix}-${i}@loadtest.local`, password: 'LoadTest#123',
      }), { headers: JSON_HEADERS, tags: { name: 'setup' } }]);
    }
    for (const res of http.batch(batch)) {
      if (res.status !== 201) {
        throw new Error(`Registration failed (${res.status}): ${res.body}`);
      }
      tokens.push(res.json('token'));
    }
  }
  return tokens;
}

/** Puts `quantity` of a product in each customer's cart, 50 at a time. */
export function fillCarts(tokens, productId, quantity) {
  for (let start = 0; start < tokens.length; start += 50) {
    const batch = tokens.slice(start, start + 50).map((token) => ['POST', `${BASE}/api/cart/items`,
      JSON.stringify({ productId, quantity }), auth(token, 'setup')]);
    for (const res of http.batch(batch)) {
      if (res.status !== 200) {
        throw new Error(`Adding to cart failed (${res.status}): ${res.body}`);
      }
    }
  }
}

/** Current stock straight from the database (admin endpoint, never cached). */
export function stockOf(admin, product) {
  // Tried a few times: right after a big test the shop may still be busy for a moment
  for (let attempt = 1; attempt <= 5; attempt++) {
    const res = http.get(`${BASE}/api/admin/products?q=${encodeURIComponent(product.name)}`, auth(admin, 'setup'));
    if (res.status === 200 && res.json('content') && res.json('content').length > 0) {
      return res.json('content')[0].stock;
    }
    console.warn(`Reading the stock failed (${res.status}), trying again in 3 s...`);
    sleep(3);
  }
  return 'n/a';
}

export function ms(value) {
  return value === undefined ? 'n/a' : `${Math.round(value)} ms`;
}
