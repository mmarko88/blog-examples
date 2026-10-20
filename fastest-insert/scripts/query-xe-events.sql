SET QUOTED_IDENTIFIER ON;
GO
-- Query Extended Events ring buffer for TDS_Benchmark session
SELECT TOP 30
    event_data.value('(event/@name)[1]', 'varchar(50)') AS event_name,
    event_data.value('(event/data[@name="cpu_time"]/value)[1]', 'bigint') AS cpu_time_us,
    event_data.value('(event/data[@name="duration"]/value)[1]', 'bigint') AS duration_us,
    event_data.value('(event/data[@name="row_count"]/value)[1]', 'bigint') AS row_count,
    event_data.value('(event/data[@name="logical_reads"]/value)[1]', 'bigint') AS logical_reads,
    event_data.value('(event/data[@name="writes"]/value)[1]', 'bigint') AS writes,
    LEFT(event_data.value('(event/data[@name="statement"]/value)[1]', 'nvarchar(max)'), 120) AS sql_preview
FROM (
    SELECT CAST(target_data AS xml) AS target_xml
    FROM sys.dm_xe_session_targets st
    JOIN sys.dm_xe_sessions s ON s.address = st.event_session_address
    WHERE s.name = 'TDS_Benchmark'
    AND st.target_name = 'ring_buffer'
) t
CROSS APPLY target_xml.nodes('//RingBufferTarget/event') AS xevent(event_data)
ORDER BY event_data.value('(event/action[@name="event_sequence"]/value)[1]', 'bigint');
GO
