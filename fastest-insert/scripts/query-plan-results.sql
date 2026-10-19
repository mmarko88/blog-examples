SELECT method_name, AVG(duration_ms) AS avg_ms, MIN(duration_ms) AS min_ms, MAX(duration_ms) AS max_ms
FROM test_result
WHERE method_name LIKE 'plan_%'
   OR method_name LIKE 'latency_%'
   OR method_name LIKE 'insertSelect_%'
   OR method_name LIKE 'bulkCopy_%'
GROUP BY method_name
ORDER BY method_name;
