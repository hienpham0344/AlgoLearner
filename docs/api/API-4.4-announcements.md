# API 4.4 — Thông báo lớp học

Nguồn: SRS ver 1.1 mục 4.4, [AnnouncementController](../../backend/src/main/java/com/algolearner/platform/announcement/AnnouncementController.java), [AnnouncementDtos](../../backend/src/main/java/com/algolearner/platform/announcement/AnnouncementDtos.java). Xem [quy ước chung](README.md). Base path: `/api/v1/classrooms/{classId}/announcements`; mọi route cần Bearer token.

| Method | Đường dẫn tiếp theo | Body | Thành công | Quyền |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/` | `page`, `size` | 200 `PageResponse<AnnouncementView>` | Chủ lớp hoặc Student thành viên hợp lệ |
| POST | `/` | `CreateRequest` | 201 `ApiResponse<AnnouncementView>` | Teacher chủ lớp |
| GET | `/{announcementId}` | — | 200 `ApiResponse<AnnouncementView>` | Chủ lớp hoặc Student thành viên hợp lệ |
| PUT | `/{announcementId}` | `UpdateRequest` | 200 `ApiResponse<AnnouncementView>` | Teacher chủ lớp |
| DELETE | `/{announcementId}` | — | 204 | Teacher chủ lớp |
| PATCH | `/{announcementId}/pin` | — | 200 `ApiResponse<AnnouncementView>` | Teacher chủ lớp |
| PATCH | `/{announcementId}/unpin` | — | 200 `ApiResponse<AnnouncementView>` | Teacher chủ lớp |

`CreateRequest` và `UpdateRequest` cùng cấu trúc `{ "title": "...", "content": "..." }`: hai trường không được rỗng; `title` tối đa 255 ký tự. `AnnouncementView` gồm `id`, `classroomId`, `authorId`, `authorName`, `title`, `content`, `isPinned`, `pinnedAt`, `createdAt`, `updatedAt`. Danh sách phân trang, bài ghim đứng trước rồi sắp theo thời gian tạo. `classId` và `announcementId` đều phải đúng phạm vi lớp; chỉ chủ lớp hiện tại được đổi/xóa/ghim.

```http
POST /api/v1/classrooms/{classId}/announcements HTTP/1.1
Authorization: Bearer <access_token>
Content-Type: application/json

{"title":"Lịch học tuần này","content":"Nội dung thông báo"}
```

Lỗi thường gặp: `400` nội dung sai, `401` thiếu/hết hạn token, `403` không có quyền hoặc lớp chỉ đọc, `404` thông báo/lớp ngoài phạm vi. API hiện tại không có route lên lịch, bình luận hoặc đính kèm thông báo; nội dung được trả dưới dạng text, phía giao diện cần làm sạch khi render Markdown/HTML theo [quy tắc bảo mật](../rules/SECURITY_AND_SANDBOX_RULES.md).
