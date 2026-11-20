# Báo cáo rà soát backend SRS 4.1–4.6

Ngày: 16/11/2026, giờ Việt Nam. Mốc mã nguồn: `614331a`.

Đây là kết quả kiểm tra trước khi sửa. Đợt khắc phục tiếp theo được ghi riêng tại
[báo cáo khắc phục P1](/home/hien/Project/AlgoLearner/docs/test-reports/2026-11-16-backend-p1-remediation.md).

## Kết luận

**Backend chưa đáp ứng đầy đủ SRS 4.1–4.6.** Phát hiện 22 vấn đề: **7 P1, 15 P2**; ngoài ra có các chức năng chưa triển khai và rủi ro đồng thời cần kiểm chứng PostgreSQL. Hai API có lỗi phân quyền chéo lớp; luồng Assignment còn lỗi bảo vệ đề, thời gian nộp, chấm điểm và bảo toàn lịch sử.

Build và các test được thực thi đã thành công: **179 test Java đạt, 49 test bị bỏ qua, 0 failure/error**; **9 test Python đạt**. Các probe bổ sung vẫn tái hiện được lỗi mà bộ test hiện tại chưa phát hiện. Không thể suy ra hệ thống đạt yêu cầu chỉ từ kết quả build.

Đây là rà soát và báo cáo; không sửa mã ứng dụng, migration hoặc test của repository, không commit/push. Thư mục `outputs/` chưa được Git theo dõi đã tồn tại trước khi rà soát và được giữ nguyên.

## Phạm vi và mức độ bằng chứng

Đối chiếu SRS gốc `docs/SRS ver 1.1.docx`, API Design Guidelines, Coding Standards và Security & Sandbox Rules. Rà soát 53 mã FR thuộc 4.1–4.6, controller/service/repository/DTO/entity, security dùng chung, migration hai track và test liên quan. Con số 53 là phạm vi yêu cầu được đọc, không phải số yêu cầu đã có test đạt.

- **Probe:** chạy service đã biên dịch với Mockito repository; một số probe dùng Jakarta Validator/JSON serialization. Xác nhận hành vi logic, không thay thế HTTP/JPA/PostgreSQL integration.
- **Mã/schema:** xác minh trực tiếp đường thực thi và ràng buộc schema; chưa tái hiện trên PostgreSQL.
- **Cần xác minh:** nhận diện lịch chạy đồng thời có rủi ro; không cộng vào 22 phát hiện đã xác nhận.

Không kết nối DB ứng dụng/Supabase, không chạy migration vào DB ngoài, không gửi email thật. Chưa xác minh runtime PostgreSQL/Flyway, HTTP tích hợp có DB, tải đồng thời, hiệu năng thực tế hoặc hành vi frontend.

## Tình trạng theo phân hệ

| Phân hệ | Phạm vi FR | Kết quả rà soát |
| --- | --- | --- |
| 4.1 Tài khoản/xác thực | 6 FR-ACC, 6 FR-AUTH | Luồng chính khá đầy đủ; lỗi giao nhau định danh email/username đã tái hiện. |
| 4.2 Profile/Dashboard | 5 FR-PRO | Profile/privacy/avatar có triển khai; Dashboard thiếu tích hợp Assignment, notification và audit đang có dữ liệu. |
| 4.3 Lớp học | 10 FR-CLS | Có owner/membership/invitation/join modes và khóa theo cặp; thiếu audit chuyển owner, còn rủi ro cập nhật đồng thời. |
| 4.4 Thông báo lớp | 4 FR-ANN | Có CRUD/ghim/tạo notification; API đọc notification không trả nội dung/đường dẫn; giới hạn title không nhất quán. |
| 4.5 Bài học | 5 FR-LES | Có CRUD/trạng thái/link/completion; lỗi IDOR, completion, archive/restore; thiếu xử lý file và một số tích hợp. |
| 4.6 Bài tập | 17 FR-ASG | Có khung CRUD/draft/attempt/chấm trắc nghiệm/tự luận; chưa hoàn chỉnh các bất biến quan trọng và nhiều cấu hình mới chỉ được lưu. |

## Phát hiện P1 — cần xử lý trước khi sử dụng chính thức

### AL-01 — Giáo viên xóa câu hỏi của Assignment thuộc lớp khác

- Vị trí: [AssignmentService.java:318](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:318).
- `deleteQuestion()` xác minh owner của `classroomId` nhưng chỉ tìm câu hỏi theo `questionId,assignmentId`, không xác minh Assignment thuộc lớp đó.
- Tái hiện: Teacher A gọi `DELETE /api/v1/classrooms/{classA}/assignments/{assignmentB}/questions/{questionB}`. Với ID hợp lệ của lớp B, câu hỏi B bị xóa. **Probe xác nhận lệnh delete được gọi.**
- Vi phạm FR-ASG-002 AC-06, FR-ASG-007 AC-07 và quy tắc IDOR. Cần truy vấn Assignment theo đồng thời ID và lớp trước khi sửa câu hỏi.

### AL-02 — Giáo viên gỡ liên kết Lesson thuộc lớp khác

- Vị trí: [LessonService.java:231](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:231).
- `unlinkItem()` chỉ kiểm tra owner lớp trên URL; truy vấn tiếp bằng `lessonId,itemId` không ràng buộc lớp.
- Tái hiện: Teacher A dùng `classA` cùng `lessonB,itemB` trong API DELETE liên kết. **Probe xác nhận liên kết B bị xóa.**
- Vi phạm FR-LES-002 AC-06/07. Cần scope Lesson bằng `lessonId,classroomId` trước khi truy cập liên kết.

### AL-03 — Chọn tất cả đáp án vẫn nhận trọn điểm Single Choice/True-False

- Vị trí: [AssignmentService.java:653](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:653).
- Bộ chấm chỉ kiểm tra chuỗi `selectedOptionIds` có chứa UUID đáp án đúng, không kiểm tra đúng một lựa chọn và không loại lựa chọn sai đi kèm.
- **Probe:** câu 10 điểm, gửi cả đáp án đúng lẫn sai, nhận `autoScore=10`.
- Vi phạm FR-ASG-014 AC-03/05. Cần parse danh sách UUID, xác minh thuộc câu hỏi và đúng số lượng trước khi chấm.

### AL-04 — Bỏ qua trạng thái đóng/hết giờ/archive khi nộp bài

- Vị trí: [AssignmentService.java:444](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:444), [LessonService.java:157](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:157).
- `submitAttempt()` kiểm tra chủ attempt, membership, trạng thái attempt và deadline; không kiểm tra Assignment CLOSED hoặc timer. `requireClassroom()` cũng không kiểm tra ARCHIVED; luồng Assignment không tra Lesson liên kết đã archive.
- **Probe xác nhận riêng:** Assignment CLOSED vẫn submit; timer hết với `LOCK_WITHOUT_SUBMIT` vẫn tạo bài nộp; lớp ARCHIVED vẫn submit. Việc Lesson archive không chặn Assignment được xác minh bằng luồng mã.
- Xử lý timeout hiện chỉ kích hoạt khi gọi `saveDraftAnswer()`; không có tiến trình tự nộp/khóa khi Student đóng tab hoặc ngừng gửi request. `getActiveAttempt()` vẫn trả attempt quá hạn.
- Vi phạm FR-ASG-003 AC-06, FR-ASG-005 AC-05, FR-ASG-006 AC-05, FR-ASG-010 AC-05/06/07, FR-LES-005 AC-05. Cần dùng chung điều kiện làm/nộp/autosave và có cơ chế xử lý timeout phía server.

### AL-05 — Student đọc được nội dung đề trước Start Time

- Vị trí: [AssignmentService.java:60](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:60).
- `get()` chỉ chặn DRAFT; trả `description`, toàn bộ câu hỏi/lựa chọn/code template dù `startTime` còn ở tương lai. Việc chặn bắt đầu attempt không ngăn đọc đề.
- **Probe:** Assignment mở sau một giờ vẫn trả prompt bí mật trong `questions`.
- Vi phạm FR-ASG-005 AC-02 và FR-ASG-006 AC-03. Trước giờ mở, response cho Student chỉ nên có tên và thời điểm mở theo SRS.

### AL-06 — Xóa câu hỏi có thể xóa luôn câu trả lời lịch sử

- Vị trí: [AssignmentService.java:318](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:318), [migration:180](/home/hien/Project/AlgoLearner/backend/src/main/resources/db/migration/V202611140001__classroom_modules_4_4_4_5_4_6.sql:180).
- API xóa câu hỏi không chặn khi đã có submission. FK `tbl_assignment_answer.question_id` dùng `ON DELETE CASCADE`; khi xóa câu hỏi, câu trả lời tương ứng trong bài nộp cũ bị xóa theo. Entity/schema cũng chưa có snapshot nội dung, quy tắc chấm và testcase cho attempt.
- **Bằng chứng mã/schema; chưa thực thi DELETE trên PostgreSQL.** Kết quả điểm cũ có thể còn nhưng không còn đủ câu hỏi/câu trả lời để kiểm toán.
- Vi phạm FR-ASG-007 AC-03/04/06 và yêu cầu bảo toàn lịch sử. Cần version/snapshot hoặc chặn thao tác phá dữ liệu đã được sử dụng.

### AL-07 — Student tự hoàn thành Lesson cần điều kiện

- Vị trí: [LessonService.java:242](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:242).
- `completeLesson()` không kiểm tra `completionMode`; luôn ghi `completed=true`. `AUTO_ITEMS` bị xử lý như MANUAL; `NONE` cũng không được phân biệt. Chưa có evaluator/listener hoàn thành dựa trên kết quả bài liên kết.
- **Probe:** Lesson AUTO_ITEMS được đánh dấu hoàn thành mà không kiểm tra Assignment/Quiz/Practice nào.
- Vi phạm FR-LES-003 AC-04/05. Cần kiểm tra mode và điều kiện phía backend, đồng thời cập nhật tự động từ kết quả các module liên kết.

## Phát hiện P2 — cần hoàn thiện nghiệp vụ và tích hợp

### AL-08 — Username dạng email chọn nhầm tài khoản khi đăng nhập/reset

- Vị trí: [AuthService.java:218](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/auth/AuthService.java:218), [AuthDtos.java:14](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/auth/AuthDtos.java:14).
- A có username `victim@example.test`; B có email đó. Đăng ký/validator và unique từng cột cho phép hai định danh này cùng tồn tại. Lookup ưu tiên email nên A nhập đúng mật khẩu của A vẫn được kiểm tra với B; luồng quên mật khẩu cũng có lookup tương tự.
- **Probe:** validator trả 0 lỗi; 4 lần `INVALID_CREDENTIALS`, lần 5 `ACCOUNT_TEMP_LOCKED`; A không tăng failure, B tăng 5 lần và bị khóa.
- FR-AUTH-001 AC-01/02, FR-AUTH-003. Cần namespace không giao nhau hoặc kiểm tra collision đối xứng khi đăng ký.

### AL-09 — Dashboard không đọc Assignment/submission đã có storage

- Vị trí: [JdbcDashboardRepository.java:56](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/profile/dashboard/JdbcDashboardRepository.java:56), dòng 33 và 75.
- `studentAssignments`, `teacherAssignments`, `ungraded`, `recentSubmissions` trả rỗng cố định; thống kê lớp vẫn gán các số Assignment/submission bằng 0. Tạo/nộp bài 4.6 không làm các mục này cập nhật.
- **Bằng chứng mã.** FR-PRO-003 AC-02/04, FR-PRO-004 AC-02/04. Cần truy vấn theo đúng Student hoặc các lớp Teacher sở hữu.

### AL-10 — Luồng đọc notification vẫn chỉ hỗ trợ invitation

- Vị trí: [JdbcDashboardRepository.java:63](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/profile/dashboard/JdbcDashboardRepository.java:63), [NotificationService.java:28](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/notification/NotificationService.java:28).
- Dashboard đếm mọi notification nhưng INNER JOIN invitation để lấy items; notification ANNOUNCEMENT/ASSIGNMENT có invitation null bị loại. Kiểm chứng riêng semantics của JOIN bằng SQLite: `total=1`, `items=0`; chưa phải PostgreSQL integration.
- API `/api/v1/notifications` có lấy notification mới, nhưng map sang `InvitationNotificationView`, bỏ `title,message,linkUrl,announcementId,assignmentId`; `classroomId` cũng null khi không có invitation. **Probe serialization xác nhận.** Client không có nội dung hoặc đích mở thông báo lớp.
- FR-PRO-003/004, FR-ANN-003 AC-03/04. Cần DTO/mapping đa loại và bộ lọc count/items nhất quán.

### AL-11 — Dashboard Admin bỏ qua audit đã lưu

- Vị trí: [JdbcDashboardRepository.java:78](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/profile/dashboard/JdbcDashboardRepository.java:78).
- `activities()` và `audits()` luôn rỗng dù account moderation đã ghi `tbl_account_audit_log`. Admin đình chỉ/mở lại tài khoản vẫn không có sự kiện trong Dashboard.
- **Bằng chứng mã.** FR-PRO-005 AC-03/05/06. Cần nối với nguồn audit thật và phân trang phù hợp.

### AL-12 — Chuyển Owner lớp không ghi Audit Log

- Vị trí: [ClassroomService.java:281](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/classroom/ClassroomService.java:281).
- `transferOwner()` chỉ kiểm tra Admin rồi `setOwner()`, không có audit/event/hook tương ứng. Test chuyển Owner cũng chưa kiểm tra audit.
- **Bằng chứng mã.** Trình tự SRS 4.3 và FR-ADM-003 AC-09 yêu cầu ghi vết chuyển quyền. Cần lưu actor, Owner cũ/mới, lớp và thời điểm cùng giao dịch.

### AL-13 — Restore biến Lesson Draft thành Published

- Vị trí: [LessonService.java:169](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:169).
- Restore luôn gán PUBLISHED, không lưu trạng thái trước archive. **Probe:** `DRAFT → ARCHIVED → restore` thành PUBLISHED, làm Student có thể đọc bản nháp chưa định xuất bản.
- Vi phạm FR-LES-005 AC-08. Cần lưu/phục hồi trạng thái trước archive. `LessonServiceTest` hiện còn kỳ vọng chính hành vi sai này.

### AL-14 — File bài học/bài tự luận mới là metadata tự khai

- Vị trí: [LessonService.java:174](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:174), [AssignmentService.java:432](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:432).
- Lesson lưu nguyên JSON URL/tên/loại/kích thước, chưa có upload/download qua File Storage Service. Essay nhận `fileUrl`, chưa xác minh tệp thật/quyền sở hữu và chưa ép `TEXT_ONLY/FILE_ONLY/TEXT_AND_FILE`.
- **Probe Lesson:** metadata `malware.exe`, loại PDF, URL `javascript:alert(1)`, kích thước `Long.MAX_VALUE` vượt qua validator và service. Không kết luận XSS đã thực thi vì chưa kiểm tra frontend.
- FR-LES-001 AC-03/04/08, FR-ASG-015 AC-01/02/03. Cần storage và kiểm tra bytes/type/size, tham chiếu tệp được cấp quyền và hình thức trả lời.

### AL-15 — Title thông báo hợp lệ vượt cột notification

- Vị trí: [AnnouncementService.java:74](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/announcement/AnnouncementService.java:74), [migration:10](/home/hien/Project/AlgoLearner/backend/src/main/resources/db/migration/V202611140001__classroom_modules_4_4_4_5_4_6.sql:10).
- DTO cho phép 255 ký tự nhưng notification thêm tiền tố 18 ký tự vào cột `VARCHAR(255)`. Title dài 238–255 ký tự, lớp có Student ACTIVE, sẽ vượt giới hạn và rollback. Publish Assignment có tiền tố tương tự cần kiểm tra cùng lúc.
- **Bằng chứng mã/schema; chưa chạy PostgreSQL.** FR-ANN-001 AC-01. Cần giới hạn/định dạng title notification nhất quán mà không làm thất bại thao tác hợp lệ.

### AL-16 — Partial Credit xóa điểm của các lựa chọn đúng

- Vị trí: [AssignmentService.java:687](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:687).
- Khi có bất kỳ đáp án sai, hàm trả 0 thay vì cộng điểm cho lựa chọn đúng theo Partial Credit. **Probe:** chọn một đúng và một sai nhận 0.
- Vi phạm FR-ASG-014 AC-04: lựa chọn đúng được cộng, lựa chọn sai không bị trừ. Cần áp dụng đúng quy tắc đã đặc tả và tránh sai số cộng điểm do làm tròn từng phần.

### AL-17 — Tự trừ điểm bài Late trái baseline SRS

- Vị trí: [AssignmentService.java:502](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:502).
- Có nhánh trừ `latePenaltyPercent` khi nộp trễ, lặp lại trong `recalculateFinalScore()`. Test `submitAttempt_AppliesLatePenaltyPercent` còn xác nhận 10 điểm bị trừ 20% còn 8.
- **Mã và test hiện tại, test đó đã chạy đạt.** FR-ASG-005 AC-04 yêu cầu Late không tự trừ điểm chỉ vì nộp trễ. Cần bỏ hành vi khỏi baseline hoặc có thay đổi SRS được chấp nhận trước khi giữ lại.

### AL-18 — Chấm điểm thiếu kiểm tra trạng thái/giới hạn

- Vị trí: [AssignmentService.java:517](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:517), [AssignmentDtos.java:91](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentDtos.java:91).
- `gradeEssay()` không yêu cầu attempt đã submit và không giới hạn score theo điểm tối đa câu hỏi. **Probe:** câu 10 điểm đang IN_PROGRESS được chấm 1000; attempt thành GRADED dù `submittedAt=null`, làm Student mất khả năng nộp.
- `recalculateFinalScore()` còn gán `finalScore` khi vẫn có câu chưa chấm; điều kiện hoàn tất điểm chưa tách rõ khỏi tổng tạm.
- FR-ASG-003/013/015/016. Cần chặn chấm bản nháp, kiểm tra miền điểm và chỉ hoàn tất Final Grade sau đủ điều kiện.

### AL-19 — Override/đổi điểm không lưu đầy đủ lịch sử

- Vị trí: [AssignmentService.java:543](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:543).
- Override thay trực tiếp `overriddenScore,overrideReason,finalScore`; lần tiếp theo ghi đè lần trước. Có giữ `autoScore` nhưng không có bản ghi lịch sử override với actor/thời điểm/điểm cũ-mới. Chấm lại Essay cũng ghi đè score/feedback.
- **Bằng chứng mã/schema.** FR-ASG-016 AC-08/09. Cần lịch sử thay đổi bất biến, không chỉ giữ giá trị cuối cùng và lý do cuối cùng.

### AL-20 — Rejudge chưa đánh giá lại bài và luôn tạo notification

- Vị trí: [AssignmentService.java:560](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:560).
- Rejudge chỉ gọi hàm cộng lại score đã lưu, không chấm lại đáp án hoặc chạy Code Judge/testcase version. Vòng lặp bỏ qua IN_PROGRESS nhưng vẫn bao gồm TIMED_OUT; không phân biệt bản nháp bị khóa với official submission.
- Notification được tạo không điều kiện, kể cả `oldScore == newScore`. **Probe xác nhận trường hợp điểm không đổi vẫn gọi notify.** Payload lưu có điểm chưa publish, nhưng API notification hiện cắt message theo AL-10; chưa xác nhận Student đọc được điểm qua endpoint hiện tại.
- FR-ASG-017 AC-03/04/05/06, FR-ASG-010 AC-06. Cần rejudge đúng official submission, có snapshot/lịch sử đánh giá và chỉ thông báo khi kết quả chính thức đổi.

### AL-21 — Auto Publish Grade chỉ được lưu, không có hiệu lực

- Vị trí: [AssignmentService.java:498](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:498), [AssignmentService.java:788](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:788).
- Hiển thị điểm chỉ dựa `isGradePublished`; không áp dụng `gradePublishMode` khi hoàn tất bài. **Probe:** cấu hình IMMEDIATE, bài trắc nghiệm có `finalScore` nội bộ nhưng response Student vẫn null, flag vẫn false.
- FR-ASG-013 AC-03/06. Cần áp dụng auto publish cho bài tự động chấm và giữ ẩn khi Essay chưa hoàn tất.

### AL-22 — Cấu hình Assignment không kiểm tra miền giá trị/thứ tự thời gian

- Vị trí: [AssignmentDtos.java:16](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentDtos.java:16), [AssignmentService.java:77](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:77).
- Không có ràng buộc thích hợp cho duration/maxAttempts/penalty hoặc kiểm tra Start Time trước Deadline. Nhánh `>0` biến duration âm thành không giới hạn giờ, maxAttempts âm thành không giới hạn lượt.
- **Probe:** cấu hình duration `-5`, maxAttempts `-1`, penalty `-10`, Deadline trước Start Time vượt qua cả Jakarta Validator và `create()`.
- FR-ASG-005/009/010. Cần quy định giá trị vô hạn rõ ràng, kiểm tra giới hạn và các điều kiện liên trường ở service/DTO.

## Chức năng còn thiếu/chưa tích hợp

Các mục sau không cộng thêm vào số lỗi, nhưng ngăn kết luận module đã hoàn thành:

- **FR-LES-002:** QUIZ và PRACTICE links bị từ chối vì chưa có module tương ứng; danh sách link Lesson chưa lọc theo trạng thái/khả dụng của Assignment.
- **FR-LES-003:** chưa có endpoint Teacher xem tiến độ từng Student; list/get chỉ đọc completion theo ID người gọi. AUTO_ITEMS thiếu cập nhật từ kết quả bài liên kết.
- **FR-ASG-002:** chưa có API chỉnh sửa/đổi thứ tự câu hỏi; hiện chỉ thêm/xóa và gán thứ tự theo số câu.
- **FR-ASG-006:** SCHEDULED có trong enum/schema nhưng chưa có luồng đặt trạng thái/lịch; trạng thái có thể tạo qua API hiện là DRAFT rồi PUBLISHED/CLOSED.
- **FR-ASG-008:** clone nội dung/câu hỏi/options đã có; chưa sao chép Public Problem và testcase độc lập, chưa có kho testcase trong Assignment.
- **FR-ASG-009:** chưa có Give Extra Attempt/Reset Attempt Count theo từng Student và audit tương ứng; count hiện tính cả IN_PROGRESS/TIMED_OUT, chưa tách số Official Submission theo SRS.
- **FR-ASG-011:** shuffleQuestions/shuffleAnswers chỉ được lưu/trả ra; truy vấn vẫn sắp `displayOrder`, chưa tạo và giữ thứ tự xáo trộn cho từng attempt.
- **FR-ASG-012:** AFTER_SUBMIT/AFTER_DEADLINE chưa được áp dụng; `canSeeAnswers` chỉ xử lý ALWAYS. Code feedback chưa có kết quả judge để thực thi các mức hiển thị yêu cầu.
- **FR-ASG-003/006/013:** chưa có API Student truy vấn lịch sử/chính một attempt đã hoàn tất; chỉ có active attempt, response lúc submit, summary và API submissions dành Owner.
- **FR-ASG-016/017:** Code Question chưa đưa vào hàng đợi judge, chưa có testcase/weight/snapshot/verdict. Probe xác nhận CODE sau submit còn SUBMITTED, không có final score. Đây là thiếu tích hợp 4.9, không phải bằng chứng code người dùng đã được chạy.
- **ScoringPolicy.LATEST:** được lưu nhưng `myBestScore` luôn dùng điểm lớn nhất; UNGRADED chưa có luồng completion riêng, logic submit vẫn thực hiện tính điểm.
- Dashboard course/quiz/public practice còn rỗng do các phân hệ ngoài 4.1–4.6 chưa có storage; phân biệt với AL-09 vì Assignment đã có storage nhưng chưa được nối.

## Rủi ro đồng thời cần kiểm chứng riêng

Không tính các mục dưới đây là lỗi đã tái hiện PostgreSQL:

1. **Classroom cập nhật cũ có thể ghi đè chuyển Owner/code/config mới.** Entity không có `@Version`/`@DynamicUpdate` hoặc row lock lớp dùng chung. Lịch cần thử: A đọc để update → Admin chuyển Owner sang B và commit → A commit entity cũ. Hibernate cập nhật toàn bộ cột có thể ghi đè `owner_id` về A. Cần optimistic lock hoặc khóa nhất quán và concurrency test.
2. **Join song song regenerate/close/archive có thể dùng snapshot lớp cũ.** `join()` tải lớp trước pair lock, không refresh; các mutation cấu hình lớp không dùng cùng khóa lớp. Cần test join sau khi thay đổi mã/trạng thái commit.
3. **Autosave song song Submit:** AssignmentAttempt/Answer chưa có version/row lock để bảo vệ bất biến bài đã nộp. Cần chứng minh một autosave đã đọc IN_PROGRESS trước submit không thể ghi đè câu trả lời official sau commit.
4. **Hai request start/autosave đồng thời:** cần xác minh hành vi unique constraint, response lỗi và retry; không chỉ dựa count/check-then-save ở service.

## Kết quả kiểm thử và bằng chứng

### Bộ test repository

- JDK đã dùng: Temurin `21.0.13+9`; Maven `3.9.16`; dependency có sẵn, chạy offline.
- Lượt `mvn --offline --batch-mode --no-transfer-progress verify` đầu thất bại vì Mockito inline self-attach không hoạt động trong môi trường chạy. Các failure/error ở lượt đó không được coi là lỗi nghiệp vụ.
- Chạy lại bằng Mockito premain agent, giữ nguyên source/POM:

```bash
export JAVA_HOME='/home/hien/.local/opt/jdk-21.0.13+9'
export PATH="$JAVA_HOME/bin:$PATH"
cd /home/hien/Project/AlgoLearner/backend
/home/hien/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn \
  --offline --batch-mode --no-transfer-progress \
  '-DargLine=-javaagent:/home/hien/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' \
  verify
```

- **Kết quả quan sát:** BUILD SUCCESS; 228 test được báo cáo, 179 đạt, 49 skipped, 0 failures, 0 errors. Artifact JAR được đóng gói thành công.
- Các nhóm skipped: AccountFlows 13; Classroom API 15; Auth API 4; AuthManagement API 4; ClassroomConcurrency 2; ClassroomQueryCount 3; Announcement API, Assignment API, Lesson API, CrossIntegration, MigrationUpgrade, LiveSupabaseFlow, RegistrationBenchmark, SupabaseConnection mỗi nhóm 1.
- `TEST_DB_URL` chưa được cấu hình. Live/probe/benchmark còn có điều kiện opt-in riêng; không bật các chế độ đó trong lần rà soát này.
- `python3 -B -m unittest discover -s scripts/tests`: **9 tests, OK**.
- Không đưa tỷ lệ JaCoCo vào kết luận vì không cô lập/kiểm chứng độ mới của toàn bộ dữ liệu coverage có sẵn trong `target`.

### Probe bổ sung

- Assignment: `/tmp/algolearner-audit/AssignmentAuditProbe.java`, log `/tmp/algolearner-audit/assignment-probe.log`; chạy service thật với Mockito và validator. Các kiểm chứng gồm CLOSED, timeout LOCK, class archive, đọc đề sớm, Single Choice, Partial Credit, IMMEDIATE publish, IDOR câu hỏi, notify rejudge không đổi, chấm draft quá điểm, CODE pending và cấu hình sai.
- Lesson/notification: `/tmp/AuditLessonProbe.java`, log `/tmp/algolearner-audit-lesson-probe.txt`; 6 kiểm chứng đạt điều kiện tái hiện: IDOR link, restore Draft, AUTO_ITEMS, validator metadata, service metadata, serialization notification.
- Auth: `/tmp/AuthIdentityAuditProbe.java`, log `/tmp/algolearner-auth-identity-probe-agent.log`; xác nhận collision và khóa nhầm tài khoản sau 5 lần.
- Probe đặt trong `/tmp`, không bổ sung vào bộ test repository. Các kiểm chứng này mô tả hành vi sai đang tồn tại; exit 0 có nghĩa đã tái hiện đúng điều kiện kiểm tra, không có nghĩa backend đúng SRS.

## Thứ tự xử lý đề xuất

1. Chặn IDOR AL-01/02; bảo vệ đọc đề, submit/timeout/archive và lịch sử submission AL-04/05/06.
2. Sửa chấm trắc nghiệm, completion và điều kiện chấm điểm AL-03/07/16/17/18; bổ sung regression tests đúng SRS, thay các test đang kỳ vọng hành vi sai.
3. Hoàn thiện snapshot/audit/rejudge, grade publishing, namespace định danh, file storage và notification/dashboard.
4. Chạy lại integration trên PostgreSQL test riêng cho cả hai migration track; bổ sung các lịch concurrency trước khi xác nhận toàn bộ 4.1–4.6 đạt yêu cầu.
