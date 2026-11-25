import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';
const require = createRequire(import.meta.url);
const axios = require('axios');
const React = require('react');
const { act, createElement } = React;
const { createRoot } = require('react-dom/client');
const { QueryClient, QueryClientProvider } = require('@tanstack/react-query');
const { MemoryRouter } = require('react-router-dom');

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

async function loadBundle({ mode = 'production', dev = false } = {}) {
  const compiled = await build({
    stdin: {
      contents: `
        export * from './src/services/apiSource.ts';
        export * from './src/services/apiClient.ts';
        export * from './src/components/BackendStatusBanner.tsx';
        export * from './src/context/AuthContext.tsx';
        export { AdminAccountsPage } from './src/features/admin/pages/AdminAccountsPage.tsx';
        export { SessionManager, DeactivateAccountForm } from './src/features/profile/components/AccountSecurityPanels.tsx';
      `,
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    jsx: 'automatic',
    define: {
      'import.meta.env.MODE': JSON.stringify(mode),
      'import.meta.env.DEV': JSON.stringify(dev),
    },
    external: [
      'axios',
      'react',
      'react-dom',
      'react-dom/client',
      'react/jsx-runtime',
      '@tanstack/react-query',
      'react-router-dom',
    ],
  });
  const mod = { exports: {} };
  new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
    mod,
    mod.exports,
    require,
  );
  return mod.exports;
}

function setupDom() {
  const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {
    url: 'http://localhost/',
  });
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  Object.defineProperty(globalThis, 'navigator', { value: dom.window.navigator, configurable: true });
  globalThis.HTMLElement = dom.window.HTMLElement;
  globalThis.Event = dom.window.Event;
  globalThis.StorageEvent = dom.window.StorageEvent;
  globalThis.Node = dom.window.Node;

  const store = new Map();
  const storage = {
    getItem: key => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => { store.set(String(key), String(value)); },
    removeItem: key => { store.delete(String(key)); },
    clear: () => { store.clear(); },
  };
  globalThis.localStorage = storage;
  Object.defineProperty(dom.window, 'localStorage', { value: storage, configurable: true });
  return { dom, storage, container: dom.window.document.getElementById('root') };
}

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false, gcTime: 0 },
    },
  });
}

async function flush() {
  await act(async () => {
    await new Promise(resolve => setTimeout(resolve, 20));
  });
}

test('CRITICAL-1: BackendStatusBanner observes JSON body source when X-Algolearner-Source header is absent', async () => {
  const { container } = setupDom();
  const origFetch = globalThis.fetch;
  const root = createRoot(container);
  try {
    const mod = await loadBundle({ mode: 'production' });
    assert.equal(mod.getApiSource(), 'unknown');

    globalThis.fetch = async () => new Response(
      JSON.stringify({ success: true, data: { source: 'backend' }, timestamp: '2026-11-19T00:00:00Z' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    );

    await act(async () => {
      root.render(createElement(mod.BackendStatusBanner));
    });
    await flush();

    assert.equal(mod.getApiSource(), 'backend');
    assert.equal(container.textContent, '');
  } finally {
    await act(async () => { root.unmount(); });
    globalThis.fetch = origFetch;
  }
});

test('CRITICAL-1: BackendStatusBanner does not authorize mock on non-ok response without header, and prefers header when present', async () => {
  const { container } = setupDom();
  const origFetch = globalThis.fetch;
  const root = createRoot(container);
  try {
    const mod1 = await loadBundle({ mode: 'production' });
    globalThis.fetch = async () => new Response(
      JSON.stringify({ success: false, data: { source: 'mock' } }),
      { status: 500, headers: { 'Content-Type': 'application/json' } },
    );
    await act(async () => { root.render(createElement(mod1.BackendStatusBanner)); });
    await flush();
    assert.equal(mod1.getApiSource(), 'unknown');

    const mod2 = await loadBundle({ mode: 'development', dev: true });
    globalThis.fetch = async () => new Response(
      JSON.stringify({ success: true, data: { source: 'backend' } }),
      { status: 200, headers: { 'Content-Type': 'application/json', 'X-Algolearner-Source': 'mock' } },
    );
    await act(async () => { root.render(createElement(mod2.BackendStatusBanner)); });
    await flush();
    assert.equal(mod2.getApiSource(), 'mock');
    assert.match(container.textContent, /Backend is not running/);
  } finally {
    await act(async () => { root.unmount(); });
    globalThis.fetch = origFetch;
  }
});

test('HIGH-3: useIsDemoMode and useIsPrototypeDemo react to token events without requiring AuthProvider', async () => {
  const { container, storage } = setupDom();
  const root = createRoot(container);
  try {
    const mod = await loadBundle({ mode: 'auth-demo', dev: true });

    function Probe() {
      const isDemo = mod.useIsDemoMode();
      const isProto = mod.useIsPrototypeDemo();
      return createElement('output', { id: 'probe' }, `${isDemo}:${isProto}`);
    }

    await act(async () => { root.render(createElement(Probe)); });
    assert.equal(container.querySelector('#probe')?.textContent, 'true:true');

    // Switching to a real JWT via SESSION_REFRESHED_EVENT must re-render immediately
    storage.setItem('algolearner_token', 'real-jwt-token');
    await act(async () => {
      window.dispatchEvent(new Event(mod.SESSION_REFRESHED_EVENT));
    });
    assert.equal(container.querySelector('#probe')?.textContent, 'false:false');

    // Switching to a mock token via storage event must re-render immediately
    storage.setItem('algolearner_token', 'mock-student');
    await act(async () => {
      window.dispatchEvent(new StorageEvent('storage', { key: 'algolearner_token', newValue: 'mock-student' }));
    });
    assert.equal(container.querySelector('#probe')?.textContent, 'true:true');

    // Clearing token via UNAUTHORIZED_EVENT must re-render (in development mode, back to false)
    const modDev = await loadBundle({ mode: 'development', dev: true });
    function ProbeDev() {
      return createElement('output', { id: 'probe-dev' }, String(modDev.useIsDemoMode()));
    }
    await act(async () => { root.render(createElement(ProbeDev)); });
    assert.equal(container.querySelector('#probe-dev')?.textContent, 'true');

    storage.removeItem('algolearner_token');
    await act(async () => {
      window.dispatchEvent(new Event(modDev.UNAUTHORIZED_EVENT));
    });
    assert.equal(container.querySelector('#probe-dev')?.textContent, 'false');
  } finally {
    await act(async () => { root.unmount(); });
  }
});

test('MEDIUM-3 & HIGH-3: AuthContext initAuth retains refreshed token and handleAuthSuccess dispatches SESSION_REFRESHED_EVENT', async () => {
  const { container, storage } = setupDom();
  const origAdapter = axios.defaults.adapter;
  const queryClient = makeQueryClient();
  const root = createRoot(container);
  try {
    storage.setItem('algolearner_token', 'expired-access-token');
    const user = { id: 'u1', username: 'alice', email: 'alice@example.test', role: 'STUDENT' };

    axios.defaults.adapter = async config => {
      const ok = data => ({ status: 200, statusText: 'OK', config, headers: {}, data: { success: true, data } });
      if (config.url === '/auth/refresh-token') {
        return ok({ token: 'fresh-refreshed-token', user });
      }
      if (config.url === '/auth/me') {
        if (config.headers.get('Authorization') !== 'Bearer fresh-refreshed-token') {
          throw new axios.AxiosError('Expired', 'ERR_BAD_REQUEST', config, undefined, { status: 401, headers: {} });
        }
        return ok(user);
      }
      if (config.url === '/auth/login') {
        return ok({ token: 'login-jwt-token', user });
      }
      throw new Error(`Unexpected request: ${config.method} ${config.url}`);
    };

    const mod = await loadBundle({ mode: 'auth-demo', dev: true });
    let capturedAuth;

    function Observer() {
      const auth = React.useContext(mod.AuthContext);
      capturedAuth = auth;
      const isDemo = mod.useIsDemoMode();
      return createElement('div', null, `${auth?.token ?? 'none'}|${isDemo}`);
    }

    await act(async () => {
      root.render(
        createElement(
          QueryClientProvider,
          { client: queryClient },
          createElement(mod.AuthProvider, null, createElement(Observer)),
        ),
      );
    });
    await flush();

    // Must retain the refreshed token, NOT overwrite with stale expired-access-token
    assert.equal(storage.getItem('algolearner_token'), 'fresh-refreshed-token');
    assert.equal(capturedAuth.token, 'fresh-refreshed-token');
    assert.equal(container.textContent, 'fresh-refreshed-token|false');

    // Now test login dispatching SESSION_REFRESHED_EVENT
    storage.setItem('algolearner_token', 'mock-student');
    await act(async () => {
      window.dispatchEvent(new Event(mod.SESSION_REFRESHED_EVENT));
    });
    assert.match(container.textContent, /\|true$/);

    let refreshEvents = 0;
    const onRefreshed = () => { refreshEvents += 1; };
    window.addEventListener(mod.SESSION_REFRESHED_EVENT, onRefreshed);
    await act(async () => {
      await capturedAuth.login({ identifier: 'alice@example.test', password: 'Password123!' });
    });
    window.removeEventListener(mod.SESSION_REFRESHED_EVENT, onRefreshed);

    assert.ok(refreshEvents >= 1, 'handleAuthSuccess should dispatch SESSION_REFRESHED_EVENT');
    assert.equal(capturedAuth.token, 'login-jwt-token');
    assert.equal(container.textContent, 'login-jwt-token|false');
  } finally {
    await act(async () => { root.unmount(); });
    queryClient.clear();
    axios.defaults.adapter = origAdapter;
  }
});

test('CRITICAL-1: AdminAccountsPage loads accounts when source is unknown in production, and blocks in mock demo mode', async () => {
  const { container, storage } = setupDom();
  const origAdapter = axios.defaults.adapter;
  const queryClient = makeQueryClient();
  const root = createRoot(container);
  try {
    storage.setItem('algolearner_token', 'real-admin-jwt');
    let listCalls = 0;
    axios.defaults.adapter = async config => {
      if (config.url === '/admin/accounts') {
        listCalls += 1;
        return {
          status: 200, statusText: 'OK', config, headers: {},
          data: {
            success: true,
            data: {
              items: [{ id: 'stu-1', username: 'student1', email: 's1@example.test', role: 'STUDENT', status: 'ACTIVE', createdAt: '2026-11-19T00:00:00Z' }],
              pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true },
            },
          },
        };
      }
      throw new Error(`Unexpected request: ${config.method} ${config.url}`);
    };

    const mod = await loadBundle({ mode: 'production' });
    assert.equal(mod.getApiSource(), 'unknown');
    const adminAuth = {
      user: { id: 'admin-1', username: 'admin', email: 'admin@example.test', role: 'ADMIN' },
      token: 'real-admin-jwt', isAuthenticated: true, isLoading: false,
      login: async () => {}, register: async () => ({ email: '', message: '' }),
      verifyOtp: async () => {}, quickLogin: async () => {}, logout: async () => {},
    };

    await act(async () => {
      root.render(
        createElement(
          QueryClientProvider,
          { client: queryClient },
          createElement(
            MemoryRouter,
            { initialEntries: ['/admin/accounts'] },
            createElement(
              mod.AuthContext.Provider,
              { value: adminAuth },
              createElement(mod.AdminAccountsPage),
            ),
          ),
        ),
      );
    });
    await flush();

    assert.equal(listCalls, 1);
    assert.match(container.textContent, /student1/);
    assert.doesNotMatch(container.textContent, /Backend connection is required/);

    // Switch to mock source: must show backend-required message
    await act(async () => { mod.observeApiSource('mock'); });
    assert.match(container.textContent, /Backend connection is required to manage accounts/);
  } finally {
    await act(async () => { root.unmount(); });
    queryClient.clear();
    axios.defaults.adapter = origAdapter;
  }
});

test('CRITICAL-1: AccountSecurityPanels work when source is unknown in production, and disable in mock demo mode', async () => {
  const { container, storage } = setupDom();
  const origAdapter = axios.defaults.adapter;
  const queryClient = makeQueryClient();
  const root = createRoot(container);
  try {
    storage.setItem('algolearner_token', 'real-user-jwt');
    let sessionCalls = 0;
    axios.defaults.adapter = async config => {
      if (config.url === '/auth/sessions') {
        sessionCalls += 1;
        return {
          status: 200, statusText: 'OK', config, headers: {},
          data: {
            success: true,
            data: [{
              id: 'sess-1', current: true, userAgent: 'Chrome Linux',
              createdAt: '2026-11-19T00:00:00Z', expiresAt: '2026-12-19T00:00:00Z', rememberMe: false,
            }],
          },
        };
      }
      throw new Error(`Unexpected request: ${config.method} ${config.url}`);
    };

    const mod = await loadBundle({ mode: 'production' });
    assert.equal(mod.getApiSource(), 'unknown');

    await act(async () => {
      root.render(
        createElement(
          QueryClientProvider,
          { client: queryClient },
          createElement(
            MemoryRouter,
            null,
            createElement('div', null,
              createElement(mod.SessionManager),
              createElement(mod.DeactivateAccountForm),
            ),
          ),
        ),
      );
    });
    await flush();

    assert.equal(sessionCalls, 1);
    assert.match(container.textContent, /Current device/);
    assert.doesNotMatch(container.textContent, /Backend connection is required to manage sessions/);
    assert.doesNotMatch(container.textContent, /Backend connection is required to deactivate your account/);
    const pwInput = container.querySelector('#deactivate-password');
    assert.equal(pwInput?.disabled, false);

    // Switch to mock source: must disable deactivation and show notices
    await act(async () => { mod.observeApiSource('mock'); });
    assert.match(container.textContent, /Backend connection is required to manage sessions/);
    assert.match(container.textContent, /Backend connection is required to deactivate your account/);
    assert.equal(container.querySelector('#deactivate-password')?.disabled, true);
  } finally {
    await act(async () => { root.unmount(); });
    queryClient.clear();
    axios.defaults.adapter = origAdapter;
  }
});

test('BackendStatusBanner polls at 60s interval, skips hidden tabs, and rechecks on visibility/focus', async () => {
  const { container } = setupDom();
  const origFetch = globalThis.fetch;
  const root = createRoot(container);
  try {
    const mod = await loadBundle({ mode: 'production' });
    assert.equal(mod.STATUS_POLL_INTERVAL_MS, 60_000);

    let fetchCount = 0;
    globalThis.fetch = async () => {
      fetchCount += 1;
      return new Response(
        JSON.stringify({ success: true, data: { source: 'backend' } }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    await act(async () => { root.render(createElement(mod.BackendStatusBanner)); });
    await flush();
    assert.equal(fetchCount, 1);

    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')); });
    await flush();
    assert.equal(fetchCount, 1);

    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    await act(async () => { document.dispatchEvent(new Event('visibilitychange')); });
    await flush();
    assert.equal(fetchCount, 2);

    await act(async () => { window.dispatchEvent(new Event('focus')); });
    await flush();
    assert.equal(fetchCount, 3);
  } finally {
    await act(async () => { root.unmount(); });
    globalThis.fetch = origFetch;
  }
});
