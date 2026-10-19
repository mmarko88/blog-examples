SELECT method_name, AVG(duration_ms) AS avg_ms, MIN(duration_ms) AS min_ms, MAX(duration_ms) AS max_ms
FROM test_result
WHERE method_name LIKE 'rcsi_%'
   OR method_name LIKE 'merge_%'
   OR method_name LIKE 'trigger_%'
   OR method_name LIKE 'async_%'
GROUP BY method_name
ORDER BY method_name;
