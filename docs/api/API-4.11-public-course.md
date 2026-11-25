# Backend 4.11 — Khóa học công khai

Public Course không gắn với Class. Student và Teacher có tài khoản ACTIVE được học. Teacher đề xuất nội dung qua revision, Admin duyệt, xuất bản và quản lý vòng đời. Response dùng `ApiResponse` hoặc `PageResponse` (`?page=&size=`, tối đa 100). Lỗi dùng `ApiErrorResponse.errorCode`. Thời gian theo ISO-8601 UTC (`...Z`). Course MVP miễn phí, không có video.

## Trạng thái

- **Publication** (trên course): `DRAFT`, `PUBLISHED`, `UNPUBLISHED` (kèm `unpublishPolicy` là `ENROLLED_CONTINUE` hoặc `LOCK_ALL`), `ARCHIVED`.
- **Review** (trên revision, tách khỏi publication): `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `CHANGES_REQUESTED`, `REJECTED`. Mỗi course có tối đa một revision đang mở.
- **Progress** (trên enrollment): `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`. Mở lesson đầu tiên, hoàn thành lesson hoặc bắt đầu quiz sẽ chuyển sang `IN_PROGRESS`.

Quyền truy cập nội dung của người học:

| Course | Chưa enroll | Đã enroll |
|---|---|---|
| PUBLISHED | Outline và các lesson `preview=true` | Toàn bộ |
| UNPUBLISHED + ENROLLED_CONTINUE | 404 | Toàn bộ |
| UNPUBLISHED + LOCK_ALL | 404 | 403 `COURSE_ACCESS_LOCKED`, vẫn đọc được progress và lịch sử |
| ARCHIVED | 404 | Toàn bộ, không nhận enroll mới |
| DRAFT | 404 (author Teacher vẫn xem được outline) | — |

## Learner — `/api/v1/courses` (STUDENT, TEACHER)

| Method | Path | Nội dung |
|---|---|---|
| GET | `/?q=&level=&topic=` | Catalog các course PUBLISHED. `q` tìm theo tên, không phân biệt hoa thường. `level` là `BEGINNER`, `INTERMEDIATE` hoặc `ADVANCED`. `topic` so khớp chính xác, không phân biệt hoa thường. Item là `{course, enrollmentStatus}` |
| GET | `/enrollments/me` | Enrollment và progress của chính mình |
| GET | `/{courseId}` | Preview: `course`, `lessons[]` (outline), `quizzes[]` (không có câu hỏi), `progress` (null nếu chưa enroll), `contentAccessible` |
| GET | `/{courseId}/lessons/{lessonId}` | Nội dung `blocks[]`. Mở được theo bất kỳ thứ tự nào; `prerequisiteNote` chỉ mang tính khuyến nghị |
| POST | `/{courseId}/enrollments` | Enroll, idempotent. 409 `COURSE_NOT_ENROLLABLE` nếu course UNPUBLISHED hoặc ARCHIVED |
| DELETE | `/{courseId}/enrollments/me` | Unenroll khi chưa bắt đầu; nếu đã bắt đầu trả 409 `COURSE_ALREADY_STARTED` |
| POST | `/{courseId}/lessons/{lessonId}/completion` | Hoàn thành lesson, idempotent, trả về `Progress` |
| GET | `/{courseId}/progress` | `Progress` của chính mình |
| POST | `/{courseId}/progress/update-acknowledgement` | Tắt cờ `hasUpdate` ("Có cập nhật mới") |
| GET | `/{courseId}/quizzes/{quizId}` | `QuizStatus`: số lượt đã dùng và còn lại, `officialScore` (điểm cao nhất), các attempt |
| POST | `/{courseId}/quizzes/{quizId}/attempts` | Bắt đầu attempt hoặc trả attempt đang mở (timer không bị reset). Hết lượt trả 409 `QUIZ_ATTEMPTS_EXHAUSTED` |
| PUT | `.../attempts/{attemptId}/answers` | `{answers:[{questionId, optionIds}]}`, gộp theo câu. Quá deadline thì tự nộp với các câu đã lưu và trả 409 `QUIZ_TIME_EXPIRED` |
| POST | `.../attempts/{attemptId}/submission` | Nộp bài, body tùy chọn (giống PUT answers). Chấm tự động |

`Progress` gồm `status`, `progressPercent`, `completedUnits/totalUnits`, `completedMandatoryQuizzes/totalMandatoryQuizzes`, `completedLessonIds`, `completedQuizIds`, `enrolledAt`, `startedAt`, `completedAt`, `hasUpdate`, `contentLocked`.

`Attempt` gồm `deadlineAt` (null nếu không giới hạn thời gian), `serverTime`, `score` (0–100), `passed`, `answersRevealed`. Các trường `options[].correct`, `questions[].correct` và `explanation` chỉ có giá trị khi `answersRevealed=true`, theo `answerVisibility`: `NEVER`, `AFTER_SUBMIT` hoặc `AFTER_PASSED`.

**Điều kiện Completed:** hoàn thành tất cả lesson đang hoạt động và tất cả quiz `mandatory`. Quiz `SUBMIT` chỉ cần nộp; quiz `PASSING_SCORE` phải đạt `score >= passingScore`. Practice link không bao giờ chặn completion. Đã Completed thì không bị hạ trạng thái khi nội dung thay đổi; hệ thống chỉ bật `hasUpdate`.

## Teacher contribution — `/api/v1/courses` (TEACHER, chỉ author)

| Method | Path | Nội dung |
|---|---|---|
| POST | `/` | `{title, description, level, topic, estimatedMinutes}`, tạo course DRAFT và revision DRAFT |
| GET | `/contributions?status=PENDING_REVIEW` | Danh sách revision của chính mình theo review status |
| GET / PUT | `/{courseId}/contribution` | Đọc / thay toàn bộ `CourseContent` của revision đang mở. Sửa course đã Published sẽ tạo revision mới, bản công khai giữ nguyên. Đang `PENDING_REVIEW` thì trả 409 `COURSE_REVIEW_PENDING` |
| POST | `/{courseId}/lessons` | Thêm một lesson vào revision: `{title, content}` hoặc `{title, blocks}` |
| POST | `/{courseId}/review-requests` | Gửi duyệt (cần ít nhất 1 lesson), có ghi audit. Gửi lại được sau `CHANGES_REQUESTED` |

`CourseContent = {title, description, level, topic, estimatedMinutes, lessons[], quizzes[]}`:

- `lessons[] = {id?, title, estimatedMinutes, prerequisiteNote, preview, blocks[]}`
- `blocks[] = {type, text | code+language | imageUrl+alt | quizId | problemId}` với `type` là `TEXT`, `CODE`, `IMAGE`, `QUIZ` hoặc `PRACTICE_LINK`
- `quizzes[] = {id?, lessonId?, title, mandatory, completionRule (SUBMIT|PASSING_SCORE), passingScore, maxAttempts (null = không giới hạn), timeLimitSeconds (null = không timer, 60–86400), answerVisibility, questions[]}`
- `questions[] = {id?, prompt, type (SINGLE_CHOICE|MULTIPLE_CHOICE|TRUE_FALSE), points, explanation, options[{id?, text, correct}]}`. Không hỗ trợ Essay.

Id bỏ trống sẽ được sinh tự động. Client gửi lại đúng id cũ để giữ completion và attempt của người học.

## Admin — `/api/v1/admin/courses` (ADMIN)

| Method | Path | Nội dung |
|---|---|---|
| GET | `/?status=&q=` | Tất cả course |
| POST | `/` | Admin tạo course với `CourseContent`; nội dung áp dụng trực tiếp, course ở trạng thái DRAFT |
| GET | `/{courseId}` | `AdminDetail`: nội dung công khai (có đáp án), revision đang mở, số enrollment |
| PUT | `/{courseId}/content` | Admin sửa trực tiếp: tính lại progress của người chưa Completed và bảo toàn Completed |
| GET | `/reviews?status=PENDING_REVIEW` | Hàng đợi duyệt |
| GET | `/reviews/{revisionId}` | Nội dung revision được đề xuất |
| POST | `/{courseId}/reviews` | `{decision: APPROVE|CHANGES_REQUESTED|REJECT, reason, publish?}` (hỗ trợ cú pháp cũ `{approved}`). `reason` bắt buộc trừ khi Approve. Approve sẽ áp dụng nội dung, tính lại progress và (mặc định, với course DRAFT) xuất bản. Có gửi notification `PUBLIC_COURSE_REVIEW` cho Teacher |
| POST | `/{courseId}/publication` | `{action: PUBLISH|UNPUBLISH|ARCHIVE, unpublishPolicy, reason}`, có ghi audit |
| DELETE | `/{courseId}` | Hard delete khi chưa có enrollment nào; ngược lại trả 409 `COURSE_HAS_LEARNING_DATA` (hãy Archive) |

Mã audit: `PUBLIC_COURSE_SUBMITTED`, `PUBLIC_COURSE_REVIEWED`, `PUBLIC_COURSE_PUBLICATION_CHANGED`, `PUBLIC_COURSE_CREATED`, `PUBLIC_COURSE_CONTENT_UPDATED`, `PUBLIC_COURSE_DELETED`.

## Bảo toàn dữ liệu

Lesson và quiz bị bỏ khỏi nội dung chỉ được xóa mềm (`removed_at`). Unenroll là xóa mềm (`unenrolled_at`). Enrollment, completion, attempt (kèm snapshot quiz tại lúc làm) và `tbl_course_enrollment_event` không bao giờ bị xóa bởi unpublish, archive, cập nhật nội dung hay unenroll.

## Giới hạn MVP

- Block `IMAGE` nhận URL `https` trỏ tới file png, jpg, gif, webp hoặc svg. Upload ảnh qua File Storage chưa được hỗ trợ, vì `tbl_stored_file` hiện bắt buộc gắn với một classroom.
- Không có versioning cho course/lesson; revision là snapshot của toàn bộ course.
- Catalog yêu cầu đăng nhập.

Migration `V202611240001__public_course_full_lifecycle.sql` có mặt ở cả hai Flyway location và chuyển đổi dữ liệu MVP cũ: status `PENDING_REVIEW`/`REJECTED` chuyển thành revision, course trở về `DRAFT`.
