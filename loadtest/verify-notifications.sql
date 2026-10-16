-- Run against the NOTIFICATION SERVICE database after the shop's verify.sql:
--   Get-Content loadtest\verify-notifications.sql | docker exec -i commercehub_notifications_db psql -U postgres -d notifications
--
-- notifications_total must equal events_total from verify.sql: every event arrived exactly once
-- (none lost while Kafka was down, none recorded twice when it was re-sent).

SELECT count(*)                 AS notifications_total,
       count(DISTINCT event_id) AS distinct_events
FROM notifications;
