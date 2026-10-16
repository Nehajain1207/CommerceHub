# Steady shopping + chaos test

- Concurrent customers: 100 for 3m
- Requests: 17039 (90 per second on average)
- Orders placed: 5645, paid: 5645
- Checkouts that failed (expected while a copy is being killed or payments are failing): 0
- Checkout latency: median 150 ms, p95 2690 ms, p99 4791 ms
- Failed requests overall: 0.00%

Now run loadtest/verify.sql (see loadtest/CHAOS.md) to check nothing was lost or doubled.
