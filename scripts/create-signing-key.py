#!/usr/bin/env python3
"""Create a private, persistent signing key without printing its password. Never overwrites a key."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
folder = root / '.signing'
key = folder / 'medic-logger.jks'
props = folder / 'signing.properties'
if key.exists() or props.exists():
    raise SystemExit('Signing material already exists; keeping it unchanged.')
folder.mkdir(mode=0o700, exist_ok=True)
keytool = str(Path(os.environ['JAVA_HOME']) / 'bin/keytool') if os.environ.get('JAVA_HOME') else shutil.which('keytool')
password = secrets.token_urlsafe(32)
env = os.environ.copy()
env['MEDICLOGGER_SIGN_PASSWORD'] = password
subprocess.run([
    keytool, '-genkeypair', '-keystore', str(key), '-storetype', 'JKS',
    '-alias', 'medic-logger', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
    '-dname', 'CN=Medic Logger, OU=Local Android App, O=Medic Logger',
    '-storepass:env', 'MEDICLOGGER_SIGN_PASSWORD', '-keypass:env', 'MEDICLOGGER_SIGN_PASSWORD',
], env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
props.write_text(f'storePassword={password}\nkeyPassword={password}\n', encoding='utf-8')
key.chmod(0o600)
props.chmod(0o600)
print('Created .signing/medic-logger.jks and private signing.properties. Keep both for future app updates.')
