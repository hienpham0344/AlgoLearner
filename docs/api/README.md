# API backend — SRS 4.1–4.6

| Phân hệ | Tài liệu |
| :--- | :--- |
| 4.1 Tài khoản và xác thực | [API-4.1-account-auth.md](API-4.1-account-auth.md) |
| 4.2 Hồ sơ và dashboard | [API-4.2-user-profile-dashboard.md](API-4.2-user-profile-dashboard.md) |
| 4.3 Lớp học | [API-4.3-classroom.md](API-4.3-classroom.md) |
| 4.4 Thông báo lớp | [API-4.4-announcements.md](API-4.4-announcements.md) |
| 4.5 Bài học | [API-4.5-lessons.md](API-4.5-lessons.md) |
| 4.6 Bài tập | [API-4.6-assignments.md](API-4.6-assignments.md) |

**Quy ước chung.** Base path là `/api/v1`. Request JSON không bọc envelope. Thành công JSON thông thường là `ApiResponse<T>` gồm `success`, `message`, `data`, `timestamp`; danh sách phân trang là `PageResponse<T>` gồm `success`, `data.items`, `data.pagination`, `timestamp`. Lỗi là `ApiErrorResponse` gồm `success=false`, `errorCode`, `message`, `path`, `timestamp` và có thể có `errors`. `204` không có body; response file/avatar là binary. Toàn bộ `Instant` ở dạng ISO-8601 UTC kết thúc `Z`. Các route riêng tư dùng `Authorization: Bearer <access_token>`; quyền còn được kiểm tra theo trạng thái tài khoản và phạm vi lớp tại service.

DTO, enum, OpenAPI tại `/v3/api-docs` của backend đang chạy và [API Design Guidelines](../rules/API_DESIGN_GUIDELINES.md) là nguồn đối chiếu khi tích hợp. Các trang này mô tả hợp đồng code tại ngày 18-11-2026; [báo cáo kiểm thử](../test-reports/2026-11-18-backend-implementation-verification.md) nêu rõ mức xác minh cục bộ và phần chưa xác minh trên dịch vụ thật.

Smoke test HTTP cục bộ: [Postman collection 4.1–4.6](../postman/README.md) và [kết quả PostgreSQL/Newman](../test-reports/2026-11-19-postman-db-smoke.md).
