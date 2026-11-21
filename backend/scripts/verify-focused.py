#!/usr/bin/env python3
"""Verify a stable source snapshot without sharing Maven's mutable target directory."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from xml.etree import ElementTree


def fingerprint(backend):
    digest = hashlib.sha256()
    paths = [backend / 'pom.xml', *sorted((backend / 'src').rglob('*'))]
    for path in paths:
        if path.is_file():
            digest.update(str(path.relative_to(backend)).encode())
            digest.update(path.read_bytes())
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tests', help='Surefire test selector')
    parser.add_argument('label', help='Evidence file prefix')
    parser.add_argument('--main', action='store_true', help='Use the main-auth migration track')
    parser.add_argument('--verify', action='store_true', help='Run Maven verify, including application packaging')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', args.label):
        parser.error('Invalid evidence label')
    backend = Path(__file__).resolve().parents[1]
    evidence = backend / 'target/verification'
    evidence.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    if not re.fullmatch(r'jdbc:postgresql://[^/]+/[^?]*_test(?:\?.*)?', env.get('TEST_DB_URL', '')):
        parser.error('TEST_DB_URL must identify a dedicated database ending in _test')
    if any(key not in env for key in ['TEST_DB_USERNAME', 'TEST_DB_PASSWORD']):
        parser.error('Set TEST_DB_USERNAME and TEST_DB_PASSWORD explicitly')
    if not shutil.which('java', path=env.get('PATH')):
        candidates = sorted((Path.home() / '.local/opt').glob('jdk-*/bin/java'))
        if not candidates:
            parser.error('Java 21 is required')
        env['JAVA_HOME'] = str(candidates[-1].parents[1])
        env['PATH'] = str(candidates[-1].parent) + os.pathsep + env.get('PATH', '')
    maven = env.get('MVN_BIN') or shutil.which('mvn')
    if not maven:
        candidates = sorted((Path.home() / '.m2/wrapper/dists').glob('apache-maven-*/*/bin/mvn'), key=lambda path: tuple(map(int,re.findall(r'\d+',path.parents[2].name))))
        if not candidates:
            parser.error('Maven is required')
        maven = str(candidates[-1])
    if args.main:
        env['FLYWAY_LOCATIONS'] = 'classpath:db/main-auth'
    else:
        env.pop('FLYWAY_LOCATIONS', None)
    env.pop('SPRING_PROFILES_INCLUDE', None)
    env['MAVEN_OPTS'] = env.get('MAVEN_OPTS', '') + ' -Xmx384m -XX:ActiveProcessorCount=2'
    env.setdefault('SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE', '0')
    env.setdefault('SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE', '4')
    snapshot = Path(tempfile.mkdtemp(prefix=args.label + '-', dir=evidence))
    # Retry capture if any source changed while copying. No .env, target or secrets are copied.
    for attempt in range(3):
        before = fingerprint(backend)
        shutil.copy2(backend / 'pom.xml', snapshot / 'pom.xml')
        shutil.copytree(backend / 'src', snapshot / 'src', dirs_exist_ok=True)
        if before == fingerprint(backend) == fingerprint(snapshot):
            break
        shutil.rmtree(snapshot / 'src')
    else:
        raise SystemExit('Source changed repeatedly while snapshotting; retry after the current edit batch')
    command = [maven, '--offline', '--batch-mode', '--no-transfer-progress', '-Djacoco.skip=true',
               '-Dspring.test.context.cache.maxSize=8',
               '-Dtest=' + args.tests, 'verify' if args.verify else 'test']
    agents = sorted((Path.home() / '.m2/repository/org/mockito/mockito-core').glob('*/mockito-core-*.jar'), key=lambda path: tuple(map(int,re.findall(r'\d+',path.parent.name))))
    if agents:
        command.insert(4, '-DargLine=-Xmx512m -XX:ActiveProcessorCount=2 -javaagent:' + str(agents[-1]))
    log = evidence / (args.label + '.log')
    # Two independent snapshots may build concurrently; bound memory and DB connections.
    slot = None
    while slot is None:
        for index in range(2):
            candidate = (evidence / ('snapshot-build-' + str(index) + '.lock')).open('a')
            try:
                fcntl.flock(candidate, fcntl.LOCK_EX | fcntl.LOCK_NB)
                slot = candidate
                break
            except BlockingIOError:
                candidate.close()
        if slot is None:
            time.sleep(0.2)
    try:
        with log.open('w') as output:
            result = subprocess.run(command, cwd=snapshot, env=env, stdout=output, stderr=subprocess.STDOUT)
    finally:
        slot.close()
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    for report in (snapshot / 'target/surefire-reports').glob('TEST-*.xml'):
        root = ElementTree.parse(report).getroot()
        for key in counts:
            counts[key] += int(root.get(key, '0'))
    metadata = dict(source_sha256=before, snapshot=str(snapshot), selector=args.tests,
                    phase='verify' if args.verify else 'test',
                    track='main-auth' if args.main else 'migration', exit=result.returncode, **counts)
    (evidence / (args.label + '.json')).write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps(metadata))
    for line in log.read_text().splitlines():
        if 'Tests run:' in line or 'BUILD SUCCESS' in line or 'BUILD FAILURE' in line:
            print(line[:240])
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
