import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const navPath = new URL('../src/components/layout/workspaceNav.ts', import.meta.url);
const source = await readFile(navPath, 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext },
});
const { getWorkspaceNavItems } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('student taskbar excludes standalone Class lessons link and keeps Public courses inside workspace', () => {
  const onDashboard = getWorkspaceNavItems('STUDENT', '/dashboard');
  const dashboardMap = Object.fromEntries(onDashboard.map((item) => [item.label, item]));

  assert.equal('Class lessons' in dashboardMap, false);
  assert.equal(dashboardMap['Dashboard'].isCurrent, true);
  assert.equal(dashboardMap['My classes'].href, '/student/classrooms');
  assert.equal(dashboardMap['Public courses'].href, '/student/courses');
  assert.equal(dashboardMap['Coding practice'].href, '/practice');
  assert.equal(dashboardMap['Notifications'].href, '/notifications');
  assert.equal(dashboardMap['My profile'].href, '/profile');

  const onClassroomLessons = getWorkspaceNavItems('STUDENT', '/student/classrooms/cls-algo-2026/lessons');
  const lessonsMap = Object.fromEntries(onClassroomLessons.map((item) => [item.label, item]));
  assert.equal(lessonsMap['My classes'].isCurrent, true);
});

test('teacher and admin taskbars exclude standalone Class lessons and Practice links', () => {
  const teacherLessons = getWorkspaceNavItems('TEACHER', '/teacher/classrooms/cls-algo-2026/lessons');
  assert.equal(teacherLessons.some((item) => /practice|class lessons/i.test(item.label)), false);
  const teacherMap = Object.fromEntries(teacherLessons.map((item) => [item.label, item]));
  assert.equal(teacherMap['My classes'].isCurrent, true);

  const adminClassrooms = getWorkspaceNavItems('ADMIN', '/admin/classrooms');
  assert.equal(adminClassrooms.some((item) => /practice|class lessons/i.test(item.label)), false);
  const adminMap = Object.fromEntries(adminClassrooms.map((item) => [item.label, item]));
  assert.equal(adminMap['Classrooms'].isCurrent, true);
  assert.equal(adminMap['Account security'].href, '/account/security');
});
