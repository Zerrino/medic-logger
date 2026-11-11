#!/usr/bin/env python3
"""Package the reviewable sources, excluding build output and all private signing files."""
from pathlib import Path
import hashlib,zipfile

root=Path(__file__).resolve().parent.parent
destination=root/'artifacts/Medic-Logger-source.zip'
folders=['app','ios','assets','docs','scripts','gradle']
skip={'build','DerivedData','.gradle','.tools','.signing','xcuserdata','__pycache__'}
files=[]
for folder in folders:
    for path in (root/folder).rglob('*'):
        if path.is_file() and not any(part in skip for part in path.relative_to(root).parts) and path.name!='.DS_Store':files.append(path)
for name in ['README.md','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore']:
    path=root/name
    if path.is_file():files.append(path)
with zipfile.ZipFile(destination,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in sorted(set(files)):
        assert path.suffix not in ['.jks','.keystore','.enc','.apk','.aab','.apks','.xcuserstate']
        assert path.name not in ['signing.properties','local.properties']
        archive.write(path,'Medic-Logger-source/'+str(path.relative_to(root)))
    assert archive.testzip() is None
digest=hashlib.sha256(destination.read_bytes()).hexdigest()
destination.with_suffix('.zip.sha256').write_text(digest+'  '+destination.name+'\n')
print(f'Packaged {len(files)} source files; no build output or private signing material.')
