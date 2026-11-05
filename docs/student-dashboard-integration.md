# Student Dashboard integration status

The Student Dashboard implements `SCR-STU-01` at `/dashboard`. It includes Continue Learning, upcoming assignments and quizzes, enrolled classes, public practice statistics, and recent notifications. These sections correspond to `FR-PRO-003`, `FR-NOT-006`, and `FR-PPRC-006` in SRS 1.1.

## Available integration

The frontend already requests the current user's profile at `GET /api/v1/users/me/profile`. The dashboard uses only `fullName`, `publicPracticeStatistics.solved`, and `streak.currentStreak` if returned. The repository's Spring backend currently contains common DTO classes but no controller implementing this endpoint or the other dashboard resources.

## Backend gaps

| Dashboard area | Required account-scoped data | Current state |
| --- | --- | --- |
| Continue Learning | Current public course, lesson/module, completion percent, destination | No course enrollment or progress endpoint/route |
| Upcoming deadlines | Student's assignments and quizzes, due time, status, destination | No assignment/quiz endpoint or detail route |
| My classes | Student's enrolled classes, relevant metadata, destination | No student classroom endpoint or detail route |
| Practice pulse | Current streak and Easy/Medium/Hard solved counts | Profile client supports streak and total solved only; backend endpoint absent |
| Recent notifications | Latest account notifications, unread state, timestamp, destination | No notification endpoint, read mutation, or center route |

The production UI displays unavailable/empty states where data is not provided and does not create fake links or actions. Backend responses must be scoped to the authenticated student. Date/time values should use UTC ISO-8601 and display in UTC+7 per the project API rules. Define the resource DTOs and routes in OpenAPI before connecting the remaining sections.

`npm run dev:auth-demo` shows clearly labeled sample data for layout review. It does not represent real enrollment, deadlines, progress, or notifications.
