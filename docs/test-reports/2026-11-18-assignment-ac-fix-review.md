# Rà soát độc lập Assignment acceptance fixwave — 2026-11-18

Phạm vi: sáu gap đang sửa của SRS 4.6: compile budget câu Code trực tiếp; nộp trễ có/không Timer; Scheduled và metadata trước Start Time; ba mức Code Feedback; lịch sử Official Attempt khi Closed; polling RUN/official generation theo owner và quy tắc công bố. Không sửa production/Auth, không chạy lại Maven của implementer. Người dùng sau đó chọn Rejudge hỗ trợ cả original và new published, mặc định original; review mở rộng sang tính bất biến của selected revision/scoring context và ranh giới công bố kết quả của lựa chọn này.

Skill áp dụng: `code-review-excellence`, đối chiếu trực tiếp `docs/SRS ver 1.1.docx` và code/test hiện hành. Đây là review code độc lập; kết quả test của implementer sẽ được ghi riêng khi có XML thực tế.

## Phát hiện cần xử lý

1. **P2 — Summary Scheduled lộ cấu hình trước Start Time — đã sửa trong snapshot review.** Trigger: Student thành viên Active gọi `GET /api/v1/classrooms/{classId}/assignments` trước giờ mở. Summary cũ trả cả cấu hình, grade và quota. SRS FR-ASG-005 AC-02 (paragraph 1427/1436) chỉ cho tên và thời điểm mở. `AssignmentService.toSummaryView` hiện early-return với các cấu hình/grade/quota/date là null; `AssignmentSummaryView` đổi primitive sang nullable ở các trường cần che. Regression đã PASS trong XML bên dưới. Cùng Deadline từng đi qua Dashboard Student; root đã che `effectiveDueAt` bằng điều kiện `a.start_time>:now` tại `JdbcDashboardRepository.assignments`. Lượt regression Dashboard do root quản lý, không suy diễn PASS từ việc đọc code.

2. **P2 — Batch Scheduled có thể starvation bài đủ điều kiện — đã sửa trong snapshot review.** Trigger cũ: 100 Scheduled quá giờ mở đứng đầu batch thuộc Class/owner không Active; bài đủ điều kiện thứ 101 không được chọn qua mọi poll. `AssignmentRepository.findDueScheduledIds` hiện join Class và owner, lọc cả hai `ACTIVE` trước `LIMIT 100`, khớp guard activation. Regression PostgreSQL 101 bài đã PASS trong XML bên dưới.

3. **P2 — Đổi Late policy sau Deadline có thể ghi timestamp official lùi trước Started At — đã sửa guard trong snapshot review.** Trigger cũ: Student bắt đầu hợp lệ sau Deadline với Late được bật; Teacher giữ nguyên Deadline nhưng tắt Late; cutoff bị ép lùi về Deadline trước Started At. `AssignmentService.update:188-191` hiện từ chối đổi Late policy khi Deadline đã đến và Assignment có Attempt. Regression đã PASS và kiểm tra request bị chặn, cấu hình/timer hiện có được giữ; hậu quả timestamp cũ được suy ra từ code, không được trình bày như một assertion runtime đã quan sát sau finalize.

## Bằng chứng RED đã đọc trực tiếp

- `backend/target/verification/assignment-scheduled-redaction-red.json`: 1 test, 1 failure, 0 errors/skip. XML `assignment-scheduled-redaction-red-e02nc18j/target/surefire-reports/TEST-com.algolearner.platform.assignment.AssignmentAcceptanceIntegrationTest.xml`, method `futurePublicationIsPersistedScheduledAndActivatesAtExactServerStart`: kỳ vọng `$.data[0].type` không tồn tại, thực tế `GRADED`.
- `backend/target/verification/assignment-schedule-starvation-red.json`: 1 test, 1 failure, 0 errors/skip. XML `assignment-schedule-starvation-red-p25elisn/target/surefire-reports/TEST-com.algolearner.platform.assignment.AssignmentAcceptanceIntegrationTest.xml`, method `blockedSchedulesCannotStarveAnActiveAssignmentOutsideTheFirstHundredRows`: batch due không chứa bài đủ điều kiện đứng sau 100 blocker.
- `backend/target/verification/assignment-late-policy-red.json`: 1 test, 1 failure, 0 errors/skip. XML `assignment-late-policy-red-zaxrj3in/target/surefire-reports/TEST-com.algolearner.platform.assignment.AssignmentAcceptanceIntegrationTest.xml`, method `elapsedDeadlineCannotBeReintroducedBeforeALateAttemptsStartTime`: update đáng lẽ bị từ chối nhưng không ném lỗi.

## Các luồng đã đọc

- Direct Code authoring dùng compile budget 10 giây, tương thích giới hạn 1–10 của `JudgePolicy`; test đi qua `JdbcJudgeGateway` thật, xác nhận RUN không tạo `tbl_submission`, SUBMIT tạo record/snapshot đầy đủ.
- `expiresAt` và query scheduler chỉ dùng Deadline làm cutoff khi không cho Late; Late được phép giữ Timer theo `base_expires_at`; test gồm untimed bắt đầu sau Deadline và timed vượt Deadline rồi Auto Submit đúng hạn Timer.
- Feedback được lưu trong immutable revision/attempt, chuyển sang request judge; kết quả bị redaction hai tầng theo policy ban đầu của job và policy hiện tại. `TESTCASE_STATUS` không trả testcase I/O; hidden không cho details vẫn bị `JudgePolicy` che.
- Student history chỉ chọn Official Submitted/Graded của chính mình; detail kiểm tra class/assignment/owner attempt và dùng frozen questions. Điểm lấy từ published evaluation, đáp án theo `ViewAnswersMode`.
- Polling kiểm `JobReference`: owner, attempt, resource, scope `CLASS_ASSIGNMENT`, revision và frozen question; official job còn phải thuộc published evaluation. RUN có thể đọc lại sample result của chính attempt; generation rejudge chưa công bố không được đọc qua endpoint mới.
- Clone giữ Start Time/Deadline và các cấu hình hợp lệ, tạo Draft với ID/questions/options độc lập, không sao chép grades/history; source/target owner checks đều có. Đã đọc regression clone trong XML, không phát hiện thêm lỗi trong thay đổi này.

## Review Rejudge original/new published

- `RejudgeRequest` mặc định `ORIGINAL`; Teacher/Admin đều truyền target. `LATEST_PUBLISHED` đọc immutable revision và kiểm assignment/provenance. Câu Code gốc phải còn trong revision đích; lỗi xảy ra trong cùng transaction nên không commit một phần.
- Source, language, câu objective/Essay, max points và question weight giữ theo Attempt gốc. Chỉ testcase/case weights/limits và policy của selected revision được dùng cho generation mới; `tbl_assignment_code_job.revision_id` lưu context riêng, có FK và trigger cấm thay identity/context. Hai migration track có nội dung 180014 giống nhau; chưa triển khai lên DB ứng dụng.
- `AssignmentCodeJudge.completed` chỉ xét latest generation chưa apply, rồi lấy case weights từ chính persisted target revision, không từ working authoring hiện tại. Published evaluation cũ không bị pending generation ghi đè.
- `codeResult` official yêu cầu đúng published part job/question/revision và revision thuộc cùng Assignment; RUN vẫn yêu cầu original Attempt revision. `JudgeGateway.rejudge` giữ owner/resource/attempt/question/request key/source/language và tái dùng Official Submission; generation mới phải lớn hơn hiện hành. Không phát hiện thêm P1/P2 trong luồng đã đọc.
- Legacy evaluation parts không có `judgeRevisionId` bị từ chối đọc job details; grade snapshot cũ vẫn đọc được. Đây là giới hạn fail-closed đã được implementer nêu rõ, không dùng fallback lấy context hiện tại.

## Bằng chứng GREEN đã đọc trực tiếp

`backend/target/verification/assignment-ac-wave-green.json`, source SHA-256 `65105eec0e83ad4b2c1496f042b31ea29c43ba9fa504a076ba93d600140d157c`, snapshot `assignment-ac-wave-green-ic2cr08g`: XML `AssignmentAcceptanceIntegrationTest` xác nhận các method sau PASS:

- `futurePublicationIsPersistedScheduledAndActivatesAtExactServerStart`
- `blockedSchedulesCannotStarveAnActiveAssignmentOutsideTheFirstHundredRows`
- `elapsedDeadlineCannotBeReintroducedBeforeALateAttemptsStartTime`
- `rejudgeSelectsLatestPublishedCasesAndWeightsButKeepsOriginalIdentitySourceAndHistory`
- `ownCodePollingRequiresPublishedGenerationAndRedactsHiddenIo`
- `studentReadsOnlyOwnOfficialHistoryAndFrozenClosedSubmissionUnderReleasePolicy`
- `threeTeacherFeedbackLevelsAreAcceptedPersistedAndSnapshotted`
- `cloneKeepsSettingsAndIndependentQuestionSnapshotsWithoutLearnerHistory`

Lượt này tổng thể **chưa PASS**: 63 tests, 0 failures, 1 error ở SQL fixture quota audit (`target_id` text/UUID); vì vậy chỉ dùng các testcase XML đã đạt làm bằng chứng cho đúng hành vi tương ứng. `assignment-ac-wave-green2` cũng chưa đạt toàn lượt (1 failure); không dùng làm chứng nhận gate.

`backend/target/verification/judge-revision-rejudge-green.json`, source `d343dbc69a77abd918c74ab6a38cfed3663d565296e0ac7532de50e31d870c1a`: 18/18 PASS, 0 errors/failures/skip. Đã kiểm method `explicitRejudgeCanSelectAnotherRevisionWhileKeepingOfficialIdentityAndOriginalSnapshot` và `rejudgeCreatesNewJobWithoutCountingAnotherOfficialSubmission` trong XML thật.

## Giới hạn và trạng thái

Ba phát hiện được sửa ở code đã review và các regression tương ứng đã PASS trong snapshot nêu trên; chưa thấy P1/P2 khác trong phạm vi đã đọc. Implementer đang chạy lại Assignment trên cả hai track sau khi sửa fixture và tăng assertion; báo cáo này chưa thay thế gate cuối hoặc khẳng định release. SMTP, sandbox worker thật và UI không thuộc bằng chứng của review này.
