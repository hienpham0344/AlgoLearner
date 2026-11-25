import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';

const outputDir = new URL('../test-results/', import.meta.url);
const directories = (await readdir(outputDir, { withFileTypes: true })).filter(entry => entry.isDirectory());
const modules = new Map();
let reports = 0;

for (const directory of directories) {
  let entries;
  try {
    entries = JSON.parse(await readFile(join(outputDir.pathname, directory.name, 'source-coverage.json'), 'utf8'));
  } catch (error) {
    if (error?.code === 'ENOENT') continue;
    throw error;
  }
  reports++;
  for (const entry of entries) {
    if (!entry.path.startsWith('/src/')) continue;
    const functions = modules.get(entry.path) ?? new Map();
    for (const fn of entry.functions) {
      const key = `${fn.start}:${fn.end}`;
      functions.set(key, Boolean(fn.hit) || functions.get(key) === true);
    }
    modules.set(entry.path, functions);
  }
}

if (reports === 0) throw new Error('No Playwright coverage reports found. Run npm run test:e2e first.');
const total = [...modules.values()].reduce((sum, functions) => sum + functions.size, 0);
const covered = [...modules.values()].reduce((sum, functions) => sum + [...functions.values()].filter(Boolean).length, 0);
console.log(`Playwright V8 function coverage: ${covered}/${total} (${(100 * covered / total).toFixed(2)}%) across ${modules.size} loaded frontend/src modules and ${reports} browser scenarios.`);
console.log('This measures executed functions, not line or branch coverage. Mocked HTTP responses do not verify Spring Boot runtime.');
