# API 4.5 — Bài học trong lớp

Nguồn: SRS ver 1.1 mục 4.5, [LessonController](../../backend/src/main/java/com/algolearner/platform/lesson/LessonController.java), [LessonDtos](../../backend/src/main/java/com/algolearner/platform/lesson/LessonDtos.java), [LessonCompletionRule](../../backend/src/main/java/com/algolearner/platform/lesson/LessonCompletionRule.java). Xem [quy ước chung](README.md). Base path: `/api/v1/classrooms/{classId}/lessons`; mọi route cần Bearer token.

| Method | Đường dẫn tiếp theo | Body / query | Thành công | Quyền chính |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/` | — | 200 `ApiResponse<LessonSummaryView[]>` | Chủ lớp/thành viên hợp lệ |
| POST | `/` | `CreateLessonRequest` | 201 `ApiResponse<LessonDetailView>` | Teacher chủ lớp |
| GET | `/{lessonId}` | — | 200 `ApiResponse<LessonDetailView>` | Chủ lớp/thành viên hợp lệ |
| PUT | `/{lessonId}` | `UpdateLessonRequest` | 200 `ApiResponse<LessonDetailView>` | Teacher chủ lớp |
| DELETE | `/{lessonId}` | — | 204 | Teacher chủ lớp; chỉ khi không có tiến độ/bài nộp |
| PATCH | `/{lessonId}/publish` | — | 200 `ApiResponse<LessonDetailView>` | Teacher chủ lớp |
| PATCH | `/{lessonId}/unpublish` | — | 200 `ApiResponse<LessonDetailView>` | Teacher chủ lớp |
| PATCH | `/{lessonId}/archive` | — | 200 `ApiResponse<LessonDetailView>` | Teacher chủ lớp |
| PATCH | `/{lessonId}/restore` | — | 200 `ApiResponse<LessonDetailView>` | Teacher chủ lớp |
| POST | `/{lessonId}/materials` | `AddMaterialRequest` | 201 `ApiResponse<MaterialView>` | Teacher chủ lớp |
| POST | `/{lessonId}/items` | `LinkItemRequest` | 201 `ApiResponse<LinkedItemView>` | Teacher chủ lớp |
| DELETE | `/{lessonId}/items/{itemId}` | — | 204 | Teacher chủ lớp |
| POST | `/{lessonId}/complete` | — | 204 | Student thành viên, bài `PUBLISHED` + `MANUAL` |
| GET | `/{lessonId}/completion-rule` | — | 200 `ApiResponse<CompletionRuleView>` | Teacher chủ lớp |
| PUT | `/{lessonId}/completion-rule` | `LessonCompletionRule` | 200 `ApiResponse<null>` | Teacher chủ lớp |
| GET | `/{lessonId}/progress` | `page`, `size`, `sort` | 200 `ApiResponse<PageResponse<StudentProgressView>>` | Teacher chủ lớp |

## Nội dung, tài liệu và liên kết

- `CreateLessonRequest`/`UpdateLessonRequest`: `title` bắt buộc, tối đa 255 ký tự; `content`, `codeExample`, `externalLinks`, `completionMode` (`MANUAL`, `AUTO_ITEMS`, `NONE`). `LessonDetailView` gồm nội dung, trạng thái `DRAFT`/`PUBLISHED`/`ARCHIVED`, `materials`, `linkedItems`, `isCompleted`, `completedAt` và các timestamp. `LessonSummaryView` là bản rút gọn cho danh sách.
- Để thêm tài liệu, trước hết `POST /api/v1/classrooms/{classId}/files?purpose=LESSON_MATERIAL` với multipart field `file`; chỉ dùng `fileId` trạng thái `READY` trong `POST /materials`, ví dụ `{ "fileId": "<uuid>" }`. Các trường URL/tên/loại/kích thước từ client không quyết định file thực; URL mới không được dùng thay upload. `MaterialView.fileUrl` trỏ đến `/api/v1/files/{fileId}/content`, phải gửi Bearer token và kiểm tra quyền khi tải. Loại tệp: `PDF`, `DOCX`, `PPTX`, `ZIP`, `IMAGE`.
- `LinkItemRequest`: `{ "itemType": "ASSIGNMENT|QUIZ|PRACTICE", "itemId": "<uuid>" }`; item phải thuộc cùng lớp. Không thể bỏ liên kết đang được completion rule tham chiếu; sửa rule trước. `LinkedItemView` gồm `id`, `itemType`, `itemId`, `displayOrder`.
- `MANUAL` cho Student đánh dấu hoàn thành qua `/complete`. `AUTO_ITEMS` dùng rule versioned; `NONE` không có thao tác hoàn thành thủ công. Rule có `operator=ALL|ANY` và `conditions[]` gồm `itemType`, `itemId`, `requirement=SUBMITTED|SCORE_AT_LEAST`, `scorePercent` từ 0–100 khi dùng ngưỡng. Kết quả dựa trên lượt nộp/điểm chính thức đã công bố, không dựa trên RUN hay bản nháp.
- Archive giữ lịch sử và chặn truy cập mới qua item liên kết; restore trở về trạng thái trước archive. Xóa vật lý chỉ dành cho bài chưa phát sinh tiến độ/bài nộp cần giữ. `GET /progress` bọc `PageResponse` **bên trong** `ApiResponse`, khác danh sách lớp/thông báo.

Lỗi thường gặp: `400` body/rule/fileId sai hoặc không thể xóa vì lịch sử cần giữ (`CANNOT_HARD_DELETE_LESSON`), `401` chưa xác thực, `403` không đúng chủ lớp/thành viên hoặc trạng thái không cho phép, `404` lesson/item/file không thuộc phạm vi, `409` khi Quiz đã gắn với Lesson khác, `503` storage/scanner không sẵn sàng.
