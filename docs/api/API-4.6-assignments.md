# API 4.6 — Bài tập trong lớp

Nguồn: SRS ver 1.1 mục 4.6, [AssignmentController](../../backend/src/main/java/com/algolearner/platform/assignment/AssignmentController.java), [AssignmentAdministrationController](../../backend/src/main/java/com/algolearner/platform/assignment/AssignmentAdministrationController.java), [AssignmentDtos](../../backend/src/main/java/com/algolearner/platform/assignment/AssignmentDtos.java). Xem [quy ước chung](README.md). Base path của các route chính: `/api/v1/classrooms/{classId}/assignments`; mọi route cần Bearer token.

## Soạn và quản lý bài tập

| Method | Đường dẫn tiếp theo | Body / query | Thành công | Quyền |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/` | — | 200 `ApiResponse<AssignmentSummaryView[]>` | Chủ lớp/thành viên hợp lệ |
| POST | `/` | `CreateAssignmentRequest` | 201 `ApiResponse<AssignmentDetailView>` | Teacher chủ lớp |
| GET | `/{assignmentId}` | — | 200 `ApiResponse<AssignmentDetailView>` | Chủ lớp/thành viên hợp lệ |
| PUT | `/{assignmentId}` | `UpdateAssignmentRequest` | 200 `ApiResponse<AssignmentDetailView>` | Teacher chủ lớp |
| DELETE | `/{assignmentId}` | — | 204 | Teacher chủ lớp, khi chưa có attempt và chưa có revision đã công bố |
| PATCH | `/{assignmentId}/publish` | — | 200 `ApiResponse<AssignmentDetailView>` | Teacher chủ lớp |
| PATCH | `/{assignmentId}/close` | — | 200 `ApiResponse<AssignmentDetailView>` | Teacher chủ lớp |
| POST | `/{assignmentId}/publish-grades` | — | 204 | Teacher chủ lớp |
| POST | `/{assignmentId}/clone` | `targetClassroomId` query | 201 `ApiResponse<AssignmentDetailView>` | Teacher sở hữu cả hai lớp |
| POST | `/{assignmentId}/questions` | `AddQuestionRequest` | 201 `ApiResponse<QuestionView>` | Teacher chủ lớp |
| PUT | `/{assignmentId}/questions/{questionId}` | `AddQuestionRequest` | 200 `ApiResponse<QuestionView>` | Teacher chủ lớp |
| DELETE | `/{assignmentId}/questions/{questionId}` | — | 204 | Teacher chủ lớp |
| PUT | `/{assignmentId}/question-order` | JSON array UUID đầy đủ | 204 | Teacher chủ lớp |
| PATCH | `/{assignmentId}/questions/{questionId}/code-case-weights` | `CodeCaseWeightsRequest` | 200 `ApiResponse<CodeCaseWeightsView>` | Teacher chủ lớp |
| POST | `/{assignmentId}/students/{studentId}/attempt-grants` | `AttemptGrantRequest` | 204 | Teacher chủ lớp |

`CreateAssignmentRequest`/`UpdateAssignmentRequest` có cùng trường: `title` bắt buộc (tối đa 255 ký tự), `description`, `type=GRADED|UNGRADED`, `startTime`, `deadline`, `allowLateSubmission`, `latePenaltyPercent`, `durationMinutes`, `timeExpiredAction=AUTO_SUBMIT|LOCK_WITHOUT_SUBMIT`, `maxAttempts`, `scoringPolicy=HIGHEST|LATEST`, `shuffleQuestions`, `shuffleAnswers`, `gradePublishMode=IMMEDIATE|AFTER_DEADLINE|MANUAL`, `viewAnswersMode=ALWAYS|AFTER_DEADLINE|AFTER_SUBMIT|NEVER`, `codeFeedbackLevel=PASS_FAIL|TESTCASE_STATUS|FULL_DETAILS`. `SUMMARY` và `DETAILED` là alias legacy của feedback. Thời gian gửi dạng UTC `Z`; `deadline` phải sau `startTime`, duration dương, `maxAttempts=0` nghĩa là không giới hạn. `latePenaltyPercent` hiện chỉ chấp nhận `0`/null; cho nộp muộn không tự trừ điểm. `AFTER_DEADLINE` cần `deadline`.

`AddQuestionRequest` gồm `questionType=CODE|SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE|ESSAY`, `title`, `prompt`, `points` không âm, `gradingPolicy=ALL_OR_NOTHING|PARTIAL_CREDIT`, `essayFormat=TEXT_ONLY|FILE_ONLY|TEXT_AND_FILE`, `explanation`, `codeTemplate`, `codeLanguage`, `options[{content,isCorrect}]`, `publicProblemId`, `testCases[{input,expectedOutput,sample,allowDetails}]`, `questionWeight`, `codeCaseWeights`. Các trường áp dụng theo loại câu hỏi; testcase/correct answer thuộc dữ liệu Teacher, không được dựa vào `QuestionView` của Student để lấy đáp án ẩn. `CodeCaseWeightsRequest` là `{ "weights": [1, 2], "reason": "..." }`, lý do bắt buộc tối đa 500 ký tự. Chỉnh sửa chỉ thay working copy; revision đã công bố và attempt đang có giữ snapshot riêng. Sau khi chỉnh, Teacher phải publish revision mới để áp dụng cho attempt mới.

Bài tập có trạng thái `DRAFT`, `SCHEDULED`, `PUBLISHED`, `CLOSED`. Publish trước `startTime` tạo `SCHEDULED`; tới giờ bắt đầu mới mở cho Student. Student trước giờ mở chỉ thấy metadata được phép trong danh sách, không thấy câu hỏi/cấu hình nhạy cảm ở API chi tiết. Đã có revision công bố hoặc attempt thì không xóa vật lý được; dùng close để giữ lịch sử. Clone tạo bài `DRAFT` ở lớp đích, không sao chép lịch sử nộp hay thông báo. `AttemptGrantRequest` là `{ "extraAttempts": 1, "reset": false, "reason": "..." }`; cấp/reset quota có audit và không xóa bài nộp cũ.

## Làm bài, autosave và kết quả của chính mình

| Method | Đường dẫn tiếp theo | Body | Thành công | Quyền |
| :--- | :--- | :--- | :--- | :--- |
| POST | `/{assignmentId}/attempts` | — | 201 `ApiResponse<AttemptView>` | Student thành viên active; bắt đầu hoặc resume |
| GET | `/{assignmentId}/attempts/active` | — | 200 `ApiResponse<AttemptView>` | Student có attempt active |
| PUT | `/{assignmentId}/attempts/{attemptId}/draft` | `SaveDraftAnswerRequest` | 200 `ApiResponse<AnswerView>` | Student sở hữu attempt đang mở |
| POST | `/{assignmentId}/attempts/{attemptId}/runs` | `{questionId,source}` | 202 `ApiResponse<UUID>` job ID | Student sở hữu attempt; sample RUN |
| GET | `/{assignmentId}/attempts/{attemptId}/jobs/{jobId}` | — | 200 `ApiResponse<EvaluationResult>` | Student sở hữu job đúng attempt/câu hỏi/phạm vi |
| POST | `/{assignmentId}/attempts/{attemptId}/submit` | — | 200 `ApiResponse<AttemptView>` | Student sở hữu attempt đang mở |
| GET | `/{assignmentId}/attempts` | — | 200 `ApiResponse<AttemptView[]>` | Student; chỉ lịch sử chính thức của mình |
| GET | `/{assignmentId}/attempts/{attemptId}` | — | 200 `ApiResponse<AttemptView>` | Student; chỉ lượt nộp chính thức của mình |

`SaveDraftAnswerRequest`: `questionId` bắt buộc, `selectedOptions?: UUID[]` cho trắc nghiệm, `textAnswer?`, `codeSubmission?`, `fileId?` cho tự luận, `expectedVersion?`, `clientMutationId?`. Các trường `selectedOptionIds`/`fileUrl` còn tồn tại để đọc tương thích cũ; client mới dùng UUID và `fileId`. Với file tự luận, upload trước qua `POST /api/v1/classrooms/{classId}/files?purpose=ESSAY_SUBMISSION` (multipart field `file`), chờ trạng thái `READY`, rồi gửi `fileId`; URL tự do bị từ chối. `AnswerView.version` hỗ trợ phát hiện bản nháp cũ; gửi lại cùng `clientMutationId` không tạo bản ghi mới. Lỗi version yêu cầu tải lại đáp án trước khi sửa.

`AttemptView` có `id`, `assignmentId`, `studentId`, `attemptNumber`, `status`, `startedAt`, `submittedAt`, `isLate`, điểm, `remainingSeconds`, `answers`, `revisionId`, `snapshotProvenance`, `questions`, `codeJobs`. `questions` và đáp án được lọc theo quyền/grade release. Attempt đã nộp vẫn đọc được khi bài `CLOSED`, nhưng Student không đọc attempt của người khác. RUN chỉ chấm sample, tối đa 5 lần/phút, không tạo official submission; SUBMIT chấm bộ testcase đã snapshot và tính quota. `EvaluationResult` gồm `jobId`, `generation`, `verdict`, `passed`, `total`, `cases`; nội dung testcase ẩn và output bị lọc theo chính sách feedback/snapshot. Polling phải dùng đường dẫn đủ cả `assignmentId`, `attemptId`, `jobId`.

Khi hết thời gian, `AUTO_SUBMIT` tạo lượt nộp chính thức từ bản nháp; `LOCK_WITHOUT_SUBMIT` khóa attempt, giữ nháp và không tính là lượt nộp chính thức. Teacher phải cấp lượt mới nếu cần. `allowLateSubmission=true` cho bắt đầu/nộp sau deadline khi còn điều kiện khác; `isLate` phản ánh thời điểm thực tế. Điểm/đáp án của Student chỉ hiện theo `gradePublishMode`, `viewAnswersMode` và chính sách feedback được phép; kết quả rejudge chưa công bố không thay điểm đang hiển thị.

## Chấm, lịch sử và Rejudge

| Method | Đường dẫn tiếp theo | Body | Thành công | Quyền |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/{assignmentId}/submissions` | — | 200 `ApiResponse<AttemptView[]>` | Teacher chủ lớp |
| GET | `/{assignmentId}/submissions/{attemptId}/evaluations` | — | 200 `ApiResponse<Evaluation[]>` | Teacher chủ lớp |
| POST | `/{assignmentId}/submissions/{attemptId}/grade-essay` | `GradeEssayRequest` | 204 | Teacher chủ lớp |
| POST | `/{assignmentId}/submissions/{attemptId}/override-grade` | `OverrideGradeRequest` | 204 | Teacher chủ lớp |
| POST | `/{assignmentId}/rejudge` | `RejudgeRequest?` | 204 | Teacher chủ lớp |

`GradeEssayRequest`: `{ questionId, score, feedback? }`; `OverrideGradeRequest`: `{ overriddenScore, reason }`. Điểm thủ công, override và các lần chấm lại được lưu theo thế hệ đánh giá, không ghi đè lịch sử. Rejudge chỉ áp dụng lượt nộp chính thức, giữ source/language/định danh và điểm gốc của attempt. `RejudgeRequest`: `{ "reason": "Sửa testcase", "target": "ORIGINAL" }`; `target` có `ORIGINAL` (mặc định khi bỏ trống body/trường) hoặc `LATEST_PUBLISHED` (revision mới nhất đã publish, không dùng working copy). `LATEST_PUBLISHED` yêu cầu revision tin cậy và câu CODE tương ứng; dữ liệu legacy thiếu snapshot tin cậy không được chấm lại. Điểm chính thức cũ còn nguyên cho tới khi thế hệ mới hợp lệ được áp dụng/công bố.

Admin active có ba route riêng dưới `/api/v1/admin/classrooms/{classId}/assignments/{assignmentId}`: `POST /rejudge` (204, **bắt buộc** `{reason,target?}`), `GET /submissions/{attemptId}/evaluations` (200 `ApiResponse<Evaluation[]>`), `PATCH /questions/{questionId}/code-case-weights` (200 `ApiResponse<CodeCaseWeightsView>`). Admin không trở thành Teacher hay thành viên lớp; service kiểm tra role Admin hiện hành và ghi audit người thực hiện. Không dùng route Teacher với Bearer Admin.

Các lỗi cần xử lý: `400` body/UUID sai, xung đột `expectedVersion` (`VALIDATION_FAILED`) hoặc trạng thái không cho phép; `401` chưa xác thực; `403` sai chủ lớp/Student hoặc snapshot không đủ tin cậy; `404` tài nguyên không thuộc phạm vi; `422` cấu hình bài tập không hợp lệ; `429` RUN quá giới hạn; `503` worker/storage/scanner không sẵn sàng. Mã cụ thể lấy từ `ApiErrorResponse.errorCode` của backend.
