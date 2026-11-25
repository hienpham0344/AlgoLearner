# Test Report — Backend 4.11 Khóa học công khai

- **Ngày:** 2026-11-24 · **Branch:** `feat/be-4.11-public-course` · **Base:** `main` @ `78255b9`
- **Môi trường:** Windows 11, OpenJDK 21.0.13, Maven 3.10.0, PostgreSQL 16-alpine (Docker, database `algolearner_test`, mỗi lần chạy dùng schema riêng do `PostgresIntegrationTest` tạo)
- **Lệnh chạy:** `mvn test` với `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`

## Kết quả

| Bộ test | Kết quả |
|---|---|
| `CourseRulesTest` (calculator, grader, content rules; JUnit 5) | 11/11 pass |
| `PublicCourseServiceTest` (Mockito) | 8/8 pass |
| `PublicCourseFlowTest` (PostgreSQL + MockMvc) | 9/9 pass |
| `DashboardPersistenceTest`, `DashboardServiceTest` (query dashboard đã cập nhật) | 12/12 pass |
| `MainAuthMigrationUpgradeTest`, `BackendMigrationVerificationTest` (chạy migration trên cả hai Flyway location) | pass |
| **Toàn bộ backend** | 672 test: 665 pass, 3 skipped, 7 lỗi đã giải thích bên dưới |

**Các lỗi không do thay đổi này gây ra.** Tất cả đã được chạy đối chứng trên worktree của `main`.

- `DockerSandboxLifecycleTest` (3 failure, 3 error, `UnsupportedOperationException`): lỗi giống hệt trên `main`. Nguyên nhân là môi trường Windows, nằm ngoài phạm vi 4.11.
- `LessonLinkedContentSafetyTest.committedArchiveWinsOverConcurrentQuizStart`: chỉ lỗi khi chạy cả suite với `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=2`. Biến này được đặt để tránh lỗi `too many clients` của Postgres (`max_connections=100`) khi Spring cache nhiều context. Với pool mặc định, test pass 5/5 trên cả branch này và `main`.

## Đối chiếu AC

| FR | AC chính đã kiểm | Test |
|---|---|---|
| 001, 003 | Duyệt và tìm theo tên/level/topic; course chưa publish không hiển thị; search không tạo enrollment | `catalogSearchFiltersByName…`, `httpCourseFlow…` |
| 002, 009 | Preview không cần enroll; enroll idempotent; unenroll chỉ khi chưa bắt đầu; enroll lại; từ chối UNPUBLISHED/ARCHIVED; Admin không được học | `unenrollBeforeStarting…`, `PublicCourseServiceTest` |
| 004 | Mở lesson tùy ý; lesson không preview trả 403 `COURSE_NOT_ENROLLED` khi chưa enroll | `previewLessonIsOpen…`, `httpCourseFlow…` |
| 005, 006 | Not Started → In Progress → Completed, có `completedAt`; mandatory quiz chặn completion; quiz không bắt buộc và practice không chặn; progress độc lập; IDOR với progress | `CourseRulesTest.Progress`, `reviewedCourseEnrollment…`, `mandatoryQuiz…` |
| 007, 015 | Thêm lesson sau Completed: giữ Completed và `completedAt`, bật `hasUpdate`; người chưa Completed được tính lại; xóa lesson là xóa mềm, completion còn nguyên | `publishedChangesNeedReReview…` |
| 008, 011 | Đủ 5 loại block; ảnh không phải https bị từ chối; practice link phải trỏ tới bài PUBLISHED; có Estimated Duration | `CourseRulesTest.Content` |
| 010 | SUBMIT / PASSING_SCORE; max attempts; điểm chính thức là điểm cao nhất; ẩn đáp án theo visibility; resume không reset timer; hết giờ tự nộp với câu đã lưu; attempt cũ còn nguyên | `mandatoryQuiz…`, `expiredAttempt…`, `CourseRulesTest.Grading` |
| 012 | Review tách khỏi Publication; Changes Requested/Reject bắt buộc reason; gửi lại sau CR; sửa bản đã Published tạo revision mới và không đụng bản công khai; giữ author; Teacher khác không sửa được; có notification; RBAC endpoint Admin | `publishedChangesNeedReReview…`, `studentsCannotAuthor…`, `httpCourseFlow…` |
| 013, 014 | ENROLLED_CONTINUE và LOCK_ALL; Archive; Hard Delete bị chặn khi đã có enrollment; xóa course chưa có dữ liệu; có audit | `unpublishArchiveAndDelete…`, `lockAllBlocksContent…` |

## Bug phát hiện trong lúc test và đã sửa

Một lỗi quyền truy cập ném ra trong bean `@Transactional` lồng nhau làm transaction chung bị đánh dấu rollback-only, khiến `saveAnswers` trả về `UnexpectedRollbackException` (HTTP 500) thay vì 403. Đã sửa bằng cách chuyển `learningEnrollment` sang `CourseAccessPolicy`, một component không có transaction.

## Chưa xác minh / còn mở

- Chưa tích hợp với FE 4.11 và chưa chạy Postman/Newman cho 4.11.
- Upload ảnh qua File Storage chưa hỗ trợ (chỉ nhận URL https), vì storage hiện bắt buộc gắn classroom.
- Mã TC chính thức của Test Plan nhóm PCRS chưa có trong repo; bảng trên đối chiếu theo AC trong SRS.
