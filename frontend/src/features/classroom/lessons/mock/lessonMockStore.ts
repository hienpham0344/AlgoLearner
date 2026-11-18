import { readStoredClassrooms } from '../../mock/useClassroomStore';
import type { Classroom } from '../../types';
import { validateAttachment, validateLesson } from '../lessonRules';
import type {
  Lesson,
  LessonActivity,
  LessonAttachment,
  LessonCapabilities,
  LessonInput,
  LessonProgress,
  LessonRole,
  StudentLessonProgress,
} from '../types';
import type { PaginatedData } from '@/types/api';

const STORAGE_KEY_LESSONS = 'algolearn_prototype_lessons_v4';
const STORAGE_KEY_LESSON_PROGRESS = 'algolearn_prototype_lesson_progress_v2';
const STORAGE_KEY_ATTACHMENTS = 'algolearn_prototype_lesson_files_v2';

const DEMO_POLICY: LessonCapabilities = {
  uploadsAvailable: true,
  activityLinkingAvailable: true,
  maxFileSizeBytes: 10 * 1024 * 1024,
  allowedExtensions: ['pdf', 'docx', 'pptx', 'zip', 'png', 'jpg', 'jpeg', 'gif', 'webp'],
};

const SEED_ACTIVITIES_BY_CLASS: Record<string, LessonActivity[]> = {
  'cls-algo-2026': [
    {
      id: 'act-algo-quiz-1',
      type: 'QUIZ',
      title: 'Asymptotic Analysis & Recurrence Check-in',
      linkedLessonId: 'les-algo-2',
      available: true,
      path: null,
      completed: false,
    },
    {
      id: 'act-algo-assign-1',
      type: 'ASSIGNMENT',
      title: 'Network Routing with Dynamic Latency',
      linkedLessonId: null,
      available: true,
      path: null,
      completed: false,
    },
    {
      id: 'act-algo-prac-1',
      type: 'PRACTICE',
      title: 'Topological Sort & Cycle Detection',
      linkedLessonId: null,
      available: true,
      path: null,
      completed: false,
    },
  ],
  'cls-cp-lab': [
    {
      id: 'act-cp-quiz-1',
      type: 'QUIZ',
      title: 'Range Query Complexity Drill',
      linkedLessonId: null,
      available: true,
      path: null,
      completed: false,
    },
  ],
  'cls-web-eng': [
    {
      id: 'act-web-assign-1',
      type: 'ASSIGNMENT',
      title: 'REST API Validation & JWT Guard Lab',
      linkedLessonId: null,
      available: true,
      path: null,
      completed: false,
    },
  ],
};

function makeSeedLesson(
  id: string,
  classroomId: string,
  title: string,
  status: Lesson['status'],
  content: string,
  codeExample: Lesson['codeExample'],
  overrides: Partial<Lesson> = {},
): Lesson {
  return {
    id,
    classroomId,
    title,
    status,
    previousStatus: status === 'ARCHIVED' ? 'PUBLISHED' : null,
    content,
    codeExample,
    externalLinks: [
      {
        label: 'Visualizing Data Structures & Algorithms',
        url: 'https://visualgo.net/en',
      },
    ],
    completionMode: 'MANUAL',
    requiredActivityIds: [],
    activities: [],
    attachments: [],
    progress: null,
    canDelete: true,
    createdAt: '2026-10-10T08:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  };
}

export const INITIAL_MOCK_LESSONS: Lesson[] = [
  makeSeedLesson(
    'les-algo-1',
    'cls-algo-2026',
    'Asymptotic Complexity & Recursion Trees',
    'PUBLISHED',
    `## Analyzing Recursive Work
Every recursive algorithm splits input into smaller subproblems and combines their answers. Before guessing the running time, isolate the **branching factor**, the **shrinking factor**, and the **non-recursive combine work** at each call.

1. Write the **recurrence relation** $T(n)$.
2. Sketch the **recursion tree** level by level.
3. Sum work across levels or apply the Master Theorem.

> Always verify your base case complexity before summing across all tree levels.

## Recursion Tree Method
Suppose Merge Sort splits an array of size $n$ into $2$ halves and takes $c \\cdot n$ steps to merge them:

- **Level 0 (Root)**: 1 node of size $n$, cost $= c \\cdot n$
- **Level 1**: 2 nodes of size $\\frac{n}{2}$, total cost $= 2 \\cdot c \\cdot \\frac{n}{2} = c \\cdot n$
- **Level $k$**: $2^k$ nodes of size $\\frac{n}{2^k}$, total cost $= c \\cdot n$

Because the subproblem size halves at every step, the tree stops at depth $h = \\log_2(n)$, giving $\\log_2(n) + 1$ levels and total cost $\\Theta(n \\log n)$.

### Common Pitfalls When Summing Levels
- Do not forget the **leaf level**: when branching factor $a > 1$, leaves can dominate the total work (e.g. $T(n) = 3T(n/2) + n$ is dominated by $n^{\\log_2 3}$).
- Check whether subproblems shrink proportionally ($\\frac{n}{b}$) or additively ($n - 1$). Additive reduction creates a linear chain of depth $n$, not $\\log n$.

## Master Theorem Summary
For recurrences of the form:

$$T(n) = aT\\!\\left(\\frac{n}{b}\\right) + f(n), \\quad a \\ge 1,\\ b > 1$$

compare $f(n)$ against the watershed function $n^{\\log_b a}$:

| Case | Condition | Asymptotic Bound | Example |
| :--- | :--- | :--- | :--- |
| Case 1 | $f(n)$ grows polynomially slower | $\\Theta(n^{\\log_b a})$ | $T(n) = 4T(n/2) + n \\implies \\Theta(n^2)$ |
| Case 2 | $f(n)$ matches $n^{\\log_b a} \\log^k n$ | $\\Theta(n^{\\log_b a} \\log^{k+1} n)$ | Merge Sort $\\implies \\Theta(n \\log n)$ |
| Case 3 | $f(n)$ grows polynomially faster | $\\Theta(f(n))$ | $T(n) = 2T(n/2) + n^2 \\implies \\Theta(n^2)$ |

## Practical Checklist Before Submission
Before submitting solutions to recursive graph or divide-and-conquer problems, double-check:
- Does every recursive branch strictly make progress toward a reachable base case?
- Is the call stack depth safe under the problem constraints (e.g. Python default recursion limit of 1,000 frames)?
- Can repeated subproblems be memoized to prevent exponential blowup?`,
    {
      language: 'python',
      code: 'def merge_sort(arr):\n    if len(arr) <= 1:\n        return arr\n    mid = len(arr) // 2\n    left = merge_sort(arr[:mid])\n    right = merge_sort(arr[mid:])\n    return merge(left, right)',
    },
    {
      attachments: [
        {
          id: 'att-seed-algo-notes',
          name: 'lecture-01-recursion-trees-notes.pdf',
          size: 245760,
          contentType: 'text/plain; charset=utf-8',
        },
        {
          id: 'att-seed-master-theorem',
          name: 'master-theorem-cheatsheet.png',
          size: 118400,
          contentType: 'text/plain; charset=utf-8',
        },
      ],
    },
  ),
  makeSeedLesson(
    'les-algo-2',
    'cls-algo-2026',
    'Shortest Paths: Dijkstra & Bellman-Ford',
    'PUBLISHED',
    `## Relaxation in Weighted Graphs
Shortest-path algorithms maintain an upper-bound distance estimate \`dist[v]\` for every vertex and repeatedly improve it via **edge relaxation**: if \`dist[u] + w(u, v) < dist[v]\`, update \`dist[v]\`.

## Dijkstra's Greedy Strategy
When all edge weights are non-negative ($w \\ge 0$), Dijkstra greedily finalizes the nearest unvisited vertex using a min-heap priority queue:

- Use **adjacency lists** ($\\mathcal{O}(V + E)$) rather than dense $\\mathcal{O}(V^2)$ matrices.
- Discard stale priority queue entries when \`d > dist[u]\` instead of implementing decrease-key.
- Watch for 64-bit integer overflow when path weights sum past $2^{31} - 1$.

## Handling Negative Weights with Bellman-Ford
If edges can carry negative weights, Dijkstra's greedy invariant breaks. Bellman-Ford relaxes **all $E$ edges** $V - 1$ times in $\\mathcal{O}(V \\cdot E)$ time. A final pass on iteration $V$ detects negative-weight cycles if any distance can still be reduced.`,
    {
      language: 'python',
      code: 'import heapq\ndef dijkstra(adj, start):\n    dist = {start: 0}\n    pq = [(0, start)]\n    while pq:\n        d, u = heapq.heappop(pq)\n        if d > dist[u]:\n            continue\n        for v, w in adj[u]:\n            if d + w < dist.get(v, float("inf")):\n                dist[v] = d + w\n                heapq.heappush(pq, (dist[v], v))\n    return dist',
    },
    {
      completionMode: 'CONDITION_BASED',
      requiredActivityIds: ['act-algo-quiz-1'],
      activities: [SEED_ACTIVITIES_BY_CLASS['cls-algo-2026'][0]],
    },
  ),
  makeSeedLesson(
    'les-algo-3',
    'cls-algo-2026',
    'Network Flow: Ford-Fulkerson & Dinic (Draft)',
    'DRAFT',
    '## Residual Graphs & Augmenting Paths\nDraft lecture notes preparing for next week’s flow problem set.',
    null,
  ),
  makeSeedLesson(
    'les-algo-4',
    'cls-algo-2026',
    'Greedy Exchange Arguments (Archived)',
    'ARCHIVED',
    '## Archived Module\nHistorical reference notes on interval scheduling and exchange proofs.',
    null,
  ),
  makeSeedLesson(
    'les-cp-1',
    'cls-cp-lab',
    'Segment Trees & Lazy Propagation',
    'PUBLISHED',
    '## O(log N) Range Updates\nDefer range modifications with lazy tags until child nodes are visited.',
    {
      language: 'java',
      code: 'void push(int node, int l, int r) {\n  if (lazy[node] == 0 || l == r) return;\n  tree[node * 2] += lazy[node];\n  lazy[node * 2] += lazy[node];\n  lazy[node] = 0;\n}',
    },
  ),
  makeSeedLesson(
    'les-cp-2',
    'cls-cp-lab',
    'Fenwick Trees (BIT) in 1D & 2D',
    'PUBLISHED',
    '## Prefix Sums with Bit Tricks\nUse `i & -i` to jump across binary indexed subtrees in constant code size.',
    null,
  ),
  makeSeedLesson(
    'les-web-1',
    'cls-web-eng',
    'TypeScript Discriminated Unions & State Machines',
    'PUBLISHED',
    '## Making Impossible States Unrepresentable\nModel loading, error, and success states explicitly with tagged unions.',
    {
      language: 'text',
      code: "type LoadState<T> =\n  | { status: 'loading' }\n  | { status: 'error'; message: string }\n  | { status: 'ready'; data: T };",
    },
  ),
  makeSeedLesson(
    'les-web-2',
    'cls-web-eng',
    'Spring Boot Stateless Security Filters (Draft)',
    'DRAFT',
    '## JWT Verification Pipeline\nDraft notes covering `OncePerRequestFilter` and RBAC enforcement.',
    null,
  ),
  makeSeedLesson(
    'les-py-1',
    'cls-archived-py',
    'Python Reference Semantics & Mutability',
    'PUBLISHED',
    '## Names, Objects, and Aliasing\nUnderstanding why mutating a list alias affects every reference.',
    {
      language: 'python',
      code: 'a = [1, 2, 3]\nb = a\nb.append(4)\nassert a == [1, 2, 3, 4]',
    },
  ),
];

type ProgressMap = Record<string, Record<string, LessonProgress>>;
type FileContentMap = Record<string, { name: string; contentType: string; text: string }>;

const INITIAL_PROGRESS: ProgressMap = {
  'les-algo-1': {
    'student-2': {
      completed: true,
      completedAt: '2026-11-02T09:30:00Z',
    },
  },
};

const SEED_FILE_CONTENTS: FileContentMap = {
  'att-seed-algo-notes': {
    name: 'lecture-01-recursion-trees-notes.pdf',
    contentType: 'text/plain; charset=utf-8',
    text: 'AlgoLearn Lecture 01 - Asymptotic Complexity & Recursion Trees\n\n1. Recurrence T(n) = aT(n/b) + f(n)\n2. Master Theorem Cases 1, 2, 3\n3. Merge Sort Proof: O(n log n)\n',
  },
  'att-seed-master-theorem': {
    name: 'master-theorem-cheatsheet.png',
    contentType: 'text/plain; charset=utf-8',
    text: 'Master Theorem Quick Reference: Compare f(n) against n^(log_b a).',
  },
};

function getStorage(): Storage | null {
  try {
    return typeof sessionStorage !== 'undefined' ? sessionStorage : null;
  } catch {
    return null;
  }
}

function readLessons(): Lesson[] {
  const storage = getStorage();
  if (!storage) return [...INITIAL_MOCK_LESSONS];
  try {
    const raw = storage.getItem(STORAGE_KEY_LESSONS);
    if (raw) return JSON.parse(raw) as Lesson[];
  } catch {
    // fallback
  }
  return [...INITIAL_MOCK_LESSONS];
}

function writeLessons(lessons: Lesson[]): void {
  const storage = getStorage();
  if (!storage) return;
  try {
    storage.setItem(STORAGE_KEY_LESSONS, JSON.stringify(lessons));
  } catch {
    // ignore storage quota errors in demo
  }
}

function readProgress(): ProgressMap {
  const storage = getStorage();
  if (!storage) return { ...INITIAL_PROGRESS };
  try {
    const raw = storage.getItem(STORAGE_KEY_LESSON_PROGRESS);
    if (raw) return JSON.parse(raw) as ProgressMap;
  } catch {
    // fallback
  }
  return { ...INITIAL_PROGRESS };
}

function writeProgress(map: ProgressMap): void {
  const storage = getStorage();
  if (!storage) return;
  try {
    storage.setItem(STORAGE_KEY_LESSON_PROGRESS, JSON.stringify(map));
  } catch {
    // ignore
  }
}

function readFiles(): FileContentMap {
  const storage = getStorage();
  if (!storage) return {};
  try {
    const raw = storage.getItem(STORAGE_KEY_ATTACHMENTS);
    if (raw) return JSON.parse(raw) as FileContentMap;
  } catch {
    // fallback
  }
  return {};
}

function writeFiles(files: FileContentMap): void {
  const storage = getStorage();
  if (!storage) return;
  try {
    storage.setItem(STORAGE_KEY_ATTACHMENTS, JSON.stringify(files));
  } catch {
    // ignore
  }
}

function paginate<T>(items: T[], page: number, size = 20): PaginatedData<T> {
  const start = page * size;
  const totalPages = Math.max(1, Math.ceil(items.length / size));
  return {
    items: items.slice(start, start + size),
    pagination: {
      page,
      size,
      totalElements: items.length,
      totalPages: items.length === 0 ? 0 : totalPages,
      isFirst: page === 0,
      isLast: start + size >= items.length,
    },
  };
}

function enrichLesson(
  lesson: Lesson,
  role: LessonRole,
  userId: string,
  progressMap: ProgressMap,
): Lesson {
  const userProgress = role === 'STUDENT' ? progressMap[lesson.id]?.[userId] || null : null;
  const anyProgress = Object.keys(progressMap[lesson.id] || {}).length > 0;
  return {
    ...lesson,
    progress: userProgress,
    canDelete: !anyProgress && lesson.activities.length === 0,
  };
}

export function getSynchronizedClassroom(classId: string): Classroom | undefined {
  return readStoredClassrooms().find(c => c.id === classId);
}

export function getLessonCountForClassroom(classId: string, role: LessonRole): number {
  const lessons = readLessons().filter(l => l.classroomId === classId);
  if (role === 'STUDENT') {
    return lessons.filter(l => l.status === 'PUBLISHED').length;
  }
  return lessons.length;
}

export interface ClassroomLessonSummary {
  totalCount: number;
  publishedCount: number;
  draftCount: number;
  archivedCount: number;
  completedCount: number;
  nextLesson: Lesson | null;
}

export function getClassroomLessonSummary(
  classId: string,
  role: LessonRole,
  userId: string,
): ClassroomLessonSummary {
  const progressMap = readProgress();
  const classLessons = readLessons().filter(l => l.classroomId === classId);
  const published = classLessons
    .filter(l => l.status === 'PUBLISHED')
    .map(l => enrichLesson(l, role, userId, progressMap));
  const draftCount = classLessons.filter(l => l.status === 'DRAFT').length;
  const archivedCount = classLessons.filter(l => l.status === 'ARCHIVED').length;
  const completedCount =
    role === 'STUDENT' ? published.filter(l => l.progress?.completed).length : 0;

  const nextLesson =
    role === 'STUDENT'
      ? published.find(l => !l.progress?.completed) || published[0] || null
      : published[0] || classLessons[0] || null;

  return {
    totalCount: role === 'STUDENT' ? published.length : classLessons.length,
    publishedCount: published.length,
    draftCount,
    archivedCount,
    completedCount,
    nextLesson,
  };
}

export const lessonMockStore = {
  getCapabilities(): LessonCapabilities {
    return DEMO_POLICY;
  },

  getActivities(classId: string): LessonActivity[] {
    const lessons = readLessons().filter(l => l.classroomId === classId);
    const baseActivities = SEED_ACTIVITIES_BY_CLASS[classId] || [
      {
        id: `act-${classId}-quiz`,
        type: 'QUIZ' as const,
        title: 'Module Check-in Quiz',
        linkedLessonId: null,
        available: true,
        path: null,
        completed: false,
      },
      {
        id: `act-${classId}-assign`,
        type: 'ASSIGNMENT' as const,
        title: 'Applied Practice Assignment',
        linkedLessonId: null,
        available: true,
        path: null,
        completed: false,
      },
    ];
    return baseActivities.map(activity => ({
      ...activity,
      linkedLessonId:
        activity.type === 'QUIZ'
          ? lessons.find(l => l.activities.some(a => a.id === activity.id))?.id || null
          : null,
    }));
  },

  list(classId: string, role: LessonRole, userId: string, page = 0): PaginatedData<Lesson> {
    const progressMap = readProgress();
    const filtered = readLessons()
      .filter(l => l.classroomId === classId && (role === 'TEACHER' || l.status === 'PUBLISHED'))
      .map(l => enrichLesson(l, role, userId, progressMap));
    return paginate(filtered, page);
  },

  get(classId: string, lessonId: string, role: LessonRole, userId: string): Lesson {
    const lesson = readLessons().find(l => l.id === lessonId && l.classroomId === classId);
    if (!lesson || (role === 'STUDENT' && lesson.status !== 'PUBLISHED')) {
      throw new Error('This lesson is not available to you.');
    }
    return enrichLesson(lesson, role, userId, readProgress());
  },

  create(classId: string, input: LessonInput, userId: string): Lesson {
    const classroom = getSynchronizedClassroom(classId);
    if (!classroom || classroom.status === 'ARCHIVED') {
      throw new Error('This classroom cannot create lessons right now.');
    }
    const activities = this.getActivities(classId);
    const errors = validateLesson(input, activities);
    if (Object.keys(errors).length) {
      throw new Error(Object.values(errors).join(' '));
    }
    const now = new Date().toISOString();
    const created: Lesson = {
      id: `les-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      classroomId: classId,
      title: input.title,
      status: 'DRAFT',
      previousStatus: null,
      content: input.content,
      codeExample: input.codeExample,
      externalLinks: input.externalLinks,
      completionMode: input.completionMode,
      requiredActivityIds: input.requiredActivityIds,
      activities: activities.filter(a => input.activityIds.includes(a.id)),
      attachments: [],
      progress: null,
      canDelete: true,
      createdAt: now,
      updatedAt: now,
    };
    writeLessons([...readLessons(), created]);
    return enrichLesson(created, 'TEACHER', userId, readProgress());
  },

  update(classId: string, lessonId: string, input: LessonInput, userId: string): Lesson {
    const all = readLessons();
    const index = all.findIndex(l => l.id === lessonId && l.classroomId === classId);
    if (index < 0) throw new Error('Lesson not found.');
    const existing = all[index];
    if (existing.status === 'ARCHIVED') {
      throw new Error('Restore this lesson before editing.');
    }
    const activities = this.getActivities(classId);
    const errors = validateLesson(input, activities, lessonId);
    if (Object.keys(errors).length) {
      throw new Error(Object.values(errors).join(' '));
    }
    const updated: Lesson = {
      ...existing,
      ...input,
      activities: activities.filter(a => input.activityIds.includes(a.id)),
      updatedAt: new Date().toISOString(),
    };
    all[index] = updated;
    writeLessons(all);
    return enrichLesson(updated, 'TEACHER', userId, readProgress());
  },

  transition(
    classId: string,
    lessonId: string,
    action: 'publish' | 'draft' | 'archive' | 'restore',
    userId: string,
  ): Lesson {
    const all = readLessons();
    const index = all.findIndex(l => l.id === lessonId && l.classroomId === classId);
    if (index < 0) throw new Error('Lesson not found.');
    const current = all[index];
    const next = { ...current, updatedAt: new Date().toISOString() };

    if (action === 'publish' && current.status === 'DRAFT') {
      next.status = 'PUBLISHED';
    } else if (action === 'draft' && current.status === 'PUBLISHED') {
      next.status = 'DRAFT';
    } else if (action === 'archive' && current.status !== 'ARCHIVED') {
      next.previousStatus = current.status;
      next.status = 'ARCHIVED';
    } else if (action === 'restore' && current.status === 'ARCHIVED') {
      next.status = current.previousStatus || 'DRAFT';
      next.previousStatus = null;
    } else {
      throw new Error('This state transition is not available.');
    }

    all[index] = next;
    writeLessons(all);
    return enrichLesson(next, 'TEACHER', userId, readProgress());
  },

  remove(classId: string, lessonId: string): void {
    const all = readLessons();
    const target = all.find(l => l.id === lessonId && l.classroomId === classId);
    if (!target) throw new Error('Lesson not found.');
    const enriched = enrichLesson(target, 'TEACHER', '', readProgress());
    if (!enriched.canDelete) {
      throw new Error('This lesson has learning data. Archive it instead.');
    }
    writeLessons(all.filter(l => !(l.id === lessonId && l.classroomId === classId)));
  },

  complete(classId: string, lessonId: string, userId: string): Lesson {
    const classroom = getSynchronizedClassroom(classId);
    if (!classroom || classroom.status === 'ARCHIVED') {
      throw new Error('This classroom is archived and read-only.');
    }
    const lesson = readLessons().find(l => l.id === lessonId && l.classroomId === classId);
    if (!lesson || lesson.status !== 'PUBLISHED' || lesson.completionMode !== 'MANUAL') {
      throw new Error('Only students can mark a manual published lesson complete.');
    }
    const progressMap = readProgress();
    const perLesson = progressMap[lessonId] || {};
    perLesson[userId] = { completed: true, completedAt: new Date().toISOString() };
    progressMap[lessonId] = perLesson;
    writeProgress(progressMap);
    return enrichLesson(lesson, 'STUDENT', userId, progressMap);
  },

  progress(classId: string, lessonId: string, page = 0): PaginatedData<StudentLessonProgress> {
    const classroom = getSynchronizedClassroom(classId);
    const activeMembers = (classroom?.members || []).filter(m => m.status === 'ACTIVE');
    const perLesson = readProgress()[lessonId] || {};
    const rows: StudentLessonProgress[] = activeMembers.map(member => {
      const record = perLesson[member.studentId];
      return {
        studentId: member.studentId,
        displayName: member.fullName || member.username,
        completed: Boolean(record?.completed),
        completedAt: record?.completedAt || null,
      };
    });
    return paginate(rows, page);
  },

  async uploadAttachment(classId: string, lessonId: string, file: File): Promise<LessonAttachment> {
    const err = validateAttachment(file, DEMO_POLICY);
    if (err) throw new Error(err);
    const all = readLessons();
    const index = all.findIndex(l => l.id === lessonId && l.classroomId === classId);
    if (index < 0) throw new Error('Lesson not found.');
    const attachment: LessonAttachment = {
      id: `att-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      name: file.name,
      size: file.size,
      contentType: file.type || 'application/octet-stream',
    };
    all[index] = {
      ...all[index],
      attachments: [...all[index].attachments, attachment],
      updatedAt: new Date().toISOString(),
    };
    writeLessons(all);

    const files = readFiles();
    const text = await file.text().catch(() => `Attachment: ${file.name}`);
    files[attachment.id] = {
      name: file.name,
      contentType: attachment.contentType,
      text: text.slice(0, 50_000),
    };
    writeFiles(files);
    return attachment;
  },

  removeAttachment(classId: string, lessonId: string, attachmentId: string): void {
    const all = readLessons();
    const index = all.findIndex(l => l.id === lessonId && l.classroomId === classId);
    if (index < 0) throw new Error('Lesson not found.');
    all[index] = {
      ...all[index],
      attachments: all[index].attachments.filter(a => a.id !== attachmentId),
      updatedAt: new Date().toISOString(),
    };
    writeLessons(all);
    const files = readFiles();
    delete files[attachmentId];
    writeFiles(files);
  },

  downloadAttachment(attachmentId: string): Blob {
    const file = readFiles()[attachmentId] || SEED_FILE_CONTENTS[attachmentId];
    if (!file) {
      throw new Error('Attachment content is unavailable.');
    }
    return new Blob([file.text], { type: file.contentType });
  },
};
