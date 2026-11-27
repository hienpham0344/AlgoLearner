import { randomUUID } from 'node:crypto';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Plugin } from 'vite';
import { isOfflineRequest } from './backendFirstPlugin';

interface DemoClassPractice {
  id: string;
  classroomId: string;
  lessonId: string | null;
  title: string;
  description: string;
  problemType: 'STANDARD_IO' | 'FUNCTION_METHOD';
  status: 'PUBLISHED';
  available: boolean;
  allowedLanguages: Array<'JAVA' | 'PYTHON'>;
  starterCode: Record<'JAVA' | 'PYTHON', string>;
  timeLimitMs: number;
  memoryLimitMb: number;
  testcaseVersion: number;
  completionStatus: 'COMPLETED' | 'NOT_COMPLETED';
  sampleTestcases: Array<{ id: string; input: string; expectedOutput: string; orderIndex: number }>;
  createdAt: string;
  updatedAt: string;
}

interface DemoSubmission {
  id: string;
  practiceId: string;
  studentId: string;
  studentUsername: string;
  language: 'JAVA' | 'PYTHON';
  code: string;
  testcaseVersion: number;
  verdict: 'ACCEPTED' | 'WRONG_ANSWER' | 'COMPILATION_ERROR';
  testsPassed: number;
  totalTests: number;
  runtimeMs: number;
  memoryKb: number;
  rejudgeStatus: string | null;
  submittedAt: string;
}

function sendJson(response: ServerResponse, status: number, body: object) {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(JSON.stringify(body));
}

function ok(response: ServerResponse, data: unknown, status = 200) {
  sendJson(response, status, { success: true, data, timestamp: new Date().toISOString() });
}

function fail(response: ServerResponse, status: number, errorCode: string, message: string) {
  sendJson(response, status, { success: false, errorCode, message, timestamp: new Date().toISOString() });
}

function page<T>(items: T[]) {
  return {
    items,
    pagination: {
      page: 0,
      size: 20,
      totalElements: items.length,
      totalPages: items.length ? 1 : 0,
      isFirst: true,
      isLast: true,
    },
  };
}

async function readJson(request: IncomingMessage): Promise<Record<string, unknown>> {
  let raw = '';
  for await (const chunk of request) {
    raw += chunk.toString();
    if (raw.length > 256_000) throw new Error('Payload too large');
  }
  return raw ? (JSON.parse(raw) as Record<string, unknown>) : {};
}

/**
 * Development-only offline fixture for SRS 4.8 (/classrooms/:id/practices) and SRS 4.12 (/problems).
 * Only responds when the backend connection was refused (`isOfflineRequest`), never bundled in production,
 * and never intercepts `/api/v1/classrooms` so existing Classroom offline detection remains intact.
 */
export function practiceDemoPlugin(): Plugin {
  const baseTime = '2026-11-20T08:30:00Z';

  const makeProblemsForClass = (classroomId: string): DemoClassPractice[] => [
    {
      id: 'prac-sliding-window',
      classroomId,
      lessonId: null,
      title: 'Longest Subarray Within Budget',
      description: `Given an array of $n$ positive integers representing execution costs and an integer budget $B$, find the maximum length of a contiguous subarray whose sum of costs is at most $B$.

### Input Format
- First line: two space-separated integers $n$ and $B$ ($1 \\le n \\le 2 \\cdot 10^5$, $1 \\le B \\le 10^9$).
- Second line: $n$ space-separated integers $a_1, a_2, \\dots, a_n$.

### Output Format
Print a single integer — the maximum valid window length.`,
      problemType: 'STANDARD_IO',
      status: 'PUBLISHED',
      available: true,
      allowedLanguages: ['JAVA', 'PYTHON'],
      starterCode: {
        JAVA: `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        if (!sc.hasNextInt()) return;
        int n = sc.nextInt();
        long budget = sc.nextLong();
        long sum = 0;
        int left = 0, best = 0;
        long[] a = new long[n];
        for (int right = 0; right < n; right++) {
            a[right] = sc.nextLong();
            sum += a[right];
            while (sum > budget) sum -= a[left++];
            best = Math.max(best, right - left + 1);
        }
        System.out.println(best);
    }
}
`,
        PYTHON: `import sys


def solve() -> None:
    tokens = sys.stdin.read().split()
    if not tokens:
        return
    n = int(tokens[0])
    budget = int(tokens[1])
    arr = [int(x) for x in tokens[2:2 + n]]
    left = 0
    total = 0
    best = 0
    for right, val in enumerate(arr):
        total += val
        while total > budget:
            total -= arr[left]
            left += 1
        best = max(best, right - left + 1)
    print(best)


if __name__ == "__main__":
    solve()
`,
      },
      timeLimitMs: 1500,
      memoryLimitMb: 256,
      testcaseVersion: 1,
      completionStatus: 'NOT_COMPLETED',
      sampleTestcases: [
        { id: 's-1', input: '5 8\n3 1 2 7 4', expectedOutput: '3', orderIndex: 0 },
        { id: 's-2', input: '4 3\n5 6 7 8', expectedOutput: '0', orderIndex: 1 },
      ],
      createdAt: baseTime,
      updatedAt: baseTime,
    },
    {
      id: 'prac-two-sum-sorted',
      classroomId,
      lessonId: null,
      title: 'Pair Sum in Monotonic Sequence',
      description: `Given a non-decreasing array of $n$ integers and a target integer $T$, print 1-based indices \`i j\` ($i < j$) such that $a_i + a_j = T$, or \`-1\` if no valid pair exists.

### Constraints
- $2 \\le n \\le 10^5$
- $-10^9 \\le a_i, T \\le 10^9$`,
      problemType: 'STANDARD_IO',
      status: 'PUBLISHED',
      available: true,
      allowedLanguages: ['JAVA', 'PYTHON'],
      starterCode: {
        JAVA: `import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Two-pointer scan
    }
}
`,
        PYTHON: `import sys


def solve() -> None:
    # Two-pointer scan
    pass


if __name__ == "__main__":
    solve()
`,
      },
      timeLimitMs: 1000,
      memoryLimitMb: 256,
      testcaseVersion: 1,
      completionStatus: 'COMPLETED',
      sampleTestcases: [
        { id: 's-3', input: '4 9\n2 7 11 15', expectedOutput: '1 2', orderIndex: 0 },
      ],
      createdAt: baseTime,
      updatedAt: baseTime,
    },
    {
      id: 'prac-bracket-validator',
      classroomId,
      lessonId: null,
      title: 'Balanced Delimiter Depth',
      description: `Implement a function-style solution that reads a bracket string consisting of \`()\`, \`[]\`, and \`{}\` and outputs the maximum nesting depth if valid, or \`-1\` if unbalanced.`,
      problemType: 'FUNCTION_METHOD',
      status: 'PUBLISHED',
      available: true,
      allowedLanguages: ['JAVA', 'PYTHON'],
      starterCode: {
        JAVA: `import java.util.*;

public class Main {
    public static int maxDepth(String s) {
        return 0;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String line = sc.hasNextLine() ? sc.nextLine().trim() : "";
        System.out.println(maxDepth(line));
    }
}
`,
        PYTHON: `import sys


def max_depth(s: str) -> int:
    return 0


if __name__ == "__main__":
    print(max_depth(sys.stdin.read().strip()))
`,
      },
      timeLimitMs: 1000,
      memoryLimitMb: 128,
      testcaseVersion: 2,
      completionStatus: 'NOT_COMPLETED',
      sampleTestcases: [
        { id: 's-4', input: '{[()]}', expectedOutput: '3', orderIndex: 0 },
        { id: 's-5', input: '([)]', expectedOutput: '-1', orderIndex: 1 },
      ],
      createdAt: baseTime,
      updatedAt: baseTime,
    },
  ];

  const byClassroom = new Map<string, DemoClassPractice[]>();
  const submissionsByPractice = new Map<string, DemoSubmission[]>([
    [
      'prac-two-sum-sorted',
      [
        {
          id: 'sub-demo-initial',
          practiceId: 'prac-two-sum-sorted',
          studentId: 'offline-student',
          studentUsername: 'learner',
          language: 'PYTHON',
          code: 'import sys\n\ndata = list(map(int, sys.stdin.read().split()))\nn, target = data[0], data[1]\narr = data[2:]\nl, r = 0, n - 1\nwhile l < r:\n    s = arr[l] + arr[r]\n    if s == target:\n        print(l + 1, r + 1)\n        break\n    elif s < target:\n        l += 1\n    else:\n        r -= 1\nelse:\n    print(-1)\n',
          testcaseVersion: 1,
          verdict: 'ACCEPTED',
          testsPassed: 8,
          totalTests: 8,
          runtimeMs: 31,
          memoryKb: 14336,
          rejudgeStatus: null,
          submittedAt: baseTime,
        },
      ],
    ],
  ]);

  const getClassList = (classroomId: string): DemoClassPractice[] => {
    const found = byClassroom.get(classroomId);
    if (found) return found;
    const seeded = makeProblemsForClass(classroomId);
    byClassroom.set(classroomId, seeded);
    return seeded;
  };

  return {
    name: 'algolearn-student-practice-demo',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use(async (request, response, next) => {
        if (!isOfflineRequest(request)) {
          next();
          return;
        }
        const route = request.url?.split('?')[0] ?? '';
        const method = request.method ?? 'GET';

        // SRS 4.8 Classroom Practice endpoints
        const listMatch = route.match(/^\/api\/v1\/classrooms\/([^/]+)\/practices$/);
        if (listMatch && method === 'GET') {
          const classroomId = decodeURIComponent(listMatch[1]);
          const items = getClassList(classroomId).map(({ description: _d, starterCode: _s, sampleTestcases: _tc, testcaseVersion: _v, ...summary }) => summary);
          ok(response, page(items));
          return;
        }

        const detailMatch = route.match(/^\/api\/v1\/classrooms\/([^/]+)\/practices\/([^/]+)$/);
        if (detailMatch && method === 'GET') {
          const classroomId = decodeURIComponent(detailMatch[1]);
          const practiceId = decodeURIComponent(detailMatch[2]);
          const found = getClassList(classroomId).find(p => p.id === practiceId);
          if (!found) {
            fail(response, 404, 'PRACTICE_NOT_FOUND', 'Practice problem not found in this classroom.');
            return;
          }
          ok(response, found);
          return;
        }

        const runMatch = route.match(/^\/api\/v1\/classrooms\/([^/]+)\/practices\/([^/]+)\/run$/);
        if (runMatch && method === 'POST') {
          const classroomId = decodeURIComponent(runMatch[1]);
          const practiceId = decodeURIComponent(runMatch[2]);
          const found = getClassList(classroomId).find(p => p.id === practiceId);
          if (!found) {
            fail(response, 404, 'PRACTICE_NOT_FOUND', 'Practice problem not found.');
            return;
          }
          const body = await readJson(request);
          const code = String(body.sourceCode ?? '');
          const customInput = typeof body.customInput === 'string' && body.customInput.length > 0 ? body.customInput : null;

          if (code.includes('SYNTAX_ERR')) {
            ok(response, {
              verdict: 'COMPILATION_ERROR',
              cases: [
                {
                  index: 0,
                  input: customInput ?? found.sampleTestcases[0]?.input ?? '',
                  expectedOutput: customInput ? '' : found.sampleTestcases[0]?.expectedOutput ?? '',
                  verdict: 'COMPILATION_ERROR',
                  stdout: '',
                  stderr: 'Main.java:8: error: \';\' expected\n        SYNTAX_ERR\n                  ^\n1 error',
                  runtimeMs: null,
                },
              ],
            });
            return;
          }

          const isPassing = code.includes('best') || code.includes('print') || code.includes('System.out');
          const verdict = isPassing ? 'ACCEPTED' : 'WRONG_ANSWER';
          const cases = customInput
            ? [
                {
                  index: 0,
                  input: customInput,
                  expectedOutput: '(custom input run)',
                  verdict: 'ACCEPTED' as const,
                  stdout: '3',
                  stderr: '',
                  runtimeMs: 14,
                },
              ]
            : found.sampleTestcases.map((tc, index) => ({
                index,
                input: tc.input,
                expectedOutput: tc.expectedOutput,
                verdict: (isPassing ? 'ACCEPTED' : 'WRONG_ANSWER') as 'ACCEPTED' | 'WRONG_ANSWER',
                stdout: isPassing ? tc.expectedOutput : '0',
                stderr: '',
                runtimeMs: 12 + index * 4,
              }));

          ok(response, { verdict, cases });
          return;
        }

        const submitMatch = route.match(/^\/api\/v1\/classrooms\/([^/]+)\/practices\/([^/]+)\/submit$/);
        if (submitMatch && method === 'POST') {
          const classroomId = decodeURIComponent(submitMatch[1]);
          const practiceId = decodeURIComponent(submitMatch[2]);
          const found = getClassList(classroomId).find(p => p.id === practiceId);
          if (!found) {
            fail(response, 404, 'PRACTICE_NOT_FOUND', 'Practice problem not found.');
            return;
          }
          const body = await readJson(request);
          const language = body.language === 'PYTHON' ? 'PYTHON' : 'JAVA';
          const code = String(body.sourceCode ?? '');
          const isCompilationError = code.includes('SYNTAX_ERR');
          const isAccepted = !isCompilationError && (code.includes('while') || code.includes('best') || code.includes('maxDepth') || code.includes('max_depth'));
          const verdict = isCompilationError ? 'COMPILATION_ERROR' : isAccepted ? 'ACCEPTED' : 'WRONG_ANSWER';
          const totalTests = 10;
          const testsPassed = isCompilationError ? 0 : isAccepted ? 10 : 6;

          if (isAccepted) {
            found.completionStatus = 'COMPLETED';
          }

          const created: DemoSubmission = {
            id: `sub-${randomUUID().slice(0, 8)}`,
            practiceId: found.id,
            studentId: 'offline-student',
            studentUsername: 'learner',
            language,
            code,
            testcaseVersion: found.testcaseVersion,
            verdict,
            testsPassed,
            totalTests,
            runtimeMs: isCompilationError ? 0 : 22,
            memoryKb: isCompilationError ? 0 : 15872,
            rejudgeStatus: null,
            submittedAt: new Date().toISOString(),
          };
          const list = submissionsByPractice.get(found.id) ?? [];
          list.unshift(created);
          submissionsByPractice.set(found.id, list);
          ok(response, created, 201);
          return;
        }

        const historyMatch = route.match(/^\/api\/v1\/classrooms\/([^/]+)\/practices\/([^/]+)\/submissions\/me$/);
        if (historyMatch && method === 'GET') {
          const practiceId = decodeURIComponent(historyMatch[2]);
          ok(response, page(submissionsByPractice.get(practiceId) ?? []));
          return;
        }

        const singleSubMatch = route.match(/^\/api\/v1\/classrooms\/([^/]+)\/practices\/([^/]+)\/submissions\/([^/]+)$/);
        if (singleSubMatch && method === 'GET') {
          const practiceId = decodeURIComponent(singleSubMatch[2]);
          const subId = decodeURIComponent(singleSubMatch[3]);
          const sub = (submissionsByPractice.get(practiceId) ?? []).find(s => s.id === subId);
          if (!sub) {
            fail(response, 404, 'SUBMISSION_NOT_FOUND', 'Submission not found.');
            return;
          }
          ok(response, sub);
          return;
        }

        // SRS 4.12 Public Practice read endpoints for offline preview
        if (route === '/api/v1/problems' && method === 'GET') {
          ok(
            response,
            page([
              { id: 'pub-interval-merge', title: 'Disjoint Interval Consolidation', status: 'PUBLISHED', createdAt: baseTime },
              { id: 'pub-shortest-path', title: 'Unweighted Grid Shortest Path', status: 'PUBLISHED', createdAt: baseTime },
            ]),
          );
          return;
        }

        if (route === '/api/v1/problems/statistics/me' && method === 'GET') {
          ok(response, { solved: 1, currentStreak: 2, highestStreak: 5 });
          return;
        }

        const pubDetail = route.match(/^\/api\/v1\/problems\/([^/]+)$/);
        if (pubDetail && method === 'GET') {
          const id = decodeURIComponent(pubDetail[1]);
          ok(response, {
            id,
            revisionId: 'rev-pub-1',
            title: id === 'pub-shortest-path' ? 'Unweighted Grid Shortest Path' : 'Disjoint Interval Consolidation',
            statement:
              id === 'pub-shortest-path'
                ? 'Given an $R \\times C$ binary grid where `0` denotes open cells and `1` denotes walls, output the minimum steps from $(0,0)$ to $(R-1,C-1)$ moving in 4 cardinal directions, or `-1` if unreachable.'
                : 'Given $n$ closed intervals $[l_i, r_i]$, merge all overlapping intervals and print the resulting non-overlapping intervals in ascending order.',
            languages: ['JAVA', 'PYTHON'],
            samples: id === 'pub-shortest-path'
              ? [{ input: '3 3\n0 0 1\n1 0 0\n1 0 0', expectedOutput: '4', sample: true, allowDetails: false }]
              : [{ input: '4\n1 3\n2 6\n8 10\n15 18', expectedOutput: '1 6\n8 10\n15 18', sample: true, allowDetails: false }],
            limits: { compileSeconds: 10, runtimeMillis: 2000, outputBytes: 65536 },
            status: 'PUBLISHED',
          });
          return;
        }

        const pubRun = route.match(/^\/api\/v1\/problems\/([^/]+)\/(run|submissions)$/);
        if (pubRun && method === 'POST') {
          ok(response, 'job-demo-1', pubRun[2] === 'submissions' ? 201 : 200);
          return;
        }
        if (pubRun && method === 'GET') {
          ok(response, page([]));
          return;
        }

        const pubJob = route.match(/^\/api\/v1\/problems\/([^/]+)\/jobs\/([^/]+)$/);
        if (pubJob && method === 'GET') {
          const gridProblem = decodeURIComponent(pubJob[1]) === 'pub-shortest-path';
          ok(response, {
            jobId: pubJob[2],
            generation: 1,
            verdict: 'ACCEPTED',
            passed: 1,
            total: 1,
            cases: [
              {
                index: 0,
                verdict: 'ACCEPTED',
                input: gridProblem ? '3 3\n0 0 1\n1 0 0\n1 0 0' : '4\n1 3\n2 6\n8 10\n15 18',
                expectedOutput: gridProblem ? '4' : '1 6\n8 10\n15 18',
                actualOutput: gridProblem ? '4' : '1 6\n8 10\n15 18',
              },
            ],
          });
          return;
        }

        next();
      });
    },
  };
}

