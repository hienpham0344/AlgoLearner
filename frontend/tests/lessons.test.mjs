import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';
import { JSDOM } from 'jsdom';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const dom = new JSDOM('<!doctype html><html><body></body></html>');
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.localStorage = { getItem: () => null };
const compiled = await build({
  stdin: { contents: `export * from './src/features/classroom/lessons/lessonRules.ts';
    export * from './src/features/classroom/lessons/lessonReading.ts';
    export * from './src/features/classroom/lessons/api/lessonApi.ts';
    export { apiClient } from './src/services/apiClient.ts';
    export { LessonContent, renderLessonMarkdown } from './src/features/classroom/lessons/components/LessonContent.tsx';`,
    resolveDir: process.cwd(), loader: 'ts' },
  alias: { '@': './src' }, bundle: true, platform: 'node', format: 'cjs', write: false, jsx: 'automatic',
  external: ['axios', 'react', 'react/jsx-runtime', 'dompurify'],
});
const module = { exports: {} };
new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(module, module.exports, createRequire(import.meta.url));
const { validateLesson, validateAttachment, safeExternalUrl, visibleLessons, canCompleteLesson,
  extractLessonOutline, estimateReadingMinutes,
  activitySelectable, activityPath, lessonDate, lessonKeys, lessonApi, apiClient, LessonContent, renderLessonMarkdown } = module.exports;
const input = { title: 'Recursion', content: '# Recursion', codeExample: null, externalLinks: [],
  completionMode: 'MANUAL', requiredActivityIds: [], activityIds: [] };

test('LES-10: validation preserves content and requires a linked assignment/quiz for conditional completion', () => {
  assert.deepEqual(validateLesson(input, []), {});
  assert.match(validateLesson({ ...input, title: ' ' }, []).title, /title/i);
  assert.ok(validateLesson({ ...input, completionMode: 'CONDITION_BASED' }, []).completion);
  const quiz = { id: 'q1', type: 'QUIZ', title: 'Quiz', linkedLessonId: null };
  assert.deepEqual(validateLesson({ ...input, completionMode: 'CONDITION_BASED', activityIds: ['q1'], requiredActivityIds: ['q1'] }, [quiz]), {});
  assert.ok(validateLesson({ ...input, completionMode: 'CONDITION_BASED', activityIds: ['p1'], requiredActivityIds: ['p1'] }, [{ id: 'p1', type: 'PRACTICE' }]).completion);
  assert.ok(validateLesson({ ...input, externalLinks: [{ label: 'Unsafe', url: 'javascript:alert(1)' }] }, []).links);
});

test('LES-01: only published lessons and manual completion are available to students', () => {
  const lessons = ['DRAFT', 'PUBLISHED', 'ARCHIVED'].map(status => ({ ...input, status }));
  assert.equal(visibleLessons(lessons, 'STUDENT').length, 1);
  assert.equal(visibleLessons(lessons, 'TEACHER').length, 3);
  assert.equal(canCompleteLesson({ ...lessons[1], progress: null }), true);
  assert.equal(canCompleteLesson({ ...lessons[1], completionMode: 'CONDITION_BASED' }), false);
  assert.equal(canCompleteLesson({ ...lessons[1], progress: { completed: true } }), false);
  assert.equal(canCompleteLesson(lessons[0]), false);
});

test('LES-10-11: quiz selection respects the existing lesson and permits the current lesson', () => {
  const quiz = { type: 'QUIZ', linkedLessonId: 'lesson-a' };
  assert.equal(activitySelectable(quiz, 'lesson-b'), false);
  assert.equal(activitySelectable(quiz, 'lesson-a'), true);
  assert.equal(activitySelectable({ ...quiz, type: 'ASSIGNMENT' }, 'lesson-b'), true);
});

test('LES-10-09: upload policy rejects unsafe types, empty files and oversized files', () => {
  const policy = { uploadsAvailable: true, maxFileSizeBytes: 1024, allowedExtensions: ['pdf', 'svg', 'exe'] };
  assert.equal(validateAttachment({ name: 'notes.PDF', size: 512 }, policy), null);
  for (const file of [{ name: 'a.exe', size: 1 }, { name: 'a.svg', size: 1 }, { name: 'a.pdf', size: 1025 }, { name: 'a.pdf', size: 0 }]) {
    assert.ok(validateAttachment(file, policy));
  }
  assert.ok(validateAttachment({ name: 'a.pdf', size: 1 }, null));
});

test('LES security: sanitize HTML, SVG, event handlers, dangerous schemes and external resources', () => {
  for (const value of ['javascript:alert(1)', 'data:text/html,x', '//evil.test', 'https://user:password@example.test', 'file:///etc/passwd']) assert.equal(safeExternalUrl(value), null);
  assert.equal(safeExternalUrl('https://example.test/guide'), 'https://example.test/guide');
  const html = renderLessonMarkdown(`# Safe\n<script>alert(1)</script><img src=x onerror=alert(2)><svg onload=alert(3)></svg>\n[bad](javascript:alert(4))\n[Guide](https://example.test)\n\`\`\`java\nSystem.out.println("ok");\n\`\`\``);
  const fragment = JSDOM.fragment(html);
  assert.equal(fragment.querySelector('script, img, svg, iframe'), null);
  assert.equal(fragment.querySelector('[onerror], [onload], [href^="javascript:"]'), null);
  assert.equal(fragment.querySelector('h1').textContent, 'Safe');
  assert.match(fragment.querySelector('code').textContent, /println/);
  assert.match(fragment.querySelector('a[href]')?.getAttribute('rel'), /noopener/);
});

test('LESSON contract: list array normalization, detail parsing, and completionMode mapping', async () => {
  const calls = [];
  const summaryRows = Array.from({ length: 25 }, (_, idx) => ({
    id: `lesson-${idx}`,
    classroomId: 'class-a',
    title: `Lesson ${idx}`,
    status: 'PUBLISHED',
    completionMode: idx % 2 === 0 ? 'AUTO_ITEMS' : 'MANUAL',
    displayOrder: idx,
    isCompleted: idx === 0 ? true : null,
    completedAt: idx === 0 ? '2026-11-11T00:00:00Z' : null,
    createdAt: '2026-11-11T00:00:00Z',
    updatedAt: '2026-11-11T00:00:00Z',
  }));
  apiClient.defaults.adapter = async config => {
    calls.push(config);
    if (config.url === '/classrooms/class-a/lessons') {
      return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, timestamp: '2026-11-11T00:00:00Z', data: summaryRows } };
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-0') {
      return {
        config, status: 200, statusText: 'OK', headers: {},
        data: {
          success: true, timestamp: '2026-11-11T00:00:00Z',
          data: {
            id: 'lesson-0', classroomId: 'class-a', title: 'Lesson 0', content: '# Title',
            codeExample: JSON.stringify({ language: 'python', code: 'print(1)' }),
            externalLinks: JSON.stringify([{ label: 'Docs', url: 'https://example.test/docs' }]),
            status: 'PUBLISHED', completionMode: 'AUTO_ITEMS', displayOrder: 0,
            materials: [{ id: 'mat-1', fileName: 'guide.pdf', fileUrl: '/api/v1/files/stored-1/content', fileType: 'PDF', fileSize: 512, createdAt: '2026-11-11T00:00:00Z' }],
            linkedItems: [{ id: 'link-1', itemType: 'QUIZ', itemId: 'quiz-1', displayOrder: 0, title: 'Quiz 1', available: true }],
            isCompleted: false, completedAt: null, createdAt: '2026-11-11T00:00:00Z', updatedAt: '2026-11-11T00:00:00Z',
            previousStatus: 'DRAFT', canDelete: false, requiredItemIds: ['quiz-1'],
          },
        },
      };
    }
    throw new Error(`Unexpected request: ${config.method} ${config.url}`);
  };

  const page1 = await lessonApi.list('class-a', 1);
  assert.equal(calls[0].url, '/classrooms/class-a/lessons');
  assert.equal(page1.items.length, 5);
  assert.deepEqual(page1.pagination, { page: 1, size: 20, totalElements: 25, totalPages: 2, isFirst: false, isLast: true });
  assert.equal(page1.items[0].completionMode, 'CONDITION_BASED');
  assert.equal(page1.items[0].content, '');
  assert.deepEqual(page1.items[0].attachments, []);

  const page0 = await lessonApi.list('class-a', 0);
  assert.deepEqual(page0.items[0].progress, { completed: true, completedAt: '2026-11-11T00:00:00Z' });
  assert.equal(page0.items[1].progress, null);

  const detail = await lessonApi.get('class-a', 'lesson-0');
  assert.deepEqual(detail.codeExample, { language: 'python', code: 'print(1)' });
  assert.deepEqual(detail.externalLinks, [{ label: 'Docs', url: 'https://example.test/docs' }]);
  assert.equal(detail.completionMode, 'CONDITION_BASED');
  assert.deepEqual(detail.requiredActivityIds, ['quiz-1']);
  assert.deepEqual(detail.attachments, [{ id: 'mat-1', name: 'guide.pdf', size: 512, contentType: 'application/pdf' }]);
  assert.deepEqual(detail.activities, [{ id: 'quiz-1', type: 'QUIZ', title: 'Quiz 1', linkedLessonId: 'lesson-0', available: true, path: null, completed: false }]);
  assert.deepEqual(detail.progress, { completed: false, completedAt: null });
  assert.equal(detail.previousStatus, 'DRAFT');
  assert.equal(detail.canDelete, false);
  assert.notDeepEqual(lessonKeys.root('student-a', 'class-a'), lessonKeys.root('student-b', 'class-a'));
});

test('LESSON contract: create/update serialize fields, sequence items + completion-rule, and handle unpublish/complete/progress', async () => {
  const calls = [];
  let lessonDetail = {
    id: 'lesson-1', classroomId: 'class-a', title: 'Recursion', content: '# Recursion',
    codeExample: '{"language":"java","code":"int f(){}"}', externalLinks: '[]',
    status: 'PUBLISHED', completionMode: 'MANUAL', displayOrder: 0,
    materials: [], linkedItems: [{ id: 'link-q1', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Quiz 1', available: true }],
    isCompleted: null, completedAt: null, createdAt: '2026-11-11T00:00:00Z', updatedAt: '2026-11-11T00:00:00Z',
    previousStatus: null, canDelete: false, requiredItemIds: [],
  };
  apiClient.defaults.adapter = async config => {
    calls.push({ method: config.method?.toUpperCase(), url: config.url, params: config.params, data: config.data ? JSON.parse(config.data) : undefined });
    const ok = (data, status = 200) => ({ config, status, statusText: 'OK', headers: {}, data: { success: true, timestamp: '2026-11-11T00:00:00Z', data } });
    if (config.url === '/classrooms/class-a/lessons/activities' && config.method === 'get') {
      return ok([
        { id: 'q1', type: 'QUIZ', title: 'Quiz 1', linkedLessonId: null, available: true },
        { id: 'a1', type: 'ASSIGNMENT', title: 'Assign 1', linkedLessonId: null, available: true },
      ]);
    }
    if (config.url === '/classrooms/class-a/lessons/capabilities' && config.method === 'get') {
      return ok({ uploadsAvailable: true, activityLinkingAvailable: true, maxFileSizeBytes: 1024, allowedExtensions: ['pdf'] });
    }
    if (config.url === '/classrooms/class-a/lessons' && config.method === 'post') {
      return ok({ ...lessonDetail, id: 'created-1', status: 'DRAFT', linkedItems: [], requiredItemIds: [] }, 201);
    }
    if (config.url === '/classrooms/class-a/lessons/created-1/items' && config.method === 'post') {
      return ok({ id: 'link-new', itemType: 'QUIZ', itemId: 'q1', displayOrder: 0, title: 'Quiz 1', available: true }, 201);
    }
    if (config.url === '/classrooms/class-a/lessons/created-1/completion-rule' && config.method === 'put') {
      return ok(null);
    }
    if (config.url === '/classrooms/class-a/lessons/created-1' && config.method === 'get') {
      return ok({ ...lessonDetail, id: 'created-1', status: 'DRAFT', completionMode: 'AUTO_ITEMS', requiredItemIds: ['q1'] });
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1' && config.method === 'get') {
      return ok(lessonDetail);
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1' && config.method === 'put') {
      return ok(lessonDetail);
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1/items' && config.method === 'post') {
      return ok({ id: 'link-a1', itemType: 'ASSIGNMENT', itemId: 'a1', displayOrder: 1, title: 'Assign 1', available: true }, 201);
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1/completion-rule' && config.method === 'put') {
      lessonDetail = { ...lessonDetail, completionMode: 'AUTO_ITEMS', requiredItemIds: ['a1'] };
      return ok(null);
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1/items/q1' && config.method === 'delete') {
      lessonDetail = { ...lessonDetail, linkedItems: [{ id: 'link-a1', itemType: 'ASSIGNMENT', itemId: 'a1', displayOrder: 0, title: 'Assign 1', available: true }] };
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1/unpublish' && config.method === 'patch') {
      return ok({ ...lessonDetail, status: 'DRAFT' });
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1/complete' && config.method === 'post') {
      lessonDetail = { ...lessonDetail, isCompleted: true, completedAt: '2026-11-11T01:00:00Z' };
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-1/progress' && config.method === 'get') {
      return ok({ items: [{ studentId: 'student-1', username: 'alice', completed: true, completedAt: '2026-11-11T01:00:00Z' }], pagination: { page: 0, size: 20, totalElements: 1, totalPages: 1, isFirst: true, isLast: true } });
    }
    throw new Error(`Unexpected request: ${config.method} ${config.url}`);
  };

  // Conditional create: sends MANUAL first, links items, sets completion-rule, then fetches detail
  const created = await lessonApi.create('class-a', {
    ...input,
    completionMode: 'CONDITION_BASED',
    activityIds: ['q1', 'a1'],
    requiredActivityIds: ['q1'],
  });
  assert.equal(created.completionMode, 'CONDITION_BASED');
  assert.deepEqual(created.requiredActivityIds, ['q1']);
  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'POST /classrooms/class-a/lessons',
    'GET /classrooms/class-a/lessons/activities',
    'POST /classrooms/class-a/lessons/created-1/items',
    'POST /classrooms/class-a/lessons/created-1/items',
    'PUT /classrooms/class-a/lessons/created-1/completion-rule',
    'GET /classrooms/class-a/lessons/created-1',
  ]);
  assert.equal(calls[0].data.completionMode, 'MANUAL');
  assert.equal(calls[0].data.codeExample, '');
  assert.equal(calls[0].data.externalLinks, '[]');
  assert.deepEqual(calls[4].data, {
    operator: 'ALL',
    conditions: [{ itemType: 'QUIZ', itemId: 'q1', requirement: 'SUBMITTED', scorePercent: null }],
  });

  // Update published lesson: MANUAL on PUT first, link a1, update completion-rule BEFORE unlinking q1
  calls.length = 0;
  const updated = await lessonApi.update('class-a', 'lesson-1', {
    ...input,
    codeExample: null,
    completionMode: 'CONDITION_BASED',
    activityIds: ['a1'],
    requiredActivityIds: ['a1'],
  });
  assert.equal(updated.completionMode, 'CONDITION_BASED');
  assert.deepEqual(calls.map(c => `${c.method} ${c.url}`), [
    'GET /classrooms/class-a/lessons/lesson-1',
    'PUT /classrooms/class-a/lessons/lesson-1',
    'POST /classrooms/class-a/lessons/lesson-1/items',
    'PUT /classrooms/class-a/lessons/lesson-1/completion-rule',
    'DELETE /classrooms/class-a/lessons/lesson-1/items/q1',
    'GET /classrooms/class-a/lessons/lesson-1',
  ]);
  assert.equal(calls[1].data.completionMode, 'MANUAL');
  assert.equal(calls[1].data.codeExample, '');

  // Transition 'draft' -> PATCH .../unpublish
  const drafted = await lessonApi.transition('class-a', 'lesson-1', 'draft');
  assert.equal(drafted.status, 'DRAFT');

  // Complete: 204 No Content triggers GET detail
  const completed = await lessonApi.complete('class-a', 'lesson-1');
  assert.equal(completed.progress?.completed, true);

  // Capabilities, activities, and progress username -> displayName
  const caps = await lessonApi.capabilities('class-a');
  assert.equal(caps.uploadsAvailable, true);
  const acts = await lessonApi.activities('class-a');
  assert.deepEqual(acts[0], { id: 'q1', type: 'QUIZ', title: 'Quiz 1', linkedLessonId: null, available: true, path: null, completed: false });
  const prog = await lessonApi.progress('class-a', 'lesson-1', 0);
  assert.equal(prog.items[0].displayName, 'alice');
});

test('LESSON contract: two-step files+materials upload, material removal, and cached/uncached file download', async () => {
  const calls = [];
  apiClient.defaults.adapter = async config => {
    calls.push(config);
    const ok = (data, status = 200) => ({ config, status, statusText: 'OK', headers: {}, data: { success: true, timestamp: '2026-11-11T00:00:00Z', data } });
    if (config.url === '/classrooms/class-a/files' && config.method === 'post') {
      return ok({ id: 'stored-file-1', name: 'notes.pdf', mediaType: 'application/pdf', kind: 'PDF', size: 5, purpose: 'LESSON_MATERIAL', state: 'READY', sha256: 'abc', createdAt: '2026-11-11T00:00:00Z' }, 201);
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-a/materials' && config.method === 'post') {
      return ok({ id: 'mat-1', fileName: 'notes.pdf', fileUrl: '/api/v1/files/stored-file-1/content', fileType: 'PDF', fileSize: 5, createdAt: '2026-11-11T00:00:00Z' }, 201);
    }
    if (config.url === '/files/stored-file-1/content' && config.method === 'get') {
      return { config, status: 200, statusText: 'OK', headers: {}, data: new Blob(['PDF'], { type: 'application/pdf' }) };
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-a' && config.method === 'get') {
      return ok({
        id: 'lesson-a', classroomId: 'class-a', title: 'Lesson A', content: '', codeExample: '', externalLinks: '[]',
        status: 'PUBLISHED', completionMode: 'MANUAL', displayOrder: 0,
        materials: [{ id: 'mat-uncached', fileName: 'slides.pptx', fileUrl: '/api/v1/files/stored-uncached/content', fileType: 'PPTX', fileSize: 12, createdAt: '2026-11-11T00:00:00Z' }],
        linkedItems: [], isCompleted: null, completedAt: null, createdAt: '2026-11-11T00:00:00Z', updatedAt: '2026-11-11T00:00:00Z',
        previousStatus: null, canDelete: false, requiredItemIds: [],
      });
    }
    if (config.url === '/files/stored-uncached/content' && config.method === 'get') {
      return { config, status: 200, statusText: 'OK', headers: {}, data: new Blob(['PPTX']) };
    }
    if (config.url === '/classrooms/class-a/lessons/lesson-a/materials/mat-1' && config.method === 'delete') {
      return { config, status: 204, statusText: 'No Content', headers: {}, data: '' };
    }
    throw new Error(`Unexpected request: ${config.method} ${config.url}`);
  };

  const uploaded = await lessonApi.upload('class-a', 'lesson-a', new File(['hello'], 'notes.pdf', { type: 'application/pdf' }));
  assert.equal(calls[0].url, '/classrooms/class-a/files');
  assert.equal(calls[0].params.purpose, 'LESSON_MATERIAL');
  assert.equal(calls[0].data instanceof FormData, true);
  assert.equal(calls[0].data.get('file').name, 'notes.pdf');
  assert.notEqual(calls[0].headers.get('Content-Type'), 'application/json');
  assert.equal(calls[1].url, '/classrooms/class-a/lessons/lesson-a/materials');
  assert.deepEqual(JSON.parse(calls[1].data), { fileId: 'stored-file-1' });
  assert.deepEqual(uploaded, { id: 'mat-1', name: 'notes.pdf', size: 5, contentType: 'application/pdf' });

  const cachedBlob = await lessonApi.download('class-a', 'lesson-a', 'mat-1');
  assert.equal(cachedBlob instanceof Blob, true);
  assert.equal(calls[2].url, '/files/stored-file-1/content');

  const uncachedBlob = await lessonApi.download('class-a', 'lesson-a', 'mat-uncached');
  assert.equal(uncachedBlob instanceof Blob, true);
  assert.equal(calls[3].url, '/classrooms/class-a/lessons/lesson-a');
  assert.equal(calls[4].url, '/files/stored-uncached/content');

  await lessonApi.removeAttachment('class-a', 'lesson-a', 'mat-1');
  assert.equal(calls[5].method, 'delete');
  assert.equal(calls[5].url, '/classrooms/class-a/lessons/lesson-a/materials/mat-1');
});

test('lesson content component escapes source code and keeps safe reading links', () => {
  const html = renderToStaticMarkup(createElement(LessonContent, { lesson: { ...input,
    codeExample: { language: 'java', code: '<script>alert("example only")</script>' },
    externalLinks: [{ label: 'Docs', url: 'https://example.test/docs' }, { label: 'Blocked', url: 'data:text/html,danger' }],
  } }));
  const fragment = JSDOM.fragment(html);
  assert.equal(fragment.querySelector('script'), null);
  assert.equal(fragment.querySelector('code').textContent, '<script>alert("example only")</script>');
  assert.equal(fragment.querySelectorAll('a').length, 1);
  assert.match(fragment.textContent, /This link is unavailable/);
});

test('activity navigation cannot escape the current role/class and dates use UTC+7', () => {
  assert.equal(activityPath('/student/classrooms/class-a/quizzes/q1', 'class-a', 'STUDENT'), '/student/classrooms/class-a/quizzes/q1');
  for (const path of ['https://evil.test', '//evil.test', '/teacher/classrooms/class-a/quizzes/q1', '/student/classrooms/class-a/../class-b/quizzes/q1', '/student/classrooms/class-a/%2e%2e/class-b/quizzes/q1']) {
    assert.equal(activityPath(path, 'class-a', 'STUDENT'), null);
  }
  assert.match(lessonDate('2026-11-11T00:00:00Z'), /07:00/);
});

test('lesson reading helpers extract outline IDs matching rendered headings and estimate duration', () => {
  const sample = {
    content: '## Intro to Trees\nFirst paragraph.\n\n### Base Case\nDetails.\n\n## Intro to Trees\nSecond section.',
    codeExample: { language: 'python', code: 'def f(n):\n    return n' },
    externalLinks: [{ label: 'Docs', url: 'https://example.test' }],
  };
  const outline = extractLessonOutline(sample, true);
  assert.deepEqual(
    outline.map(item => item.id),
    ['intro-to-trees', 'base-case', 'intro-to-trees-2', 'lesson-code-example', 'lesson-further-reading', 'lesson-materials-completion'],
  );
  const rendered = JSDOM.fragment(renderLessonMarkdown(sample.content));
  assert.equal(rendered.querySelectorAll('h2, h3')[0]?.id, 'intro-to-trees');
  assert.equal(rendered.querySelectorAll('h2, h3')[1]?.id, 'base-case');
  assert.equal(rendered.querySelectorAll('h2, h3')[2]?.id, 'intro-to-trees-2');
  assert.ok(estimateReadingMinutes(sample) >= 1);
});

test('lesson math renderer formats inline and display LaTeX safely while ignoring code and prices', () => {
  const md = [
    '## Complexity $O(n \\log n)$',
    'At level $k$, there are $2^k$ nodes of cost $\\frac{n}{2^k}$.',
    '',
    '$$T(n) = aT\\!\\left(\\frac{n}{b}\\right) + f(n)$$',
    '',
    'Keep currency literal: $10 and $20.',
    'Keep code literal: `$not_math$` and:',
    '```bash',
    'echo "$PATH"',
    '```',
    'Reject XSS inside math: $<img src=x onerror=alert(1)> \\text{<script>alert(2)</script>}$',
  ].join('\n');

  const html = renderLessonMarkdown(md);
  const fragment = JSDOM.fragment(html);
  assert.ok(fragment.querySelector('.lesson-math-inline .katex'));
  assert.ok(fragment.querySelector('.lesson-math-block .katex-display'));
  assert.match(fragment.textContent, /\$10 and \$20/);
  assert.match(fragment.querySelector('code')?.textContent || '', /\$not_math\$/);
  assert.match(fragment.querySelector('pre code')?.textContent || '', /\$PATH/);
  assert.equal(fragment.querySelector('script, img, iframe'), null);
  assert.equal(fragment.querySelector('[onerror]'), null);

  const outline = extractLessonOutline({ content: md, codeExample: null, externalLinks: [] }, false);
  const heading = fragment.querySelector('h2');
  assert.equal(heading?.id, outline[0]?.id);
});
