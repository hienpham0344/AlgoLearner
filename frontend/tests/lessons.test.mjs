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

test('LESSON contract: class-scoped routes, envelope, pagination, multipart and server-authoritative completion', async () => {
  const calls = [];
  apiClient.defaults.adapter = async config => {
    calls.push(config);
    return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true,
      timestamp: '2026-11-11T00:00:00Z', data: config.url.endsWith('/completion') ? { ...input, progress: { completed: true } }
        : { items: [], pagination: { page: 2, totalPages: 3 } } } };
  };
  assert.deepEqual((await lessonApi.list('class-a', 2)).items, []);
  assert.equal(calls[0].url, '/classrooms/class-a/lessons');
  assert.equal(calls[0].params.page, 2);
  await lessonApi.create('class-a', input);
  assert.equal(JSON.parse(calls[1].data).title, 'Recursion');
  const result = await lessonApi.complete('class-a', 'lesson-a');
  assert.equal(result.progress.completed, true);
  assert.equal(calls[2].url, '/classrooms/class-a/lessons/lesson-a/completion');
  await lessonApi.upload('class-a', 'lesson-a', new File(['hello'], 'notes.pdf', { type: 'application/pdf' }));
  assert.equal(calls[3].data instanceof FormData, true);
  assert.equal(calls[3].data.get('file').name, 'notes.pdf');
  assert.notEqual(calls[3].headers.get('Content-Type'), 'application/json');
  assert.notDeepEqual(lessonKeys.root('student-a', 'class-a'), lessonKeys.root('student-b', 'class-a'));
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
