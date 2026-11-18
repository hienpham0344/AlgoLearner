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

test('student taskbar preserves separate flows and keeps Public courses inside workspace', () => {
  const onDashboard = getWorkspaceNavItems('STUDENT', '/dashboard');
  const dashboardMap = Object.fromEntries(onDashboard.map((item) => [item.label, item]));

  assert.equal(dashboardMap['Dashboard'].isCurrent, true);
  assert.equal(dashboardMap['My classes'].href, '/student/classrooms');
  assert.equal(dashboardMap['Class lessons'].href, '#lessons');
  assert.equal(dashboardMap['Public courses'].href, '/student/courses');
  assert.equal(dashboardMap['Coding practice'].href, '/practice');
  assert.equal(dashboardMap['Notifications'].href, '/notifications');
  assert.equal(dashboardMap['My profile'].href, '/profile');

  const onClassroomDetail = getWorkspaceNavItems('STUDENT', '/student/classrooms/demo-class-1', 'demo-class-1');
  const detailMap = Object.fromEntries(onClassroomDetail.map((item) => [item.label, item]));
  assert.equal(detailMap['My classes'].isCurrent, true);
  assert.equal(detailMap['Class lessons'].href, '/student/classrooms/demo-class-1/lessons');

  const onProfile = getWorkspaceNavItems('STUDENT', '/profile');
  const profileMap = Object.fromEntries(onProfile.map((item) => [item.label, item]));
  assert.equal(profileMap['My profile'].isCurrent, true);
  assert.equal(profileMap['Class lessons'].href, '/student/lessons');
});

test('teacher and admin taskbars never include student Practice link and highlight active routes', () => {
  const teacherClassroom = getWorkspaceNavItems('TEACHER', '/teacher/classrooms/demo-class-1/lessons', 'demo-class-1');
  assert.equal(teacherClassroom.some((item) => /practice/i.test(item.label)), false);
  const teacherMap = Object.fromEntries(teacherClassroom.map((item) => [item.label, item]));
  assert.equal(teacherMap['Class lessons'].isCurrent, true);
  assert.equal(teacherMap['My classes'].isCurrent, false);

  const adminClassrooms = getWorkspaceNavItems('ADMIN', '/admin/classrooms');
  assert.equal(adminClassrooms.some((item) => /practice/i.test(item.label)), false);
  const adminMap = Object.fromEntries(adminClassrooms.map((item) => [item.label, item]));
  assert.equal(adminMap['Classrooms'].isCurrent, true);
  assert.equal(adminMap['Account security'].href, '/account/security');
});

