import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const apiSourcePath = new URL('../src/services/apiSource.ts', import.meta.url);
const sourceCode = await readFile(apiSourcePath, 'utf8');
const { outputText } = ts.transpileModule(sourceCode, {
  compilerOptions: { module: ts.ModuleKind.ESNext },
});
const { resolveDemoFlags } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('resolveDemoFlags handles auth-demo with mock tokens and backend overrides', () => {
  assert.equal(typeof resolveDemoFlags, 'function');

  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'auth-demo', token: 'mock-student' }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'auth-demo', token: 'mock-teacher' }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'auth-demo', token: null }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });

  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'auth-demo', token: 'backend-access' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'backend', mode: 'auth-demo', token: 'mock-student' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'backend', mode: 'auth-demo', token: null }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
});

test('resolveDemoFlags handles development offline mock and online backend transitions', () => {
  assert.deepEqual(resolveDemoFlags({ source: 'mock', mode: 'development', token: null }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'mock', mode: 'development', token: 'mock-student' }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'development', token: 'mock-teacher' }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'development', token: 'real-jwt' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'development', token: null }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'backend', mode: 'development', token: 'real-jwt' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
});

test('resolveDemoFlags keeps lessons-demo on HTTP APIs and backend-mock on real/mocked HTTP', () => {
  assert.deepEqual(resolveDemoFlags({ source: 'mock', mode: 'lessons-demo', token: 'mock-teacher' }), {
    isDemoMode: true,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'lessons-demo', token: 'mock-student' }), {
    isDemoMode: true,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'lessons-demo', token: null }), {
    isDemoMode: true,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'backend', mode: 'lessons-demo', token: 'backend-access' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });

  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'backend-mock', token: 'lesson-test-token' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'unknown', mode: 'backend-mock', token: null }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'backend', mode: 'backend-mock', token: 'fixture-teacher' }), {
    isDemoMode: false,
    isPrototypeDemo: false,
  });
  assert.deepEqual(resolveDemoFlags({ source: 'mock', mode: 'backend-mock', token: 'mock-student' }), {
    isDemoMode: true,
    isPrototypeDemo: true,
  });
});
