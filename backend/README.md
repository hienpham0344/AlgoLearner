# AlgoLearner Backend

## Chạy ứng dụng với Supabase

Yêu cầu Java 21+, Maven 3.9+, Supabase PostgreSQL và Gmail SMTP. Module 4.1 dùng
xác thực nội bộ Spring Boot; Supabase chỉ lưu dữ liệu, không dùng Supabase Auth.
Module 4.2 đọc/ghi profile và dashboard qua DB; module 4.3 dùng các JPA repository
cho lớp học, thành viên, yêu cầu tham gia và lời mời. Không dùng H2 hoặc backend mock
trong luồng chạy ứng dụng. Thiếu cấu hình Supabase thì backend dừng và báo lỗi.

1. Nếu chưa có `backend/.env`, tạo từ `.env.example`; không ghi đè file đang có.
2. Trong Supabase **Connect**, lấy JDBC host/username của **Session pooler**, cổng 5432
   ([hướng dẫn Spring Boot chính thức](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot)).
   Điền `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` vào `.env`. URL cần `sslmode=require`
   hoặc xác minh chứng chỉ; không đặt mật khẩu trong URL. Dùng role sở hữu các bảng
   ứng dụng và đủ quyền chạy Flyway. Có thể dùng direct host `db.<ref>.supabase.co`
   nếu máy truy cập được địa chỉ mạng đó.
3. Điền `MAIL_USERNAME`, `MAIL_PASSWORD` (Google App Password), `JWT_SECRET`
   ngẫu nhiên ít nhất 32 byte và `OTP_HMAC_SECRET` là Base64 của ít nhất 32 byte
   ngẫu nhiên. Tạo một lần bằng `openssl rand -hex 32` cho JWT và
   `openssl rand -base64 32` cho OTP; giữ hai khóa riêng qua các lần restart.
   `MAIL_FROM` để trống sẽ dùng địa chỉ gửi. Giữ `.env` qua các lần pull/restart.
4. Trong thư mục `backend`, chạy `bash scripts/run-local-email.sh`.
5. Trong `frontend`, chạy `npm run dev`, mở http://127.0.0.1:3000.

Launcher tự đọc `.env` từ thư mục backend; biến môi trường đã có được ưu tiên.
Parser chỉ đọc literal `KEY=VALUE`, chấp nhận dấu nháy bao quanh, comment nguyên dòng,
CRLF; không chạy shell expression, nội suy biến hoặc inline comment.
Mật khẩu không xuất hiện trong command arguments. File `.env` được Git bỏ qua.
Spring Boot tự nó không nạp `.env`; khi chạy Maven trực tiếp cần truyền biến môi trường.
`mvn-local.ps1` hỗ trợ nạp file cho PowerShell.

Flyway quản lý schema; Hibernate chỉ `validate`, không xóa/tạo lại bảng khi restart.
Các bảng nghiệp vụ bật RLS và thu hồi quyền trực tiếp của `anon`/`authenticated`:
frontend chỉ gọi Spring API, không truy cập bảng tài khoản qua Supabase Data API.
Backend dùng JDBC owner role; phân quyền role/owner/student được kiểm tra trong Spring.
Xem [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security) về quyền owner và việc bật RLS.
Chưa cung cấp thông tin Supabase thì chưa thể xác nhận kết nối hay áp dụng migration lên đó.
Dữ liệu từng mất trong những lần chạy DB RAM cũ không tự phục hồi.

Profile, tài khoản và lớp học lưu trong PostgreSQL. Avatar hiện lưu file tại
`AVATAR_DIRECTORY`; cần giữ/backup thư mục này cùng cấu hình riêng khi chuyển máy chủ.
Dashboard đếm tài khoản, lớp học và thành viên thật. Các nhóm assignment, quiz,
course, notification, submission, audit chưa có storage trong phạm vi hiện tại sẽ
trả danh sách rỗng/0, không trả số liệu demo.

## Frontend khi backend dừng

Vite luôn chuyển API tới backend trước, kể cả khi dùng alias `dev:auth-demo` hay
`dev:backend-mock`. Chỉ kết nối bị từ chối trước khi gửi request mới dùng mock frontend.
HTTP 400/401/403/404/5xx và lỗi sau khi đã kết nối không kích hoạt mock hoặc gửi lại thao tác.
Giao diện có banner dữ liệu mẫu, tách phiên thật/mẫu; khi backend trở lại phải đăng nhập
bằng tài khoản thật. Mock chỉ phục vụ Vite development, không được đóng gói thành backend.
Dữ liệu mẫu không đồng bộ lên Supabase. Có thể đăng nhập mẫu bằng
`learner@example.test`, `teacher`, `admin` với mật khẩu `DemoPass123!`.
Các thao tác chưa có mô phỏng sẽ báo cần backend; không giả vờ đăng ký/gửi Gmail thành công.

## Auth và profile (4.1, 4.2)

- Mật khẩu ít nhất 8 ký tự, gồm chữ ASCII và số, tối đa 72 byte UTF-8; BCrypt.
- OTP đăng ký/reset: 6 số, hết hạn 10 phút, cooldown 60 giây, tối đa 5 lần sai; chỉ dùng một lần.
- Quên mật khẩu: `/api/v1/auth/forgot-password` → `/verify-otp` với `RESET_PASSWORD`
  → `/reset-password` bằng grant 5 phút. Reset thu hồi mọi session; đổi mật khẩu yêu cầu
  mật khẩu hiện tại và giữ session hiện tại, thu hồi những session khác.
- Access JWT 15 phút; refresh session 7 ngày, Remember me 30 ngày; token refresh xoay vòng,
  lưu hash trong DB và cookie HttpOnly/SameSite=Strict. Refresh/logout cần
  `X-Requested-With: XMLHttpRequest`. Triển khai HTTPS để cookie có Secure.
- `/auth/me` khôi phục identity; `/users/me/profile` đọc/ghi hồ sơ, avatar, privacy.
  Profile công khai không lộ email, profile riêng tư trả 404. Admin không tự đăng ký.
- Sai mật khẩu 5 lần khóa 15 phút. SMTP lỗi trả 503 và rollback thay đổi OTP.
- Để tạo Admin cố định lần đầu, cấu hình đồng thời `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_PASSWORD` trong `.env` trước khi chạy backend. Backend chỉ tạo khi chưa có tài khoản đó; khởi động lại không đổi mật khẩu hoặc vai trò. Cấu hình thiếu hoặc trùng định danh tài khoản khác khiến khởi động thất bại.
- `GET /api/v1/auth/sessions` liệt kê phiên của chính tài khoản; `DELETE /api/v1/auth/sessions/{id}` thu hồi phiên khác; `POST /api/v1/auth/logout-all` thu hồi mọi phiên. `POST /api/v1/auth/deactivate` yêu cầu `{ "currentPassword": "...", "confirmed": true }` và thu hồi mọi phiên.
- Admin dùng `GET /api/v1/admin/accounts` (phân trang), `POST /api/v1/admin/accounts/{id}/suspend` với `{ "reason": "...", "endsAt": null }`, `POST /unsuspend` và `POST /reactivate`. Đình chỉ có thời hạn được tự gỡ khi hết hạn; các thao tác đổi trạng thái ghi vào `tbl_account_audit_log`.

Swagger: http://localhost:8080/swagger-ui/index.html; đăng nhập lấy JWT thật để Authorize.
Không dùng token `mock-*` với backend ứng dụng. `/api/v1/status` chỉ báo backend đang chạy.

## Kiểm thử

`python3 -B -m unittest discover -s scripts/tests` kiểm tra launcher không cần DB.
`mvn verify` chạy unit/API mock tests; JDBC probe và integration tests được bỏ qua nếu
chưa cấu hình DB test. Không còn dependency H2.

Điều kiện `TEST_DB_URL` được khai báo trực tiếp trên từng lớp integration test;
JUnit không kế thừa `@EnabledIfEnvironmentVariable` từ lớp cha.
Trong `frontend`, chạy `npm test`, `npm run build` và
`npm run test:e2e -- e2e/backend-source.spec.ts` để kiểm tra nguồn dữ liệu của 4.1–4.3:
backend khả dụng trả account/profile/dashboard/classroom; backend tắt mới dùng mẫu;
backend trở lại kết thúc phiên mẫu; lỗi HTTP hoặc mất kết nối sau khi gửi request
không chuyển sang mock hay chạy lại thao tác ghi. E2E này dùng HTTP fixture localhost,
không xác nhận kết nối hoặc lưu dữ liệu trên Supabase thật.

Để chạy integration tests, đặt `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`
trỏ tới PostgreSQL dành riêng cho test, tên database phải kết thúc bằng `_test`.
Không dùng DB ứng dụng hoặc schema public. Mỗi JVM tạo schema ngẫu nhiên, chạy Flyway
và Hibernate validate, rồi xóa đúng schema của nó khi thoát; dữ liệu ở schema khác giữ nguyên.
Có thể lưu biến test trong `.env.test` (Git ignore), chạy `python3 -B scripts/run-postgres-tests.py`.
Tests dùng mail transport giả để không phát email; không chứng minh Gmail đã tới inbox.
Profile `mock,test` và fixture chỉ dùng bên trong test, không phải cách chạy ứng dụng.

Probe Supabase chỉ đọc `SELECT 1`, không migration: sau khi xuất biến DB từ `.env`, chạy
`mvn -Dsupabase.check=true -Dtest=SupabaseConnectionTest test`, hoặc PowerShell
`./mvn-local.ps1 '-Dsupabase.check=true' '-Dtest=SupabaseConnectionTest' test`.
Probe thành công không có nghĩa ứng dụng đã được khởi động hay có đủ bảng.
Kiểm thử HTTP/JPA trực tiếp trên Supabase ứng dụng chỉ chạy khi chủ động đặt
`-Dlive.supabase.check=true -Dtest=LiveSupabaseFlowTest`. Bài này tạo hai tài khoản
và một lớp/lời mời/thông báo có tiền tố `codexlive`, dùng mail sender giả, và giữ dữ liệu
để đối chiếu sau kiểm thử; không gửi email thật.
