import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';

const require = createRequire(import.meta.url);
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const result = await build({
  entryPoints: ['src/components/ui/LoadingScreen.tsx'], alias: { '@': './src' },
  bundle: true, platform: 'node', format: 'cjs', write: false, jsx: 'automatic',
  external: ['react', 'react-dom', 'react/jsx-runtime'],
});
const module = { exports: {} };
new Function('module', 'exports', 'require', result.outputFiles[0].text)(module, module.exports, require);
const { LoadingScreen, LoadingValue, Skeleton } = module.exports;
const render = (component, props) => new JSDOM(renderToStaticMarkup(React.createElement(component, props))).window.document;

test('shared loading variants expose their message without fabricating progress', () => {
  for (const variant of ['fullscreen', 'page', 'inline']) {
    const doc = render(LoadingScreen, { variant, message: 'Fetching data…' });
    assert.match(doc.querySelector('[role="status"]').textContent, /Fetching data…/);
    assert.ok(doc.querySelector('.loading-line[aria-hidden="true"]'));
    assert.equal(doc.querySelectorAll('[aria-valuenow], button, input').length, 0);
  }
});

test('content placeholders remain hidden from assistive technology', () => {
  const doc = render(Skeleton, { className: 'w-20' });
  assert.equal(doc.querySelector('.loading-bone').getAttribute('aria-hidden'), 'true');
});

test('pending values preserve a text line without exposing a fake zero', () => {
  const doc = render(LoadingValue, { width: '6ch' });
  assert.equal(doc.querySelector('.loading-value').getAttribute('aria-hidden'), 'true');
  assert.match(doc.querySelector('.loading-value').getAttribute('style'), /6ch/);
});
