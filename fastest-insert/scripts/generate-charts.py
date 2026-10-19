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
        ('Parallel Bulk API 4t', 330),
        ('Multi-value 400 + 8t', 368),
        ('Multi-value 400 + 4t', 426),
        ('Parallel Bulk API 8t', 434),
        ('Bulk API', 492),
        ('Parallel JdbcTemplate 16t', 608),
        ('Parallel JdbcTemplate 4t + bulkCopy', 731),
        ('TVP (batch=5000)', 818),
        ('BCP utility', 843),
        ('Multi-value 200 (single)', 828),
        ('StatelessSession + bulkCopy', 1075),
        ('JdbcTemplate + bulkCopy', 1373),
        ('Plain JDBC + bulkCopy', 1516),
        ('Hibernate + bulkCopy', 2144),
        ('Clustered + TABLOCK', 2185),
        ('JDBC PreparedStatement', 2423),
        ('StatelessSession', 2650),
        ('JdbcTemplate', 2985),
        ('SWITCH partition pattern', 3470),
        ('Persistable saveAll', 3426),
        ('Plain JDBC', 3905),
        ('Hibernate persist', 4048),
        ('Partitioned direct insert', 4652),
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
        b = next((r['avg'] for r in data if r['method'] == f'bulkApi_{ix}idx' and r['batch'] == 1000), None)
        j = next((r['avg'] for r in data if r['method'] == f'jdbcTemplate_{ix}idx' and r['batch'] == 1000), None)
        h = next((r['avg'] for r in data if r['method'] == f'hibernate_{ix}idx' and r['batch'] == 1000), None)
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
        ('Narrow (5 cols)', 3699, '#4CAF50'),
        ('Wide (17 cols)', 6952, '#FF9800'),
        ('LOB (NVARCHAR MAX)\n50k rows', 9834, '#F44336'),
        ('No compression', 2417, '#2196F3'),
        ('ROW compression', 2595, '#00BCD4'),
        ('PAGE compression', 3229, '#9C27B0'),
        ('Columnstore 1k', 8402, '#795548'),
        ('Columnstore 5k', 4880, '#795548'),
    ]
    svg_bar_chart('Row Size, Compression & Columnstore (JDBC batch)',
                  [d[0] for d in row_data], [d[1] for d in row_data],
                  [d[2] for d in row_data],
                  filename=os.path.join(out_dir, '06_row_compression.svg'))

    # ---- Chart 7: Fill factor + partitioning ----
    print('Chart 7: Fill Factor & Partitioning')
    ff_data = [
        ('Fill factor 50 (Bulk)', 1762, '#F44336'),
        ('Fill factor 70 (Bulk)', 1199, '#FF9800'),
        ('Fill factor 90 (Bulk)', 869, '#4CAF50'),
        ('Fill factor 100 (Bulk)', 916, '#2196F3'),
        ('Partitioned direct insert', 4652, '#9C27B0'),
        ('SWITCH partition pattern', 3470, '#00BCD4'),
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
        ('RCSI ON (Bulk API)', 720, '#4CAF50'),
        ('RCSI OFF (Bulk API)', 1211, '#2196F3'),
        ('Async producer-consumer', 2676, '#00BCD4'),
        ('RCSI OFF (JDBC)', 2817, '#9C27B0'),
        ('Plain INSERT', 3177, '#607D8B'),
        ('No trigger baseline', 3261, '#795548'),
        ('MERGE upsert', 3514, '#FF9800'),
        ('RCSI ON (JDBC)', 3565, '#9C27B0'),
        ('Sync baseline', 3816, '#F44336'),
        ('AFTER INSERT trigger', 7023, '#E91E63'),
    ]
    svg_bar_chart('Advanced Patterns (100k persons)',
                  [d[0] for d in ap_data], [d[1] for d in ap_data],
                  [d[2] for d in ap_data],
                  filename=os.path.join(out_dir, '09_advanced_patterns.svg'))

    # ---- Chart 10: Storage Patterns ----
    print('Chart 10: Storage Patterns')
    sp_data = [
        ('Temporal baseline', 3009, '#4CAF50'),
        ('INT clustered (sequential)', 3015, '#2196F3'),
        ('Change Tracking baseline', 3206, '#9C27B0'),
        ('GUID sequential (NEWSEQ)', 3340, '#00BCD4'),
        ('GUID random', 3588, '#FF9800'),
        ('Temporal (SYSTEM_VERSIONED)', 3936, '#9C27B0'),
        ('Change Tracking enabled', 5205, '#F44336'),
    ]
    svg_bar_chart('Storage Patterns: CT, Temporal, GUID Keys (100k persons)',
                  [d[0] for d in sp_data], [d[1] for d in sp_data],
                  [d[2] for d in sp_data],
                  filename=os.path.join(out_dir, '10_storage_patterns.svg'))

    # ---- Chart 11: Index Varieties ----
    print('Chart 11: Index Varieties & Logging')
    iv_data = [
        ('TF610 ON + TABLOCK', 2336, '#1565C0'),
        ('TF610 OFF + TABLOCK', 2386, '#2196F3'),
        ('No index baseline', 2782, '#4CAF50'),
        ('Computed cols (1 persisted + 1 non)', 2707, '#00BCD4'),
        ('Delayed Durability DISABLED', 3147, '#607D8B'),
        ('Filtered index (17% match)', 3461, '#8BC34A'),
        ('INCLUDE index', 3824, '#CDDC39'),
        ('Composite 3-col index', 4071, '#FFC107'),
        ('Delayed Durability FORCED', 4698, '#FF5722'),
        ('Full non-clustered index', 4956, '#F44336'),
    ]
    svg_bar_chart('Indexes & Logging Patterns (100k persons)',
                  [d[0] for d in iv_data], [d[1] for d in iv_data],
                  [d[2] for d in iv_data],
                  filename=os.path.join(out_dir, '11_index_varieties.svg'))

    # ---- Chart 12: Column Types & Stats ----
    print('Chart 12: Column Types & Stats')
    ct_data = [
        ('Baseline (stats ON)', 2966, '#4CAF50'),
        ('Nonsparse mostly NULL', 3912, '#2196F3'),
        ('Baseline (encrypted test)', 3948, '#607D8B'),
        ('Sparse mostly NULL', 4005, '#00BCD4'),
        ('AUTO_UPDATE_STATS OFF', 4474, '#FF9800'),
        ('JSON column (NVARCHAR+ISJSON)', 4752, '#9C27B0'),
        ('Nonsparse mostly filled', 5053, '#2196F3'),
        ('Sparse mostly filled', 5772, '#00BCD4'),
        ('XML column', 10916, '#F44336'),
        ('ENCRYPTBYPASSPHRASE (3 cols)', 15952, '#E91E63'),
    ]
    svg_bar_chart('Column Types, Stats & Encryption (100k persons)',
                  [d[0] for d in ct_data], [d[1] for d in ct_data],
                  [d[2] for d in ct_data],
                  filename=os.path.join(out_dir, '12_column_types.svg'))

    # ---- Chart 13: In-Memory, BULK INSERT, procs ----
    print('Chart 13: In-Memory & BULK')
    im_data = [
        ('BULK INSERT (server-side file)', 637, '#4CAF50'),
        ('In-Memory interpreted', 2726, '#2196F3'),
        ('Ad-hoc INSERT (baseline)', 2734, '#607D8B'),
        ('Check constraints disabled', 2909, '#00BCD4'),
        ('Check constraints enabled', 2954, '#00BCD4'),
        ('Stored procedure (interpreted)', 4807, '#FF9800'),
        ('In-Memory natively compiled proc (no batch)', 58542, '#F44336'),
    ]
    svg_bar_chart('In-Memory OLTP, BULK INSERT & Procs (100k persons)',
                  [d[0] for d in im_data], [d[1] for d in im_data],
                  [d[2] for d in im_data], width=900,
                  filename=os.path.join(out_dir, '13_inmem_bulk.svg'))

    print(f'\nAll charts generated in {out_dir}/')


if __name__ == '__main__':
    main()
