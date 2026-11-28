import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';

const require = createRequire(import.meta.url);
const { AxiosError } = require('axios');

function setupDom() {
  const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {
    url: 'http://localhost/',
  });
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  Object.defineProperty(globalThis, 'navigator', { value: dom.window.navigator, configurable: true });
  globalThis.HTMLElement = dom.window.HTMLElement;
  globalThis.Event = dom.window.Event;
  globalThis.KeyboardEvent = dom.window.KeyboardEvent;
  globalThis.Node = dom.window.Node;
  if (!dom.window.HTMLElement.prototype.scrollIntoView) {
    dom.window.HTMLElement.prototype.scrollIntoView = () => {};
  }
  const store = new Map();
  const storage = {
    getItem: key => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => { store.set(String(key), String(value)); },
    removeItem: key => { store.delete(String(key)); },
    clear: () => { store.clear(); },
  };
  globalThis.localStorage = storage;
  Object.defineProperty(dom.window, 'localStorage', { value: storage, configurable: true });
  return { dom, container: dom.window.document.getElementById('root') };
}

setupDom();

const React = require('react');
const { act, createElement } = React;
const { createRoot } = require('react-dom/client');
const { renderToStaticMarkup } = require('react-dom/server');
const { QueryClient, QueryClientProvider } = require('@tanstack/react-query');

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

async function loadModule() {
  const compiled = await build({
    stdin: {
      contents: `
        export { apiClient } from './src/services/apiClient.ts';
        export { announcementKeys } from './src/features/classroom/api/announcementKeys.ts';
        export { announcementApi, announcementError, mapAnnouncement } from './src/features/classroom/api/announcementApi.ts';
        export * from './src/features/classroom/hooks/useAnnouncements.ts';
        export { AnnouncementCard } from './src/features/classroom/components/AnnouncementCard.tsx';
        export { AnnouncementFormModal } from './src/features/classroom/components/AnnouncementFormModal.tsx';
        export { ClassroomAnnouncementsList } from './src/features/classroom/components/ClassroomAnnouncementsList.tsx';
      `,
      resolveDir: process.cwd(),
      loader: 'ts',
    },
    alias: { '@': './src' },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    loader: { '.css': 'empty' },
    jsx: 'automatic',
    define: {
      'import.meta.env.MODE': '"test"',
      'import.meta.env.DEV': 'false',
    },
    external: [
      'axios',
      'react',
      'react-dom',
      'react-dom/client',
      'react/jsx-runtime',
      '@tanstack/react-query',
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

const sampleBackendView = (overrides = {}) => ({
  id: 'ann-1',
  classroomId: 'class-1',
  authorId: 'teacher-1',
  authorName: 'Prof. Ada Lovelace',
  title: 'Midterm Schedule',
  content: 'Exam starts at **09:00** in Room `301`.',
  isPinned: false,
  pinnedAt: null,
  createdAt: '2026-11-20T08:00:00Z',
  updatedAt: '2026-11-20T08:00:00Z',
  ...overrides,
});

test('announcementKeys scopes all list and detail keys by classId', async () => {
  setupDom();
  const { announcementKeys } = await loadModule();

  assert.deepEqual(announcementKeys.list('class-a', 0, 20), ['announcements', 'class-a', 'list', { page: 0, size: 20 }]);
  assert.deepEqual(announcementKeys.list('class-b', 1, 20), ['announcements', 'class-b', 'list', { page: 1, size: 20 }]);
  assert.deepEqual(announcementKeys.detail('class-a', 'ann-9'), ['announcements', 'class-a', 'detail', 'ann-9']);
  assert.notDeepEqual(announcementKeys.lists('class-a'), announcementKeys.lists('class-b'));
});

test('announcementApi calls backend endpoints with strict DTO payloads and maps responses without guessing authorRole', async () => {
  setupDom();
  const { apiClient, announcementApi, announcementError } = await loadModule();
  const calls = [];

  apiClient.defaults.adapter = async config => {
    calls.push({
      method: config.method,
      url: config.url,
      params: config.params,
      body: config.data ? JSON.parse(config.data) : null,
    });

    if (config.method === 'get' && config.url === '/classrooms/class-1/announcements') {
      return {
        config,
        status: 200,
        statusText: 'OK',
        headers: {},
        data: {
          success: true,
          data: {
            items: [sampleBackendView({ isPinned: true, pinnedAt: '2026-11-20T08:05:00Z' })],
            pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true },
          },
          timestamp: '2026-11-20T08:05:00Z',
        },
      };
    }

    if (config.method === 'delete') {
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }

    const pinned = config.url?.endsWith('/pin')
      ? true
      : config.url?.endsWith('/unpin')
        ? false
        : false;

    return {
      config,
      status: config.method === 'post' ? 201 : 200,
      statusText: 'OK',
      headers: {},
      data: {
        success: true,
        message: 'Success',
        data: sampleBackendView({
          title: config.data ? JSON.parse(config.data).title : 'Midterm Schedule',
          content: config.data ? JSON.parse(config.data).content : 'Body',
          isPinned: pinned,
          pinnedAt: pinned ? '2026-11-20T09:00:00Z' : null,
        }),
        timestamp: '2026-11-20T09:00:00Z',
      },
    };
  };

  const page = await announcementApi.list('class-1', 0, 20);
  assert.equal(page.items.length, 1);
  assert.equal(page.items[0].isPinned, true);
  assert.equal(page.items[0].authorRole, undefined);
  assert.equal(page.pagination.totalElements, 1);

  const detail = await announcementApi.get('class-1', 'ann-1');
  assert.equal(detail.id, 'ann-1');
  assert.equal('authorRole' in detail, false);

  const created = await announcementApi.create('class-1', {
    title: 'New Notice',
    content: 'Hello students',
    classroomId: 'should-strip',
    isPinned: true,
  });
  assert.equal(created.title, 'New Notice');
  assert.deepEqual(calls[2].body, { title: 'New Notice', content: 'Hello students' });
  assert.equal('classroomId' in calls[2].body, false);
  assert.equal('isPinned' in calls[2].body, false);

  await announcementApi.update('class-1', 'ann-1', {
    title: 'Updated Notice',
    content: 'Updated content',
    isPinned: true,
  });
  assert.deepEqual(calls[3].body, { title: 'Updated Notice', content: 'Updated content' });
  assert.equal('isPinned' in calls[3].body, false);

  const pinned = await announcementApi.pin('class-1', 'ann-1');
  assert.equal(pinned.isPinned, true);

  const unpinned = await announcementApi.unpin('class-1', 'ann-1');
  assert.equal(unpinned.isPinned, false);

  await announcementApi.remove('class-1', 'ann-1');

  assert.deepEqual(
    calls.map(c => `${c.method.toUpperCase()} ${c.url}`),
    [
      'GET /classrooms/class-1/announcements',
      'GET /classrooms/class-1/announcements/ann-1',
      'POST /classrooms/class-1/announcements',
      'PUT /classrooms/class-1/announcements/ann-1',
      'PATCH /classrooms/class-1/announcements/ann-1/pin',
      'PATCH /classrooms/class-1/announcements/ann-1/unpin',
      'DELETE /classrooms/class-1/announcements/ann-1',
    ],
  );

  // Error mapping checks
  const validationErr = new AxiosError('Bad Request', 'ERR_BAD_REQUEST', undefined, undefined, {
    status: 400,
    data: {
      success: false,
      errorCode: 'VALIDATION_FAILED',
      message: 'Invalid request payload',
      errors: [{ field: 'title', message: 'Title is required' }],
    },
  });
  assert.match(announcementError(validationErr), /title: Title is required/);

  const forbiddenErr = new AxiosError('Forbidden', 'ERR_BAD_REQUEST', undefined, undefined, { status: 403, data: {} });
  assert.match(announcementError(forbiddenErr), /permission/i);
});

test('announcementApi rejects malformed PageResponse payloads', async () => {
  setupDom();
  const { apiClient, announcementApi } = await loadModule();

  apiClient.defaults.adapter = async config => ({
    config,
    status: 200,
    statusText: 'OK',
    headers: {},
    data: { success: true, data: { items: 'not-an-array' } },
  });

  await assert.rejects(() => announcementApi.list('class-1', 0, 20), /Unexpected paginated announcement response format/);
});

test('AnnouncementCard escapes unsafe HTML, omits unverified authorRole badge, and gates actions by permission', async () => {
  setupDom();
  const { AnnouncementCard } = await loadModule();

  const xssHtml = renderToStaticMarkup(
    createElement(AnnouncementCard, {
      announcement: sampleBackendView({
        content: 'Read <script>alert(1)</script> and <img src=x onerror=alert(2)> carefully.',
      }),
      isTeacherOrAdmin: false,
    }),
  );

  const frag = JSDOM.fragment(xssHtml);
  assert.equal(frag.querySelector('script'), null);
  assert.equal(frag.querySelector('img'), null);
  assert.equal(frag.querySelector('[onerror]'), null);
  assert.match(frag.textContent, /<script>alert\(1\)<\/script>/);
  assert.equal(frag.textContent.includes('Instructor'), false);
  assert.equal(frag.textContent.includes('Administrator'), false);
  assert.equal(frag.querySelector('button[aria-label="Edit announcement"]'), null);
  assert.equal(frag.querySelector('button[aria-label="Delete announcement"]'), null);
  assert.equal(frag.querySelector('button[aria-label="Pin announcement"]'), null);

  const teacherHtml = renderToStaticMarkup(
    createElement(AnnouncementCard, {
      announcement: sampleBackendView(),
      isTeacherOrAdmin: true,
      onEdit: () => {},
      onDelete: () => {},
      onTogglePin: () => {},
    }),
  );
  const teacherFrag = JSDOM.fragment(teacherHtml);
  assert.ok(teacherFrag.querySelector('button[aria-label="Edit announcement"]'));
  assert.ok(teacherFrag.querySelector('button[aria-label="Delete announcement"]'));
  assert.ok(teacherFrag.querySelector('button[aria-label="Pin announcement"]'));
});

test('AnnouncementFormModal hides pin toggle by default and enforces 255-char title limit', async () => {
  setupDom();
  const { AnnouncementFormModal } = await loadModule();

  const html = renderToStaticMarkup(
    createElement(AnnouncementFormModal, {
      isOpen: true,
      onClose: () => {},
      onSubmit: () => {},
    }),
  );
  const frag = JSDOM.fragment(html);
  assert.equal(frag.querySelector('#announcement-pin'), null);
  assert.equal(frag.querySelector('#announcement-title')?.getAttribute('maxlength'), '255');
});

test('ClassroomAnnouncementsList renders loading, error retry, empty, and paginated filter scope clarity', async () => {
  setupDom();
  const { ClassroomAnnouncementsList } = await loadModule();

  const loadingHtml = renderToStaticMarkup(
    createElement(ClassroomAnnouncementsList, {
      classroomId: 'class-1',
      isTeacherOrAdmin: false,
      announcements: [],
      isLoading: true,
    }),
  );
  assert.match(loadingHtml, /Loading classroom announcements/);

  const errorHtml = renderToStaticMarkup(
    createElement(ClassroomAnnouncementsList, {
      classroomId: 'class-1',
      isTeacherOrAdmin: false,
      announcements: [],
      error: 'Forbidden access',
      onRetry: () => {},
    }),
  );
  const errFrag = JSDOM.fragment(errorHtml);
  assert.ok(errFrag.querySelector('[role="alert"]'));
  assert.match(errFrag.textContent, /Forbidden access/);
  assert.match(errFrag.textContent, /Retry/);

  const paginatedHtml = renderToStaticMarkup(
    createElement(ClassroomAnnouncementsList, {
      classroomId: 'class-1',
      isTeacherOrAdmin: false,
      announcements: [sampleBackendView()],
      pagination: { page: 0, size: 20, totalElements: 25, totalPages: 2, isFirst: true, isLast: false },
      onPageChange: () => {},
    }),
  );
  assert.match(paginatedHtml, /All on page \(1\)/);
  assert.match(paginatedHtml, /Search and filter apply to the current page only/);
  assert.match(paginatedHtml, /Page 1 of 2/);
  assert.match(paginatedHtml, /Previous/);
  assert.match(paginatedHtml, /Next/);
});

test('useCreateAnnouncementMutation & ClassroomAnnouncementsList wait for server confirmation before showing toast and sync cache', async () => {
  const { container } = setupDom();
  const {
    apiClient,
    announcementKeys,
    useAnnouncementsQuery,
    useCreateAnnouncementMutation,
    ClassroomAnnouncementsList,
  } = await loadModule();

  let serverItems = [];
  let resolveCreate;
  apiClient.defaults.adapter = async config => {
    if (config.method === 'get' && config.url === '/classrooms/class-1/announcements') {
      return {
        config,
        status: 200,
        statusText: 'OK',
        headers: {},
        data: {
          success: true,
          data: {
            items: serverItems,
            pagination: {
              page: 0,
              size: 20,
              totalElements: serverItems.length,
              totalPages: serverItems.length ? 1 : 0,
              isFirst: true,
              isLast: true,
            },
          },
          timestamp: '2026-11-20T08:00:00Z',
        },
      };
    }
    if (config.method === 'post' && config.url === '/classrooms/class-1/announcements') {
      const payload = JSON.parse(config.data);
      assert.deepEqual(payload, { title: 'Exam Room', content: 'Room 202' });
      const created = sampleBackendView({ id: 'ann-new', title: payload.title, content: payload.content });
      await new Promise(resolve => {
        resolveCreate = () => {
          serverItems = [created];
          resolve();
        };
      });
      return {
        config,
        status: 201,
        statusText: 'Created',
        headers: {},
        data: { success: true, data: created, timestamp: '2026-11-20T08:01:00Z' },
      };
    }
    throw new Error(`Unexpected call: ${config.method} ${config.url}`);
  };

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  function Harness() {
    const query = useAnnouncementsQuery('class-1', 0, 20, true);
    const createMutation = useCreateAnnouncementMutation('class-1');
    return createElement(ClassroomAnnouncementsList, {
      classroomId: 'class-1',
      isTeacherOrAdmin: true,
      announcements: query.data?.items ?? [],
      isLoading: query.isPending,
      isCreating: createMutation.isPending,
      onCreate: data => createMutation.mutateAsync({ title: data.title, content: data.content }),
    });
  }

  const root = createRoot(container);
  await act(async () => {
    root.render(
      createElement(QueryClientProvider, { client: queryClient }, createElement(Harness)),
    );
    await new Promise(r => setTimeout(r, 20));
  });

  // Open create modal
  const newBtn = [...container.querySelectorAll('button')].find(b => b.textContent?.includes('New Announcement'));
  assert.ok(newBtn);
  await act(async () => {
    newBtn.click();
  });

  const titleInput = container.querySelector('#announcement-title');
  const contentInput = container.querySelector('#announcement-content');
  const form = container.querySelector('form');
  assert.ok(titleInput && contentInput && form);

  const setNativeValue = (el, val) => {
    const proto = el instanceof globalThis.window.HTMLTextAreaElement
      ? globalThis.window.HTMLTextAreaElement.prototype
      : globalThis.window.HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, val);
    el.dispatchEvent(new globalThis.window.Event('input', { bubbles: true }));
  };

  await act(async () => {
    setNativeValue(titleInput, 'Exam Room');
    setNativeValue(contentInput, 'Room 202');
  });

  // Start submit (does not resolve yet)
  let submitPromise;
  await act(async () => {
    submitPromise = form.dispatchEvent(new globalThis.window.Event('submit', { bubbles: true, cancelable: true }));
    await Promise.resolve();
  });

  // While pending: toast must NOT appear yet, and modal must still be open
  assert.equal(container.querySelector('[role="status"]'), null);
  assert.ok(container.querySelector('[role="dialog"]'));

  // Resolve server response
  await act(async () => {
    resolveCreate();
    await submitPromise;
    await new Promise(r => setTimeout(r, 20));
  });

  // After confirmation: toast appears, modal closes, and list shows created announcement
  const toast = container.querySelector('[role="status"]');
  assert.ok(toast);
  assert.match(toast.textContent, /In-app notifications dispatched/);
  assert.equal(container.querySelector('[role="dialog"]'), null);
  assert.match(container.textContent, /Exam Room/);
  assert.ok(queryClient.getQueryData(announcementKeys.detail('class-1', 'ann-new')));

  await act(async () => {
    root.unmount();
  });
  queryClient.clear();
});
