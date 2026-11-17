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
- Dừng và chạy lại dev server để xóa bộ đếm thử nghiệm trong bộ nhớ.

Để thử luồng quên mật khẩu khi chưa có backend, mở `/forgot-password`, nhập `learner@example.test`, rồi dùng mã demo `123456` ở màn hình xác minh. Form đặt mật khẩu mới chỉ hiện sau khi xác minh thành công. Mật khẩu mới dùng được để đăng nhập tài khoản demo trong cùng phiên dev server. Chế độ này không gửi email thật.

Backend thật cần trả `resetToken` ngắn hạn từ `POST /api/v1/auth/verify-otp` với `purpose: RESET_PASSWORD`, rồi xác thực và tiêu thụ token đó trong `POST /api/v1/auth/reset-password`. Token phải hết hạn và chỉ dùng một lần.

### Kết nối 4.1 với backend thật

Chạy backend không có profile `mock` với PostgreSQL, SMTP/Mailpit và các biến JWT trong [`backend/.env.example`](backend/.env.example), sau đó chạy `npm run dev` trong `frontend` (hoặc đặt `ALGOLEARN_API_TARGET` nếu API không ở cổng 8080). Giao diện dùng API thật cho đăng ký, OTP, đăng nhập, quên/đặt lại mật khẩu, `/auth/me`, refresh token và logout. Sau khi đăng nhập, mở `/account/security` để đổi mật khẩu, xem/thu hồi phiên, đăng xuất mọi thiết bị hoặc vô hiệu hóa tài khoản. `dev:auth-demo` và `dev:backend-mock` tiếp tục là chế độ thử riêng; chế độ `dev` mặc định không giả lập đăng ký/OTP khi API lỗi.

Chế độ này chỉ mô phỏng API trong Vite khi dùng lệnh `dev:auth-demo`; nó không kiểm chứng bảo mật hay dữ liệu của Spring backend thật.

Sau khi đăng nhập tài khoản demo, mở `/dashboard` để xem Dashboard Student với dữ liệu mẫu được gắn nhãn. Các phần dữ liệu thật còn thiếu API được ghi tại [`docs/student-dashboard-integration.md`](docs/student-dashboard-integration.md).

Trong cùng chế độ `dev:auth-demo`, chọn **My profile** trên Dashboard hoặc mở thẳng `http://localhost:3000/profile`. Có thể sửa tên/bio, tải avatar PNG/JPEG, bật hồ sơ công khai rồi xem `/u/learner`. Để thử Dashboard Teacher/Admin không cần backend, đăng xuất rồi đăng nhập lần lượt bằng `teacher@example.test` hoặc `admin@example.test` (cũng chấp nhận username `teacher`/`admin`), đều dùng mật khẩu `DemoPass123!`. Teacher có thêm `/profile` và `/u/teacher`; Admin chỉ có dashboard quản trị. Tất cả số liệu của chế độ này là dữ liệu mẫu; khởi động lại dev server sẽ đặt lại hồ sơ và avatar.

Để thử frontend 4.2/4.3 với backend cục bộ, chạy `./scripts/run-local-integration.sh` trong `backend`, rồi chạy `ALGOLEARN_API_TARGET=http://127.0.0.1:18080 npm run dev:backend-mock` trong `frontend`. Đăng nhập bằng `student`, `teacher` hoặc `admin` với mật khẩu `DemoPass123!`; mock token và H2 chỉ dành cho môi trường phát triển. Mở `/dashboard`, `/profile` (Student/Teacher), `/u/student` hoặc trang lớp học. Xem [Test Report issue #7](docs/test-reports/issue-7-profile-dashboard.md) để biết các tiêu chí chưa thể nghiệm thu khi backend còn in-memory.

### Nguồn dữ liệu lớp học (4.3)

Các trang `/teacher/classrooms`, `/student/classrooms`, `/admin/classrooms` và trang chi tiết dùng `GET /api/v1/classrooms` để nhận biết API lớp học. Khi API trả dữ liệu, các trang gọi API backend cho danh sách, chi tiết và các thao tác 4.3 đã có. Lỗi xác thực/phân quyền (401/403) được hiển thị, không thay bằng dữ liệu mẫu. Trong môi trường phát triển, nếu API lớp học chưa tồn tại hoặc backend không kết nối được, giao diện dùng prototype `sessionStorage` để thử luồng. `npm run dev:auth-demo` luôn dùng prototype; `npm run dev:backend-mock` thử API backend trước. Chế độ production không tự chuyển sang dữ liệu mẫu.

Backend 4.3 hiện chưa có API liệt kê lời mời, tra cứu Teacher và danh sách lớp công khai. Trong chế độ API, các thao tác lời mời/đổi chủ sở hữu dùng UUID nhập thủ công; Student chỉ thấy lớp đã tham gia. Các số liệu mẫu trong prototype không xuất hiện ở chế độ API.

Kiểm thử giao diện 4.2/4.3 bằng Chrome: từ `frontend`, chạy `npm run test:e2e` hoặc `npm run test:e2e:coverage` để xem độ bao phủ hàm của mã frontend được nạp. Bộ test tự chạy Vite ở cổng 3100/3101; có thể đặt `PLAYWRIGHT_CHROME_PATH` nếu Chrome không ở `/usr/bin/google-chrome`. Mặc định các phản hồi API trong Playwright được giả lập.

Để kiểm tra UI qua Spring Boot thật với H2 và mock token cục bộ, từ thư mục `backend` chạy `./scripts/run-local-integration.sh` bằng JDK 21/Maven ở một terminal. Sau đó trong `frontend` chạy `ALGOLEARN_LIVE_BACKEND=1 ALGOLEARN_API_TARGET=http://127.0.0.1:18080 npm run test:e2e`. Script chỉ dùng tài khoản test trong `backend/src/test/resources/integration-seed.sql` và không xác nhận cấu hình PostgreSQL/JWT production. Xem [báo cáo kiểm thử 09-11-2026](docs/test-reports/2026-11-09-e2e-coverage.md).
