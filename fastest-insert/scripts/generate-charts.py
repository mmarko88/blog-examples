#!/usr/bin/env python3
"""Generate SVG benchmark charts from CSV data. No external dependencies."""
import csv
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(SCRIPT_DIR, '..', 'benchmark-summary.csv')
BLOG_DIR = os.environ.get('BLOG_DIR', '')


def read_csv():
    rows = []
    with open(CSV_PATH, newline='', encoding='utf-8-sig') as f:
        for r in csv.DictReader(f):
            rows.append({
                'method': r['method_name'],
                'batch': int(r['batch_size']),
                'people': int(r['total_no_of_people']),
                'opi': int(r['no_of_people_per_insert']),
                'runs': int(r['runs']),
                'avg': float(r['avg_ms']),
                'min': float(r['min_ms']),
                'max': float(r['max_ms']),
            })
    return rows


def svg_bar_chart(title, labels, values, colors=None, width=800, bar_h=28, filename='chart.svg'):
    """Generate a horizontal bar chart as SVG."""
    margin_left = 280
    margin_right = 80
    margin_top = 50
    margin_bottom = 20
    chart_w = width - margin_left - margin_right
    n = len(labels)
    height = margin_top + n * (bar_h + 6) + margin_bottom
    max_val = max(values) if values else 1
    default_colors = ['#2196F3', '#4CAF50', '#FF9800', '#F44336', '#9C27B0', '#00BCD4', '#795548']

    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'font-family="Segoe UI, Arial, sans-serif">',
        f'<rect width="{width}" height="{height}" fill="white"/>',
        f'<text x="{width//2}" y="30" text-anchor="middle" font-size="16" font-weight="bold">{title}</text>',
    ]
    for i, (label, val) in enumerate(zip(labels, values)):
        y = margin_top + i * (bar_h + 6)
        bar_w = max(1, int(val / max_val * chart_w))
        c = (colors[i] if colors else default_colors[i % len(default_colors)])
        lines.append(f'<text x="{margin_left - 8}" y="{y + bar_h // 2 + 5}" text-anchor="end" font-size="12">{label}</text>')
        lines.append(f'<rect x="{margin_left}" y="{y}" width="{bar_w}" height="{bar_h}" fill="{c}" rx="3"/>')
        lines.append(f'<text x="{margin_left + bar_w + 5}" y="{y + bar_h // 2 + 5}" font-size="12" fill="#333">{int(val)} ms</text>')
    lines.append('</svg>')
    svg = '\n'.join(lines)
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(svg)
    print(f'  -> {filename}')
    return svg


def svg_line_chart(title, x_labels, series, width=700, height=400, filename='chart.svg'):
    """Generate a multi-series line chart as SVG. series = [(name, values, color), ...]"""
    margin = {'left': 80, 'right': 160, 'top': 50, 'bottom': 60}
    chart_w = width - margin['left'] - margin['right']
    chart_h = height - margin['top'] - margin['bottom']
    all_vals = [v for _, vals, _ in series for v in vals if v is not None]
    max_val = max(all_vals) if all_vals else 1
    n = len(x_labels)

    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'font-family="Segoe UI, Arial, sans-serif">',
        f'<rect width="{width}" height="{height}" fill="white"/>',
        f'<text x="{width//2}" y="30" text-anchor="middle" font-size="16" font-weight="bold">{title}</text>',
    ]
    # axes
    ox, oy = margin['left'], margin['top']
    lines.append(f'<line x1="{ox}" y1="{oy}" x2="{ox}" y2="{oy+chart_h}" stroke="#ccc"/>')
    lines.append(f'<line x1="{ox}" y1="{oy+chart_h}" x2="{ox+chart_w}" y2="{oy+chart_h}" stroke="#ccc"/>')
    # x labels
    for i, xl in enumerate(x_labels):
        x = ox + int(i / max(1, n - 1) * chart_w) if n > 1 else ox + chart_w // 2
        lines.append(f'<text x="{x}" y="{oy+chart_h+20}" text-anchor="middle" font-size="11">{xl}</text>')
    # grid lines
    for g in range(5):
        gy = oy + int(g / 4 * chart_h)
        gv = int(max_val * (1 - g / 4))
        lines.append(f'<line x1="{ox}" y1="{gy}" x2="{ox+chart_w}" y2="{gy}" stroke="#eee"/>')
        lines.append(f'<text x="{ox-8}" y="{gy+4}" text-anchor="end" font-size="10" fill="#999">{gv}</text>')
    # series
    for si, (name, vals, color) in enumerate(series):
        points = []
        for i, v in enumerate(vals):
            if v is None:
                continue
            x = ox + int(i / max(1, n - 1) * chart_w) if n > 1 else ox + chart_w // 2
            y = oy + chart_h - int(v / max_val * chart_h)
            points.append((x, y))
        if len(points) > 1:
            pts = ' '.join(f'{x},{y}' for x, y in points)
            lines.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        for x, y in points:
            lines.append(f'<circle cx="{x}" cy="{y}" r="4" fill="{color}"/>')
        # legend
        lx = ox + chart_w + 15
        ly = oy + 20 + si * 20
        lines.append(f'<rect x="{lx}" y="{ly-8}" width="12" height="12" fill="{color}"/>')
        lines.append(f'<text x="{lx+16}" y="{ly+3}" font-size="11">{name}</text>')
    lines.append('</svg>')
    svg = '\n'.join(lines)
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(svg)
    print(f'  -> {filename}')


def main():
    data = read_csv()
    out_dir = os.path.join(SCRIPT_DIR, '..', 'charts')
    os.makedirs(out_dir, exist_ok=True)

    # ---- Chart 1: Strategy Ranking (all strategies, best config) ----
    print('Chart 1: Strategy Ranking')
    ranking = [
        ('INSERT ... SELECT', 275),
        ('INSERT ... SELECT ORDER BY', 273),
        ('Parallel Bulk API 4t', 321),
        ('Bulk API (batch=5000)', 412),
        ('Multi-value 400 + 8t', 418),
        ('BulkCopy batch=5000', 420),
        ('Parallel Bulk API 8t', 426),
        ('Multi-value 400 + 4t', 490),
        ('Parallel Bulk 4t + bulkCopy', 493),
        ('BulkCopy +TABLOCK', 564),
        ('Parallel JdbcTemplate 16t', 612),
        ('TVP (batch=5000)', 626),
        ('Multi-value 400 (single)', 662),
        ('Multi-value 200 (single)', 666),
        ('StatelessSession + bulkCopy', 781),
        ('JdbcTemplate + bulkCopy', 824),
        ('Plain JDBC + bulkCopy', 901),
        ('Hibernate + bulkCopy', 1052),
        ('BULK INSERT (file)', 1469),
        ('Clustered + TABLOCK', 1862),
        ('Plain JDBC', 2018),
        ('JdbcTemplate', 2031),
        ('StatelessSession', 2046),
        ('Hibernate persist', 2313),
        ('Persistable saveAll', 2398),
    ]
    ranking.sort(key=lambda x: x[1])
    labels = [r[0] for r in ranking]
    vals = [r[1] for r in ranking]
    color_map = {
        'Bulk': '#2196F3', 'Parallel Bulk': '#1565C0', 'Multi-value': '#4CAF50',
        'TVP': '#FF9800', 'JdbcTemplate': '#9C27B0', 'Parallel Jdbc': '#7B1FA2',
        'StatelessSession': '#00BCD4', 'Hibernate': '#F44336', 'Persistable': '#E91E63',
        'Plain JDBC': '#795548', 'JDBC Prepared': '#607D8B', 'BCP': '#FFC107',
        'Clustered': '#3F51B5', 'saveAll': '#FF5722',
    }
    colors = []
    for l in labels:
        c = '#607D8B'
        for k, v in color_map.items():
            if k in l:
                c = v
                break
        colors.append(c)
    svg_bar_chart('All Strategies Ranked — 100k Persons (avg ms)', labels, vals, colors,
                  filename=os.path.join(out_dir, '01_strategy_ranking.svg'))

    # ---- Chart 2: Multi-value sweep ----
    print('Chart 2: Multi-value Sweep')
    mv_data = [(r['method'].replace('mvSweep_', ''), r['avg'])
               for r in data if r['method'].startswith('mvSweep_')]
    mv_data.sort(key=lambda x: int(x[0]))
    svg_line_chart('Multi-Value objectsPerInsert Sweep (JdbcTemplate, batch=1000)',
                   [d[0] for d in mv_data],
                   [('JdbcTemplate', [d[1] for d in mv_data], '#9C27B0')],
                   filename=os.path.join(out_dir, '02_multivalue_sweep.svg'))

    # ---- Chart 3: Thread count scaling ----
    print('Chart 3: Thread Count Scaling')
    threads = ['1', '2', '4', '8', '16']
    bulk_threads = []
    jdbc_threads = []
    for t in threads:
        b = next((r['avg'] for r in data if r['method'] == f'parallelBulkApi_{t}t' and r['batch'] == 1000), None)
        j = next((r['avg'] for r in data if r['method'] == f'parallelJdbcTemplate_{t}t' and r['batch'] == 1000), None)
        bulk_threads.append(b)
        jdbc_threads.append(j)
    svg_line_chart('Thread Count vs Insert Time (100k, batch=1000)',
                   threads,
                   [('Parallel Bulk API', bulk_threads, '#2196F3'),
                    ('Parallel JdbcTemplate', jdbc_threads, '#9C27B0')],
                   filename=os.path.join(out_dir, '03_thread_scaling.svg'))

    # ---- Chart 4: Index impact ----
    print('Chart 4: Index Impact')
    idx_labels = ['0', '1', '3', '5']
    idx_bulk = []
    idx_jdbc = []
    idx_hib = []
    for ix in idx_labels:
        b = next((r['avg'] for r in data if r['method'] == f'bulkApi_idx{ix}' and r['batch'] == 1000), None)
        j = next((r['avg'] for r in data if r['method'] == f'jdbcTemplate_idx{ix}' and r['batch'] == 1000), None)
        h = next((r['avg'] for r in data if r['method'] == f'hibernate_idx{ix}' and r['batch'] == 1000), None)
        idx_bulk.append(b)
        idx_jdbc.append(j)
        idx_hib.append(h)
    svg_line_chart('Index Impact on Insert Performance (100k, batch=1000)',
                   [f'{x} indexes' for x in idx_labels],
                   [('Bulk API', idx_bulk, '#2196F3'),
                    ('JdbcTemplate', idx_jdbc, '#9C27B0'),
                    ('Hibernate', idx_hib, '#F44336')],
                   filename=os.path.join(out_dir, '04_index_impact.svg'))

    # ---- Chart 5: Batch size sweep ----
    print('Chart 5: Batch Size Sweep')
    bs_data_bulk = [(r['batch'], r['avg']) for r in data if r['method'].startswith('bulkApi_bs')]
    bs_data_jdbc = [(r['batch'], r['avg']) for r in data if r['method'].startswith('jdbcTemplate_bs')]
    bs_data_bulk.sort()
    bs_data_jdbc.sort()
    all_batches = sorted(set(b for b, _ in bs_data_bulk + bs_data_jdbc))
    bulk_by_batch = {b: v for b, v in bs_data_bulk}
    jdbc_by_batch = {b: v for b, v in bs_data_jdbc}
    svg_line_chart('Batch Size Sweep (100k persons)',
                   [str(b) for b in all_batches],
                   [('Bulk API', [bulk_by_batch.get(b) for b in all_batches], '#2196F3'),
                    ('JdbcTemplate', [jdbc_by_batch.get(b) for b in all_batches], '#9C27B0')],
                   width=800,
                   filename=os.path.join(out_dir, '05_batch_sweep.svg'))

    # ---- Chart 6: Row size + compression ----
    print('Chart 6: Row Size & Compression')
    row_data = [
        ('Narrow (5 cols)', 2030, '#4CAF50'),
        ('Wide (17 cols)', 3321, '#FF9800'),
        ('LOB (NVARCHAR MAX)\n50k rows', 6280, '#F44336'),
        ('No compression', 2033, '#2196F3'),
        ('ROW compression', 2298, '#00BCD4'),
        ('PAGE compression', 2546, '#9C27B0'),
        ('Columnstore 1k', 4293, '#795548'),
        ('Columnstore 5k', 4181, '#795548'),
    ]
    svg_bar_chart('Row Size, Compression & Columnstore (JDBC batch)',
                  [d[0] for d in row_data], [d[1] for d in row_data],
                  [d[2] for d in row_data],
                  filename=os.path.join(out_dir, '06_row_compression.svg'))

    # ---- Chart 7: Fill factor + partitioning ----
    print('Chart 7: Fill Factor & Partitioning')
    ff_data = [
        ('Fill factor 50 (Bulk)', 698, '#F44336'),
        ('Fill factor 70 (Bulk)', 567, '#FF9800'),
        ('Fill factor 90 (Bulk)', 572, '#4CAF50'),
        ('Fill factor 100 (Bulk)', 556, '#2196F3'),
        ('Partitioned direct insert', 2134, '#9C27B0'),
        ('SWITCH partition pattern', 2209, '#00BCD4'),
    ]
    svg_bar_chart('Fill Factor & Partitioning (100k persons)',
                  [d[0] for d in ff_data], [d[1] for d in ff_data],
                  [d[2] for d in ff_data],
                  filename=os.path.join(out_dir, '07_fillfactor_partition.svg'))

    # ---- Chart 8: Transaction + GC ----
    print('Chart 8: Transaction Patterns & GC')
    tx_data = [
        ('Commit per batch', 3543, '#4CAF50'),
        ('One large txn', 3773, '#2196F3'),
        ('Savepoints per batch', 3949, '#FF9800'),
        ('Serializable isolation', 2963, '#9C27B0'),
        ('Read committed', 3037, '#9C27B0'),
        ('Read uncommitted', 3189, '#9C27B0'),
        ('G1 512m (JDBC)', 3194, '#00BCD4'),
        ('G1 4g (JDBC)', 3095, '#00BCD4'),
        ('Parallel 2g (JDBC)', 3401, '#00BCD4'),
        ('ZGC 4g (JDBC)', 3291, '#00BCD4'),
    ]
    svg_bar_chart('Transaction & GC Tuning (100k persons)',
                  [d[0] for d in tx_data], [d[1] for d in tx_data],
                  [d[2] for d in tx_data],
                  filename=os.path.join(out_dir, '08_transaction_gc.svg'))

    # ---- Chart 9: Advanced Patterns ----
    print('Chart 9: Advanced Patterns')
    ap_data = [
        ('RCSI ON (Bulk API)', 613, '#4CAF50'),
        ('RCSI OFF (Bulk API)', 617, '#2196F3'),
        ('Async producer-consumer', 2002, '#00BCD4'),
        ('Plain INSERT', 2009, '#607D8B'),
        ('RCSI OFF (JDBC)', 2024, '#9C27B0'),
        ('No trigger baseline', 2088, '#795548'),
        ('Sync baseline', 2101, '#F44336'),
        ('RCSI ON (JDBC)', 2141, '#9C27B0'),
        ('MERGE upsert', 2527, '#FF9800'),
        ('AFTER INSERT trigger', 5175, '#E91E63'),
    ]
    svg_bar_chart('Advanced Patterns (100k persons)',
                  [d[0] for d in ap_data], [d[1] for d in ap_data],
                  [d[2] for d in ap_data],
                  filename=os.path.join(out_dir, '09_advanced_patterns.svg'))

    # ---- Chart 10: Storage Patterns ----
    print('Chart 10: Storage Patterns')
    sp_data = [
        ('INT clustered (sequential)', 2027, '#2196F3'),
        ('Change Tracking baseline', 2036, '#9C27B0'),
        ('Temporal baseline', 2165, '#4CAF50'),
        ('GUID sequential (NEWSEQ)', 2250, '#00BCD4'),
        ('GUID random', 2630, '#FF9800'),
        ('Temporal (SYSTEM_VERSIONED)', 3022, '#9C27B0'),
        ('Change Tracking enabled', 3109, '#F44336'),
    ]
    svg_bar_chart('Storage Patterns: CT, Temporal, GUID Keys (100k persons)',
                  [d[0] for d in sp_data], [d[1] for d in sp_data],
                  [d[2] for d in sp_data],
                  filename=os.path.join(out_dir, '10_storage_patterns.svg'))

    # ---- Chart 11: Index Varieties ----
    print('Chart 11: Index Varieties & Logging')
    iv_data = [
        ('TF610 ON + TABLOCK', 1824, '#1565C0'),
        ('TF610 OFF + TABLOCK', 1843, '#2196F3'),
        ('No index baseline', 2007, '#4CAF50'),
        ('Delayed Durability DISABLED', 2010, '#607D8B'),
        ('Computed cols (1 persisted + 1 non)', 2073, '#00BCD4'),
        ('Filtered index (17% match)', 2774, '#8BC34A'),
        ('Full non-clustered index', 2885, '#F44336'),
        ('INCLUDE index', 2965, '#CDDC39'),
        ('Composite 3-col index', 2966, '#FFC107'),
        ('Delayed Durability FORCED', 2996, '#FF5722'),
    ]
    svg_bar_chart('Indexes & Logging Patterns (100k persons)',
                  [d[0] for d in iv_data], [d[1] for d in iv_data],
                  [d[2] for d in iv_data],
                  filename=os.path.join(out_dir, '11_index_varieties.svg'))

    # ---- Chart 12: Column Types & Stats ----
    print('Chart 12: Column Types & Stats')
    ct_data = [
        ('Baseline (stats ON)', 2038, '#4CAF50'),
        ('Baseline (encrypted test)', 2054, '#607D8B'),
        ('AUTO_UPDATE_STATS OFF', 2053, '#FF9800'),
        ('Nonsparse mostly NULL', 2090, '#2196F3'),
        ('Sparse mostly NULL', 2234, '#00BCD4'),
        ('Nonsparse mostly filled', 3518, '#2196F3'),
        ('JSON column (NVARCHAR+ISJSON)', 3878, '#9C27B0'),
        ('Sparse mostly filled', 3912, '#00BCD4'),
        ('XML column', 6013, '#F44336'),
        ('ENCRYPTBYPASSPHRASE (3 cols)', 12076, '#E91E63'),
    ]
    svg_bar_chart('Column Types, Stats & Encryption (100k persons)',
                  [d[0] for d in ct_data], [d[1] for d in ct_data],
                  [d[2] for d in ct_data],
                  filename=os.path.join(out_dir, '12_column_types.svg'))

    # ---- Chart 13: In-Memory, BULK INSERT, procs ----
    print('Chart 13: In-Memory & BULK')
    im_data = [
        ('BULK INSERT (server-side file)', 1469, '#4CAF50'),
        ('In-Memory interpreted', 1837, '#2196F3'),
        ('Check constraints disabled', 2007, '#00BCD4'),
        ('Ad-hoc INSERT (baseline)', 2038, '#607D8B'),
        ('Check constraints enabled', 2277, '#00BCD4'),
        ('Stored procedure (interpreted)', 3957, '#FF9800'),
        ('In-Memory natively compiled proc (no batch)', 667872, '#F44336'),
    ]
    svg_bar_chart('In-Memory OLTP, BULK INSERT & Procs (100k persons)',
                  [d[0] for d in im_data], [d[1] for d in im_data],
                  [d[2] for d in im_data], width=900,
                  filename=os.path.join(out_dir, '13_inmem_bulk.svg'))

    # ---- Chart 14: Identity / FK / Retry ----
    print('Chart 14: Identity, FK, Retry')
    id_data = [
        ('App-generated ID', 2029, '#2196F3'),
        ('No FK constraint', 2065, '#4CAF50'),
        ('FK disabled (NOCHECK)', 2186, '#FFC107'),
        ('IDENTITY column', 2528, '#9C27B0'),
        ('SET IDENTITY_INSERT ON (explicit)', 2598, '#607D8B'),
        ('FK trusted (CHECK)', 2600, '#FF9800'),
        ('IDENTITY + RETURN_GENERATED_KEYS', 2851, '#F44336'),
        ('SEQUENCE default', 3544, '#00BCD4'),
    ]
    svg_bar_chart('ID Generation & Foreign Keys (100k persons)',
                  [d[0] for d in id_data], [d[1] for d in id_data],
                  [d[2] for d in id_data], width=900,
                  filename=os.path.join(out_dir, '14_identity_fk.svg'))

    # ---- Chart 15: Final patterns ----
    print('Chart 15: Plans, Latency, SELECT')
    fp_data = [
        ('INSERT ... SELECT ORDER BY', 273, '#8BC34A'),
        ('INSERT ... SELECT (server-side)', 275, '#4CAF50'),
        ('INSERT…SELECT (tiny grant, SPILL)', 320, '#FFC107'),
        ('BulkCopy batch=5000', 420, '#2196F3'),
        ('BulkCopy +TABLOCK', 564, '#9C27B0'),
        ('BulkCopy +checkConstraints', 570, '#00BCD4'),
        ('BulkCopy default (batch=1000)', 619, '#607D8B'),
        ('Latency 0ms baseline', 2030, '#FFC107'),
        ('Plan cached (default)', 2059, '#795548'),
        ('Latency +1ms/batch', 2148, '#FF9800'),
        ('Latency +5ms/batch', 2536, '#FF5722'),
        ('OPTION(RECOMPILE) !!', 93031, '#F44336'),
    ]
    svg_bar_chart('Query Plans, Latency & INSERT…SELECT (100k persons)',
                  [d[0] for d in fp_data], [d[1] for d in fp_data],
                  [d[2] for d in fp_data], width=950,
                  filename=os.path.join(out_dir, '15_plans_latency.svg'))

    # ---- Chart 16: SNAPSHOT + SWITCH with indexes ----
    print('Chart 16: SNAPSHOT & SWITCH with indexes')
    sw_data = [
        ('SNAPSHOT single writer', 2115, '#2196F3'),
        ('RC + 4 readers (readers blocked)', 2127, '#8BC34A'),
        ('SWITCH, 0 indexes', 2163, '#4CAF50'),
        ('SNAPSHOT + 4 readers (560 reads)', 2313, '#F44336'),
        ('SWITCH, 1 index', 3106, '#FFC107'),
        ('SNAPSHOT + 2 readers (310 reads)', 4309, '#9C27B0'),
        ('SWITCH, 3 indexes', 4567, '#FF5722'),
    ]
    svg_bar_chart('SNAPSHOT Isolation & Partition SWITCH with Indexes',
                  [d[0] for d in sw_data], [d[1] for d in sw_data],
                  [d[2] for d in sw_data], width=900,
                  filename=os.path.join(out_dir, '16_snapshot_switch.svg'))

    # ---- Chart 17: XML/JSON indexes & tempdb spill ----
    print('Chart 17: XML/JSON indexes + spill')
    xj_data = [
        ('INSERT…SELECT (normal grant)', 273, '#4CAF50'),
        ('INSERT…SELECT (tiny grant, SPILL)', 320, '#FFC107'),
        ('JSON no index', 3357, '#2196F3'),
        ('JSON + computed-col index', 4952, '#9C27B0'),
        ('XML no index', 5893, '#FF9800'),
        ('XML + primary XML index', 25946, '#F44336'),
    ]
    svg_bar_chart('XML/JSON Indexes & tempdb Spill (100k persons)',
                  [d[0] for d in xj_data], [d[1] for d in xj_data],
                  [d[2] for d in xj_data], width=900,
                  filename=os.path.join(out_dir, '17_xmljson_spill.svg'))

    print(f'\nAll charts generated in {out_dir}/')


if __name__ == '__main__':
    main()
