import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';

const require = createRequire(import.meta.url);
const { act, createElement } = require('react');
const { MemoryRouter } = require('react-router-dom');
const { QueryClient, QueryClientProvider } = require('@tanstack/react-query');
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const compiled = await build({
  entryPoints: ['src/features/notification/components/NotificationDropdown.tsx'],
  alias: { '@': './src' }, bundle: true, platform: 'node', format: 'cjs', write: false,
  jsx: 'automatic', define: { 'import.meta.env.DEV': 'false' },
  external: ['react', 'react-dom', 'react/jsx-runtime', 'react-router-dom', '@tanstack/react-query', 'axios'],
  plugins: [{ name: 'demo-session', setup(build) {
    build.onResolve({ filter: /(?:hooks\/useAuth|components\/BackendStatusBanner)$/ }, args => ({ path: args.path, namespace: 'session' }));
    build.onLoad({ filter: /.*/, namespace: 'session' }, () => ({ contents: `export const useAuth = () => ({ user: { id: 'student', role: 'STUDENT' } }); export const useIsDemoMode = () => true;` }));
  } }],
});
const module = { exports: {} };
new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(module, module.exports, require);
const { NotificationDropdown } = module.exports;

test('notification dropdown opens in place and restores trigger focus on Escape', async () => {
  const dom = new JSDOM('<div id="root"></div><button id="outside">Outside</button>', { url: 'http://localhost/' });
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  globalThis.Node = dom.window.Node;
  const { createRoot } = require('react-dom/client');
  const root = createRoot(document.getElementById('root'));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  try {
    await act(async () => root.render(createElement(QueryClientProvider, { client }, createElement(MemoryRouter, {}, createElement(NotificationDropdown)))));
    const trigger = document.querySelector('.notification-dropdown > button');
    assert.equal(trigger.getAttribute('aria-expanded'), 'false');
    await act(async () => trigger.click());
    assert.equal(trigger.getAttribute('aria-expanded'), 'true');
    const panel = document.querySelector('[role="dialog"]');
    assert.equal(document.activeElement, panel);
    assert.equal(panel.querySelector('.notification-dropdown-footer').getAttribute('href'), '/notifications');
    await act(async () => document.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.equal(document.activeElement, trigger);
    await act(async () => trigger.click());
    await act(async () => document.getElementById('outside').dispatchEvent(new window.Event('pointerdown', { bubbles: true })));
    assert.equal(document.querySelector('[role="dialog"]'), null);
  } finally {
    await act(async () => root.unmount());
    client.clear();
    dom.window.close();
  }
});
