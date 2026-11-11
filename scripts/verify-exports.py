"""Validate generated formats with independent parsers. No third-party packages required."""
import csv
import io
import json
from pathlib import Path
import sys
import zipfile
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
data = json.loads((root / 'example.json').read_text(encoding='utf-8'))
assert len(data['events']) == 2 and len(data['sessions']) == 1
assert data['schema_version'] == 3
assert data['sessions'][0]['effectiveness_rating'] == 8 and data['events'][0]['effectiveness_rating'] == 8
assert data['events'][0]['monitor_effects'] is True
assert data['events'][0]['stock_batch_id'] is None and data['events'][0]['stock_units'] == 0
assert data['events'][0]['type'] == 'effects_started'
assert data['events'][0]['medicine'] == 'Example, "medicine" é 💊'
assert data['sessions'][0]['time_to_effect_seconds'] == 2100
assert data['sessions'][0]['effect_duration_seconds'] == 9000
rows = list(csv.DictReader(io.StringIO((root / 'example.csv').read_text(encoding='utf-8-sig'))))
assert len(rows) == 2
assert rows[0]['medicine'] == data['events'][0]['medicine']
assert rows[0]['notes'] == data['events'][0]['notes']
assert rows[0]['short_name'] == "'=NOT_A_FORMULA()"
assert rows[0]['effectiveness_rating'] == '8'
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
for name in ('example.xlsx', 'empty.xlsx'):
    with zipfile.ZipFile(root / name) as z:
        assert z.testzip() is None
        for path in z.namelist():
            if path.endswith(('.xml', '.rels')):
                ET.fromstring(z.read(path))
        workbook = ET.fromstring(z.read('xl/workbook.xml'))
        assert len(workbook.findall('s:sheets/s:sheet', ns)) == 3
        events = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
        assert len(events.findall('s:sheetData/s:row', ns)) == (3 if name == 'example.xlsx' else 1)
        assert events.find('.//s:f', ns) is None
        if name == 'example.xlsx':
            cell = events.find(".//s:c[@r='E2']", ns)
            assert cell.attrib['t'] == 'inlineStr'
            assert cell.find('s:is/s:t', ns).text == '=NOT_A_FORMULA()'
            doses = ET.fromstring(z.read('xl/worksheets/sheet2.xml'))
            assert doses.find(".//s:c[@r='K2']/s:v", ns).text == '2100'
            assert doses.find(".//s:c[@r='L2']/s:v", ns).text == '9000'
            assert doses.find(".//s:c[@r='R2']/s:v", ns).text == '8'
for ext in ('json', 'csv', 'xlsx'):
    with zipfile.ZipFile(root / f'example-{ext}.zip') as z:
        assert z.testzip() is None and len(z.namelist()) == 1
        assert z.read(z.namelist()[0]) == (root / f'example.{ext}').read_bytes() if ext != 'xlsx' else z.read(z.namelist()[0])[:2] == b'PK'
with zipfile.ZipFile(root / 'example.zip') as z:
    assert z.testzip() is None and len(z.namelist()) == 3
    assert {Path(p).suffix for p in z.namelist()} == {'.json', '.csv', '.xlsx'}
    xlsx = next(p for p in z.namelist() if p.endswith('.xlsx'))
    with zipfile.ZipFile(io.BytesIO(z.read(xlsx))) as workbook:
        assert workbook.testzip() is None
print('PASS: independent JSON, CSV, XLSX, empty workbook and all four ZIP variants')
