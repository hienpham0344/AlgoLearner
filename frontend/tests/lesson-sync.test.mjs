import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { createRequire } from 'node:module';

const storage = new Map();
globalThis.sessionStorage = {
  getItem: (key) => (storage.has(key) ? storage.get(key) : null),
  setItem: (key, value) => { storage.set(key, String(value)); },
  removeItem: (key) => { storage.delete(key); },
};

const compiled = await build({
  stdin: {
    contents: `
      export * from './src/features/classroom/mock/classroomMockData.ts';
      export { STORAGE_KEY_CLASSROOMS } from './src/features/classroom/mock/useClassroomStore.ts';
      export * from './src/features/classroom/lessons/mock/lessonMockStore.ts';
    `,
    resolveDir: process.cwd(),
    loader: 'ts',
  },
  alias: { '@': './src' },
  bundle: true,
  platform: 'node',
  format: 'cjs',
  write: false,
  external: ['react', 'axios', 'dompurify'],
});

const module = { exports: {} };
new Function('module', 'exports', 'require', compiled.outputFiles[0].text)(
  module,
  module.exports,
  createRequire(import.meta.url),
);

const {
  INITIAL_MOCK_CLASSROOMS,
  STORAGE_KEY_CLASSROOMS,
  getClassroomLessonSummary,
  getLessonCountForClassroom,
  getSynchronizedClassroom,
  lessonMockStore,
} = module.exports;

test('lessons sync directly with current classroom store including dynamically created and archived classes', async () => {
  storage.clear();

  // Seeded classroom sync: student sees only PUBLISHED lessons; teacher sees all statuses
  assert.equal(getSynchronizedClassroom('cls-algo-2026')?.name, 'Data Structures & Advanced Algorithms');
  assert.equal(getLessonCountForClassroom('cls-algo-2026', 'STUDENT'), 2);
  assert.equal(getLessonCountForClassroom('cls-algo-2026', 'TEACHER'), 4);

  const initialSummary = getClassroomLessonSummary('cls-algo-2026', 'STUDENT', 'student-1');
  assert.equal(initialSummary.publishedCount, 2);
  assert.equal(initialSummary.completedCount, 0);
  assert.equal(initialSummary.nextLesson?.id, 'les-algo-1');

  const blob = lessonMockStore.downloadAttachment('att-seed-algo-notes');
  assert.ok((await blob.text()).includes('Recursion Trees'));

  // Dynamically add a brand-new classroom as Teacher and create/publish a lesson for it
  const dynamicClassroom = {
    ...INITIAL_MOCK_CLASSROOMS[0],
    id: 'cls-dynamic-new',
    name: 'Dynamic Class Test',
    status: 'ACTIVE',
  };
  sessionStorage.setItem(STORAGE_KEY_CLASSROOMS, JSON.stringify([...INITIAL_MOCK_CLASSROOMS, dynamicClassroom]));

  assert.equal(getSynchronizedClassroom('cls-dynamic-new')?.name, 'Dynamic Class Test');
  assert.equal(lessonMockStore.list('cls-dynamic-new', 'TEACHER', 'teacher-1').items.length, 0);

  const created = lessonMockStore.create(
    'cls-dynamic-new',
    {
      title: 'Dynamic Graph Lecture',
      content: '## Intro\nDynamic classroom lesson.',
      codeExample: null,
      externalLinks: [],
      completionMode: 'MANUAL',
      activityIds: [],
      requiredActivityIds: [],
    },
    'teacher-1',
  );
  assert.equal(getLessonCountForClassroom('cls-dynamic-new', 'TEACHER'), 1);
  assert.equal(getLessonCountForClassroom('cls-dynamic-new', 'STUDENT'), 0);

  lessonMockStore.transition('cls-dynamic-new', created.id, 'publish', 'teacher-1');
  assert.equal(getLessonCountForClassroom('cls-dynamic-new', 'STUDENT'), 1);

  const completed = lessonMockStore.complete('cls-dynamic-new', created.id, 'student-1');
  assert.equal(completed.progress?.completed, true);
  assert.equal(getClassroomLessonSummary('cls-dynamic-new', 'STUDENT', 'student-1').completedCount, 1);

  // Archiving the classroom blocks new lesson creation or student completion immediately
  const archivedList = [...INITIAL_MOCK_CLASSROOMS, { ...dynamicClassroom, status: 'ARCHIVED' }];
  sessionStorage.setItem(STORAGE_KEY_CLASSROOMS, JSON.stringify(archivedList));
  assert.throws(
    () => lessonMockStore.complete('cls-dynamic-new', created.id, 'student-1'),
    /archived/i,
  );
});
