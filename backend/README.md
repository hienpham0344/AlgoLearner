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
Hai lịch sử xác thực đã tồn tại: Supabase hiện dùng `V202611100001__internal_registration.sql`
và mặc định nạp `classpath:db/migration`. Database từng chạy
`V202611100001__auth_module_schema.sql` từ `main` phải đặt
`FLYWAY_LOCATIONS=classpath:db/main-auth` trước khi khởi động. Track này giữ nguyên
các migration đã áp dụng, bổ sung schema cho `internal-auth`, chuyển OTP còn hạn và
thu hồi các refresh session cũ vì hợp đồng cookie mới. Không đổi track trên một
database đã có `flyway_schema_history`; kiểm tra cột `script` trong bảng này trước.
Các bảng nghiệp vụ bật RLS và thu hồi quyền trực tiếp của `anon`/`authenticated`:
frontend chỉ gọi Spring API, không truy cập bảng tài khoản qua Supabase Data API.
Backend dùng JDBC owner role; phân quyền role/owner/student được kiểm tra trong Spring.
Xem [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security) về quyền owner và việc bật RLS.
Chưa cung cấp thông tin Supabase thì chưa thể xác nhận kết nối hay áp dụng migration lên đó.
Dữ liệu từng mất trong những lần chạy DB RAM cũ không tự phục hồi.

Profile, tài khoản và lớp học lưu trong PostgreSQL. File mới và avatar mới lưu trong
Supabase Storage private; cấu hình theo phần File bên dưới. Giữ/backup `AVATAR_DIRECTORY`
để đọc avatar cũ cho tới khi hoàn tất chuyển dữ liệu.
Dashboard lấy dữ liệu Assignment, Quiz, Public Course, Public Practice, notification và audit
trực tiếp từ PostgreSQL, theo tài khoản/thành viên/chủ lớp. Preview có count/list cùng bộ lọc,
sắp xếp `created_at DESC, id`, tối đa 100 bản ghi tại repository (API giữ giới hạn 20).
Tổng catalog chỉ gồm nội dung `PUBLISHED`; tổng bài nộp tính mỗi official Assignment/Quiz
attempt và Public/Class Practice submission một lần, loại RUN và code-question trùng attempt.
Practice dùng kết quả SUBMIT generation mới nhất; rejudge thay ACCEPTED bằng kết quả khác
sẽ cập nhật solved/streak, ngày streak hiển thị theo UTC+7.

Dashboard Admin đếm tài khoản có vi phạm đã xác nhận chưa thu hồi từ ledger riêng, không
suy diễn từ khóa/đình chỉ. Admin xác nhận qua
`POST /api/v1/admin/accounts/{accountId}/violations` với `{requestId, reason}`;
`requestId` lặp lại cùng nội dung không tạo thêm bản ghi/audit. Thu hồi qua
`POST /api/v1/admin/accounts/{accountId}/violations/{violationId}/revoke` với `{reason}`.
Lịch sử xác nhận/thu hồi và audit được ghi atomic; đây là dependency tối thiểu của
FR-PRO-005, chưa triển khai quy trình báo cáo vi phạm hoặc giao diện quản trị đầy đủ.

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
Tài liệu tích hợp API 4.1–4.6: [docs/api/README.md](../docs/api/README.md).
Không dùng token `mock-*` với backend ứng dụng. `/api/v1/status` chỉ báo backend đang chạy.

## Bảo vệ Lesson và Assignment (4.5, 4.6)

- Student gọi chi tiết Assignment trước `startTime` nhận `ASSIGNMENT_NOT_STARTED`;
  danh sách chỉ trả tên, thời điểm mở và định danh/trạng thái, ẩn các cấu hình khác.
  Publish trước giờ mở lưu `SCHEDULED`; scheduler `app.assignment.schedule-poll-ms`
  (mặc định `1000`) kích hoạt đúng thời gian server, đồng thời đường start hỗ trợ kích hoạt khi tới giờ.
  Teacher Owner vẫn đọc được để quản lý.
- Bắt đầu, lưu nháp và nộp bài đều kiểm tra trạng thái Assignment, lớp, Owner,
  deadline và Lesson liên kết đã archive. Xóa câu hỏi là soft-retire khỏi bản soạn thảo;
  question, options và đáp án lịch sử vẫn được giữ. Revision đã publish và attempt đã start
  dùng snapshot bất biến; publish bản mới mới loại câu hỏi đã retire. Thời lượng và
  chính sách timeout không được đổi khi đã có attempt.
- Timer dùng thời gian server. Tới hạn, `AUTO_SUBMIT` dùng đáp án đã lưu và mốc hết giờ
  làm `submittedAt`; `LOCK_WITHOUT_SUBMIT` khóa bản nháp, không tạo bài nộp chính thức.
  Không cho nộp trễ thì cutoff là mốc sớm hơn deadline/thời lượng; cho nộp trễ thì
  timer chỉ dựa trên thời lượng, bài nộp sau deadline mang cờ Late và không bị trừ điểm.
  Autosave tới sau cutoff không thêm nội dung mới vào bài. Không đổi late policy khi
  deadline đã qua và đã có attempt để tránh ghi nhận thời điểm nộp trước lúc bắt đầu.
- Job xử lý timeout chạy ngoài profile `mock`/`test`, tối đa 100 attempt mỗi lượt;
  `app.assignment.timeout-poll-ms` mặc định `1000`. Các thao tác trên cùng Assignment
  dùng khóa PostgreSQL để tuần tự hóa start/autosave/submit/close/xóa câu hỏi.
- Lesson `/complete` chỉ dành cho `PUBLISHED` với `completionMode=MANUAL`.
  `AUTO_ITEMS` dùng luật ALL/ANY theo bài đã nộp hoặc ngưỡng điểm đã công bố từ
  Assignment/Quiz; lưu lịch sử thay đổi completion. Teacher đọc tiến độ có phân trang.
  Khôi phục Lesson archived dùng trạng thái trước archive; legacy không rõ nguồn về DRAFT.
- Admin đang hoạt động có ba quyền chấm điểm qua namespace riêng
  `/api/v1/admin/classrooms/{classId}/assignments/{assignmentId}`:
  `POST /rejudge`, `GET /submissions/{attemptId}/evaluations` và
  `PATCH /questions/{questionId}/code-case-weights` (`{weights, reason}`). Rejudge và
  đổi trọng số yêu cầu lý do, giữ actor `ADMIN` trong audit, bị chặn khi lớp archive
  hoặc Owner bị khóa; lịch sử vẫn đọc được. Admin không trở thành Teacher/thành viên lớp.
  Teacher Owner có endpoint đổi trọng số tương ứng trong namespace lớp học.
  Trọng số cập nhật bản soạn thảo; Teacher Owner publish revision mới cho lượt làm tiếp theo.
- Bộ chấm trắc nghiệm kiểm tra danh sách UUID đúng câu hỏi và số lựa chọn; Partial Credit
  giữ điểm của lựa chọn đúng khi có lựa chọn sai. Assignment chấm theo revision của attempt,
  phân bổ trọng số câu hỏi và testcase, lưu evaluation/release history; Code Question đưa
  job vào Judge và lấy verdict nội bộ để tính điểm, không dùng hidden input/output trong response.
  Rejudge giữ cùng official submission qua `ownerId`/`requestKey`, tăng `generation` riêng.
  Teacher/Admin gửi `{reason, target}` với `target=ORIGINAL` (mặc định) hoặc `LATEST_PUBLISHED`;
  chỉ dùng testcase/trọng số testcase/giới hạn từ revision đã publish, giữ source, ngôn ngữ,
  điểm tối đa và trọng số câu hỏi của attempt gốc. Working edits chưa publish không được dùng.
  Mỗi generation giữ `judgeRevisionId`; kết quả đang chờ/infra không thay kết quả đã công bố.
  Clone giữ cấu hình, thời gian và snapshot câu hỏi độc lập, ở DRAFT và không sao chép lịch sử Student.
  Student đọc official history qua `GET .../assignments/{id}/attempts` và `/{attemptId}`
  kể cả Closed. `AttemptView.codeJobs` chỉ chứa job đã công bố; đọc phản hồi bằng
  `GET .../attempts/{attemptId}/jobs/{jobId}` với kiểm tra owner/attempt/question/revision.
  `PASS_FAIL`, `TESTCASE_STATUS`, `FULL_DETAILS` áp chính sách Teacher đã snapshot và
  chính sách hiện hành (chỉ có thể giảm mức tiết lộ); alias `SUMMARY`/`DETAILED` vẫn nhận.
  Hidden input/output chỉ được lộ khi testcase `allowDetails=true`; RUN chỉ dùng sample.
  Các kiểm thử PostgreSQL dùng worker fixture; chưa xác minh Docker sandbox thực tế.

## File private và avatar

Tạo bucket **private** tên `SUPABASE_STORAGE_BUCKET` (mặc định `learning-private`)
trong project staging trước khi thử luồng upload. Đặt `SUPABASE_STORAGE_URL` là origin
project HTTPS, `SUPABASE_STORAGE_SERVICE_KEY` là credential phía server. Không đưa
service key vào frontend, URL hoặc log. Bucket cần cho phép các kiểu file được chính
sách ứng dụng chấp nhận và giới hạn upload tối đa 20 MiB.

Cấu hình `FILE_SCANNER_HOST`, `FILE_SCANNER_PORT` cho clamd INSTREAM trên mạng riêng;
backend cần truy cập được scanner. `FILE_SCANNER_TIMEOUT_MS` mặc định 30000.
Upload kiểm tra cấu trúc PDF/OOXML/ZIP/ảnh, kích thước và malware trước khi READY.
Thiếu Storage/scanner hoặc provider lỗi trả 503; file chưa scan không được bind/download.
`FILE_MAX_BYTES` chỉ được giảm từ giới hạn 20 MiB. Admin đổi chính sách bằng API
`/api/v1/admin/file-policy`; backend vẫn kiểm quyền và giới hạn theo loại file.

`POST /api/v1/classrooms/{classId}/files` nhận multipart `file` và `purpose`
(`LESSON_MATERIAL` hoặc `ESSAY_SUBMISSION`). Dùng `data.id` trong `fileId` của material/
answer; tên, MIME, kích thước lấy từ metadata đã xác thực. Đọc metadata qua
`GET /api/v1/files/{fileId}` và tải qua `/content`; mỗi lần kiểm quyền hiện tại,
không trả public object URL. Bài tự luận chỉ tác giả và Teacher Owner được đọc.

Recovery scan tối đa ba lần; job dọn blob mồ côi chỉ xét file hơn 24 giờ và không còn
tham chiếu từ Lesson/answer. External IO chạy ngoài transaction DB; lease bảo vệ nhiều
instance xử lý cùng file. Cần giám sát file PENDING/REJECTED và lỗi provider trước rollout.

Avatar mới được chuẩn hóa PNG, scan và kiểm checksum trước khi thay `avatar_key` bằng
compare-and-set. Đọc avatar cũ từ `AVATAR_DIRECTORY` vẫn được hỗ trợ. Sau khi kiểm thử
staging và backup, operator có thể chuyển từng batch bằng
`--app.storage.avatar-migration-enabled=true --app.storage.avatar-migration-batch-size=100`.
Mặc định không chạy migration; giữ file local gốc để đối chiếu. Không bật trên dữ liệu
thật khi chưa phê duyệt kế hoạch chuyển đổi.

Xóa avatar remote thất bại được ghi vào `tbl_storage_cleanup_job` bằng transaction
riêng. Worker kiểm tra key không còn là avatar hiện tại rồi xóa ngoài transaction;
retry backoff tối đa năm lần, sau đó giữ trạng thái `FAILED` để operator xử lý.
Các thuộc tính `app.storage.avatar-cleanup-{poll-ms,max-attempts,backoff-seconds,lease-seconds,batch-size}`
có mặc định `30000/5/60/120/20`. Scheduler dùng pool bốn thread để IO Storage/scanner
không giữ toàn bộ lịch timeout; có thể cấu hình bằng `SPRING_TASK_SCHEDULING_POOL_SIZE`.

Code Judge dùng worker Linux riêng: xem [hướng dẫn worker](judge-worker/README.md).
API không cần quyền Docker. Các ảnh Java/Python phải có digest cố định, được preload;
chạy kiểm thử sandbox thật trên worker trước khi phát hành.

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

`python3 scripts/verify-focused.py 'TênTest,TênTestKhác' ten-bang-chung [--main]`
chạy trên source snapshot cố định, target riêng và lưu SHA/log/JSON tại
`target/verification`. Cần xuất ba biến `TEST_DB_*` nói trên; runner không đọc `.env`
ứng dụng. Dùng selector `'*Test'` để chạy toàn bộ, thêm `--verify` để đóng gói ứng dụng.
`--main` chọn track `db/main-auth`;
không đổi track của DB đang dùng. Test HTTP Storage và protocol scanner dùng fixture,
không thay thế xác minh Supabase/ClamAV/Docker thật.

Probe Supabase chỉ đọc `SELECT 1`, không migration: sau khi xuất biến DB từ `.env`, chạy
`mvn -Dsupabase.check=true -Dtest=SupabaseConnectionTest test`, hoặc PowerShell
`./mvn-local.ps1 '-Dsupabase.check=true' '-Dtest=SupabaseConnectionTest' test`.
Probe thành công không có nghĩa ứng dụng đã được khởi động hay có đủ bảng.
Kiểm thử HTTP/JPA trực tiếp trên Supabase ứng dụng chỉ chạy khi chủ động đặt
`-Dlive.supabase.check=true -Dtest=LiveSupabaseFlowTest`. Bài này tạo hai tài khoản
và một lớp/lời mời/thông báo có tiền tố `codexlive`, dùng mail sender giả, và giữ dữ liệu
để đối chiếu sau kiểm thử; không gửi email thật.
