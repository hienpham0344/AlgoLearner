import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';

const require = createRequire(import.meta.url);
const axios = require('axios');
const { AxiosError } = axios;
const React = require('react');
const { act, createElement } = React;
const { createRoot } = require('react-dom/client');
const { QueryClient, QueryClientProvider } = require('@tanstack/react-query');
const { MemoryRouter, Route, Routes } = require('react-router-dom');

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

function setupDom() {
  const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {
    url: 'http://localhost/',
  });
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  Object.defineProperty(globalThis, 'navigator', { value: dom.window.navigator, configurable: true });
  globalThis.HTMLElement = dom.window.HTMLElement;
  globalThis.SVGElement = dom.window.SVGElement;
  globalThis.Event = dom.window.Event;
  globalThis.StorageEvent = dom.window.StorageEvent;
  globalThis.Node = dom.window.Node;
  dom.window.scrollTo = () => {};

  const store = new Map([['algolearner_token', 'real-jwt']]);
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

setupDom();

const compiled = await build({
  stdin: {
    contents: `
      export * from './src/features/classroom/lessons/api/lessonApi.ts';
      export * from './src/features/classroom/lessons/lessonReading.ts';
      export { apiClient } from './src/services/apiClient.ts';
      export { observeApiSource } from './src/services/apiSource.ts';
      export { AuthContext } from './src/context/AuthContext.tsx';
      export { LessonWorkspacePage } from './src/features/classroom/lessons/pages/LessonWorkspacePage.tsx';
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
  loader: { '.css': 'empty', '.svg': 'empty' },
  define: {
    'import.meta.env.MODE': JSON.stringify('production'),
    'import.meta.env.DEV': JSON.stringify(false),
  },
  external: [
    'axios',
    'react',
    'react-dom',
    'react-dom/client',
    'react/jsx-runtime',
    '@tanstack/react-query',
    'react-router-dom',
    'dompurify',
  ],
});

const mod = { exports: {} };
new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(mod, mod.exports, require);
const {
  lessonApi,
  lessonError,
  estimateReadingMinutes,
  apiClient,
  observeApiSource,
  AuthContext,
  LessonWorkspacePage,
} = mod.exports;

const baseInput = {
  title: 'Updated Recursion',
  content: '# Updated Recursion',
  codeExample: null,
  externalLinks: [],
  completionMode: 'MANUAL',
  activityIds: [],
  requiredActivityIds: [],
};

const ok = (config, data, status = 200) => ({
  config,
  status,
  statusText: 'OK',
  headers: {},
  data: { success: true, timestamp: '2026-11-19T00:00:00Z', data },
});

test('HIGH-2: lessonApi.update links added items before PUT lesson and rolls back on 409 QUIZ_ALREADY_LINKED without touching lesson', async () => {
  const calls = [];
  apiClient.defaults.adapter = async config => {
    calls.push({
      method: config.method?.toUpperCase(),
      url: config.url,
      data: config.data ? JSON.parse(config.data) : undefined,
    });
    if (config.url === '/classrooms/c-rb/lessons/l-1' && config.method === 'get') {
      return ok(config, {
        id: 'l-1', classroomId: 'c-rb', title: 'Original', content: 'Body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'MANUAL', displayOrder: 0,
        materials: [], linkedItems: [{ id: 'lk-0', itemType: 'QUIZ', itemId: 'q-existing', displayOrder: 0, title: 'Q0', available: true }],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: [],
      });
    }
    if (config.url === '/classrooms/c-rb/lessons/activities' && config.method === 'get') {
      return ok(config, [
        { id: 'a-1', type: 'ASSIGNMENT', title: 'Assign 1', linkedLessonId: null, available: true },
        { id: 'q-taken', type: 'QUIZ', title: 'Taken Quiz', linkedLessonId: 'other-lesson', available: true },
      ]);
    }
    if (config.url === '/classrooms/c-rb/lessons/l-1/items' && config.method === 'post') {
      const payload = JSON.parse(config.data);
      if (payload.itemId === 'q-taken') {
        throw new AxiosError('Conflict', 'ERR_BAD_REQUEST', config, undefined, {
          status: 409, statusText: 'Conflict', headers: {}, config,
          data: { success: false, errorCode: 'QUIZ_ALREADY_LINKED', message: 'Quiz is already linked to another lesson', path: config.url, timestamp: '2026-11-19T00:00:00Z' },
        });
      }
      return ok(config, { id: 'lk-a1', itemType: payload.itemType, itemId: payload.itemId, displayOrder: 1, title: 'Assign 1', available: true }, 201);
    }
    if (config.url === '/classrooms/c-rb/lessons/l-1/items/a-1' && config.method === 'delete') {
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  await assert.rejects(
    lessonApi.update('c-rb', 'l-1', {
      ...baseInput,
      completionMode: 'MANUAL',
      activityIds: ['q-existing', 'a-1', 'q-taken'],
      requiredActivityIds: [],
    }),
    err => lessonError(err).includes('Quiz is already linked'),
  );

  // Must NEVER call PUT /classrooms/c-rb/lessons/l-1, and MUST roll back a-1
  assert.equal(calls.some(c => c.method === 'PUT' && c.url === '/classrooms/c-rb/lessons/l-1'), false);
  assert.deepEqual(
    calls.map(c => `${c.method} ${c.url}`),
    [
      'GET /classrooms/c-rb/lessons/l-1',
      'GET /classrooms/c-rb/lessons/activities',
      'POST /classrooms/c-rb/lessons/l-1/items',
      'POST /classrooms/c-rb/lessons/l-1/items',
      'DELETE /classrooms/c-rb/lessons/l-1/items/a-1',
    ],
  );
});

test('HIGH-2: lessonApi.update rolls back lesson, previous rule, unlinked removed items, and added items when unlink fails', async () => {
  const calls = [];
  const previousRule = {
    operator: 'ANY',
    conditions: [{ itemType: 'QUIZ', itemId: 'q1', requirement: 'SCORE_AT_LEAST', scorePercent: 75 }],
  };

  apiClient.defaults.adapter = async config => {
    calls.push({
      method: config.method?.toUpperCase(),
      url: config.url,
      data: config.data ? JSON.parse(config.data) : undefined,
    });
    if (config.url === '/classrooms/c-rb2/lessons/l-2' && config.method === 'get') {
      return ok(config, {
        id: 'l-2', classroomId: 'c-rb2', title: 'Before', content: 'Old body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0, materials: [],
        linkedItems: [
          { id: 'lk-q1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Q1', available: true },
          { id: 'lk-q2', itemType: 'QUIZ', itemId: 'q2', displayOrder: 1, title: 'Q2', available: true },
        ],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: ['q1'],
      });
    }
    if (config.url === '/classrooms/c-rb2/lessons/activities' && config.method === 'get') {
      return ok(config, [{ id: 'a-new', type: 'ASSIGNMENT', title: 'A New', linkedLessonId: null, available: true }]);
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2/completion-rule' && config.method === 'get') {
      return ok(config, { version: 3, rule: previousRule });
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2/items' && config.method === 'post') {
      const payload = JSON.parse(config.data);
      return ok(config, { id: `lk-${payload.itemId}`, itemType: payload.itemType, itemId: payload.itemId, displayOrder: 2 }, 201);
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2/completion-rule' && config.method === 'put') {
      return ok(config, null);
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2' && config.method === 'put') {
      return ok(config, {
        id: 'l-2', classroomId: 'c-rb2', title: 'After', content: 'New body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0, materials: [], linkedItems: [],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: ['a-new'],
      });
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2/items/q1' && config.method === 'delete') {
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2/items/q2' && config.method === 'delete') {
      throw new AxiosError('Server Error', 'ERR_BAD_RESPONSE', config, undefined, {
        status: 500, statusText: 'Internal Server Error', headers: {}, config,
        data: { success: false, errorCode: 'INTERNAL_ERROR', message: 'Failed to unlink q2', path: config.url, timestamp: '2026-11-19T00:00:00Z' },
      });
    }
    if (config.url === '/classrooms/c-rb2/lessons/l-2/items/a-new' && config.method === 'delete') {
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  await assert.rejects(
    lessonApi.update('c-rb2', 'l-2', {
      ...baseInput,
      title: 'After',
      content: 'New body',
      completionMode: 'CONDITION_BASED',
      activityIds: ['a-new'],
      requiredActivityIds: ['a-new'],
    }),
    err => lessonError(err).includes('Failed to unlink q2'),
  );

  // Verify rollback sequence after DELETE q2 fails:
  // 1. Re-link q1, 2. Restore PUT lesson ('Before'), 3. Restore PUT completion-rule (previousRule), 4. Unlink a-new
  const failIdx = calls.findIndex(c => c.method === 'DELETE' && c.url.endsWith('/items/q2'));
  assert.ok(failIdx >= 0);
  const rollback = calls.slice(failIdx + 1);
  assert.deepEqual(
    rollback.map(c => `${c.method} ${c.url}`),
    [
      'POST /classrooms/c-rb2/lessons/l-2/items',
      'PUT /classrooms/c-rb2/lessons/l-2',
      'PUT /classrooms/c-rb2/lessons/l-2/completion-rule',
      'DELETE /classrooms/c-rb2/lessons/l-2/items/a-new',
    ],
  );
  assert.deepEqual(rollback[0].data, { itemType: 'QUIZ', itemId: 'q1' });
  assert.equal(rollback[1].data.title, 'Before');
  assert.equal(rollback[1].data.content, 'Old body');
  assert.equal(rollback[1].data.completionMode, 'MANUAL');
  assert.deepEqual(rollback[2].data, previousRule);
});

test('MEDIUM-1: lessonApi.update preserves ANY operator and SCORE_AT_LEAST conditions when updating required items', async () => {
  const rulePuts = [];
  const callOrder = [];
  apiClient.defaults.adapter = async config => {
    callOrder.push(`${config.method?.toUpperCase()} ${config.url}`);
    if (config.url === '/classrooms/c-rule/lessons/l-rule' && config.method === 'get') {
      return ok(config, {
        id: 'l-rule', classroomId: 'c-rule', title: 'Scored Lesson', content: 'Body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0, materials: [],
        linkedItems: [
          { id: 'lk-q1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Quiz 1', available: true },
          { id: 'lk-a1', itemType: 'ASSIGNMENT', itemId: 'a1', displayOrder: 1, title: 'Assign 1', available: true },
        ],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: ['q1'],
      });
    }
    if (config.url === '/classrooms/c-rule/lessons/l-rule/completion-rule' && config.method === 'get') {
      return ok(config, {
        version: 2,
        rule: {
          operator: 'ANY',
          conditions: [{ itemType: 'QUIZ', itemId: 'q1', requirement: 'SCORE_AT_LEAST', scorePercent: 80 }],
        },
      });
    }
    if (config.url === '/classrooms/c-rule/lessons/l-rule/completion-rule' && config.method === 'put') {
      rulePuts.push(JSON.parse(config.data));
      return ok(config, null);
    }
    if (config.url === '/classrooms/c-rule/lessons/l-rule' && config.method === 'put') {
      return ok(config, {
        id: 'l-rule', classroomId: 'c-rule', title: 'Scored Lesson', content: 'Body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0, materials: [],
        linkedItems: [
          { id: 'lk-q1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Quiz 1', available: true },
          { id: 'lk-a1', itemType: 'ASSIGNMENT', itemId: 'a1', displayOrder: 1, title: 'Assign 1', available: true },
        ],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: ['q1', 'a1'],
      });
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  await lessonApi.update('c-rule', 'l-rule', {
    ...baseInput,
    title: 'Scored Lesson',
    content: 'Body',
    completionMode: 'CONDITION_BASED',
    activityIds: ['q1', 'a1'],
    requiredActivityIds: ['q1', 'a1'],
  });

  assert.equal(rulePuts.length, 1);
  assert.deepEqual(rulePuts[0], {
    operator: 'ANY',
    conditions: [
      { itemType: 'QUIZ', itemId: 'q1', requirement: 'SCORE_AT_LEAST', scorePercent: 80 },
      { itemType: 'ASSIGNMENT', itemId: 'a1', requirement: 'SUBMITTED', scorePercent: null },
    ],
  });
  // PUT /completion-rule must run BEFORE PUT /lessons/{id}
  assert.ok(
    callOrder.indexOf('PUT /classrooms/c-rule/lessons/l-rule/completion-rule') <
      callOrder.indexOf('PUT /classrooms/c-rule/lessons/l-rule'),
  );
});

test('MEDIUM-2: lessonApi.progress unwraps standard PageResponse, raw PaginatedData, and legacy double-wrapped envelope', async () => {
  const row = { studentId: 'stu-1', username: 'alice', completed: true, completedAt: '2026-11-19T01:00:00Z' };
  const pagination = { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true };

  // 1. Standard backend PageResponse<StudentProgressView>: { success, data: { items, pagination }, timestamp }
  apiClient.defaults.adapter = async config => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { success: true, data: { items: [row], pagination }, timestamp: '2026-11-19T01:00:00Z' },
  });
  const std = await lessonApi.progress('c-prog', 'l-prog', 0);
  assert.deepEqual(std, {
    items: [{ studentId: 'stu-1', displayName: 'alice', completed: true, completedAt: '2026-11-19T01:00:00Z' }],
    pagination,
  });

  // 2. Raw PaginatedData directly
  apiClient.defaults.adapter = async config => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: { items: [row], pagination },
  });
  const raw = await lessonApi.progress('c-prog', 'l-prog', 0);
  assert.equal(raw.items[0].displayName, 'alice');

  // 3. Legacy double-wrapped ApiResponse<PageResponse>
  apiClient.defaults.adapter = async config => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: {
      success: true,
      data: { success: true, data: { items: [row], pagination }, timestamp: '2026-11-19T01:00:00Z' },
      timestamp: '2026-11-19T01:00:00Z',
    },
  });
  const legacy = await lessonApi.progress('c-prog', 'l-prog', 0);
  assert.equal(legacy.items[0].displayName, 'alice');
});

test('MEDIUM-5: lessonApi.list maps materialCount & content, lessonApi.get maps completed, and LessonWorkspacePage renders materialCount', async () => {
  const longContent = '## Trees\n' + 'algorithm '.repeat(400);
  apiClient.defaults.adapter = async config => {
    if (config.url === '/classrooms/c-ui/lessons' && config.method === 'get') {
      return ok(config, [{
        id: 'l-ui', classroomId: 'c-ui', title: 'Trees & Graphs', content: longContent,
        status: 'PUBLISHED', completionMode: 'MANUAL', displayOrder: 0, materialCount: 3,
        isCompleted: false, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
      }]);
    }
    if (config.url === '/classrooms/c-ui/lessons/l-ui' && config.method === 'get') {
      return ok(config, {
        id: 'l-ui', classroomId: 'c-ui', title: 'Trees & Graphs', content: longContent,
        codeExample: '', externalLinks: '[]', status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0,
        materials: [], materialCount: 3,
        linkedItems: [
          { id: 'lk-1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Quiz 1', available: true, completed: true },
          { id: 'lk-2', itemType: 'ASSIGNMENT', itemId: 'a1', displayOrder: 1, title: 'Assign 1', available: true, completed: false },
        ],
        isCompleted: false, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: ['q1'],
      });
    }
    if (config.url === '/classrooms/c-ui' && config.method === 'get') {
      return ok(config, {
        id: 'c-ui', name: 'Algorithms 101', description: '', status: 'ACTIVE',
        joinMode: 'AUTO_JOIN', ownerId: 'teacher-1', createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
      });
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  const listed = await lessonApi.list('c-ui', 0);
  assert.equal(listed.items[0].materialCount, 3);
  assert.equal(listed.items[0].content, longContent);
  assert.ok(estimateReadingMinutes(listed.items[0]) >= 2);

  const detail = await lessonApi.get('c-ui', 'l-ui');
  assert.equal(detail.materialCount, 3);
  assert.equal(detail.activities[0].completed, true);
  assert.equal(detail.activities[1].completed, false);

  // Verify LessonWorkspacePage renders "3 materials" from lesson.materialCount even when attachments is []
  const { container } = setupDom();
  observeApiSource('backend');
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  const root = createRoot(container);
  const studentAuth = {
    user: { id: 'stu-1', username: 'student1', fullName: 'Student One', email: 's1@example.test', role: 'STUDENT' },
    token: 'real-jwt', isAuthenticated: true, isLoading: false,
    login: async () => {}, register: async () => ({ email: '', message: '' }),
    verifyOtp: async () => {}, quickLogin: async () => {}, logout: async () => {},
  };
  try {
    await act(async () => {
      root.render(
        createElement(
          QueryClientProvider,
          { client: queryClient },
          createElement(
            AuthContext.Provider,
            { value: studentAuth },
            createElement(
              MemoryRouter,
              { initialEntries: ['/student/classrooms/c-ui/lessons'] },
              createElement(
                Routes,
                null,
                createElement(Route, {
                  path: '/student/classrooms/:id/lessons',
                  element: createElement(LessonWorkspacePage, { role: 'STUDENT' }),
                }),
              ),
            ),
          ),
        ),
      );
    });
    await act(async () => { await new Promise(resolve => setTimeout(resolve, 40)); });
    assert.match(container.textContent, /3 materials/);
  } finally {
    await act(async () => { root.unmount(); });
    queryClient.clear();
  }
});

test('MEDIUM-5: lessonApi.upload cleans up orphaned stored file when POST /materials fails', async () => {
  const calls = [];
  apiClient.defaults.adapter = async config => {
    calls.push(`${config.method?.toUpperCase()} ${config.url}`);
    if (config.url === '/classrooms/c-up/files' && config.method === 'post') {
      return ok(config, {
        id: 'orphan-file-99', name: 'slides.pdf', mediaType: 'application/pdf', kind: 'PDF',
        size: 12, purpose: 'LESSON_MATERIAL', state: 'READY', sha256: 'abc', createdAt: '2026-11-19T00:00:00Z',
      }, 201);
    }
    if (config.url === '/classrooms/c-up/lessons/l-up/materials' && config.method === 'post') {
      throw new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, undefined, {
        status: 403, statusText: 'Forbidden', headers: {}, config,
        data: { success: false, errorCode: 'FORBIDDEN_OPERATION', message: 'Lesson is archived', path: config.url, timestamp: '2026-11-19T00:00:00Z' },
      });
    }
    if (config.url === '/files/orphan-file-99' && config.method === 'delete') {
      throw new AxiosError('Cleanup failed', 'ERR_BAD_RESPONSE', config, undefined, { status: 500, headers: {}, config });
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  await assert.rejects(
    lessonApi.upload('c-up', 'l-up', new File(['pdf'], 'slides.pdf', { type: 'application/pdf' })),
    err => isAxiosForbidden(err),
  );
  assert.deepEqual(calls, [
    'POST /classrooms/c-up/files',
    'POST /classrooms/c-up/lessons/l-up/materials',
    'DELETE /files/orphan-file-99',
  ]);
});

test('HIGH-2: lessonApi.update rolls back MANUAL mode and added items if PUT /lessons fails after PUT /completion-rule', async () => {
  const calls = [];
  apiClient.defaults.adapter = async config => {
    calls.push({
      method: config.method?.toUpperCase(),
      url: config.url,
      data: config.data ? JSON.parse(config.data) : undefined,
    });
    if (config.url === '/classrooms/c-mrb/lessons/l-mrb' && config.method === 'get') {
      return ok(config, {
        id: 'l-mrb', classroomId: 'c-mrb', title: 'Manual Original', content: 'Orig', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'MANUAL', displayOrder: 0, materials: [], linkedItems: [],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: [],
      });
    }
    if (config.url === '/classrooms/c-mrb/lessons/activities' && config.method === 'get') {
      return ok(config, [{ id: 'q-new', type: 'QUIZ', title: 'Quiz New', linkedLessonId: null, available: true }]);
    }
    if (config.url === '/classrooms/c-mrb/lessons/l-mrb/completion-rule' && config.method === 'get') {
      return ok(config, { version: 0, rule: null });
    }
    if (config.url === '/classrooms/c-mrb/lessons/l-mrb/items' && config.method === 'post') {
      return ok(config, { id: 'lk-qn', itemType: 'QUIZ', itemId: 'q-new', displayOrder: 0 }, 201);
    }
    if (config.url === '/classrooms/c-mrb/lessons/l-mrb/completion-rule' && config.method === 'put') {
      return ok(config, null);
    }
    if (config.url === '/classrooms/c-mrb/lessons/l-mrb' && config.method === 'put') {
      const payload = JSON.parse(config.data);
      if (payload.title === 'New Title') {
        throw new AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, undefined, {
          status: 400, statusText: 'Bad Request', headers: {}, config,
          data: { success: false, errorCode: 'VALIDATION_FAILED', message: 'Invalid lesson payload', path: config.url, timestamp: '2026-11-19T00:00:00Z' },
        });
      }
      return ok(config, {
        id: 'l-mrb', classroomId: 'c-mrb', title: payload.title, content: payload.content, codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: payload.completionMode, displayOrder: 0, materials: [], linkedItems: [],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: [],
      });
    }
    if (config.url === '/classrooms/c-mrb/lessons/l-mrb/items/q-new' && config.method === 'delete') {
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  await assert.rejects(
    lessonApi.update('c-mrb', 'l-mrb', {
      ...baseInput,
      title: 'New Title',
      completionMode: 'CONDITION_BASED',
      activityIds: ['q-new'],
      requiredActivityIds: ['q-new'],
    }),
    err => lessonError(err).includes('Invalid lesson payload'),
  );

  const putIdx = calls.findIndex(c => c.method === 'PUT' && c.url === '/classrooms/c-mrb/lessons/l-mrb');
  assert.ok(putIdx >= 0);
  const rollback = calls.slice(putIdx + 1);
  assert.deepEqual(
    rollback.map(c => `${c.method} ${c.url}`),
    [
      'PUT /classrooms/c-mrb/lessons/l-mrb',
      'DELETE /classrooms/c-mrb/lessons/l-mrb/items/q-new',
    ],
  );
  assert.equal(rollback[0].data.title, 'Manual Original');
  assert.equal(rollback[0].data.completionMode, 'MANUAL');
});

test('MEDIUM-1: switching MANUAL -> CONDITION_BASED preserves ANY + SCORE_AT_LEAST and drops stale unlinked rule items', async () => {
  const rulePuts = [];
  apiClient.defaults.adapter = async config => {
    if (config.url === '/classrooms/c-sw/lessons/l-sw' && config.method === 'get') {
      return ok(config, {
        id: 'l-sw', classroomId: 'c-sw', title: 'Switched', content: 'Body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'MANUAL', displayOrder: 0, materials: [],
        linkedItems: [{ id: 'lk-q1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Q1', available: true }],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: [],
      });
    }
    if (config.url === '/classrooms/c-sw/lessons/l-sw/completion-rule' && config.method === 'get') {
      return ok(config, {
        version: 1,
        rule: {
          operator: 'ANY',
          conditions: [
            { itemType: 'QUIZ', itemId: 'q1', requirement: 'SCORE_AT_LEAST', scorePercent: 85 },
            { itemType: 'QUIZ', itemId: 'q-unlinked', requirement: 'SUBMITTED', scorePercent: null },
          ],
        },
      });
    }
    if (config.url === '/classrooms/c-sw/lessons/l-sw/completion-rule' && config.method === 'put') {
      rulePuts.push(JSON.parse(config.data));
      return ok(config, null);
    }
    if (config.url === '/classrooms/c-sw/lessons/l-sw' && config.method === 'put') {
      return ok(config, {
        id: 'l-sw', classroomId: 'c-sw', title: 'Switched', content: 'Body', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0, materials: [],
        linkedItems: [{ id: 'lk-q1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Q1', available: true }],
        isCompleted: null, completedAt: null, createdAt: '2026-11-19T00:00:00Z', updatedAt: '2026-11-19T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: ['q1'],
      });
    }
    throw new Error(`Unexpected request: ${config.method?.toUpperCase()} ${config.url}`);
  };

  await lessonApi.update('c-sw', 'l-sw', {
    ...baseInput,
    title: 'Switched',
    content: 'Body',
    completionMode: 'CONDITION_BASED',
    activityIds: ['q1'],
    requiredActivityIds: ['q1'],
  });

  assert.deepEqual(rulePuts, [{
    operator: 'ANY',
    conditions: [{ itemType: 'QUIZ', itemId: 'q1', requirement: 'SCORE_AT_LEAST', scorePercent: 85 }],
  }]);
});

function isAxiosForbidden(err) {
  return axios.isAxiosError(err) && err.response?.status === 403;
}
