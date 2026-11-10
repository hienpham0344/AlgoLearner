# API 4.1 — Account Management & Authentication

Backend đã triển khai bằng Spring Boot tại [auth](../../backend/src/main/java/com/algolearner/platform/auth/). Swagger/OpenAPI sinh trực tiếp từ code tại `/v3/api-docs`.

Nguồn: SRS ver 1.1, mục 4.1 (FR-ACC-001–FR-ACC-006, FR-AUTH-001–FR-AUTH-006); `docs/rules/API_DESIGN_GUIDELINES.md`.

## Endpoints

Tất cả đường dẫn có tiền tố `/api/v1`. Request JSON không bọc envelope; response bọc `ApiResponse<T>` hoặc `ApiErrorResponse`. Timestamp là ISO-8601 UTC kết thúc `Z`.

| Method | Endpoint | Request Body | Quyền | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| POST | `/auth/register` | `RegisterRequest` | Public | Đăng ký tài khoản Student hoặc Teacher (mặc định Student) |
| POST | `/auth/verify-otp` | `VerifyOtpRequest` | Public | Xác thực mã OTP cho REGISTER hoặc RESET_PASSWORD |
| POST | `/auth/resend-otp` | `ResendOtpRequest` | Public | Gửi lại mã OTP (cooldown 60 giây) |
| POST | `/auth/login` | `LoginRequest` | Public | Đăng nhập bằng username/email + mật khẩu |
| POST | `/auth/forgot-password` | `ForgotPasswordRequest` | Public | Yêu cầu gửi OTP đặt lại mật khẩu |
| POST | `/auth/reset-password` | `ResetPasswordRequest` | Public | Đặt lại mật khẩu mới (thu hồi mọi phiên cũ) |
| POST | `/auth/refresh-token` | `RefreshTokenRequest` | Public | Gia hạn JWT Access Token bằng Refresh Token |
| POST | `/auth/change-password` | `ChangePasswordRequest` | Authenticated | Đổi mật khẩu (giữ phiên hiện tại, thu hồi phiên khác) |
| GET | `/auth/sessions` | Không body | Authenticated | Danh sách các phiên đăng nhập/thiết bị |
| POST | `/auth/logout` | Không body | Authenticated | Đăng xuất phiên hiện tại |
| POST | `/auth/logout-all` | Không body | Authenticated | Đăng xuất toàn bộ thiết bị |
| DELETE | `/auth/sessions/{id}` | Path `id` | Authenticated | Thu hồi phiên được chỉ định (kiểm tra quyền sở hữu IDOR) |
| POST | `/auth/deactivate` | `DeactivateAccountRequest` | Student, Teacher | Tự vô hiệu hóa tài khoản |
| GET | `/auth/me` | Không body | Authenticated | Xem thông tin tài khoản hiện tại |
| POST | `/admin/accounts/{id}/suspend` | `SuspendAccountRequest` | Admin | Đình chỉ tài khoản (bắt buộc nhập lý do) |
| POST | `/admin/accounts/{id}/unsuspend`| Không body | Admin | Gỡ đình chỉ tài khoản |
| POST | `/admin/accounts/{id}/reactivate`| Không body | Admin | Kích hoạt lại tài khoản đã vô hiệu hóa |

## Quy tắc nghiệp vụ cốt lõi

1. **Chính sách mật khẩu (FR-ACC-005)**: Tối thiểu 8 ký tự, ít nhất 1 chữ cái và 1 chữ số. Không bắt buộc ký tự hoa hay ký tự đặc biệt.
2. **Quy tắc OTP (FR-ACC-004)**: Mã 6 chữ số sinh bằng `SecureRandom`, lưu dạng hash, hạn 5 phút, tối đa 5 lần sai, cooldown 60 giây. Phân biệt mục đích `REGISTER` và `RESET_PASSWORD`. SRS ver 1.1 vẫn ghi 10 phút; dự án áp dụng 5 phút theo `AGENTS.md` và `SECURITY_AND_SANDBOX_RULES.md` cho đến khi các tài liệu được thống nhất.
3. **Khóa tự động (FR-AUTH-002)**: Sau 5 lần đăng nhập thất bại liên tiếp, áp dụng `AUTO_LOCK` trong 15 phút. Hết 15 phút hệ thống tự động gỡ bỏ hạn chế đăng nhập.
4. **Thời hạn Token (FR-AUTH-005)**: Access Token 15 phút; Refresh Token 7 ngày (hoặc 30 ngày khi chọn Remember Me).
5. **Đình chỉ & Vô hiệu hóa (FR-ACC-006)**: Đình chỉ hiển thị lý do khi đăng nhập; tự vô hiệu hóa yêu cầu mật khẩu hiện tại và thu hồi toàn bộ phiên.
