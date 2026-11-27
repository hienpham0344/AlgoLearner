import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

async function transpile(path, dependencyUrl) {
  let source = await readFile(new URL(path, import.meta.url), 'utf8');
  if (dependencyUrl) source = source.replace("'./patternCore'", `'${dependencyUrl}'`);
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } });
  return `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
}

const coreUrl = await transpile('../src/features/practice/components/generative-patterns/patternCore.ts');
const core = await import(coreUrl);
const geometry = await import(await transpile('../src/features/practice/components/generative-patterns/patternGeometry.ts', coreUrl));

test('AGPS resolves domain conservatively in English and Vietnamese', () => {
  assert.equal(core.resolvePatternFamily({ id: '1', name: 'Data Structures & Advanced Algorithms' }), 'tree');
  assert.equal(core.resolvePatternFamily({ id: '2', name: 'Full-Stack Software Engineering' }), 'architecture');
  assert.equal(core.resolvePatternFamily({ id: '3', name: 'Cơ sở dữ liệu quan hệ' }), 'relational-matrix');
  assert.equal(core.resolvePatternFamily({ id: '4', name: 'Trí tuệ nhân tạo' }), 'neural-network');
  assert.equal(core.resolvePatternFamily({ id: '5', name: 'Mạng máy tính' }), 'topology');
  assert.equal(core.resolvePatternFamily({ id: '5a', name: 'Đồ họa máy tính' }), 'wireframe');
  assert.equal(core.resolvePatternFamily({ id: '6', name: 'Applied History' }), 'universal');
  assert.equal(core.resolvePatternFamily({ id: '7', name: 'General Practice', subject: 'Computer Graphics' }), 'wireframe');
  assert.equal(core.resolvePatternFamily({ id: '8', name: 'Algorithms', visualFamily: 'viewport' }), 'viewport');
});

test('AGPS classroom identity is stable and independent from collection order', () => {
  const classrooms = [
    { id: 'cls-algo-2026', name: 'Data Structures & Advanced Algorithms' },
    { id: 'cls-web-eng', name: 'Full-Stack Software Engineering' },
    { id: 'unknown-9', name: 'Seminar Topics' },
  ];
  const before = new Map(classrooms.map(item => [item.id, core.resolvePattern(item)]));
  classrooms.reverse().forEach(item => assert.deepEqual(core.resolvePattern(item), before.get(item.id)));
  assert.equal(before.get('cls-algo-2026').surface, 'dark');
  assert.equal(before.get('cls-web-eng').surface, 'light');
  assert.notEqual(core.hashClassroomId('class-A'), core.hashClassroomId('class-B'));
});

test('every registered family produces bounded geometry and multiple variants', () => {
  for (const family of Object.keys(core.patternRegistry)) {
    const base = { seed: 12345, family, variant: 0, surface: 'dark', density: .55, complexity: .55, motionEnabled: true };
    const first = geometry.generatePatternGeometry(base);
    const again = geometry.generatePatternGeometry(base);
    const variant = geometry.generatePatternGeometry({ ...base, variant: 3 });
    assert.deepEqual(first, again, family);
    assert.notDeepEqual(first, variant, family);
    assert.ok(first.lines.length + first.circles.length + first.rects.length + first.paths.length > 0, family);
    assert.ok(first.lines.length <= 100 && first.circles.length <= 50 && first.rects.length <= 90 && first.paths.length <= 35, family);
    for (const line of first.lines) assert.ok([line.x1,line.x2].every(x => x >= 0 && x <= 520) && [line.y1,line.y2].every(y => y >= 0 && y <= 170), family);
    for (const circle of first.circles) assert.ok(circle.cx >= 0 && circle.cx <= 520 && circle.cy >= 0 && circle.cy <= 170, family);
    for (const rect of first.rects) assert.ok(rect.x >= 0 && rect.x + rect.width <= 520 && rect.y >= 0 && rect.y + rect.height <= 170, family);
  }
});

test('100 classroom patterns generate without unbounded work', () => {
  const started = performance.now();
  for (let index = 0; index < 100; index++) {
    const config = core.resolvePattern({ id: `class-${index}`, name: index % 2 ? 'Machine Learning' : 'Database Systems' });
    geometry.generatePatternGeometry(config);
  }
  assert.ok(performance.now() - started < 1000);
});
