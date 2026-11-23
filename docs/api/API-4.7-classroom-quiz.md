# Backend 4.7 — Classroom Quiz

## Nguồn yêu cầu và lựa chọn nghiệp vụ

Đối chiếu SRS 1.1 mục 4.7 và Issue #16. Issue yêu cầu UC-QUZ-01 (Student làm Quiz), UC-QUZ-10 (Teacher quản lý); SRS không liệt kê UC-QUZ-02–09. Chưa truy cập được hai liên kết Use Case/Test Plan Google trong issue, nên chưa xác nhận Test Plan chi tiết của nhóm. Chưa nghiệm thu tích hợp FE hoặc đóng issue.

Partial Credit được người yêu cầu xác nhận: Teacher cấu hình trọng số từng lựa chọn; chọn đúng cộng, sai trừ; chặn điểm câu trong [0, points]. Tổng trọng số lựa chọn đúng phải bằng points. Trọng số không âm, không lớn hơn points, tối đa 6 chữ số thập phân. All-or-Nothing yêu cầu tập lựa chọn trùng chính xác tập đáp án đúng. Điểm attempt = tổng điểm đạt / tổng points × 100, HALF_UP 2 chữ số, không làm tròn trung gian. Passing Score và Grade Override theo thang 0–100.

FR-QUZ-008 quy định Block New Attempts chỉ chặn tạo lượt mới sau deadline lịch. Attempt đã bắt đầu được tiếp tục; hạn khóa là startedAt + timeLimitSeconds nếu có. Không bật Time Limit thì không có hạn khóa attempt. Nộp sau deadline lịch được đánh dấu Late. Allow Late Attempts cho phép bắt đầu muộn; CLOSED luôn chặn lượt mới. Override deadline/timeLimit áp dụng cho lượt mới, không đặt lại timer đang chạy. Reset đặt mốc số lượt đã dùng và cấp lại maxAttempts; PUT extraAttempts đặt tổng lượt bổ sung hiện tại. Không xóa lịch sử.

Sau attempt đầu tiên, nội dung và cấu hình đánh giá/thời gian đóng băng; vẫn sửa được tiêu đề, publication và hai cờ visibility. Muốn đổi nội dung thì duplicate thành draft độc lập. Official Score là điểm cao nhất của attempt SUBMITTED, trừ khi có Grade Override. EXPIRED/ACTIVE không tham gia. UNGRADED không cung cấp điểm đóng góp lớp. Submit-is-enough hoàn thành nếu có attempt nộp hợp lệ; Passing-score so với điểm chính thức cuối cùng. Completion lưu tại từng attempt, Result tổng hợp theo Student.

## Database

Migration mới `V202611180001__classroom_quiz.sql`; không sửa migration cũ.

| Bảng | Nội dung | Ràng buộc chính |
|---|---|---|
| tbl_quiz | UUID, classroom_id, title, configuration, status, grades_published | FK Classroom; DRAFT/PUBLISHED/CLOSED |
| tbl_quiz_attempt | UUID, quiz_id, student_id, attempt_number, snapshot, answers, started_at, deadline, submitted_at, status, late, score, completed | unique số lượt; partial unique một ACTIVE; CHECK trạng thái/kết quả |
| tbl_quiz_override | UUID, quiz_id, student_id, reset_at_count, extra_attempts, deadline, time_limit_seconds | unique Quiz/Student; giới hạn không âm |
| tbl_quiz_grade_override | UUID, quiz_id, student_id, actor_id, original_score, final_score, revision, reason | append-only JPA; unique revision Quiz/Student |

Tất cả có created_at/updated_at UTC và index FK. Câu hỏi/lựa chọn là aggregate JSON TEXT trong configuration. Attempt sao chép cấu hình và thứ tự shuffle vào snapshot; answers lưu map questionId → optionIds. Không có nhu cầu bảng câu hỏi dùng chung trong FR hiện tại. JSON cấu hình nguyên bản không trả cho Student. Không cascade delete lịch sử; chỉ xóa draft chưa có attempt. Classroom có Quiz phải archive.

## API

Base `/api/v1/classrooms/{classroomId}/quizzes`, Bearer JWT như Auth hiện có. T = Teacher sở hữu lớp; S = Student membership ACTIVE. Admin không sử dụng ngữ cảnh này. Teacher xem/override được kết quả lịch sử của Student đã rời lớp; Student đã rời lớp không tiếp tục truy cập. Lớp archived hoặc owner inactive chỉ đọc.

| Method | Suffix | Request | Response data | Quyền |
|---|---|---|---|---|
| POST | / | Write | QuizView, 201 | T |
| GET | / | page,size,sort | PageResponse<QuizView> | T/S |
| GET | /{quizId} | — | QuizView | T/S |
| PUT | /{quizId} | Write | QuizView | T |
| DELETE | /{quizId} | — | 204 | T, draft chưa dùng |
| PUT | /{quizId}/status | {status} | QuizView | T |
| POST | /{quizId}/duplicate | — | QuizView, 201 | T |
| PUT | /{quizId}/grade-publication | {published:boolean} | 204 | T |
| POST | /{quizId}/attempts | — | AttemptView, 200, start hoặc resume | S |
| GET | /{quizId}/attempts | page,size,sort | PageResponse<AttemptView> | T: tất cả; S: của mình |
| GET | /{quizId}/attempts/{attemptId} | — | AttemptView | T/S chủ attempt |
| PUT | /{quizId}/attempts/{attemptId}/answers/{questionId} | {optionIds:[UUID]} | AttemptView | S chủ attempt |
| POST | /{quizId}/attempts/{attemptId}/submit | — | AttemptView | S chủ attempt |
| GET | /{quizId}/students/{studentId}/result | — | Result | T/S chính mình |
| PUT | /{quizId}/students/{studentId}/access-override | AccessOverride | 204 | T |
| POST | /{quizId}/students/{studentId}/grade-overrides | {score,reason} | GradeChange, 201 | T |
| GET | /{quizId}/students/{studentId}/grade-overrides | page,size,sort | PageResponse<GradeChange> | T |

Response dùng ApiResponse<T>, danh sách dùng PageResponse<T>, lỗi dùng ApiErrorResponse. Mã lỗi HTTP: 400 validation/đáp án sai, 401 chưa xác thực, 403 role/IDOR/membership, 404 sai scope/không tồn tại, 409 trạng thái/giới hạn/expiry. Swagger `/swagger-ui/index.html`, JSON `/v3/api-docs`.

Write = `{title,config}`. Config:

- kind: GRADED/UNGRADED; maxAttempts: 1–10000.
- startTime/deadline: ISO-8601 UTC hoặc null; allowLateAttempts: boolean.
- timeLimitSeconds: 1–604800 hoặc null; timeout: AUTO_SUBMIT/LOCK_WITHOUT_SUBMIT.
- shuffleQuestions/shuffleAnswers: boolean.
- scoring: ALL_OR_NOTHING/PARTIAL_CREDIT.
- completion: SUBMIT_IS_ENOUGH/PASSING_SCORE; passingScore bắt buộc với PASSING_SCORE.
- publication: AUTO/MANUAL; showCorrectAnswers/showExplanations: boolean.
- questions: 1–200 Question, mỗi Question chứa id UUID, text, type SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE, points, explanation, options.
- Option: id UUID, text, correct boolean, weight bắt buộc với Partial Credit; mỗi câu 2–20 lựa chọn. UUID câu hỏi/lựa chọn không trùng toàn Quiz. True/False có đúng 2 lựa chọn; Single/TrueFalse có đúng 1 lựa chọn đúng; mọi câu có ít nhất một lựa chọn sai.

AccessOverride = `{resetAttempts:boolean,extraAttempts:number,deadline:Instant|null,timeLimitSeconds:number|null}`. Grade Override chấp nhận tối đa 4 chữ số thập phân, tương ứng precision cột database. Client không được quyết định điểm tự động, trạng thái, thời gian bắt đầu hoặc thời gian nộp attempt.

## Luồng tích hợp và vận hành

Teacher tạo draft → PUT cấu hình → PUBLISHED → xem attempts/result → publish grade nếu Manual. Duplicate không sao chép dữ liệu Student, override hoặc thao tác công bố điểm đã thực hiện.

Student GET danh sách/chi tiết (chưa có câu hỏi) → POST attempts → render thứ tự đã lưu → PUT đáp án từng câu, mảng rỗng bỏ chọn → GET attempt khi reconnect → POST submit → GET result. FE debounce và tuần tự hóa request trên cùng câu để request cũ không ghi đè lựa chọn mới. Các câu khác nhau được merge độc lập trong transaction. Chỉ báo đã lưu sau response thành công. Timer hiển thị từ serverTime/deadline.

AUTO_SUBMIT chấm đáp án đã lưu tại deadline; LOCK_WITHOUT_SUBMIT lưu EXPIRED không có điểm chính thức. Đúng deadline đã khóa ghi. Resume/start trên attempt hết hạn xử lý timeout rồi trả trạng thái kết thúc; gọi start lần tiếp để xin lượt mới. Autosave muộn trả 409 nhưng transaction timeout vẫn commit. Submit lặp không chấm lại. Điểm/đáp án/giải thích chỉ được công bố sau SUBMITTED, theo publication và hai cờ visibility độc lập.

Mọi thao tác ghi khóa Quiz trước rồi Attempt, kể cả nhiều instance Backend; partial unique index là lớp bảo vệ thứ hai. Khóa theo Quiz ưu tiên tính đúng đắn, có thể giới hạn throughput lớp lớn. Scheduler đọc tối đa 100 lượt/lần, transaction riêng mỗi lượt; đọc scalar quizId trước khóa để tránh cache đáp án cũ. `app.quiz.expiry-interval-ms` mặc định 1000. Resume/autosave/submit cũng bù timeout sau restart; dữ liệu nằm trong PostgreSQL.

Gradebook/Notification chưa có module thực tế: Result là contract tích hợp điểm và completion; chưa có tác vụ đẩy sang hai module này. Grade Override có lịch sử riêng và gọi AuthAuditPort hiện có trong transaction.

Chạy PostgreSQL và cấu hình theo `backend/.env.example`, rồi `cd backend` và `./mvn-local.ps1 spring-boot:run` trên Windows hoặc `mvn spring-boot:run`. Flyway tự migrate; Hibernate validate schema. Quiz không đăng ký trong profile mock vì profile đó tắt JPA.

Kiểm thử Quiz: `./mvn-local.ps1 '-Dtest=Quiz*Test' test`. Toàn bộ build: `./mvn-local.ps1 clean verify`.

Để kiểm tra migration và concurrency trên PostgreSQL, đặt `QUIZ_TEST_POSTGRES_URL` (JDBC URL của database test), `QUIZ_TEST_POSTGRES_USERNAME` và `QUIZ_TEST_POSTGRES_PASSWORD`, rồi chạy:

```powershell
./mvn-local.ps1 '-Dtest=QuizConcurrencyTest,QuizPostgresMigrationTest' test
```

Mỗi bộ test tạo và dọn schema ngẫu nhiên riêng trên database test. `QuizPostgresMigrationTest` chạy toàn bộ migration Flyway, kiểm tra validate/re-run, unique ACTIVE/attempt number, CHECK kết quả/điểm và FK của Classroom. `QuizConcurrencyTest` chạy trên schema Flyway với Hibernate validate, kiểm tra start, autosave, submit, expiry và grade override đồng thời. Nếu thiếu URL, bộ migration được skip và bộ concurrency chạy trên H2.
