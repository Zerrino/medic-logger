#!/usr/bin/env python3
"""Independently parse the synthetic exports produced by the native iOS tests."""
from pathlib import Path
import csv,io,json,zipfile,xml.etree.ElementTree as XML

root=Path(__file__).resolve().parent.parent/'artifacts/review/ios'
data=json.loads((root/'ios-review.json').read_text())
assert data['schema_version']==3 and len(data['events'])==3 and len(data['sessions'])==2
assert data['events'][-1]['type']=='dose_taken'
assert data['sessions'][0]['effectiveness_rating']==7
assert data['sessions'][1]['effectiveness_rating'] is None
assert data['sessions'][0]['taken_at'].startswith('2026-10-31')
rows=list(csv.reader(io.StringIO((root/'ios-review.csv').read_text(encoding='utf-8-sig'))))
assert len(rows)==4 and len(rows[0])==16
assert rows[1][3].startswith("'=Review") and rows[1][-1]=='7'
with zipfile.ZipFile(root/'ios-review.zip') as archive:
    assert archive.testzip() is None
    assert set(archive.namelist())=={'medic-logger.json','medic-logger.csv','medic-logger.xlsx'}
    assert archive.read('medic-logger.xlsx')==(root/'ios-review.xlsx').read_bytes()
with zipfile.ZipFile(root/'ios-review.xlsx') as archive:
    assert archive.testzip() is None
    for name in archive.namelist(): XML.fromstring(archive.read(name))
    ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    doses=XML.fromstring(archive.read('xl/worksheets/sheet2.xml'))
    assert doses.find(".//s:c[@r='R2']/s:v",ns).text=='7'
    assert not doses.findall('.//s:f',ns)
    assert doses.find(".//s:c[@r='C2']/s:is/s:t",ns).text.startswith('=Review')
print('PASS: JSON schema/event boundaries/rating/nulls, Unicode CSV/formula defense, ZIP CRC/contents, every XLSX XML part and numeric rating.')
