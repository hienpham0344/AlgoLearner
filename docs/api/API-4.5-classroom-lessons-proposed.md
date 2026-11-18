# Contract đề xuất cho frontend 4.5 — chưa xác nhận với issue #12

Ngày: 2026-11-11. Đây là đề xuất tích hợp, **không phải API đã có trên backend**. Chưa có quyết định File Storage hoặc contract 4.6/4.7/4.8. Không đóng #13 dựa trên contract tests hoặc mock E2E.

Base `/api/v1/classrooms/{classId}`; JWT qua `apiClient`, JSON theo `ApiResponse<T>`, phân trang `data.items` và `data.pagination`. Thời gian UTC kết thúc `Z`; FE hiển thị Asia/Ho_Chi_Minh (UTC+7). DTO TypeScript: `frontend/src/features/classroom/lessons/types.ts`.

| Method | Resource | Response / yêu cầu |
| --- | --- | --- |
| GET | `/lessons?page=0&size=20&sort=createdAt,asc` | PageResponse<Lesson>; BE lọc theo quyền và trạng thái trước phân trang |
| POST | `/lessons` | LessonInput → Lesson (201), luôn tạo Draft |
| GET | `/lessons/{lessonId}` | Lesson; Student chỉ thấy Published, progress chỉ của mình |
| PUT | `/lessons/{lessonId}` | LessonInput → Lesson; cập nhật nguyên tử nội dung, links, completion config; không đổi lifecycle |
| POST | `/lessons/{lessonId}/publish`, `/draft`, `/archive`, `/restore` | Lesson; Restore dùng trạng thái trước Archive từ BE |
| DELETE | `/lessons/{lessonId}` | 204; BE kiểm tra toàn bộ dữ liệu liên quan; 409 nếu chỉ được Archive |
| POST | `/lessons/{lessonId}/completion` | Lesson kèm progress đã persist; chỉ Manual và Student hợp lệ |
| GET | `/lessons/{lessonId}/progress?page=0&size=20` | PageResponse<StudentLessonProgress>; chỉ Teacher owner |
| GET | `/lesson-capabilities` | LessonCapabilities; cấu hình upload thực tế và khả năng liên kết hoạt động |
| GET | `/lesson-activities` | LessonActivity[]; danh sách chọn hoạt động trong đúng lớp, Teacher owner |
| POST | `/lessons/{lessonId}/attachments` | multipart `file` → LessonAttachment (201); upload độc lập sau lưu Draft |
| DELETE | `/lessons/{lessonId}/attachments/{attachmentId}` | 204; không ảnh hưởng nội dung đã lưu |
| GET | `/lessons/{lessonId}/attachments/{attachmentId}/download` | Binary, ngoại lệ envelope JSON; lỗi vẫn ApiErrorResponse; kiểm tra quyền mỗi lần tải |

`LessonInput`: title (1–200), content (Markdown, <=100000), codeExample (java/python/text, <=50000 hoặc null), externalLinks (label + http(s) URL), completionMode (MANUAL/CONDITION_BASED), activityIds, requiredActivityIds. Các giới hạn nhập liệu FE này cần BE xác nhận. BE từ chối Archive update, validate URL/nội dung, loại file, MIME/signature, malware/ZIP và quota. FE chỉ kiểm tra sơ bộ extension/size; không thay thế kiểm tra bảo mật server.

`Lesson` trả thêm id, classroomId, status, previousStatus, activities, attachments, progress, canDelete, createdAt, updatedAt. `canDelete` chỉ hỗ trợ giao diện, BE kiểm tra lại khi DELETE. `progress` ở Teacher detail có thể null; roster dùng endpoint riêng. Completion dựa điều kiện do BE đánh giá theo Assignment/Quiz, không dùng Practice làm điều kiện. requiredActivityIds chỉ chọn các Assignment/Quiz đã liên kết. Không xác định đạt điều kiện từ trạng thái cục bộ FE.

`LessonCapabilities`: uploadsAvailable, activityLinkingAvailable, maxFileSizeBytes, allowedExtensions (không dấu chấm). Upload chỉ bật khi có policy hợp lệ; FE giới hạn PDF/DOCX/PPTX/ZIP/PNG/JPG/JPEG/GIF/WEBP, không cho SVG hoặc executable. Không mặc định quota hoặc provider khi endpoint vắng mặt.

`LessonActivity`: id duy nhất trong lớp, type ASSIGNMENT/QUIZ/PRACTICE, title, linkedLessonId, available, path, completed. Quiz đã thuộc lesson khác không chọn được. BE kiểm tra lại quan hệ và uniqueness nguyên tử. `path` là route nội bộ trong cùng role và class; null khi module chưa có trang hoạt động. `available=false` không mở được với Student. Trạng thái riêng và điều kiện nộp bài do module đích kiểm tra lại.

401 dùng refresh/logout chung; 403 từ chối quyền, 404 không tồn tại/không khả dụng, 409 xung đột/related data/quiz duplicate, 422 validation với errors[], 413 quá dung lượng, 503 File Storage/phụ thuộc chưa có. Không fallback sang dữ liệu demo cho lesson. Request thất bại không được ghi nhận thành công và không thay đổi progress/lifecycle cục bộ.

## Điều kiện phải kiểm tra trên BE trước nghiệm thu

- Membership đang hiệu lực, account Active, Teacher owner, IDOR class/lesson/file/progress.
- Draft/Archived ẩn cả nội dung và attachments; revoke quyền có hiệu lực ở mỗi request.
- Published → Draft và Archive bảo toàn lịch sử; Restore khôi phục đúng trạng thái trước đó, không ép Published.
- Archive chặn submission mới theo phạm vi 4.6/4.7/4.8; FE không thể bảo đảm điều này.
- Completion và hard-delete dựa dữ liệu server; progress riêng từng Student; Teacher chỉ xem lớp của mình.
- Global upload policy, storage lỗi/timeout, log kỹ thuật, chống nội dung file độc hại, rollback dữ liệu.

Phải xác nhận contract với #12 rồi cập nhật adapter/fixtures và chạy E2E với backend thật trước khi đánh dấu tích hợp hoàn tất.
