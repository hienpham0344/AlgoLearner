# API 4.2 — User Profile & Dashboard

Backend đã triển khai bằng Spring Boot tại [profile](../../backend/src/main/java/com/algolearner/platform/profile/). Contract này thay bản đề xuất trước theo danh sách API đã yêu cầu. Xem [hướng dẫn chạy và kiểm thử](../../backend/README.md). Swagger/OpenAPI sinh trực tiếp từ code tại `/v3/api-docs`.

Sources: SRS ver 1.1, section 4.2, FR-PRO-001 through FR-PRO-005; docs/rules/API_DESIGN_GUIDELINES.md. Schema documents are reference designs, not runtime requirements.

## Endpoint

Tất cả đường dẫn có tiền tố `/api/v1`. Request JSON không bọc envelope; response dùng ApiResponse hoặc ApiErrorResponse. Timestamp là ISO-8601 UTC kết thúc Z; FE hiển thị UTC+7.

| Method | Endpoint | Request | Quyền |
| :--- | :--- | :--- | :--- |
| GET | `/users/me/profile` | Không body | Student, Teacher |
| PATCH | `/users/me/profile` | ProfileUpdate | Student, Teacher |
| PUT | `/users/me/profile/avatar` | multipart field file | Student, Teacher |
| PATCH | `/users/me/profile/privacy` | boolean isPublic | Student, Teacher |
| GET | `/users/{username}/profile` | Path username | Khách, khi hồ sơ công khai hợp lệ |
| GET | `/users/{username}/profile/avatar` | Path username | Chủ tài khoản hoặc hồ sơ công khai hợp lệ |
| GET | `/dashboards/student` | previewSize?, upcomingDays? | Student |
| GET | `/dashboards/teacher` | previewSize?, upcomingDays? | Teacher |
| GET | `/admin/dashboard` | previewSize?, from?, to?, interval? | Admin |

## Hồ sơ

Schema thực thi: [ProfileDtos.java](../../backend/src/main/java/com/algolearner/platform/profile/ProfileDtos.java).

```typescript
interface ProfileUpdate {
  fullName?: string | null;
  shortBio?: string | null;
}
interface PrivacyUpdate { isPublic: boolean }
interface Streak { currentStreak: number; highestStreak: number }
interface UserProfile {
  fullName: string | null;
  username: string;
  email: string;
  avatarUrl: string | null;
  shortBio: string | null;
  role: 'STUDENT' | 'TEACHER';
  joinedAt: string;
  isPublic: boolean;
  publicPracticeStatistics: { solved: number };
  streak: Streak;
}
interface PublicUserProfile {
  username: string;
  avatarUrl: string | null;
  shortBio: string | null;
  solved: number;
  streak: Streak;
}
```

- PATCH chỉ nhận fullName/shortBio: bỏ trường giữ nguyên, null/blank xóa, trim chuỗi. Giới hạn thiết kế theo ERD: 150/500 Unicode code point. PATCH rỗng, sai kiểu, trường lạ/username/email/role/isPublic/trường hệ thống trả 400 và giữ dữ liệu cũ.
- Privacy chỉ nhận boolean thật, không nhận null, số hoặc chuỗi. Account mục tiêu lấy từ principal, không nhận ID do client gửi.
- joinedAt lấy accounts.created_at. Solved đếm Public Problem phân biệt có Official Submission ACCEPTED. Streak tính ngày UTC+7 theo submitted_at của submission Accepted, không gồm RUN/Class Practice/Assignment.
- Avatar kiểm tra bytes JPEG/PNG thực tế, dung lượng và số pixel theo cấu hình, re-encode trước khi lưu. Không nhận URL/storage key từ client. Upload/validation/repository lỗi giữ avatar cũ. Mock: remove the new file on repository failure; delete the old file only after a successful update. Private avatar reads require the owner token.
- Public profile chỉ có đúng năm trường đã định nghĩa. Private, Suspended đang hiệu lực, Deactivated hoặc không tồn tại cùng trả 404 PROFILE_NOT_FOUND. Đọc avatar kiểm tra lại cùng chính sách.

## Dashboard

Schema thực thi: [DashboardResponse.java](../../backend/src/main/java/com/algolearner/platform/profile/dashboard/DashboardResponse.java). Preview dùng `{ items, totalElements, hasMore }`; tổng tính sau lọc quyền, trước limit. Không tạo bảng dashboard/counter.

| Role | Nhóm dữ liệu |
| :--- | :--- |
| Student | classrooms, upcomingAssignments, upcomingQuizzes, enrolledCourses, publicPracticeStatistics, streak, recentNotifications |
| Teacher | classrooms, openOrUpcomingAssignments, ungradedSubmissions, recentSubmissions, recentNotifications, pendingContributions, classroomStatistics |
| Admin | totals, pendingTeacherContributions, accountModeration, recentActivities, importantAuditEvents, configurationShortcuts, charts |

Response có role, generatedAt và displayTimeZone = Asia/Ho_Chi_Minh. Student chỉ lấy membership ACTIVE và dữ liệu của chính mình, áp dụng deadline cá nhân. Teacher chỉ lấy Class ACTIVE đang sở hữu, kể cả sau chuyển quyền; bài chưa chấm lấy lượt nộp mới nhất theo assignment/student. Notification đúng người nhận, loại soft-deleted và quá 90 ngày. Contribution tách Course/Problem; Admin chỉ đếm đóng góp chờ duyệt của Teacher.

| Query | Quy tắc |
| :--- | :--- |
| previewSize | Mặc định 5, từ 1 đến 20 cho mỗi nhóm |
| upcomingDays | Student/Teacher: mặc định 7, từ 1 đến 30 |
| from, to | Admin: UTC instants, khoảng [from,to), from < to, tối đa 366 ngày; mặc định 30 ngày gần nhất |
| interval | Admin: day (mặc định), week hoặc month; tuần bắt đầu thứ Hai |

from/to chỉ áp dụng charts. Totals đếm tất cả bản ghi còn lưu, gồm tài khoản chưa xác thực/vô hiệu hóa, Class lưu trữ, Course/Problem chưa xuất bản. Official Submission không gồm RUN hay lượt nộp file/tự luận. Locked/suspended/confirmed violation đếm DISTINCT account riêng từng nhóm, không cộng thành tổng duy nhất. Biểu đồ đếm sự kiện tạo/nộp trong khoảng, nhóm UTC+7, bù bucket rỗng bằng 0 và cắt bucket đầu/cuối theo query.

## Xác thực, lỗi và truy vết

Mock mode: use Authorization: Bearer mock-student / mock-teacher / mock-admin. See backend/README.md for all demo identities. Mock authentication is a separate profile adapter; AccountRepository supplies current roles and restrictions. Replace the decoder and repository adapters when integrating real JWT authentication and the official schema. Profile updates live in memory and reset on restart; no database is required.

| HTTP | Trường hợp |
| :--- | :--- |
| 400 | Body/query sai, trường không được phép, validation thất bại |
| 401 | Thiếu/sai/hết hạn token hoặc tài khoản không được xác thực hiện hành |
| 403 | Sai vai trò hoặc quyền chức năng |
| 404 | Profile/avatar không tồn tại hoặc bị ẩn |
| 413 | Avatar quá dung lượng |
| 415 | Loại tệp không hỗ trợ |
| 500 | Lỗi DB/tổng hợp; không giả dữ liệu thành 0/[] |
| 503 | File Storage không sẵn sàng |

FR-PRO-001: ProfileController/ProfileService; FR-PRO-002: privacy/public profile/avatar access; FR-PRO-003/004/005: role-scoped dashboards. No automated tests are maintained for this scope. Storage is behind interfaces; replace in-memory adapters with PostgreSQL implementations when the official schema is available. See backend/README.md for manual verification and integration steps.
