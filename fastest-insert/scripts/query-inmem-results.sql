SELECT method_name, AVG(duration_ms) AS avg_ms, MIN(duration_ms) AS min_ms, MAX(duration_ms) AS max_ms
FROM test_result
WHERE method_name LIKE 'memopt_%'
   OR method_name LIKE 'storedProc%'
   OR method_name LIKE 'adhoc_%'
   OR method_name LIKE 'checks_%'
   OR method_name LIKE 'bulkInsert_%'
GROUP BY method_name
ORDER BY method_name;
