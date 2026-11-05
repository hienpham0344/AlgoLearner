# Student Dashboard integration status

The Student Dashboard implements `SCR-STU-01` at `/dashboard`. It includes Continue Learning, upcoming assignments and quizzes, enrolled classes, public practice statistics, and recent notifications. These sections correspond to `FR-PRO-003`, `FR-NOT-006`, and `FR-PPRC-006` in SRS 1.1.

## Available integration

The frontend reads `GET /api/v1/users/me/profile` and the role-scoped dashboards from the `feat/backend` API contract (#6). The backend code currently lives on a separate branch and runs in `mock` mode with in-memory sources; it has not been merged into this frontend branch. The production build never uses demo fixtures.

## Backend gaps

| Dashboard area | Required account-scoped data | Current state |
| --- | --- | --- |
| Continue Learning | Current public course, lesson/module, completion percent, destination | `/dashboards/student` supplies course and progress; lesson and destination are absent |
| Upcoming deadlines | Student's assignments and quizzes, due time, status, destination | `/dashboards/student` supplies titles, class names and UTC due times; status/detail routes are absent |
| My classes | Student's enrolled classes, relevant metadata, destination | `/dashboards/student` supplies class IDs and names; class detail route is absent |
| Practice pulse | Current streak and Easy/Medium/Hard solved counts | `/dashboards/student` supplies current streak and total solved; difficulty counts are absent |
| Recent notifications | Latest account notifications, unread state, timestamp, destination | `/dashboards/student` supplies title/time/read state; notification detail/read API is absent |

The production UI displays unavailable/empty states where data is not provided and does not create fake links or actions. The backend must enforce account scope; frontend filtering cannot enforce privacy. Dates display in UTC+7. The Teacher and Admin dashboard views use `/dashboards/teacher` and `/admin/dashboard` respectively. Their source data and charts are currently mock/in-memory on #6, so neither role is ready for persistence acceptance.

`npm run dev:auth-demo` shows clearly labeled sample data for layout review. It does not represent real enrollment, deadlines, progress, or notifications.

`npm run dev:backend-mock` uses the #6 backend API for all dashboard/profile data when that backend is running on port 8080. The Vite plugin supplies development sign-in tokens for `student`, `teacher`, and `admin` with password `DemoPass123!`; these are not production credentials. Start the backend from `feat/backend` with Java 21 and Maven, then start this frontend mode. Profile editing is available only to Student/Teacher; public profile view works anonymously. See the issue #7 test report for remaining blockers.
