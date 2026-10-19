SELECT method_name, AVG(duration_ms) AS avg_ms, MIN(duration_ms) AS min_ms, MAX(duration_ms) AS max_ms
FROM test_result
WHERE method_name LIKE 'fillFactor_%'
   OR method_name LIKE 'iso_%'
   OR method_name LIKE 'txn_%'
   OR method_name LIKE 'partitioned_%'
   OR method_name LIKE 'switch_%'
   OR method_name LIKE 'gc_%'
GROUP BY method_name
ORDER BY method_name;
