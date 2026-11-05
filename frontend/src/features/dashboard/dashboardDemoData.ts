import type { StudentDashboardData } from './types';

const hoursFromNow = (hours: number) => new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();

// Used only by `vite --mode auth-demo`. Every value in this fixture is sample data.
export const dashboardDemoData: StudentDashboardData = {
  courses: [
    { id: 'demo-python', title: 'Python foundations', topic: 'Programming basics', currentLesson: 'Functions and reusable logic', progressPercent: 62, href: '/courses' },
    { id: 'demo-algorithms', title: 'Thinking in algorithms', topic: 'Problem solving', currentLesson: 'Working with arrays', progressPercent: 28, href: '/courses' },
  ],
  deadlines: [
    { id: 'demo-assignment', title: 'Build a small sorting utility', className: 'Algorithms & Data Structures', kind: 'Assignment', dueAt: hoursFromNow(5), status: 'In progress' },
    { id: 'demo-quiz', title: 'Core data structures', className: 'Programming Fundamentals', kind: 'Quiz', dueAt: hoursFromNow(48), status: 'Not started' },
  ],
  classes: [
    { id: 'demo-class-1', name: 'Algorithms & Data Structures', code: 'ALGO-2026', teacher: 'Teacher Demo' },
    { id: 'demo-class-2', name: 'Programming Fundamentals', code: 'CODE-101', teacher: 'Teacher Demo' },
  ],
  practice: { currentStreak: 7, totalSolved: 18, easy: 12, medium: 5, hard: 1 },
  notifications: [
    { id: 'demo-note-1', title: 'Your assignment is due soon', detail: 'Build a small sorting utility', createdAt: hoursFromNow(-2), read: false, href: '#deadlines' },
    { id: 'demo-note-2', title: 'A new lesson is ready', detail: 'Python foundations', createdAt: hoursFromNow(-19), read: true, href: '#learning' },
  ],
};
