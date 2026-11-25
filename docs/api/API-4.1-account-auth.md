# API 4.1 — Account Management & Authentication

Backend triển khai bằng Spring Boot tại [auth](../../backend/src/main/java/com/algolearner/platform/auth/). Các route này, kể cả quản trị tài khoản, chỉ hoạt động với profile `internal-auth`. Swagger/OpenAPI sinh từ code tại `/v3/api-docs`.

Nguồn: SRS ver 1.1, mục 4.1 (FR-ACC-001–FR-ACC-006, FR-AUTH-001–FR-AUTH-006); `docs/rules/API_DESIGN_GUIDELINES.md`.

## Endpoints

Tất cả đường dẫn có tiền tố `/api/v1`. Request JSON không bọc envelope; response JSON thành công bọc `ApiResponse<T>` (danh sách phân trang dùng `PageResponse<T>`), lỗi dùng `ApiErrorResponse`. Response `204` không có body. Timestamp là ISO-8601 UTC kết thúc `Z`.

| Method | Endpoint | Request Body | Quyền | Mô tả |
| :--- | :--- | :--- | :--- | :--- |
| POST | `/auth/register` | `RegisterRequest` | Public | Đăng ký tài khoản Student hoặc Teacher (mặc định Student) |
| POST | `/auth/verify-otp` | `VerifyOtpRequest` | Public | REGISTER trả `AuthResult` và refresh cookie; RESET_PASSWORD trả `ResetVerification.resetToken` |
| POST | `/auth/resend-otp` | `EmailRequest` | Public | Gửi lại mã OTP (cooldown 60 giây) |
| POST | `/auth/login` | `LoginRequest` | Public | Đăng nhập bằng username/email + mật khẩu |
| POST | `/auth/forgot-password` | `ForgotPasswordRequest` | Public | Yêu cầu gửi OTP đặt lại mật khẩu |
| POST | `/auth/reset-password` | `ResetPasswordRequest` | Public | Đặt lại mật khẩu mới (thu hồi mọi phiên cũ) |
| POST | `/auth/refresh-token` | Refresh cookie HttpOnly | Cookie hợp lệ | Xoay refresh cookie và gia hạn access token; cần header `X-Requested-With: XMLHttpRequest` |
| POST | `/auth/change-password` | `ChangePasswordRequest` | Authenticated | Đổi mật khẩu (giữ phiên hiện tại, thu hồi phiên khác) |
| GET | `/auth/sessions` | Không body | Authenticated | Danh sách các phiên đăng nhập/thiết bị |
| POST | `/auth/logout` | Không body | Bearer hoặc refresh cookie | Đăng xuất phiên hiện tại; cần `X-Requested-With: XMLHttpRequest`; trả 200 |
| POST | `/auth/logout-all` | Không body | Authenticated | Đăng xuất toàn bộ thiết bị; trả 204 |
| DELETE | `/auth/sessions/{id}` | Path `id` | Authenticated | Thu hồi phiên khác thuộc tài khoản; trả 204 |
| POST | `/auth/deactivate` | `DeactivateRequest` | Student, Teacher | Tự vô hiệu hóa tài khoản; trả 204 |
| GET | `/auth/me` | Không body | Authenticated | Xem thông tin tài khoản hiện tại |
| GET | `/admin/accounts` | `page`, `size`, `sort` | Admin | Danh sách tài khoản; `PageResponse<AccountView>` |
| POST | `/admin/accounts/{id}/suspend` | `SuspendAccountRequest` | Admin | Đình chỉ tài khoản (bắt buộc nhập lý do) |
| POST | `/admin/accounts/{id}/unsuspend`| Không body | Admin | Gỡ đình chỉ tài khoản |
| POST | `/admin/accounts/{id}/reactivate`| Không body | Admin | Kích hoạt lại tài khoản đã vô hiệu hóa |

## Quy tắc nghiệp vụ cốt lõi

1. **Chính sách mật khẩu (FR-ACC-005)**: Tối thiểu 8 ký tự, ít nhất 1 chữ cái và 1 chữ số. Không bắt buộc ký tự hoa hay ký tự đặc biệt.
2. **Quy tắc OTP (FR-ACC-004)**: Mã 6 chữ số sinh bằng `SecureRandom`, lưu dạng hash, hạn 10 phút, tối đa 5 lần sai, cooldown 60 giây. Phân biệt mục đích `REGISTER` và `RESET_PASSWORD`. Reset grant sau khi xác minh OTP có hạn riêng 5 phút.
3. **Khóa tự động (FR-AUTH-002)**: Sau 5 lần đăng nhập thất bại liên tiếp, áp dụng `AUTO_LOCK` trong 15 phút. Hết 15 phút hệ thống tự động gỡ bỏ hạn chế đăng nhập.
4. **Thời hạn Token (FR-AUTH-005)**: Access Token 15 phút; Refresh Token 7 ngày (hoặc 30 ngày khi chọn Remember Me).
5. **Đình chỉ & Vô hiệu hóa (FR-ACC-006)**: Đình chỉ hiển thị lý do khi đăng nhập; tự vô hiệu hóa yêu cầu mật khẩu hiện tại và thu hồi toàn bộ phiên.

## Request, response và phiên

- `RegisterRequest`: `email`, `username` (3–50 ký tự), `password` (8–72 ký tự, có chữ và số), `confirmPassword`, `role` (`STUDENT`/`TEACHER`, mặc định Student), `termsAccepted`, `privacyAccepted`. Admin không tự đăng ký. Các request OTP dùng `email` hoặc `usernameOrEmail`, `purpose` (`REGISTER`/`RESET_PASSWORD`), thêm `code` khi xác minh.
- `LoginRequest`: `usernameOrEmail`, `password`, `rememberMe`, `identityType?`. `ForgotPasswordRequest` chỉ cần định danh. `ResetPasswordRequest`: định danh, `resetToken`, `newPassword`, `confirmPassword`, `identityType?`. `ChangePasswordRequest`: `currentPassword`, `newPassword`, `confirmPassword`.
- `identityType` là `AUTO`, `EMAIL` hoặc `USERNAME`. Nếu dùng `usernameOrEmail` và không chọn, mặc định `AUTO`; định danh trùng giữa hai namespace trả 409, client cần yêu cầu người dùng chọn. Nếu chỉ gửi `email`, namespace là `EMAIL`. Giữ lựa chọn nhất quán trong luồng OTP/reset.
- `AuthResult.data` chỉ chứa `token`, `user` và `expiresIn` (giây). Refresh token không có trong JSON: server đặt cookie HttpOnly `algolearner_refresh`, `SameSite=Strict`, path `/api/v1/auth`, `Secure` trên HTTPS. `logout` ưu tiên phiên Bearer khi có, nếu không dùng cookie. `reset-password` thu hồi tất cả phiên; `change-password` giữ phiên hiện tại, thu hồi phiên khác.
- `SessionView`: `id`, `createdAt`, `expiresAt`, `rememberMe`, `current`, `userAgent`. `DeactivateRequest`: `{ "currentPassword": "...", "confirmed": true }`. `SuspendRequest`: `reason` tối đa 500 ký tự và `endsAt?` UTC; `AccountView` gồm trạng thái, lý do/hạn đình chỉ và thời điểm khóa đăng nhập.
- `forgot-password` và yêu cầu gửi lại OTP reset trả thông điệp chung để không tiết lộ trạng thái tài khoản. OTP reset đã xác minh cấp `resetToken` có hạn 5 phút, không cấp JWT.

Mã lỗi thường gặp: 400 cho body/OTP không hợp lệ, 401 cho xác thực thất bại, 403 cho sai quyền hoặc đình chỉ, 409 cho định danh mơ hồ/trùng, 423 cho khóa đăng nhập tạm thời, 429 cho cooldown, 503 khi không thể gửi email đăng ký. Client dựa vào HTTP status và `errorCode`, không dựa vào nguyên văn `message`.
