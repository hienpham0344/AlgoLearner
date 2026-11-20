# API 4.3 — Quản lý lớp học

Nguồn: SRS ver 1.1 mục 4.3, [ClassroomController](../../backend/src/main/java/com/algolearner/platform/classroom/ClassroomController.java), [ClassroomDtos](../../backend/src/main/java/com/algolearner/platform/classroom/ClassroomDtos.java). Xem [quy ước chung](README.md). Mọi route dưới đây bắt đầu bằng `/api/v1/classrooms` và yêu cầu Bearer token.

## Endpoint

| Method | Đường dẫn tiếp theo | Body / query | Thành công | Quyền chính |
| :--- | :--- | :--- | :--- | :--- |
| POST | `/` | `Create` | 201 `ApiResponse<View>` | Teacher; Admin có thể chỉ định `ownerId` Teacher |
| GET | `/` | `page`, `size`, `sort` | 200 `PageResponse<View>` | Lớp thuộc phạm vi người gọi |
| GET | `/{classroomId}` | — | 200 `ApiResponse<View>` | Chủ lớp/thành viên hợp lệ |
| PATCH | `/{classroomId}` | `Update` | 200 `ApiResponse<View>` | Chủ lớp hoặc Admin active |
| POST | `/{classroomId}/archive` | — | 204 | Chủ lớp hoặc Admin active |
| DELETE | `/{classroomId}` | — | 204 | Chủ lớp hoặc Admin active; chỉ khi đủ điều kiện xóa |
| PATCH | `/{classroomId}/join-configuration` | `CodeConfig` | 200 `ApiResponse<View>` | Chủ lớp |
| POST | `/{classroomId}/class-code/regenerate` | — | 200 `ApiResponse<View>` | Chủ lớp |
| POST | `/join` | `JoinByCode` | 200 `ApiResponse<JoinResult>` | Student |
| POST | `/{classroomId}/leave` | — | 204 | Student thành viên |
| POST | `/{classroomId}/members/direct` | `DirectAdd` | 204 | Chủ lớp |
| DELETE | `/{classroomId}/members/{studentId}` | — | 204 | Chủ lớp |
| GET | `/{classroomId}/members` | `page`, `size`, `sort` | 200 `PageResponse<Member>` | Chủ lớp hoặc Admin active |
| GET | `/{classroomId}/join-requests` | `page`, `size`, `sort` | 200 `PageResponse<JoinRequest>` | Chủ lớp |
| POST | `/{classroomId}/join-requests/{requestId}/approve` | — | 204 | Chủ lớp |
| POST | `/{classroomId}/invitations` | `Invite` | 201 `ApiResponse<UUID>` | Chủ lớp |
| GET | `/{classroomId}/invitations` | `page`, `size`, `sort` | 200 `PageResponse<InvitationView>` | Chủ lớp |
| GET | `/invitations/me` | `page`, `size`, `sort` | 200 `PageResponse<InvitationView>` | Student được mời |
| POST | `/invitations/{invitationId}/accept` | — | 204 | Student được mời |
| POST | `/invitations/{invitationId}/decline` | — | 204 | Student được mời |
| POST | `/{classroomId}/invitations/{invitationId}/revoke` | — | 204 | Chủ lớp |
| POST | `/{classroomId}/transfer-owner` | `TransferOwner` | 204 | Admin active; đích là Teacher hợp lệ |

## DTO và hành vi

- `Create`: `{ name, description?, joinMode, codeExpiresAt?, ownerId? }`. `name` tối đa 150 ký tự, `description` tối đa 2000; `joinMode` là `AUTO_JOIN` hoặc `REQUIRE_APPROVAL`. `ownerId` chỉ có hiệu lực trong ngữ cảnh Admin hợp lệ; client Teacher không thể gán chủ khác. `Update` chỉ gồm `name`, `description`.
- `CodeConfig`: `{ codeExpiresAt?, codeLocked, joiningOpen, joinMode }`. Khi tắt tham gia, khóa code hoặc code hết hạn, Student mới không thể dùng mã; thành viên hiện có vẫn được giữ. Regenerate tạo mã mới và vô hiệu mã cũ.
- `JoinByCode`: `{ classCode }` (tối đa 20 ký tự). `JoinResult` gồm `outcome` (`JOINED` hoặc `PENDING_APPROVAL`), `classroomId`, `requestId` (chỉ có khi chờ duyệt). Lời mời gửi theo `{ email, expiresAt }`; Student chỉ có thể chấp nhận/từ chối lời mời gửi cho mình và còn hiệu lực.
- `DirectAdd`: `{ usernameOrEmail, identityType? }`; khi định danh mơ hồ, dùng `identityType=EMAIL` hoặc `USERNAME`. `TransferOwner`: `{ targetTeacherId }`; chỉ Admin được chuyển quyền. Quyền của Teacher được kiểm lại với chủ lớp hiện tại trong DB, kể cả sau chuyển quyền.
- `View`: `id`, `name`, `description`, `status`, `classCode`, `codeExpiresAt`, `codeLocked`, `joiningOpen`, `joinMode`, `ownerId`, `createdAt`, `updatedAt`. Student không nhận `classCode`/`codeExpiresAt` trong view. Các collection dùng phân trang Spring với `page` 0-based; mặc định `size=20`. Không suy ra quyền từ `ownerId` gửi lên hoặc ID trong URL.
- Archive đặt lớp ở trạng thái chỉ đọc. Xóa vật lý chỉ khi không còn dữ liệu phụ thuộc theo quy tắc service; nếu có tiến độ/nội dung cần giữ lịch sử, dùng archive. Quyền Student với lớp/nhóm dữ liệu riêng vẫn phụ thuộc membership còn hiệu lực.

Ví dụ tham gia lớp:

```http
POST /api/v1/classrooms/join HTTP/1.1
Authorization: Bearer <access_token>
Content-Type: application/json

{"classCode":"ABC12345"}
```

Các lỗi cần xử lý: `400` body/ID sai, `401` chưa xác thực, `403` sai vai trò/chủ lớp/thành viên, `404` lớp/lời mời không tồn tại trong phạm vi, `409` xung đột trạng thái hoặc định danh; mã lỗi chi tiết nằm ở `errorCode`.
