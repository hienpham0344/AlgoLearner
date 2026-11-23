# Backend 4.7 — Classroom Quiz

API dùng bản JDBC của PR #50. Base path là `/api/v1/classrooms/{classId}/quizzes`. Teacher chủ lớp quản lý quiz. Student có membership ACTIVE chỉ truy cập lượt làm và điểm của mình. Thời gian trả về theo ISO-8601 UTC.

| Method | Path tương đối | Nội dung |
|---|---|---|
| GET / POST | `/` | Danh sách quiz / tạo draft bằng `Configure` |
| GET / PUT | `/{quizId}` | Đọc thông tin quiz / cập nhật draft |
| GET | `/{quizId}/authoring` | Đọc cấu hình và đáp án, chỉ Teacher chủ lớp |
| POST | `/{quizId}/publish`, `/{quizId}/close` | Xuất bản hoặc đóng quiz |
| POST | `/{quizId}/duplicate` | Tạo draft độc lập, sao chép cấu hình và câu hỏi |
| GET / POST | `/{quizId}/attempts` | Lịch sử có phân trang / bắt đầu hoặc tiếp tục lượt làm |
| GET | `/{quizId}/attempts/{attemptId}` | Khôi phục câu trả lời đã lưu và cutoff gốc |
| PUT | `/{quizId}/attempts/{attemptId}/answers` | Lưu một câu với `questionId`, `selectedOptionIds`, `expectedVersion`, `idempotencyKey` |
| POST | `/{quizId}/attempts/{attemptId}/submit` | Nộp đáp án đã lưu |
| GET | `/{quizId}/attempts/{attemptId}/review` | Xem đáp án và giải thích được công bố |
| POST | `/{quizId}/publish-grades` | Công bố điểm thủ công |
| POST | `/{quizId}/attempt-rights` | `GRANT` hoặc `RESET` số lượt, có lý do và audit |
| GET | `/{quizId}/students/{studentId}/result` | Điểm chính thức và trạng thái hoàn thành |
| GET / POST | `/{quizId}/students/{studentId}/grade-overrides` | Lịch sử / ghi đè điểm bằng `{score, reason}`, chỉ Teacher chủ lớp |

`Configure` giữ các trường của PR #50 gồm `title`, `graded`, `opensAt`, `closesAt`, `durationSeconds`, `maxAttempts`, `allowLate`, hai cờ shuffle, `releasePolicy` (`MANUAL` hoặc `IMMEDIATE`), `timeoutMode` (`AUTO_SUBMIT` hoặc `LOCK`) và `questions`. Mỗi câu có `id`, `prompt`, `type`, `scoring`, `points` và `options`.

- `showCorrectAnswers` và `showExplanations` mặc định tắt và hoạt động độc lập. Câu hỏi hỗ trợ `explanation`. Với quiz có chấm điểm, Student xem nội dung review sau khi điểm của attempt đã được công bố.
- `PARTIAL` hỗ trợ `weight` cho từng lựa chọn. Nếu dùng trọng số, mỗi lựa chọn đều phải có trọng số và tổng trọng số các lựa chọn đúng phải bằng `points`. Lựa chọn sai bị trừ trọng số, điểm câu được giới hạn từ 0 đến `points`. Câu không khai báo trọng số tiếp tục dùng cách tính theo tỷ lệ đáp án đúng của PR #50.
- Deadline lịch chặn lượt mới nếu tắt `allowLate`. Attempt đang làm giữ cutoff theo time limit tại lúc bắt đầu. AttemptView trả thêm `serverTime` và `late`.
- `passingScore` là ngưỡng hoàn thành tùy chọn cho quiz có chấm điểm. Khi bỏ trống, có lượt nộp hợp lệ là đủ. Trạng thái hoàn thành được lưu theo từng Student. Nếu quiz có ngưỡng điểm thì Student thấy trạng thái theo điểm đã công bố. Override mới nhất được dùng cho kết quả tổng hợp và lesson progress khi đã công bố. Điểm chấm trên attempt và lịch sử revision được giữ nguyên.
- Classroom còn quiz phải được archive. Quiz copy bắt đầu ở `DRAFT` và chưa có attempt, điểm hoặc lịch sử công bố.

Migration theo schema của PR #50. `V202611230001__classroom_quiz_results.sql` bổ sung cấu hình hiển thị, ngưỡng đạt và lịch sử override. Cả hai Flyway location có cùng migration bổ sung.

`QuizIntegrationTest` chạy trên PostgreSQL với schema riêng. Cấu hình `TEST_DB_URL` cho database có tên kết thúc bằng `_test`, cùng `TEST_DB_USERNAME` và `TEST_DB_PASSWORD`, rồi chạy từ thư mục backend:

```powershell
./mvn-local.ps1 '-Dtest=QuizPolicyTest,QuizIntegrationTest,ClassroomServiceTest' test
```
