# Review tích hợp cuối backend 4.1–4.6

Ngày 18/11/2026. **Kết luận: không tìm thấy P1/P2 mới có bằng chứng trong lượt review tích hợp này; không còn P1/P2 đã biết trong phạm vi các báo cáo đã đối chiếu.** Gate review code tích hợp cục bộ đạt với các giới hạn bên dưới. Đây không phải chứng nhận hoàn tất mọi AC hoặc sẵn sàng production.

## Phạm vi và cách kiểm tra

Review chỉ đọc production/test/evidence; chỉ thêm báo cáo này. Không sửa production, dependency, migration hoặc test; không commit/push, không dùng DB ứng dụng, không chạy lại Maven toàn dự án. Đã tham chiếu AGENTS.md, skill `code-review-excellence`, kế hoạch `2026-11-18-backend-4.1-4.6-implementation.md`, các chuẩn API/Coding/Security và năm báo cáo `root-modules-review`, `assignment-ac-fix-review`, `judge-dashboard-ac-fix-review`, `assignment-completion`, `progress-consistency-review` cùng ngày.

Phạm vi là 53 FR backend của 4.1–4.6 và dependency cần cho các luồng này. Không mở rộng thành chứng nhận toàn bộ 4.7–4.16. Đã so trực tiếp `sourceParagraph` và chuỗi AC trong master traceability với `word/document.xml` của SRS ver 1.1: **53 FR, 372 AC, không có chuỗi AC sai khác**. Các trạng thái PARTIAL/CODE_REVIEW_ONLY/CLIENT_NOT_VERIFIED vẫn có ý nghĩa riêng; thiếu assertion không tự trở thành lỗi triển khai.

## Danh tính source và bằng chứng gate

Hai metadata `backend/target/verification/backend-release-default.json` và `backend-release-main.json` cùng SHA-256:

`aa7963c64ed12fa0003ab81af394453466c6fec1335c305acdc60670f5de9234`

- Default snapshot: `backend-release-default-kcoa00ni`; main-auth snapshot: `backend-release-main-70hqtxcf`.
- Đã so byte toàn bộ **279 tệp gồm `src/main/**` và `pom.xml`** hiện tại với từng snapshot: không có tệp thêm/xóa/đổi khác. Production được review chính là production của release đã đóng gói.
- Đã đọc và cộng độc lập **85 Surefire XML mỗi track**: mỗi track **498 declared, 495 PASS, 0 failure, 0 error, 3 skipped**; metadata `phase=verify`, `exit=0`. Mỗi snapshot có `target/platform-0.0.1-SNAPSHOT.jar`.
- Ba skip là các lớp opt-in `LiveSupabaseFlowTest`, `SupabaseConnectionTest`, `RegistrationBenchmarkTest`; không tính chúng là PASS.
- Khác biệt test so với release chỉ gồm bốn lớp acceptance mới: `AssignmentAuthorizationAcceptanceTest`, `AuthProfileAuthorizationAcceptanceTest`, `AnnouncementLessonAuthorizationAcceptanceTest`, `ClassroomAuthorizationAcceptanceTest`. Chúng không thuộc con số 498 trên. Kết quả batch/full tiếp theo do coordinator gắn riêng, không suy diễn từ review này.

## Các điểm nối đã đọc và đối chiếu

**Transaction và file binding.** `ManagedFileService.requireReady` dùng REQUIRED, tham gia transaction aggregate; `FileRecordService.requireReady` giữ file lock cho tới commit tham chiếu. Cleanup lấy Class trước File, kiểm tra lại tham chiếu trong transaction claim. `LessonService.addMaterial` và Assignment autosave dùng fileId/purpose/class/owner thật; metadata Lesson trả `/api/v1/files/{id}/content`. Đã đọc regression Spring proxy/PostgreSQL `ManagedFileBindingTransactionTest#cleanupCannotClaimFileBetweenReadyCheckAndReferenceCommit`: một invocation PASS trên mỗi release track. Không phát hiện lại cửa sổ cleanup đã sửa trong báo cáo trước.

**Canonical rejudge, revision và công bố.** `AssignmentService.rejudgeAuthorized`, `AssignmentCodeJudge.enqueue/completed`, `AssignmentEvaluationHistory` và `JdbcJudgeGateway.enqueue` khớp contract ORIGINAL mặc định hoặc LATEST_PUBLISHED. Context mới lấy revision TRUSTED đã publish của cùng Assignment; source/language/question maximum/weight gốc và official submission giữ nguyên. Context riêng theo generation lưu `revision_id`, evaluation part giữ `judgeRevisionId`; migration 180014 khóa identity/context khỏi UPDATE. Gateway giữ owner lock, kiểm idempotency/identity/generation và không tạo official thứ hai khi rejudge.

Student polling official kiểm owner/attempt/question/resource/scope cùng published evaluation part và revision; pending/unreleased generation không đi qua endpoint này. Working score có thể đổi trước release nhưng own official view và Lesson score đọc published pointer. Các đường publish manual/AFTER_DEADLINE và refresh kết quả phát event recompute trong transaction; provider completion đọc điểm đã công bố theo snapshot maximum. Đã đọc regression thực tế `AssignmentAcceptanceIntegrationTest#rejudgeSelectsLatestPublishedCasesAndWeightsButKeepsOriginalIdentitySourceAndHistory`: chọn v2, bỏ working v3, che generation chưa công bố, giữ điểm cũ trước release, trả lại ORIGINAL, giữ một submission và lịch sử v1. Method PASS trên cả hai release track. `JudgeScoringPrivacyTest` có 5 PASS/track; projection scoring không chứa hidden IO.

**Scheduled, allowLate, timer và Dashboard.** Batch Scheduled lọc Class/owner ACTIVE trước LIMIT; transition khóa aggregate, kiểm lại trạng thái và giờ mở, không dùng cấu hình working mới thay snapshot published. Student summary trước StartTime chỉ giữ tên/thời điểm mở; Dashboard che deadline. Query Dashboard cho Scheduled không deadline dùng cửa sổ start; Teacher giữ bài PUBLISHED đã quá deadline khi allowLate. Timer dùng cutoff duration/deadline phù hợp allowLate, scheduler và service cùng kiểm cutoff; thay Late policy sau deadline có attempt bị từ chối. Đã đọc code và assertions của Assignment acceptance Scheduled/starvation/late-policy/timer cùng Dashboard Scheduled/late tests; XML release ghi `AssignmentAcceptanceIntegrationTest` 15 PASS và `DashboardPersistenceTest` 10 PASS mỗi track.

**ACTIVE RBAC, Admin context và lifecycle.** Security converter lấy role/status/restriction và internal session từ persistence, không tin role claim; namespace Admin có `hasRole('ADMIN')` và service kiểm active Admin. Ba quyền grading Admin giới hạn ở rejudge/history/working testcase weights theo ruling của kế hoạch; không thêm Admin làm thành viên/Teacher. Parent scope của assignment/attempt/revision/job được kiểm trước đọc kết quả. Class lock được refresh trước child locks; trạng thái Class/owner ACTIVE và Lesson Published overlay chặn learner writes, archive vẫn giữ đường đọc lịch sử theo quyền. `publishGrades`/`refreshCodeResults` gọi completion qua event đồng bộ, không nâng Class READ thành WRITE. Các kết quả được đối chiếu với review trước và release XML, không yêu cầu thêm negative matrix chỉ để lặp review.

**Legacy và hai migration track.** 13 migration `V20261118*.sql` có mặt ở cả default/main-auth và giống byte. Các migration revision không chế tạo snapshot lịch sử: `LEGACY_MISSING`/null giữ provenance và điểm cũ; rejudge thiếu TRUSTED snapshot bị chặn. Feedback giữ alias SUMMARY/DETAILED. Migration 180014 backfill context cho code job hiện có, không sửa grade/answer lịch sử; legacy evaluation part thiếu `judgeRevisionId` bị chặn đọc job details, grade snapshot cũ vẫn đọc được.

Đã đọc `BackendMigrationVerificationTest` và XML release: 4 invocation PASS/track cho new/upgrade/repeat, Flyway validate, no-op lần chạy lại, RLS, grade 7.25/avatar legacy/material NEEDS_REUPLOAD còn nguyên. `AssignmentRevisionMigrationTest` có 2 PASS/track cho legacy grade/provenance và trigger bất biến; `AuditMigrationTest` có 2 PASS/track cho adoption không mất legacy UUID/rows/columns/RLS; `MainAuthMigrationUpgradeTest` có 1 PASS/track cho auth adoption. Không áp migration vào DB ứng dụng trong review.

`ClassroomModulesCrossIntegrationTest#fullClassroomSemesterFlow_Announcements_Lessons_Assignments` cũng có một invocation PASS/track: hợp đồng HTTP giữa Class join, Lesson, Assignment link/publication, Announcement, làm/nộp/chấm bài. Đây là bằng chứng bổ sung; không dùng một happy path để thay toàn bộ AC.

## Giới hạn và quyết định

Không có phát hiện P1/P2 mới cần coordinator đổi production trong lượt review này. Giữ source production đã freeze; ghép evidence acceptance test mới và full gate tiếp theo khi chúng hoàn tất.

Kết quả trên là code review + XML/snapshot cục bộ đã quan sát, không phải một lượt chạy test mới của reviewer. Docker daemon thực thi Java/Python, Supabase Storage thật, ClamAV engine, SMTP/inbox thật và live staging chưa được xác minh. UI rendering/XSS/cảnh báo/xác nhận và những AC còn PARTIAL vẫn cần bằng chứng đúng phạm vi. Các giới hạn này không được đổi thành tuyên bố 372 AC đã hoàn tất hoặc production đã được chứng nhận.
