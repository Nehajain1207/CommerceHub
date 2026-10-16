-- Run against the SHOP database after a load or chaos test (see loadtest/CHAOS.md).
-- Pass the test product's id, printed by k6 at the end of the run:
--   Get-Content loadtest\verify.sql | docker exec -i commercehub_postgres psql -U postgres -d commercehub -v product_id=PUT_ID_HERE

\echo ''
\echo '1. STOCK. units_left + units_in_live_orders must equal the starting stock (no unit lost or sold twice)'
SELECT p.name,
       p.stock                                                                       AS units_left,
       COALESCE(SUM(oi.quantity) FILTER (WHERE o.status <> 'CANCELLED'), 0)           AS units_in_live_orders,
       p.stock + COALESCE(SUM(oi.quantity) FILTER (WHERE o.status <> 'CANCELLED'), 0) AS total_accounted_for
FROM products p
LEFT JOIN order_items oi ON oi.product_id = p.id
LEFT JOIN orders o ON o.id = oi.order_id
WHERE p.id = :product_id
GROUP BY p.id, p.name, p.stock;

\echo '2. PAYMENTS. Both numbers must be 0'
SELECT
    (SELECT count(*) FROM (SELECT order_id FROM payments GROUP BY order_id HAVING count(*) > 1) twice)
        AS orders_with_two_payments,
    (SELECT count(*) FROM orders o JOIN payments pay ON pay.order_id = o.id
      WHERE o.status = 'PLACED' AND pay.status <> 'PAID')
        AS confirmed_orders_without_payment;

\echo '3. EVENTS. events_still_waiting must be 0 once Kafka is back. Note events_total for the next check.'
SELECT count(*)                                     AS events_total,
       count(*) FILTER (WHERE published_at IS NULL) AS events_still_waiting
FROM outbox_events;
