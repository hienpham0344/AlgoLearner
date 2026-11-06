export interface CourseProgress {
  id: string;
  title: string;
  topic: string;
  currentModule?: string;
  currentLesson?: string;
  completedUnits?: number;
  totalUnits?: number;
  progressPercent: number;
  href?: string;
}

export interface LearningDeadline {
  id: string;
  title: string;
  className: string;
  kind: 'Assignment' | 'Quiz';
  dueAt: string;
  status?: 'Not started' | 'In progress';
  href?: string;
}

export interface EnrolledClass {
  id: string;
  name: string;
  code: string;
  teacher?: string;
  currentActivity?: string;
  nextAction?: string;
  completedModules?: number;
  totalModules?: number;
  href?: string;
}

export interface DailyPracticeActivity {
  date: string;
  solvedCount: number;
}

export interface PracticeStats {
  currentStreak: number | null;
  totalSolved: number | null;
  easy: number | null;
  medium: number | null;
  hard: number | null;
  activityHistory?: DailyPracticeActivity[];
}

export interface DashboardNotification {
  id: string;
  title: string;
  content?: string;
  course?: string;
  detail: string;
  createdAt: string;
  read: boolean;
  href?: string;
}

export interface StudentDashboardData {
  courses: CourseProgress[];
  deadlines: LearningDeadline[];
  classes: EnrolledClass[];
  practice: PracticeStats;
  notifications: DashboardNotification[];
}
