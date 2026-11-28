# AlgoLearner - Programming Learning Platform

> Nền tảng học lập trình toàn diện kết hợp giữa **Classroom/LMS** và **Public Programming Learning (LeetCode-style)**.  
> Được xây dựng theo đặc tả yêu cầu phần mềm **SRS ver 1.1** (Karl Wiegers Standard).

---

## 1. Tổng quan Kiến trúc Hệ thống

Theo tài liệu **SRS ver 1.1**, hệ thống phục vụ 3 nhóm người dùng: **Student**, **Teacher**, và **Admin** trên hai phạm vi cốt lõi:
1. **Classroom / LMS**: Quản lý lớp học, bài giảng, bài tập tự luận/code, trắc nghiệm tính giờ có autosave, bài thực hành lập trình trong lớp, và sổ điểm (gradebook).
2. **Public Programming Learning**: Khóa học mở và kho bài tập luyện thuật toán tương tự LeetCode với đầy đủ bộ testcase, streak, submission history, và hệ thống sandbox an toàn.

### Công nghệ Lựa chọn (SRS Section 2.4 - 2.5)

| Thành phần | Công nghệ / Quy định | Ghi chú từ SRS |
| :--- | :--- | :--- |
| **Backend Framework** | **Spring Boot 3.x** (Java 17/21) | RESTful API, Stateless, Modular Domain |
| **Security & Auth** | **Spring Security + JWT** | Access Token & Refresh Token, OTP qua Email |
| **Database** | **PostgreSQL** | Lưu trữ chuẩn UTC; Giao diện hiển thị UTC+7 |
| **API Documentation** | **Swagger / OpenAPI 3.0** | Yêu cầu bắt buộc để đồng bộ Frontend & Backend |
| **Code Judge Sandbox** | Provider Cô lập (Java / Python) | Giới hạn Time / Memory / CPU, ngăn chặn RCE / Escape |
| **Frontend Web App** | **React + TypeScript + Vite + Tailwind CSS** | Monaco Editor trên desktop/laptop; Responsive tablet/mobile |
| **Local Services** | **Docker Compose** | PostgreSQL, Mailpit (Fake SMTP OTP), Redis |

---

## 2. Cấu trúc Dự án (Project Directory Structure)

Cấu trúc dự án được thiết kế theo chuẩn Domain-Driven / Modular Architecture, ánh xạ trực tiếp 16 phân hệ nghiệp vụ từ Mục 4 trong SRS:

```text
AlgoLearner/
├── .agents/                        # 290 AI Agent Skills & subagent definitions từ Agents-kit
│   ├── agents.toml                 # Cấu hình 22 specialized subagents
│   └── skills/                     # Symlinks tới các skills chuyên môn sâu
├── AGENTS.md                       # Bản chỉ dẫn toàn diện về SDLC, Subagents & Skills
├── .cursorrules                    # Quy tắc điều hướng cho AI Coding Agents
├── docker/                         # Docker Compose & container configurations
│   └── docker-compose.yml          # PostgreSQL 16 (UTC), Mailpit (local OTP), Redis
├── docs/                           # Tài liệu dự án
│   ├── SRS ver 1.1.docx            # Tài liệu SRS gốc phiên bản 1.1
│   ├── references/                 # Cheat sheets & kiến thức chuẩn (CSS, React 19, WCAG)
│   ├── templates/                  # Templates cho PR, SRS, Ticket, ADR
│   └── workflows/                  # Quy trình CI/CD, Bugfix, TDD, Feature development
├── scripts/                        # Automation & deployment scripts
│   ├── dev.sh                      # Khởi động nhanh môi trường local
│   └── seed-data.sh                # Nạp dữ liệu mẫu ban đầu
├── backend/                        # Spring Boot 3.x Backend Service
│   ├── pom.xml (hoặc build.gradle) # Build manifest & dependencies
│   └── src/
│       ├── main/
│       │   ├── java/com/algolearner/platform/
│       │   │   ├── auth/           # 4.1. Account & Authentication (Register, Login, OTP, JWT)
│       │   │   ├── user/           # 4.2. Profile & Dashboard (User stats, privacy)
│       │   │   ├── classroom/      # 4.3. Class Management (Class Code, Join mode, Transfer)
│       │   │   ├── announcement/   # 4.4. Announcements (Class posts, notifications)
│       │   │   ├── lesson/         # 4.5. Lessons (Rich content, attachments, progress)
│       │   │   ├── assignment/     # 4.6. Assignments (Due dates, multiple submissions)
│       │   │   ├── quiz/           # 4.7. Quizzes (Autosave, timers, attempts, auto-grade)
│       │   │   ├── practice/       # 4.8. Class Practice (Infinite attempts, Accepted state)
│       │   │   ├── judge/          # 4.9. Automated Code Judge (Sandbox, Rejudge, Runner)
│       │   │   ├── grading/        # 4.10. Gradebook & Evaluation (Weights, override, export)
│       │   │   ├── course/         # 4.11. Public Courses (Catalog, enrollment, learning paths)
│       │   │   ├── problem/        # 4.12. Public Practice (LeetCode-style problems & tests)
│       │   │   ├── notification/   # 4.13. In-App Notifications (Deadlines, streak, alerts)
│       │   │   ├── admin/          # 4.14. Admin Moderation (Account lock, content approval)
│       │   │   ├── report/         # 4.15. Reports & Data Export (Academic records, Excel/CSV)
│       │   │   ├── audit/          # 4.16. Audit & Technical Logging (Security & action logs)
│       │   │   └── common/         # Common utilities, Exception Handling, Security Filters, DTOs
│       │   └── resources/
│       │       ├── application.yml # Cấu hình Spring (PostgreSQL, JWT, Mail, UTC)
│       │       └── db/migration/   # Flyway SQL migrations
│       └── test/                   # Unit & Integration Tests (JUnit 5, Mockito, Testcontainers)
└── frontend/                       # Modern Frontend Web Application
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── assets/                 # Icons, fonts, branding
        ├── components/
        │   ├── ui/                 # Headless primitives (Button, Modal, Input, Badge)
        │   ├── layout/             # Navbar, Sidebar, Role-based Dashboard Layouts
        │   └── editor/             # Monaco Code Editor wrapper (Java/Python support)
        ├── features/
        │   ├── auth/               # Đăng nhập, Đăng ký, Nhập mã OTP
        │   ├── dashboard/          # Dashboard theo role (Student, Teacher, Admin)
        │   ├── classroom/          # Không gian lớp học (Lessons, Assignments, Quizzes)
        │   ├── course/             # Danh mục và giao diện học khóa học công khai
        │   ├── practice/           # Trình giải bài toán thuật toán (Code, Run, Submit, Testcases)
        │   ├── admin/              # Cổng quản trị Admin & Audit Logs
        │   └── notification/       # Trung tâm thông báo
        ├── hooks/                  # Custom React hooks (useAuth, useQuizTimer, useSubmission)
        ├── services/               # API clients & Axios interceptors (Auto refresh JWT token)
        ├── types/                  # TypeScript interfaces (DTOs, Enums)
        └── utils/                  # Date formatting (UTC -> UTC+7), validation helpers
```

---

## 3. Tích hợp AI Agent Kit (`Agents-kit`)

Dự án được tích hợp bộ công cụ phát triển chuyên sâu từ **`Agents-kit`**:
- **290 Skills** thuộc 21 lĩnh vực chuyên sâu (Java Spring Boot, Frontend Mastery, TDD, Clean Architecture, Security STRIDE, Performance Tuning).
- **22 Subagents** định nghĩa trong [`.agents/agents.toml`](file:///.agents/agents.toml) sẵn sàng hỗ trợ các pha SDLC từ Architecture, Implementation, Code Review đến Security Audit.
- Tương thích tối đa với công cụ CLI `agents-kit`.

## Chạy dịch vụ local

Đặt biến `POSTGRES_PASSWORD` trong môi trường shell hoặc trong tệp `docker/.env` (tệp này không được đưa lên Git), rồi chạy `docker compose -f docker/docker-compose.yml up -d`.

### Thử giao diện đăng nhập khi chưa có backend

Trong thư mục `frontend`, chạy `npm.cmd run dev:auth-demo` trên Windows hoặc `npm run dev:auth-demo` trên hệ điều hành khác, rồi mở `http://localhost:3000/login`.

- Tài khoản thử: `learner@example.test`; mật khẩu đúng: `DemoPass123!`.
- Nhập một mật khẩu sai năm lần cho cùng tài khoản để thấy bốn phản hồi `401` và phản hồi khóa `423` ở lần thứ năm. Giao diện hiển thị thời gian từ header `Retry-After` và vô hiệu hóa nút đăng nhập.
- Dữ liệu mock chỉ xuất hiện khi backend không chạy; không được lưu vào Supabase.

Để thử luồng quên mật khẩu khi chưa có backend, mở `/forgot-password`, nhập `learner@example.test`, rồi dùng mã demo `123456` ở màn hình xác minh. Form đặt mật khẩu mới chỉ hiện sau khi xác minh thành công. Mật khẩu mới dùng được để đăng nhập tài khoản demo trong cùng phiên dev server. Chế độ này không gửi email thật.

Để nhận OTP đăng ký qua Gmail thật, chạy backend với profile `internal-auth` và cấu hình SMTP theo `backend/README.md`; chạy frontend bằng `npm run dev`. Frontend luôn ưu tiên backend; các alias demo cũng chỉ mô phỏng khi backend không chạy.
Trên Linux, điền cấu hình Gmail, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` và `OTP_HMAC_SECRET` cố định vào `backend/.env` (tạo từ `backend/.env.example` nếu chưa có), rồi chạy `backend/scripts/run-local-email.sh`. Cấu hình bắt buộc trỏ tới Supabase với TLS. Script đọc cấu hình đã lưu, dùng PostgreSQL với Flyway và không dùng H2 tạm; `.env` được Git bỏ qua. Giữ nguyên DB và các khóa qua các lần khởi động để giữ tài khoản, phiên đăng nhập và OTP còn hạn. Xem `backend/README.md` để điền kết nối Supabase; không có DB local dự phòng.

Backend thật cần trả `resetToken` ngắn hạn từ `POST /api/v1/auth/verify-otp` với `purpose: RESET_PASSWORD`, rồi xác thực và tiêu thụ token đó trong `POST /api/v1/auth/reset-password`. Token phải hết hạn và chỉ dùng một lần.

Mock API chỉ hoạt động trong Vite development khi backend từ chối kết nối, với cả `npm run dev` và các alias demo. Backend trả lỗi HTTP vẫn là nguồn dữ liệu được dùng; frontend hiển thị lỗi, không chuyển sang dữ liệu mẫu. Kiểm thử mock không xác nhận kết nối Supabase thật.

Sau khi đăng nhập tài khoản demo, mở `/dashboard` để xem Dashboard Student với dữ liệu mẫu được gắn nhãn. Các phần dữ liệu thật còn thiếu API được ghi tại [`docs/student-dashboard-integration.md`](docs/student-dashboard-integration.md).

Trong cùng chế độ `dev:auth-demo`, mở menu tài khoản trên Dashboard rồi bấm username để xem hồ sơ, hoặc mở thẳng `http://localhost:3000/profile`. **Profile settings** và **Account security** mở lần lượt tab Profile và Security trong cùng trang hồ sơ. Có thể sửa tên/bio, tải avatar PNG/JPEG, bật hồ sơ công khai rồi xem `/u/learner`. Để thử Dashboard Teacher/Admin không cần backend, đăng xuất rồi đăng nhập lần lượt bằng `teacher@example.test` hoặc `admin@example.test` (cũng chấp nhận username `teacher`/`admin`), đều dùng mật khẩu `DemoPass123!`. Teacher có thêm `/profile` và `/u/teacher`; Admin chỉ có dashboard quản trị. Tất cả số liệu của chế độ này là dữ liệu mẫu; khởi động lại dev server sẽ đặt lại hồ sơ và avatar.

Để dùng 4.1/4.2/4.3 thật, cấu hình Supabase trong `backend/.env`, chạy `bash backend/scripts/run-local-email.sh`, rồi `npm run dev` trong `frontend`. Đăng ký/đăng nhập bằng tài khoản thật; tài khoản, hồ sơ và lớp học lưu trong Supabase. Backend không có chế độ H2 hoặc tài khoản mẫu trong luồng chạy ứng dụng.

### Nguồn dữ liệu lớp học (4.3)

Các trang `/teacher/classrooms`, `/student/classrooms`, `/admin/classrooms` và trang chi tiết lấy dữ liệu/thao tác từ backend. Mọi alias Vite đều thử backend trước; chỉ kết nối bị từ chối khi backend dừng mới chuyển sang mock frontend, có banner thông báo và dữ liệu lớp học mẫu trong `sessionStorage`. HTTP 401/403/404/5xx không kích hoạt mock. Backend bật lại sẽ kết thúc phiên mẫu và yêu cầu đăng nhập thật. Production không đóng gói cơ chế mock của Vite.

Backend 4.3 có API liệt kê lời mời đã gửi và thông báo lời mời của Student. Student có thể chấp nhận hoặc từ chối trực tiếp tại `/student/classrooms#invitations`; Teacher thu hồi từ danh sách lời mời trong lớp. Lời mời hết hạn hoặc bị thu hồi vẫn giữ trạng thái để đối chiếu. Chuyển quyền sở hữu hiện yêu cầu Admin nhập UUID Teacher. Các số liệu mẫu trong prototype không xuất hiện ở chế độ API.

Kiểm thử giao diện 4.2/4.3 bằng Chrome: từ `frontend`, chạy `npm run test:e2e` hoặc `npm run test:e2e:coverage` để xem độ bao phủ hàm của mã frontend được nạp. Bộ test tự chạy Vite ở cổng 3100/3101; có thể đặt `PLAYWRIGHT_CHROME_PATH` nếu Chrome không ở `/usr/bin/google-chrome`. Mặc định các phản hồi API trong Playwright được giả lập.

Để kiểm tra UI với backend thật, điền Supabase trong `backend/.env`, chạy `bash backend/scripts/run-local-email.sh`, rồi `npm run dev` trong `frontend`. Đăng ký tài khoản thật qua OTP; launcher không seed tài khoản mẫu. Các bài Playwright mặc định dùng backend HTTP fixture hoặc frontend offline, không chứng minh kết nối Supabase thật. Xem `backend/README.md` để chạy integration tests trên PostgreSQL riêng.
