SELECT method_name, total_no_of_people, AVG(duration_ms) AS avg_ms, MIN(duration_ms) AS min_ms, MAX(duration_ms) AS max_ms
FROM test_result
WHERE method_name LIKE 'id_%'
   OR method_name LIKE 'fk_%'
   OR method_name LIKE 'retry_%'
GROUP BY method_name, total_no_of_people
ORDER BY method_name;
