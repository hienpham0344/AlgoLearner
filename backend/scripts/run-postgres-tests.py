#!/usr/bin/env python3
"""Run tests using only TEST_DB_* settings; never import the application's DB credentials."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

backend = Path(__file__).resolve().parents[1]
environment = os.environ.copy()
settings = backend / '.env.test'
if settings.exists():
    for line in settings.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        match = re.fullmatch(r'\s*(TEST_DB_URL|TEST_DB_USERNAME|TEST_DB_PASSWORD)\s*=(.*)', line)
        if not match:
            raise SystemExit('Only literal TEST_DB_* entries are allowed in .env.test; contents hidden.')
        key, value = match.groups()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        environment.setdefault(key, value)
for key in ['TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD']:
    if not environment.get(key):
        raise SystemExit('Missing ' + key)
if not re.fullmatch(r'jdbc:postgresql://[^/]+/[^?]*_test(?:\?.*)?', environment['TEST_DB_URL']):
    raise SystemExit('TEST_DB_URL must name a dedicated database ending in _test.')
if not shutil.which('java', path=environment.get('PATH')):
    candidates = sorted((Path.home() / '.local/opt').glob('jdk-*/bin/java'))
    if candidates:
        environment['JAVA_HOME'] = str(candidates[-1].parents[1])
        environment['PATH'] = str(candidates[-1].parent) + os.pathsep + environment.get('PATH', '')
maven = environment.get('MVN_BIN') or shutil.which('mvn')
if not maven:
    candidates = sorted((Path.home() / '.m2/wrapper/dists').glob('apache-maven-*/*/bin/mvn'))
    if candidates:
        maven = str(candidates[-1])
if not maven:
    raise SystemExit('Maven 3.9+ is required; set MVN_BIN.')
raise SystemExit(subprocess.call([maven, '--batch-mode', '--no-transfer-progress', *(sys.argv[1:] or ['verify'])], cwd=backend, env=environment))
