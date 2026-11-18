# Review phạm vi Judge rejudge / Dashboard Assignment

Kết quả: **không tìm thấy lỗi P1/P2 có bằng chứng trong phạm vi đã giao**. Review chỉ đọc production/tests; chỉ tạo báo cáo này. Không chứng nhận toàn bộ Assignment flow hoặc source release cuối.

## Phạm vi và bằng chứng

Đã đọc `JudgeGateway`, `JdbcJudgeGateway`, `JudgeScoringPrivacyTest`, `JdbcDashboardRepository`, `DashboardPersistenceTest`; tham chiếu hẹp `JudgePolicy`, migration immutable submission và contract DTO. Không sửa production/test hoặc chạy lại Maven toàn dự án.

Đã trực tiếp đọc metadata và từng testcase XML liên quan từ hai kết quả runner của root:

- `judge-revision-rejudge-green`: source `d343dbc69a77abd918c74ab6a38cfed3663d565296e0ac7532de50e31d870c1a`, snapshot `judge-revision-rejudge-green-_tmnnm9i`, migration track, **18 PASS / 0 failures / 0 errors / 0 skipped**. Ba file Judge trong scope trùng byte với snapshot này; cả năm method của `JudgeScoringPrivacyTest` có XML PASS.
- `dashboard-schedule-late-final`: source `8b768edd1069c054366526b6d43e203b410247911a64c5003287eb922d14daf7`, snapshot `dashboard-schedule-late-final-zvhkmdfm`, migration track, **12 PASS / 0 failures / 0 errors / 0 skipped**. Hai file Dashboard trong scope trùng byte với snapshot; mười invocation của `DashboardPersistenceTest` có XML PASS.

Đây là kết quả scoped đã quan sát, không phải full verification hai migration track sau cùng.

## Judge: identity, revision và quyền đọc

Quyết định của user là hỗ trợ rejudge theo ORIGINAL hoặc revision đã Published mới, mặc định ORIGINAL. Gateway là trusted server port; caller xác thực quyền và chọn revision. Việc chọn mặc định/chuyển revision trong Assignment thuộc review độc lập của agent khác.

- `JdbcJudgeGateway.java:40`: owner row `FOR UPDATE` bao quanh kiểm idempotency, lookup canonical submission và insert job trong cùng transaction. Các API instance dùng cùng khóa owner, nên hai rejudge mới không cùng vượt qua kiểm generation trên một canonical key.
- `JdbcJudgeGateway.java:44`: retry cùng owner/key/generation/mode và cùng payload trả lại job cũ trước kiểm generation/quota; payload khác trả `JUDGE_IDEMPOTENCY_CONFLICT`.
- `JdbcJudgeGateway.java:55`: rejudge thiếu canonical SUBMIT trước đó trả `JUDGE_SUBMISSION_NOT_FOUND`, không tạo official submission mới. Original submit mới tạo `tbl_submission` một lần; các generation sau dùng lại `submission_id`.
- `JdbcJudgeGateway.java:64`: trusted rejudge kiểm toàn bộ identity qua `sameSubmissionIdentity`: owner, request key, attempt, question, scope, resource, source, language. Testcases, revision, feedback và limits được phép thay đổi; `JudgePolicy.validate` vẫn kiểm limits, kích thước source/testcases và required fields trước transaction.
- `JdbcJudgeGateway.java:66`: rejudge mới phải có generation lớn hơn MAX của canonical key; retry cùng generation đã tồn tại vẫn idempotent đúng thứ tự kiểm phía trên. Không yêu cầu generation liền nhau.
- `JdbcJudgeGateway.java:68`: submit thông thường với context mới không thể tự đổi testcase/revision/feedback/limits của canonical original. Test `explicitRejudgeCanSelectAnotherRevisionWhileKeepingOfficialIdentityAndOriginalSnapshot` assert submit revised bị từ chối trước khi trusted rejudge chấp nhận revision đó.
- `JdbcJudgeGateway.java:74`: nhánh rejudge không UPDATE original submission. Test trên assert một official submission, original `revision_id` và testcase snapshot vẫn chứa `original-output`, không chứa `revised-output`; retry rejudge trả cùng job id; changed source/stale generation/foreign owner đều bị từ chối.

`JobReference` có các ID, scope, mode RUN và generation; không có source/testcase input/expected/actual output. `ownedJob` lọc cả job id lẫn owner id. `trustedJobReferenceBindsRunToItsParentWithoutExposingCodeOrTestcases` assert record đúng parent resource, JSON không chứa source/input/expected và foreign requester bị từ chối. Đây là metadata cho caller kiểm parent/release policy, không thay thế authorization trong module gọi.

Các projection hiện tại vẫn giữ ranh giới riêng: owner feedback lọc theo feedback policy và allowDetails; trusted scoring chỉ trả case verdict/index, loại input/expected/actual output. Hai privacy tests xác nhận cả dữ liệu worker fixture chứa hidden IO cũng không làm lộ qua các projection này.

## Dashboard: Scheduled metadata, bài quá hạn và giới hạn truy vấn

- `JdbcDashboardRepository.java:85`: Student nhận `effectiveDueAt=null` khi `start_time > now`; mapper lấy cột deadline đã được CASE che. Teacher vẫn nhận deadline. `scheduledUpcomingAssignmentsAreVisibleOnlyToActiveMembers` assert đúng opensAt, due null và đúng một Scheduled item; outsider và membership LEFT đều không thấy item.
- `JdbcDashboardRepository.java:88`: cửa sổ Student là start sắp tới **hoặc** deadline sắp tới. Scheduled có start trong cửa sổ và deadline NULL vẫn được liệt kê; test `upcomingScheduledAssignmentDoesNotRequireADeadline` có assertion ID chính xác.
- `JdbcDashboardRepository.java:86`: danh sách Teacher giữ Assignment PUBLISHED sau deadline khi `allow_late_submission=true`; cùng fixture có Assignment hết hạn không cho nộp muộn bị loại. Test `teacherOpenAssignmentsIncludePublishedLateWorkAfterItsDeadline` đồng thời assert Teacher khác không thấy dữ liệu.
- `JdbcDashboardRepository.java:84`: owner scope cho Teacher và membership ACTIVE cho Student được áp trong scoped SQL trước COUNT/LIMIT. Class phải ACTIVE, Assignment chỉ PUBLISHED/SCHEDULED; DRAFT, foreign class và item ngoài cửa sổ không lọt vào Student preview trong `studentPreviewsUseMembershipPublicationWindowAndOwnEnrollment`.
- Cửa sổ dùng `< upcomingEnd` và `>= now` cho deadline; start trong tương lai dùng `> now`. Generic preview có LIMIT, stable created_at/id ordering và count riêng. `previewQueryCountStaysConstantWhenRowsExceedLimit` assert 12 total/3 items/hasMore, đúng một count query và một item query; không có truy vấn tăng theo từng item.

## Giới hạn còn lại

Test revision mới trực tiếp assert đổi testcase/revision/feedback; khả năng đổi limits được kiểm bằng code whitelist và policy validation, chưa có invocation riêng đổi limits. Các field identity khác source được so sánh rõ trong code; không yêu cầu thêm toàn bộ negative matrix để chốt scope này.

Các kết quả dùng PostgreSQL và trusted worker fixtures cục bộ; không chứng minh Docker chạy nguồn học viên hoặc provider bên ngoài. Root vẫn cần full verify cuối trên default/main-auth khi các wave đã ổn định. Không có thay đổi production/test từ review này.
