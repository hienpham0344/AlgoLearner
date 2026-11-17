import type { DailyPracticeActivity, StudentDashboardData } from './types';

const BANGKOK_OFFSET_MS = 7 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

const hoursFromNow = (hours: number) => new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();

const toBangkokDateKey = (timestampMs: number) =>
  new Date(timestampMs + BANGKOK_OFFSET_MS).toISOString().slice(0, 10);

// Chronological 12-week (84-day) daily coding history for `vite --mode auth-demo`.
// The last 7 consecutive days (daysAgo 0..6) are active to match `currentStreak: 7`,
// and total solved counts across the history sum to 18 to match `totalSolved: 18` (12 Easy + 5 Medium + 1 Hard).
const DEMO_SOLVED_BY_DAYS_AGO: Readonly<Record<number, number>> = {
  0: 2,
  1: 1,
  2: 1,
  3: 2,
  4: 1,
  5: 1,
  6: 1,
  // 7 days ago is 0 (streak boundary)
  11: 1,
  16: 2,
  23: 1,
  31: 1,
  44: 2,
  58: 1,
  72: 1,
};

const buildDemoActivityHistory = (totalDays = 84): DailyPracticeActivity[] => {
  const now = Date.now();
  const history: DailyPracticeActivity[] = [];

  for (let daysAgo = totalDays - 1; daysAgo >= 0; daysAgo -= 1) {
    history.push({
      date: toBangkokDateKey(now - daysAgo * DAY_MS),
      solvedCount: DEMO_SOLVED_BY_DAYS_AGO[daysAgo] ?? 0,
    });
  }

  return history;
};

// Used only by `vite --mode auth-demo`. Every value in this fixture is sample data.
export const dashboardDemoData: StudentDashboardData = {
  courses: [
    {
      id: 'demo-python',
      title: 'Python foundations',
      topic: 'Programming basics',
      currentModule: 'Module 03 · Control Flow & Functions',
      currentLesson: 'Functions and reusable logic',
      completedUnits: 8,
      totalUnits: 13,
      progressPercent: 62,
      href: '/courses',
    },
    {
      id: 'demo-algorithms',
      title: 'Thinking in algorithms',
      topic: 'Problem solving',
      currentModule: 'Module 02 · Linear Structures',
      currentLesson: 'Working with arrays',
      completedUnits: 3,
      totalUnits: 11,
      progressPercent: 28,
      href: '/courses',
    },
  ],
  deadlines: [
    {
      id: 'demo-assignment',
      title: 'Build a small sorting utility',
      className: 'Algorithms & Data Structures',
      kind: 'Assignment',
      dueAt: hoursFromNow(5),
      status: 'In progress',
    },
    {
      id: 'demo-quiz',
      title: 'Core data structures',
      className: 'Programming Fundamentals',
      kind: 'Quiz',
      dueAt: hoursFromNow(48),
      status: 'Not started',
    },
  ],
  classes: [
    {
      id: 'demo-class-1',
      name: 'Algorithms & Data Structures',
      code: 'ALGO-2026',
      currentActivity: 'Module 04 · Comparison-based sorting & complexity',
      nextAction: 'Complete sorting utility implementation',
      completedModules: 3,
      totalModules: 6,
    },
    {
      id: 'demo-class-2',
      name: 'Programming Fundamentals',
      code: 'CODE-101',
      currentActivity: 'Module 02 · Lists, tuples, and reference semantics',
      nextAction: 'Review core data structures notes',
      completedModules: 1,
      totalModules: 5,
    },
  ],
  practice: {
    currentStreak: 7,
    totalSolved: 18,
    easy: 12,
    medium: 5,
    hard: 1,
    activityHistory: buildDemoActivityHistory(84),
  },
  notifications: [
    {
      id: 'demo-note-1',
      title: 'New lesson unlocked',
      content: 'Functions and reusable logic',
      course: 'Python foundations',
      detail: 'Functions and reusable logic · Python foundations',
      createdAt: hoursFromNow(-2),
      read: false,
      href: '/courses',
    },
    {
      id: 'demo-note-2',
      title: 'Lab notes published',
      content: 'Comparison-based sorting & complexity',
      course: 'Algorithms & Data Structures',
      detail: 'Comparison-based sorting & complexity · Algorithms & Data Structures',
      createdAt: hoursFromNow(-19),
      read: true,
      href: '#classes',
    },
    {
      id: 'demo-note-3',
      title: 'Quiz schedule posted',
      content: 'Core data structures',
      course: 'Programming Fundamentals',
      detail: 'Core data structures · Programming Fundamentals',
      createdAt: hoursFromNow(-42),
      read: true,
      href: '#deadlines',
    },
  ],
};
