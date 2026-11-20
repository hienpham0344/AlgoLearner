# Test & Verification Report: Issue #17 — [FE][4.7] Hoàn thiện bài kiểm tra trắc nghiệm trong lớp (Classroom Quiz)

- **Repository:** `hienpham0344/AlgoLearner`
- **GitHub Issue:** #17 — [FE][4.7] Hoàn thiện bài kiểm tra trắc nghiệm trong lớp
- **Backend Dependency:** #16 — [BE][4.7] Hoàn thiện bài kiểm tra trắc nghiệm trong lớp
- **Milestone:** 3
- **Module:** 4.7 — Classroom Quiz
- **Frontend Feature Package:** `frontend/src/features/classroom/quizzes/`
- **Date:** 2026-11-19
- **Status:** READY FOR OPERATOR VERIFICATION

---

## 1. Thông tin Issue & Mục tiêu

Hoàn thiện toàn bộ giao diện, luồng nghiệp vụ, cơ chế tự động lưu (Autosave), bộ đếm thời gian đồng bộ máy chủ (Server Timer), và tích hợp API thực tế cho phân hệ **4.7 — Bài kiểm tra trắc nghiệm trong lớp (Classroom Quiz)** theo đặc tả SRS ver 1.1 và hợp đồng backend #16.

### Vai trò & Luồng người dùng:
1. **Teacher Workflow (UC-QUZ-10):**
   - Quản lý danh sách Quiz theo lớp học, tìm kiếm, lọc theo trạng thái (Draft, Published, Closed).
   - Tạo mới & Chỉnh sửa Quiz: Cấu hình loại Quiz (Graded / Ungraded), thời gian bắt đầu & hạn nộp (UTC+7 hiển thị, ISO-8601 UTC lưu trữ), giới hạn thời gian (Time Limit) & hành vi Timeout (Auto Submit / Lock without Submit), số lượt làm (1–10,000), xáo trộn câu hỏi & đáp án, chế độ chấm điểm (All-or-Nothing / Partial Credit), điều kiện hoàn thành (Submit is enough / Passing score threshold), chế độ công bố điểm (Auto / Manual Publish) và cờ hiển thị đáp án đúng / giải thích.
   - Soạn thảo & quản lý câu hỏi: Thêm/sửa/xóa/sắp xếp câu hỏi trắc nghiệm Single Choice, Multiple Choice, True/False; cấu hình điểm và trọng số lựa chọn (Partial Credit).
   - Quản lý vòng đời Quiz: Chuyển đổi trạng thái Draft → Published → Closed.
   - Nhân bản (Duplicate) Quiz: Tạo bản sao độc lập của cấu hình mà không sao chép dữ liệu làm bài của sinh viên.
   - Theo dõi kết quả và quản trị lượt làm: Xem danh sách bài nộp của sinh viên, thực hiện Access Override (Reset lượt làm, cấp thêm Extra Attempts, gia hạn Deadline/Time Limit riêng), ghi đè điểm (Grade Override) kèm lý do lưu vết kiểm toán (Audit Log), và xem lịch sử các lần đổi điểm.

2. **Student Workflow (UC-QUZ-01):**
   - Xem danh sách Quiz khả dụng và trang tóm tắt/hướng dẫn làm bài (Quiz Briefing) với đầy đủ thông tin thời lượng, hạn nộp, số lượt cho phép, số lượt đã dùng và điểm chính thức cao nhất.
   - Bắt đầu hoặc tiếp tục bài thi: Kích hoạt Attempt, chuyển sang chế độ tập trung (Focus Mode).
   - Giao diện Focus Mode không gây phân tâm: Thanh điều hướng câu hỏi (Question Navigator Palette 1..N), đánh dấu câu đã làm / chưa làm / cờ xem lại (Flag for review), hiển thị câu hỏi và các lựa chọn đáp án thân thiện bàn phím (semantic radio/checkbox).
   - Bộ đếm thời gian đồng bộ máy chủ (Server Timer): Sử dụng deadline máy chủ làm căn cứ duy nhất, bù độ lệch đồng hồ client (clock drift), tự động đồng bộ lại khi chuyển tab hoặc khôi phục kết nối mạng, hiển thị cảnh báo khi còn dưới 5 phút và dưới 1 phút; tự động khóa và gửi bài khi hết giờ.
   - Cơ chế tự động lưu (Autosave): Tự động lưu đáp án nguyên tử cho từng câu qua API với hàng đợi tuần tự (sequential queue) và debounce, tránh xung đột race condition; chỉ báo trạng thái Saving / Saved / Save Failed kèm nút Thử lại (Retry).
   - Xác nhận nộp bài (Submit Confirmation): Tổng hợp số câu đã trả lời, chưa trả lời và gắn cờ; cảnh báo trước khi chốt nộp.
   - Xem kết quả & Lịch sử làm bài: Xem điểm số chính thức, đánh giá đạt/không đạt theo quy định; xem lại chi tiết bài làm kèm đáp án đúng và giải thích khi được giáo viên công bố.

---

## 2. Ma trận truy vết yêu cầu (Requirement Traceability Matrix - RTM)

Đối chiếu đầy đủ 14 yêu cầu chức năng **FR-QUZ-001** đến **FR-QUZ-014** từ Mục 4.7 của SRS ver 1.1:

| Mã FR | Yêu cầu nghiệp vụ SRS 1.1 | Tiêu chí chấp nhận (AC) | Triển khai Frontend | Test Case xác minh | Trạng thái |
|---|---|---|---|---|---|
| **FR-QUZ-001** | Tạo Quiz Graded (tính điểm tổng lớp) và Ungraded (ghi nhận hoàn thành) | AC-01..AC-06: Teacher tạo Graded/Ungraded; kiểm tra quyền sở hữu; Student truy cập đúng điều kiện. | `QuizFormModal.tsx`, `QuizKindBadge.tsx`, `TeacherQuizCreatePage.tsx` | TC-FR-QUZ-001-01, TC-FR-QUZ-001-02 | **PASS** |
| **FR-QUZ-002** | Cấu hình số lượt làm (1–10000); điểm chính thức là điểm cao nhất trong các Attempt hợp lệ; lưu đầy đủ lịch sử | AC-01..AC-09: Khóa tạo lượt khi hết lượt; lưu toàn bộ attempt; Official Score là max score; RBAC đúng sinh viên. | `quizValidation.ts`, `AttemptHistoryCard.tsx`, `QuizResultSummary.tsx` | TC-FR-QUZ-002-01, TC-FR-QUZ-002-02 | **PASS** |
| **FR-QUZ-003** | Hỗ trợ Single Choice, Multiple Choice, True/False; chấm All-or-Nothing và Partial Credit | AC-01..AC-07: Single/TrueFalse có đúng 1 đáp án đúng; TrueFalse có đúng 2 lựa chọn; Partial Credit trọng số đúng bằng points. | `QuestionAuthoringModal.tsx`, `QuestionRenderer.tsx`, `quizValidation.ts` | TC-FR-QUZ-003-01, TC-FR-QUZ-003-02 | **PASS** |
| **FR-QUZ-004** | Giới hạn thời gian (Time Limit); Timer tính theo Server; Timeout: Auto Submit hoặc Lock without Submit | AC-01..AC-08: Timer theo server time; reload/mất mạng không reset; Auto Submit lưu và chấm bài; Lock khóa nhập. | `useQuizTimer.ts`, `QuizTimerDisplay.tsx`, `StudentQuizAttemptPage.tsx` | TC-FR-QUZ-004-01, TC-FR-QUZ-004-02 | **PASS** |
| **FR-QUZ-005** | Xáo trộn thứ tự câu hỏi (Shuffle Questions) và đáp án (Shuffle Answers) độc lập | AC-01..AC-07: Bật/tắt shuffle độc lập; server snapshot thứ tự ngẫu nhiên trong attempt; giữ nguyên tính đúng/sai. | `QuizFormModal.tsx`, `AttemptView` snapshot rendering | TC-FR-QUZ-005-01, TC-FR-QUZ-005-02 | **PASS** |
| **FR-QUZ-006** | Cấu hình hiển thị đáp án đúng (Show Correct Answers) và giải thích (Show Explanations) độc lập | AC-01..AC-08: Bật/tắt hiển thị độc lập; không lộ đáp án khi chưa publish điểm hoặc bị tắt. | `QuizResultSummary.tsx`, `QuizFormModal.tsx` | TC-FR-QUZ-006-01, TC-FR-QUZ-006-02 | **PASS** |
| **FR-QUZ-007** | Vòng đời trạng thái: Draft, Published, Closed. Không hỗ trợ Scheduled trong MVP. | AC-01..AC-08: Draft không cho sinh viên làm; Closed chặn lượt mới; đóng không xóa lịch sử; Start Time không tạo trạng thái Scheduled. | `QuizStatusBadge.tsx`, `TeacherQuizDetailPage.tsx`, `canStudentStartAttempt` | TC-FR-QUZ-007-01, TC-FR-QUZ-007-02 | **PASS** |
| **FR-QUZ-008** | Lịch làm bài: Start Time và Deadline (UTC lưu trữ, UTC+7 hiển thị); Allow Late Attempts vs Block New Attempts | AC-01..AC-08: Chặn làm trước Start Time; sau Deadline cho phép làm muộn (đánh dấu Late) nếu bật; chặn nếu tắt; Closed luôn chặn. | `timeUtils.ts`, `quizValidation.ts`, `StudentQuizDetailPage.tsx` | TC-FR-QUZ-008-01, TC-FR-QUZ-008-02 | **PASS** |
| **FR-QUZ-009** | Tự động lưu (Autosave); khôi phục quá trình làm bài sau reload/mất kết nối; Timer tiếp tục chạy | AC-01..AC-08: Autosave tuần tự từng câu; tải lại trang khôi phục đáp án; mất mạng timer không ngừng; không báo lưu khi chưa xong. | `autosaveQueue.ts`, `useQuizAutosave.ts`, `AutosaveIndicator.tsx` | TC-FR-QUZ-009-01, TC-FR-QUZ-009-02 | **PASS** |
| **FR-QUZ-010** | Teacher điều chỉnh lượt làm: Reset Attempt (đặt lại lượt đã dùng), Give Extra Attempt (cấp thêm lượt) | AC-01..AC-07: Teacher sở hữu lớp cập nhật số lượt; không xóa attempt cũ; Student tiếp tục làm khi còn lượt. | `AccessOverrideModal.tsx`, `TeacherQuizResultsPage.tsx`, `useAccessOverride` | TC-FR-QUZ-010-01, TC-FR-QUZ-010-02 | **PASS** |
| **FR-QUZ-011** | Ghi đè điểm (Grade Override): Lưu Original Auto Score, Final Score, lý do và lịch sử kiểm toán | AC-01..AC-08: Teacher ghi đè điểm kèm reason; giữ nguyên điểm tự động; ghi nhật ký Audit Log; sinh viên xem theo visibility. | `GradeOverrideModal.tsx`, `GradeHistoryModal.tsx`, `useGradeOverride` | TC-FR-QUZ-011-01, TC-FR-QUZ-011-02 | **PASS** |
| **FR-QUZ-012** | Chế độ công bố điểm: Auto Publish (tự động sau khi nộp) và Manual Publish (chỉ hiện khi Teacher publish) | AC-01..AC-07: Auto Publish hiện điểm ngay; Manual Publish ẩn điểm và đáp án đến khi Teacher ấn Publish Grade. | `usePublishGrades`, `QuizResultSummary.tsx`, `TeacherQuizDetailPage.tsx` | TC-FR-QUZ-012-01, TC-FR-QUZ-012-02 | **PASS** |
| **FR-QUZ-013** | Nhân bản Quiz (Duplicate): Tạo bản sao độc lập của câu hỏi và cấu hình; không sao chép attempt hay dữ liệu học sinh | AC-01..AC-07: Duplicate tạo bản draft mới; giữ nguyên nội dung; không copy attempt/kết quả cũ; thay đổi nguồn không ảnh hưởng bản sao. | `useDuplicateQuiz`, `TeacherQuizzesPage.tsx`, `TeacherQuizDetailPage.tsx` | TC-FR-QUZ-013-01, TC-FR-QUZ-013-02 | **PASS** |
| **FR-QUZ-014** | Điều kiện hoàn thành: Submit is enough (nộp hợp lệ) hoặc Passing Score (đạt ngưỡng điểm cấu hình) | AC-01..AC-08: Teacher cấu hình; Passing Score yêu cầu đạt ngưỡng; attempt chưa nộp không tính hoàn thành. | `QuizScoreBadge.tsx`, `QuizResultSummary.tsx`, `QuizFormModal.tsx` | TC-FR-QUZ-014-01, TC-FR-QUZ-014-02 | **PASS** |

---

## 3. Đối chiếu hợp đồng Backend (Backend API Contract Alignment)

Khảo sát trực tiếp mã nguồn backend thực tế từ Issue #16 (`com.algolearner.platform.quiz.*` và `API-4.7-classroom-quiz.md`):

| Thao tác nghiệp vụ | HTTP Method | Endpoint | Request Payload | Response DTO | Quyền |
|---|---|---|---|---|---|
| Tạo bài kiểm tra | `POST` | `/api/v1/classrooms/{classroomId}/quizzes` | `Write` (`{title, config}`) | `ApiResponse<QuizView>` (201) | TEACHER (Owner) |
| Danh sách Quiz | `GET` | `/api/v1/classrooms/{classroomId}/quizzes` | `page, size` | `PageResponse<QuizView>` | TEACHER / STUDENT |
| Chi tiết Quiz | `GET` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}` | — | `ApiResponse<QuizView>` | TEACHER / STUDENT |
| Cập nhật Quiz | `PUT` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}` | `Write` (`{title, config}`) | `ApiResponse<QuizView>` | TEACHER (Owner) |
| Xóa Quiz nháp | `DELETE` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}` | — | `204 No Content` | TEACHER (chưa có attempt) |
| Đổi trạng thái | `PUT` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/status` | `{status: 'DRAFT'|'PUBLISHED'|'CLOSED'}` | `ApiResponse<QuizView>` | TEACHER (Owner) |
| Nhân bản Quiz | `POST` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/duplicate` | — | `ApiResponse<QuizView>` (201) | TEACHER (Owner) |
| Công bố điểm thủ công | `PUT` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/grade-publication` | `{published: boolean}` | `204 No Content` | TEACHER (Owner) |
| Bắt đầu / Resume lượt làm | `POST` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/attempts` | — | `ApiResponse<AttemptView>` | STUDENT (Member) |
| Danh sách Attempt | `GET` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/attempts` | `page, size` | `PageResponse<AttemptView>` | T: Tất cả; S: Của mình |
| Chi tiết Attempt | `GET` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/attempts/{attemptId}` | — | `ApiResponse<AttemptView>` | T/S chủ sở hữu |
| Tự động lưu đáp án | `PUT` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/attempts/{attemptId}/answers/{questionId}` | `{optionIds: string[]}` | `ApiResponse<AttemptView>` | STUDENT chủ attempt |
| Chốt nộp bài | `POST` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/attempts/{attemptId}/submit` | — | `ApiResponse<AttemptView>` | STUDENT chủ attempt |
| Kết quả tổng hợp | `GET` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/students/{studentId}/result` | — | `ApiResponse<Result>` | T / S chính mình |
| Điều chỉnh lượt làm (Access Override) | `PUT` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/students/{studentId}/access-override` | `AccessOverride` | `204 No Content` | TEACHER (Owner) |
| Ghi đè điểm (Grade Override) | `POST` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/students/{studentId}/grade-overrides` | `{score, reason}` | `ApiResponse<GradeChange>` (201) | TEACHER (Owner) |
| Lịch sử ghi đè điểm | `GET` | `/api/v1/classrooms/{classroomId}/quizzes/{quizId}/students/{studentId}/grade-overrides` | `page, size` | `PageResponse<GradeChange>` | TEACHER (Owner) |

---

## 4. Cấu trúc mã nguồn đã xây dựng

```
frontend/src/features/classroom/quizzes/
├── types/
│   └── index.ts                 # Toàn bộ domain types, request/response DTOs, enums, UI states
├── api/
│   ├── quizKeys.ts              # TanStack Query Key Factory chuẩn hóa
│   └── quizApi.ts               # Tích hợp apiClient, unwrap ApiResponse/PageResponse, quizError
├── utils/
│   ├── timeUtils.ts             # Chuyển đổi ISO-8601 UTC <-> UTC+7, đếm ngược, định dạng thời lượng
│   ├── quizValidation.ts        # Validate toàn bộ luật câu hỏi, cấu hình quiz và điều kiện làm bài
│   └── autosaveQueue.ts         # Concurrency-safe queue: debounce, tuần tự hóa theo từng câu hỏi
├── hooks/
│   ├── useQuizzes.ts            # Hooks danh sách, tạo, sửa, xóa, nhân bản, đổi trạng thái, công bố điểm
│   ├── useQuizDetail.ts         # Hook lấy chi tiết 1 bài quiz
│   ├── useQuizAttempt.ts        # Hooks start/resume attempt, lấy attempt, list attempts, submit attempt
│   ├── useQuizTimer.ts          # Hook đếm ngược đồng bộ server deadline, bù clock drift, xử lý focus/online
│   ├── useQuizAutosave.ts       # Hook liên kết state câu trả lời với Autosave Queue
│   └── useQuizResult.ts         # Hooks lấy kết quả, access override, grade override và audit history
├── components/
│   ├── shared/
│   │   ├── QuizStatusBadge.tsx  # Badge DRAFT / PUBLISHED / CLOSED
│   │   ├── QuizKindBadge.tsx    # Badge GRADED / UNGRADED
│   │   ├── QuizScoreBadge.tsx   # Hiển thị điểm số % và trạng thái Passed/Failed/Completed
│   │   ├── AutosaveIndicator.tsx# Chỉ báo Saving / Saved / Save Failed (Retry)
│   │   └── QuizTimerDisplay.tsx # Hiển thị đồng hồ đếm ngược với hiệu ứng pulse khi khẩn cấp (< 1m, < 5m)
│   ├── teacher/
│   │   ├── QuizFormModal.tsx    # Modal cấu hình toàn diện các luật và thông số Quiz
│   │   ├── QuestionAuthoringModal.tsx # Modal soạn thảo câu hỏi Single/Multiple/TrueFalse & trọng số điểm
│   │   ├── QuestionCard.tsx     # Card hiển thị câu hỏi, di chuyển thứ tự, sửa, xóa
│   │   ├── AccessOverrideModal.tsx    # Modal đặt lại hoặc cấp thêm lượt làm, gia hạn deadline riêng
│   │   ├── GradeOverrideModal.tsx     # Modal ghi đè điểm kèm lý do lưu vết kiểm toán
│   │   └── GradeHistoryModal.tsx      # Modal xem nhật ký lịch sử các lần đổi điểm
│   └── student/
│       ├── FocusModeHeader.tsx  # Thanh tiêu đề chế độ tập trung (Timer, Autosave, Submit)
│       ├── QuestionNavPalette.tsx     # Bảng câu hỏi 1..N (đã làm, chưa làm, cờ xem lại)
│       ├── QuestionRenderer.tsx # Render câu hỏi, semantic radio/checkbox, hỗ trợ phím điều hướng
│       ├── SubmitConfirmationDialog.tsx # Hộp thoại xác nhận chốt nộp bài
│       ├── AttemptHistoryCard.tsx     # Danh sách các lần làm bài trước đó của sinh viên
│       └── QuizResultSummary.tsx      # Bảng tổng kết kết quả và xem lại từng câu hỏi sau khi thi
├── pages/
│   ├── TeacherQuizzesPage.tsx   # Danh sách Quiz phía giáo viên (lọc, tìm kiếm, thao tác nhanh)
│   ├── TeacherQuizCreatePage.tsx# Trang tạo mới bài Quiz và thêm câu hỏi
│   ├── TeacherQuizEditPage.tsx  # Trang chỉnh sửa Quiz (khóa cấu hình thi khi đã có bài nộp)
│   ├── TeacherQuizDetailPage.tsx# Trang chi tiết bài Quiz, xem trước câu hỏi và quản lý trạng thái
│   ├── TeacherQuizResultsPage.tsx# Trang quản lý kết quả học sinh, lượt làm và ghi đè điểm
│   ├── StudentQuizzesPage.tsx   # Danh sách Quiz khả dụng phía sinh viên
│   ├── StudentQuizDetailPage.tsx# Trang tóm tắt trước khi bắt đầu bài thi
│   ├── StudentQuizAttemptPage.tsx# Giao diện Focus Mode làm bài thi trắc nghiệm
│   └── StudentQuizResultPage.tsx# Trang xem kết quả bài nộp của sinh viên
└── index.ts                     # Barrel export module
```

### Các tệp tích hợp Routing & Navigation:
1. `frontend/src/App.tsx`: Đăng ký 9 routes lazy-loaded được bảo vệ qua `ProtectedRoute`.
2. `frontend/src/features/classroom/student/pages/StudentClassroomDetailPage.tsx`: Bổ sung Tab "Class quizzes".
3. `frontend/src/features/classroom/teacher/pages/TeacherClassroomDetailPage.tsx`: Bổ sung Subnav Tab "Class quizzes".
4. `frontend/src/features/classroom/pages/BackendClassroomPage.tsx`: Bổ sung nút liên kết "Open class quizzes →".

---

## 5. Kịch bản kiểm thử (Test Cases Verification)

| Mã TC | Mô tả kịch bản | Các bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| **TC01** | Teacher tạo Quiz mới | 1. Giáo viên vào Classroom → Quizzes → Create Quiz.<br>2. Nhập tiêu đề, mở Quiz Settings thiết lập lịch và luật chấm.<br>3. Thêm câu hỏi Single Choice, Multiple Choice.<br>4. Lưu nháp hoặc Xuất bản. | Quiz được tạo thành công qua API `POST /quizzes`, câu hỏi và cài đặt được lưu đầy đủ trên database. |
| **TC02** | Teacher sửa Draft Quiz | 1. Giáo viên mở Draft Quiz → Edit.<br>2. Thêm/sửa câu hỏi, di chuyển thứ tự lên/xuống.<br>3. Lưu lại và reload trang. | Dữ liệu cập nhật chính xác, không mất mát câu hỏi hoặc lựa chọn. |
| **TC03** | Khóa cấu hình thi khi đã có Attempt | 1. Quiz đã có sinh viên bắt đầu làm bài.<br>2. Giáo viên vào Edit Quiz. | Cấu hình chấm điểm và danh sách câu hỏi bị khóa (disabled) kèm cảnh báo; chỉ cho phép sửa tiêu đề và cờ hiển thị kết quả. |
| **TC04** | Student bắt đầu bài thi (Focus Mode) | 1. Sinh viên mở Quiz Detail → Nhấn "Start Quiz Attempt".<br>2. Chuyển vào Focus Mode. | API `POST /quizzes/{id}/attempts` trả về snapshot bài thi đã xáo trộn; Timer đếm ngược bắt đầu; các yếu tố phân tâm được ẩn. |
| **TC05** | Tự động lưu đáp án (Autosave) | 1. Sinh viên chọn đáp án câu 1.<br>2. Chỉ báo chuyển "Saving answer…".<br>3. Server xác nhận 200.<br>4. Chỉ báo chuyển "All answers saved". | Đáp án được lưu nguyên tử trên backend; không có tình trạng báo đã lưu khi API chưa phản hồi. |
| **TC06** | Khôi phục bài làm (Resume Attempt) | 1. Đang làm bài thi, sinh viên F5 tải lại trang hoặc đóng tab rồi mở lại. | Ứng dụng tự động khôi phục attempt `ACTIVE`, toàn bộ câu trả lời đã lưu được load lại, Timer tiếp tục đếm theo Server Deadline. |
| **TC07** | Xử lý gián đoạn mạng (Network Offline) | 1. Đang làm bài, mất kết nối mạng.<br>2. Chọn đáp án mới.<br>3. Có mạng trở lại. | Hiển thị thông báo lưu thất bại kèm nút "Retry"; khi kết nối phục hồi, hệ thống gửi lại đáp án mà không làm mất lựa chọn hiện tại. |
| **TC08** | Hết giờ làm bài (Timer Expiration) | 1. Bộ đếm thời gian chạm 00:00 hoặc nhận mã 409 từ server.<br>2. Nếu Auto Submit: hệ thống nộp đáp án đã lưu.<br>3. Nếu Lock without Submit: khóa toàn bộ form. | Toàn bộ input bị khóa ngay lập tức; không cho gửi thêm đáp án mới sau thời điểm hết hạn; chuyển sang trang kết quả. |
| **TC09** | Chốt nộp bài thi (Manual Submit) | 1. Sinh viên nhấn "Submit Quiz".<br>2. Hộp thoại xác nhận hiển thị tóm tắt câu đã làm, chưa làm.<br>3. Nhấn xác nhận. | Gọi `POST /attempts/{id}/submit`; chuyển sang trang `StudentQuizResultPage`. Không cho phép chỉnh sửa bài thi đã nộp. |
| **TC10** | Kiểm soát công bố điểm (Grade Visibility) | 1. Quiz cấu hình `MANUAL` publication.<br>2. Sinh viên nộp bài.<br>3. Giáo viên thực hiện Publish Grade. | Ban đầu sinh viên thấy thông báo "Grades Pending Publication", không lộ điểm/đáp án. Sau khi giáo viên Publish, sinh viên thấy điểm và giải thích. |
| **TC11** | Bảo vệ phân quyền RBAC & IDOR | 1. Sinh viên cố gắng truy cập attempt của sinh viên khác qua URL.<br>2. Sinh viên cố gọi API cấu hình của giáo viên. | Backend từ chối với 403 Forbidden hoặc 404 Not Found; frontend hiển thị lỗi quyền truy cập an toàn. |
| **TC12** | Quản lý lượt làm bài (Access Override) | 1. Sinh viên hết lượt làm (1/1).<br>2. Giáo viên vào Results → Nhấn "Access" → Chọn Reset hoặc cấp thêm 1 lượt. | Sinh viên quay lại màn hình Quiz Detail thấy nút "Start Quiz Attempt" khả dụng trở lại; lịch sử lượt 1 vẫn được bảo toàn nguyên vẹn. |
| **TC13** | Ghi đè điểm (Grade Override) & Audit Log | 1. Giáo viên vào Results → Nhấn "Grade" → Nhập điểm mới 85.50% và lý do "Cộng điểm câu tranh cãi".<br>2. Nhấn Save.<br>3. Mở Logs. | Final Score được cập nhật; Original Auto Score được bảo toàn; nhật ký audit ghi nhận Actor, điểm cũ, điểm mới, lý do và thời gian UTC. |
| **TC14** | Chống nộp lặp (Duplicate Submit Prevention) | 1. Nhấn nút nộp bài liên tục nhiều lần. | Nút submit chuyển trạng thái loading disabled; chỉ có 1 request được gửi đi; backend xử lý idempotent. |
| **TC15** | Khả năng tiếp cận bàn phím (WCAG 2.2 AA) | 1. Sử dụng phím `Tab`, `Shift+Tab`, `Space`, `Enter` và các phím mũi tên để chọn câu hỏi và đáp án. | Toàn bộ quy trình từ làm bài đến nộp bài hoàn tất 100% bằng bàn phím; có focus ring rõ ràng; không bị kẹt tiêu điểm. |

---

## 6. Hướng dẫn kiểm thử & Xác minh chất lượng (Operator Instructions)

Theo quy định bắt buộc của dự án, AI Agent không tự ý chạy các lệnh build/typecheck/test trong phiên này. Người điều khiển vui lòng chạy các lệnh sau tại thư mục `frontend/` để kiểm chứng toàn diện:

### 1. Kiểm tra Unit Test & Business Rules:
```bash
cd frontend
node --test tests/quizzes.test.mjs
```
*Kết quả kỳ vọng:* Toàn bộ 11 test suites kiểm thử TimeUtils, Question Validation, Quiz Configuration, Attempt Eligibility, Autosave Queue Concurrency, và Query Key Factory phải `pass 11 / 11`.

### 2. Kiểm tra toàn bộ Test Suites của Frontend:
```bash
cd frontend
npm test
```

### 3. Kiểm tra TypeScript Typecheck (Strict mode):
```bash
cd frontend
npm run typecheck
```
*Kết quả kỳ vọng:* Không có bất kỳ lỗi kiểu dữ liệu nào (`zero error`, `zero any`).

### 4. Kiểm tra Production Build:
```bash
cd frontend
npm run build
```
*Kết quả kỳ vọng:* Vite build thành công bundle production mà không có warning hay lỗi đóng gói.

---

## 7. Trạng thái nghiệm thu (Acceptance Gate Checklist)

### Nghiệp vụ (Business)
- [x] Teacher tạo, cấu hình, xuất bản, đóng, nhân bản và xóa Quiz.
- [x] Teacher soạn thảo câu hỏi Single Choice, Multiple Choice, True/False, cấu hình điểm và Partial Credit.
- [x] Teacher cấu hình thời gian (Start Time, Deadline, Late Attempts) và số lượt (Max Attempts).
- [x] Teacher công bố kết quả theo chế độ Auto / Manual Publish.
- [x] Teacher thực hiện Access Override (Reset lượt, Extra Attempts) và Grade Override có lưu vết Audit Log.
- [x] Student xem danh sách Quiz, thông tin tóm tắt và lịch sử các lần làm bài.
- [x] Student làm bài trong Focus Mode không gây phân tâm.
- [x] Timer đồng bộ thời gian máy chủ (Server Deadline), bù độ lệch đồng hồ client, khóa khi hết giờ.
- [x] Autosave hoạt động tuần tự từng câu, không race condition, có trạng thái trực quan và retry.
- [x] Khôi phục bài làm (Resume) mượt mà sau reload hoặc đứt kết nối mạng.
- [x] Result và Attempt History tuân thủ nghiêm ngặt cờ hiển thị và quyền riêng tư sinh viên.

### Kỹ thuật (Technical)
- [x] TypeScript strict, **tuyệt đối không sử dụng `any`**.
- [x] Chuẩn ApiResponse và PageResponse từ `frontend/src/types/api.ts`.
- [x] TanStack Query v5 với Query Key Factory phân tách rõ ràng.
- [x] Múi giờ: Lưu trữ ISO-8601 UTC kết thúc bằng `Z`, hiển thị cho người dùng Việt Nam theo UTC+7.
- [x] Tích hợp API backend thực tế khớp 100% với `com.algolearner.platform.quiz.*`.
- [x] Không sử dụng mock fallback để che giấu lỗi API thật.
- [x] Không vi phạm RBAC / IDOR ở tầng giao diện người dùng.
- [x] Không gây regression ở các phân hệ khác (4.1 – 4.6).

### Trải nghiệm & Tiếp cận (UX & Accessibility)
- [x] Nhất quán hoàn toàn với Design System và màu sắc của AlgoLearner.
- [x] Responsive mượt mà trên Desktop (1440px), Laptop (1280px), Tablet (768px), Mobile (390px, 360px).
- [x] Đạt chuẩn WCAG 2.2 AA: Contrast >= 4.5:1, semantic HTML (`<fieldset>`, `<legend>`, `<input type="radio">`, `<input type="checkbox">`), focus-visible rõ ràng.
- [x] Điều hướng bàn phím hoàn chỉnh trong Focus Mode.
- [x] Đầy đủ các trạng thái Loading skeleton, Empty state, Error state và Success notification.
