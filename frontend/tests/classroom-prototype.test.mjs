import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const mockDataPath = new URL('../src/features/classroom/mock/classroomMockData.ts', import.meta.url);
const source = await readFile(mockDataPath, 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext },
});
const { DEMO_PERSONAS, INITIAL_MOCK_CLASSROOMS } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('DEMO_PERSONAS contains valid Teachers, Students, and Admins', () => {
  const teachers = DEMO_PERSONAS.filter(p => p.role === 'TEACHER');
  const students = DEMO_PERSONAS.filter(p => p.role === 'STUDENT');
  const admins = DEMO_PERSONAS.filter(p => p.role === 'ADMIN');

  assert.ok(teachers.length >= 2, 'Must have at least 2 demo teachers for transfer flow');
  assert.ok(students.length >= 3, 'Must have diverse demo students');
  assert.equal(admins.length, 1, 'Must have platform admin');

  // Verify unique IDs and emails
  const ids = new Set(DEMO_PERSONAS.map(p => p.id));
  assert.equal(ids.size, DEMO_PERSONAS.length, 'All personas must have unique IDs');
});

test('INITIAL_MOCK_CLASSROOMS adheres to SRS Module 4.3 rules', () => {
  assert.ok(INITIAL_MOCK_CLASSROOMS.length >= 4, 'Must have diverse seed classrooms');

  const teacherIds = new Set(
    DEMO_PERSONAS.filter(p => p.role === 'TEACHER').map(t => t.id)
  );

  for (const cls of INITIAL_MOCK_CLASSROOMS) {
    // BR-CLS-002: Single Teacher Owner
    assert.ok(
      teacherIds.has(cls.ownerId),
      `Classroom ${cls.id} owner must be a valid Teacher, got ${cls.ownerId}`
    );

    // BR-CLS-004: Status must be ACTIVE or ARCHIVED
    assert.ok(
      cls.status === 'ACTIVE' || cls.status === 'ARCHIVED',
      `Classroom ${cls.id} has invalid status ${cls.status}`
    );

    // BR-CLS-006: Unique 8-character Class Code
    assert.equal(cls.classCode.length, 8, `Class code ${cls.classCode} must be 8 chars`);
    assert.match(cls.classCode, /^[A-Z0-9]{8}$/, `Code ${cls.classCode} must be uppercase alphanumeric`);
  }

  // Verify class codes are globally unique
  const codes = new Set(INITIAL_MOCK_CLASSROOMS.map(c => c.classCode));
  assert.equal(codes.size, INITIAL_MOCK_CLASSROOMS.length, 'All class codes must be unique');
});

test('Demonstrates code security conditions: locked and expired codes', () => {
  const lockedClass = INITIAL_MOCK_CLASSROOMS.find(c => c.codeLocked);
  assert.ok(lockedClass, 'Must include a classroom with codeLocked=true for testing BR-CLS-007');

  const expiredClass = INITIAL_MOCK_CLASSROOMS.find(
    c => c.codeExpiresAt && new Date(c.codeExpiresAt).getTime() < Date.now()
  );
  assert.ok(expiredClass, 'Must include an expired class code for testing BR-CLS-007');

  const archivedClass = INITIAL_MOCK_CLASSROOMS.find(c => c.status === 'ARCHIVED');
  assert.ok(archivedClass, 'Must include an ARCHIVED classroom for testing BR-CLS-004');
});

test('Demonstrates enrollment modes: AUTO_JOIN and REQUIRE_APPROVAL', () => {
  const autoJoinClass = INITIAL_MOCK_CLASSROOMS.find(c => c.joinMode === 'AUTO_JOIN');
  assert.ok(autoJoinClass, 'Must include AUTO_JOIN classroom (BR-CLS-009)');

  const reqApprovalClass = INITIAL_MOCK_CLASSROOMS.find(c => c.joinMode === 'REQUIRE_APPROVAL');
  assert.ok(reqApprovalClass, 'Must include REQUIRE_APPROVAL classroom (BR-CLS-009)');

  // Verify pending join request exists in require approval class
  assert.ok(
    reqApprovalClass.joinRequests.some(r => r.status === 'PENDING_APPROVAL'),
    'Must include at least one pending join request for teacher review verification'
  );
});

test('Demonstrates student rejoining: student with status LEFT preserved', () => {
  const classWithLeftStudent = INITIAL_MOCK_CLASSROOMS.find(c =>
    c.members.some(m => m.status === 'LEFT')
  );
  assert.ok(
    classWithLeftStudent,
    'Must include a student who previously left for testing rejoining (BR-CLS-012)'
  );
});

test('Segmented 8-character code validation rules', () => {
  // Test code cleaning and normalization
  const cleanCode = (input) => input.toUpperCase().replace(/[^A-Z0-9]/g, '');
  
  assert.equal(cleanCode('algo2026'), 'ALGO2026', 'Normalizes lowercase to uppercase');
  assert.equal(cleanCode('ALGO-2026!'), 'ALGO2026', 'Rejects non-alphanumeric punctuation');
  assert.equal(cleanCode('a l g o 2 0 2 6'), 'ALGO2026', 'Rejects spaces');
  assert.equal(cleanCode('cplab26x'), 'CPLAB26X', 'Normalizes valid code');
  
  // Test 8-character completeness verification
  const isComplete = (chars) => chars.length === 8 && chars.every(c => c.length === 1 && /[A-Z0-9]/.test(c));
  assert.equal(isComplete(['A', 'L', 'G', 'O', '2', '0', '2', '6']), true, 'Full 8 valid chars is complete');
  assert.equal(isComplete(['A', 'L', 'G', 'O', '2', '0', '2', '']), false, '7 chars is incomplete');
  assert.equal(isComplete(['', '', '', '', '', '', '', '']), false, 'Empty is incomplete');
});
