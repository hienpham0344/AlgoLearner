# Test Report — FE 4.5 Bài học trong lớp

Ngày kiểm thử: 11/11/2026 (UTC+7). Nhánh: `design/classroom-lessons`.

Frontend đã có implementation và kiểm thử theo **contract đề xuất**. Chưa nghiệm thu tích hợp và chưa đủ điều kiện đóng #13: người dùng xác nhận chưa có API/nhánh #12 và chưa chốt File Storage. Không coi mock E2E là bằng chứng backend đáp ứng SRS.

## Nguồn và baseline

- SRS `docs/SRS ver 1.1.docx`, mục 4.5, FR-LES-001..005; BR-LES-001..008.
- [Use Case](https://docs.google.com/document/d/1JJ8RykceUqcj4me708q1SVU7ZBLhF5jTDgjpAWF1SMM/edit): đã đọc UC-LES-01 và UC-LES-10, bao gồm luồng chính, AF/EF và phạm vi quyền.
- [Test Plan, tab MD6: LES](https://docs.google.com/spreadsheets/d/1REBfe8zkJhmPyQx6_z2ogEPM8g7VoBssRw7QBskys_c/edit#gid=1227874164): 18 ca, 7 Student và 11 Teacher. Baseline sheet đang ghi 18 Untested; không sửa kết quả trong tài liệu dùng chung.
- `docs/rules/API_DESIGN_GUIDELINES.md`, `CODING_STANDARDS.md`, `SECURITY_AND_SANDBOX_RULES.md`.
- Contract chính thức sau khi chốt với #12: [API 4.5 — Bài học trong lớp](../api/API-4.5-lessons.md) (thay thế bản đề xuất cũ đã gỡ bỏ).

## Implementation

- Routes Teacher/Student `/…/classrooms/:id/lessons` và `/…/lessons/:lessonId`; tạo mới qua `/teacher/classrooms/:id/lessons/new`. Có đường dẫn từ trang lớp học.
- Teacher tạo Draft, sửa, preview Markdown/code/link, cấu hình liên kết/completion, publish/draft/archive/restore, xóa khi server cho phép, xem progress phân trang.
- Student chỉ đọc Published; xem/tải file và mở hoạt động khả dụng; Manual gửi yêu cầu và chỉ ghi nhận sau kết quả server; Condition-based đọc lại trạng thái server, không tự suy luận từ UI.
- Upload riêng sau lưu Draft; lỗi file không xóa content/progress đã persist. Chỉ bật upload theo policy server. Không giả định provider/quota.
- ApiResponse<T>, TanStack Query v5, key factory có userId/classId; không dùng any. Không fallback từ lỗi quyền/API lesson sang dữ liệu demo.
- UTC → UTC+7 khi hiển thị. Module tải qua lazy import để tách Markdown/sanitizer khỏi bundle ban đầu.
- Sanitizer dùng allowlist HTML và kiểm tra URL; [DOMPurify](https://github.com/cure53/DOMPurify) thực hiện lọc HTML sau khi [Marked](https://marked.js.org/using_advanced) parse Markdown. Không render scripts, event handlers, SVG, iframe hoặc ảnh ngoài; code example được React escape.

## Môi trường và lệnh tái hiện

Windows, Node 24.15.0, npm 11.12.1, Chrome 156.0.8078.80. CI dùng Node 22 theo repository; jsdom yêu cầu Node 22.22.2+ hoặc 24.15.0+. Playwright nhận Chrome từ PLAYWRIGHT_CHROME_PATH, tìm Chrome/Edge trên Windows hoặc dùng Chromium cài bởi Playwright trong CI.

```powershell
cd frontend
npm ci
npm run typecheck
npm test
npm run build
npm run test:e2e:lessons
```

E2E tự chạy Vite auth-demo:3100 và backend-mock:3101. `lessons.spec.ts` chặn request trong browser bằng fixture contract. `lessons-live.spec.ts` không chặn request và mặc định skipped khi chưa opt-in. Repository chưa có script lint, vì vậy lint là N/A, không ghi pass.

### Test tay khi chưa có backend

```powershell
cd frontend
npm run dev:lessons-demo -- --host 127.0.0.1 --port 3002
```

Mở `http://localhost:3002/login`. Tài khoản `teacher` hoặc `student`, mật khẩu chung `DemoPass123!` (chỉ dùng demo). Sau đăng nhập, chọn **Class lessons** trên sidebar dashboard, chọn thẻ lớp **Algorithms & Data Structures**, rồi mở bài hoặc chọn Create lesson (Teacher). Trên mobile, mở menu Open navigation trước. Đường dẫn trực tiếp dự phòng:

- Teacher: `http://localhost:3002/teacher/classrooms/demo-class-1/lessons`
- Student: `http://localhost:3002/student/classrooms/demo-class-1/lessons`

Dùng cửa sổ ẩn danh cho Student để kiểm tra song song với Teacher. Có sẵn Published/Draft/Archived và lesson Condition-based. Teacher có thể tạo, sửa, upload/download, publish/draft/archive/restore; Student đọc Published và hoàn thành Manual. Dữ liệu dùng chung trong bộ nhớ của dev server, reload trang giữ dữ liệu và restart server sẽ đặt lại. Assignment/Quiz/Practice chưa có màn module đích, không giả lập kết quả chấm.

`lessonsDemoPlugin` chỉ đăng ký ở mode `lessons-demo`; mode dev thông thường và production vẫn dùng API thật. Demo không thay thế nghiệm thu backend #12 hoặc File Storage thật.

`lessons-demo.spec.ts` kiểm tra login/tạo/publish/completion qua middleware demo, không có page.route; chỉ opt-in bằng `ALGOLEARN_LESSON_DEMO_URL` khi server demo đang chạy.

### Bổ sung ngày 12/11/2026 — tìm mục Bài học từ dashboard

Phản hồi thực tế: người dùng đăng nhập được nhưng không thấy mục Bài học. Dashboard Teacher/Student trước đó thiếu lối vào; các test nhập thẳng URL đã không phát hiện khoảng trống này.

Đã thêm Class lessons vào sidebar và khối chọn lớp trên dashboard Teacher/Student; trong lớp cũng có sidebar Class lessons. Khối này đọc danh sách lớp qua API 4.3 với cache tách theo user/role/page, nên có thể hoạt động độc lập khi API dashboard chưa sẵn sàng. Teacher chỉ thấy các lớp mình sở hữu; Student dùng danh sách lớp do API phân quyền. Admin không có mục này.

Đã sửa test demo để bắt đầu bằng đăng nhập → Class lessons trên dashboard → chọn lớp → Create lesson/đọc bài, thay vì đi thẳng bằng page.goto. Teacher được kiểm tra trên desktop, Student trên mobile 390px với menu đóng/mở. Các kết quả regression trước phần bổ sung này được giữ riêng ở bảng bên dưới.

Kết quả sau sửa: typecheck và 26 unit/component/contract tests pass; regression **41 passed, 2 skipped (3.1m)** khi bật ca demo. Sau sửa contrast của nhãn CLASSROOM LEARNING, chạy lại ca điều hướng kèm axe: **1 passed (30.7s)**; khối `#lessons` có 0 violation với tags WCAG 2 A/AA, 2.1 AA và 2.2 AA cho cả hai role. Build thành công, main chunk khoảng 566.05 KB và vẫn có cảnh báo kích thước của Vite.

Bằng chứng: [regression điều hướng](evidence/issue-13/dashboard-navigation-regression.json), [kiểm tra điều hướng và accessibility cuối](evidence/issue-13/dashboard-navigation-focused.json), [dashboard Teacher](evidence/issue-13/dashboard-class-lessons-teacher.png), [dashboard Student mobile](evidence/issue-13/dashboard-class-lessons-student.png).

Đã chạy ca này với `http://127.0.0.1:3002`: **1 passed (31.9s)**. Teacher tạo/publish bài; Student đăng nhập riêng và hoàn thành; reload giữ progress; Teacher reload nhận canDelete=false. Typecheck và 26 unit/component/contract tests tiếp tục pass sau khi thêm demo. Đây là kiểm thử local demo middleware, không phải backend #12.

## Kết quả và bằng chứng

| Kiểm tra | Kết quả |
| --- | --- |
| `npm run typecheck` | Pass; cả source và 2 file E2E lesson được kiểm tra strict |
| `npm test` | 26/26 pass, gồm 8 test mới cho lesson (unit, component render, API contract) |
| `npm run build` | Pass; module lesson được tách thành JS/CSS riêng |
| Regression toàn frontend `playwright test` | 40 pass, 2 skipped, 0 failed; thời gian 3.2 phút |
| Trong regression: `lessons.spec.ts` | 21/21 pass; dữ liệu browser fixture theo contract đề xuất |
| Kiểm tra bổ sung sau khi mở rộng axe ra toàn `.lesson-shell` | 2/2 pass (40.9s); Teacher reader/editor mobile + desktop và Student reader; 0 violation cho tags wcag2a, wcag2aa, wcag21aa, wcag22aa |
| `lessons-live.spec.ts` | Skipped; chưa có BE #12/credentials/môi trường được xác nhận, không ghi pass |
| `live-backend.spec.ts` cũ cho 4.2/4.3 | Skipped theo flag của suite cũ |
| `git diff --check` | Pass |
| Lint | N/A: chưa có script/config lint trong baseline |

Vite cảnh báo main chunk vượt 500 KB: main JS khoảng 562.58 KB, lesson JS khoảng 117.43 KB (37.89 KB gzip). Build thành công.

Regression evidence: [kết quả runner](evidence/issue-13/full-e2e-result.json), [kết quả kiểm tra bổ sung](evidence/issue-13/accessibility-e2e-result.json), [Teacher mobile](evidence/issue-13/lesson-mobile.png), [Teacher editor desktop](evidence/issue-13/lesson-editor-desktop.png), [Student reader](evidence/issue-13/lesson-student.png). Ảnh dùng dữ liệu fixture. Đã mở và xem ảnh để kiểm tra layout; viewport mobile 390×844, desktop editor 1440×1000. Nguồn tạo ảnh là các ca E2E trong `frontend/e2e/lessons.spec.ts`.

Các lỗi đã phát hiện và sửa: header bị tràn ngang ở 390px; validation khi Save từ preview với required field đang ẩn; đưa focus vào lỗi validation sau render; contrast breadcrumb/role chip của lesson shell; ARIA role cho presence indicator trong UserAvatar dùng chung. Test bổ sung xác nhận lưu thất bại giữ edit, Quiz conflict không đổi relation, stale eligibility không bypass DELETE 409, 401 kết thúc session và completion không có xác nhận server không hiển thị thành công. Kiểm tra axe tự động và thao tác keyboard đã thực hiện không thay thế toàn bộ đánh giá accessibility thủ công với công nghệ hỗ trợ.

Probe read-only tới dịch vụ localhost:8080: `/v3/api-docs`, `/api/v1/auth/me` và một đường dẫn `/api/v1/classrooms/{uuid}/lessons` trả HTTP 404 trong môi trường hiện tại. Kết quả này chỉ chứng minh dịch vụ tại địa chỉ đó chưa cung cấp các đường dẫn được probe; không suy ra contract backend nào đã được triển khai.

## Truy vết từng tiêu chí SRS

Trong bảng dưới đây, “FE” chỉ giới hạn bằng chứng UI/unit/contract; các bảo đảm về persistence, security server, File Storage, lifecycle hoạt động và progress giữa nhiều tài khoản đều cần chạy lại với #12.

| FR | AC | Test Plan / bằng chứng FE | Phần còn phải nghiệm thu với BE |
| --- | --- | --- | --- |
| LES-001 | 01 | TC-LES-10-01; editor/create/update/preview | Persist các loại content |
| LES-001 | 02 | TC-LES-01-01, 10-01; Markdown/code/URL render | Lưu và đọc lại dữ liệu thật |
| LES-001 | 03 | TC-LES-10-01; validateAttachment và upload multipart | Policy, MIME/signature, provider nhận file thật |
| LES-001 | 04 | TC-LES-10-09; file extension/size/empty tests | Nội dung độc hại, quota, ZIP và kiểm tra server |
| LES-001 | 05 | TC-LES-01-01; Student Published đọc/tải | Membership và quyền tải trên từng request |
| LES-001 | 06 | TC-LES-01-04/05; URL Draft/Archived bị từ chối | BE không trả content/file ngoài quyền |
| LES-001 | 07 | TC-LES-10-10; guard owner, 403 và role mismatch | IDOR Teacher khác trên API trực tiếp |
| LES-001 | 08 | TC-LES-01-07, 10-09; lỗi file không xóa text | Rollback và log kỹ thuật storage |
| LES-001 | 09 | Unit XSS + E2E script/event/URL injection | Kiểm tra bổ sung nội dung ở server |
| LES-002 | 01 | TC-LES-10-02; chọn hoạt động theo type và gửi IDs | Validate cùng lớp, quyền và trạng thái riêng |
| LES-002 | 02 | TC-LES-10-02; lesson không bắt buộc có hoạt động | Hoạt động tồn tại độc lập ở module đích |
| LES-002 | 03 | TC-LES-10-11; disable Quiz đã liên kết, giữ edit khi 409 | Unique constraint và cập nhật nguyên tử |
| LES-002 | 04 | TC-LES-01-01; lọc available, route cùng lớp/role | Contract/route của 4.6/4.7/4.8 |
| LES-002 | 05 | TC-LES-01-01/05; không mở item unavailable | Module đích kiểm tra truy cập và submit |
| LES-002 | 06 | TC-LES-10-10; owner guard trước tải lesson | Kiểm tra quyền ở server cho relation mutations |
| LES-002 | 07 | TC-LES-10-11; lỗi save giữ nội dung form và relation đã lưu | Transaction không làm mất relation cũ |
| LES-003 | 01 | TC-LES-10-03; selector Manual/Condition-based | Persist cấu hình completion |
| LES-003 | 02 | TC-LES-01-02; nút Mark as completed chỉ Manual Published | Kiểm tra quyền và lifecycle khi POST |
| LES-003 | 03 | TC-LES-01-02; API confirmed result, refresh/reload | Persistence thật sau reload/session mới |
| LES-003 | 04 | TC-LES-01-03; chỉ hiển thị completion do GET trả về | Đánh giá điều kiện Assignment/Quiz thật |
| LES-003 | 05 | TC-LES-01-03; chưa đạt không tự complete; không có nút Manual | Không chấp nhận POST để bypass condition |
| LES-003 | 06 | Key factory phân biệt userId; Student không request roster | Progress độc lập giữa hai Student thật |
| LES-003 | 07 | TC-LES-01-02; bảng progress Teacher phân trang, UTC+7 | Roster và dữ liệu persistence thật |
| LES-003 | 08 | TC-LES-10-10; non-owner không request lesson/progress | GET progress IDOR qua API trực tiếp |
| LES-003 | 09 | Không hiển thị phần trăm toàn lớp bắt buộc | Không có yêu cầu bổ sung trong MVP |
| LES-004 | 01 | TC-LES-10-01/04/05; 3 trạng thái hiển thị | Lifecycle enum và transitions server |
| LES-004 | 02 | TC-LES-01-04; lọc list và chặn detail Draft | BE lọc trước phân trang, không trả file |
| LES-004 | 03 | TC-LES-01-01; Published Student hợp lệ | Membership/account Active |
| LES-004 | 04 | TC-LES-10-01; confirm Publish → dữ liệu server mới | Transaction Draft → Published |
| LES-004 | 05 | TC-LES-10-04; confirm Move to draft | Transaction Published → Draft |
| LES-004 | 06 | TC-LES-10-04 + 01-04; refetch, không reset progress FE | Progress/history thật không mất |
| LES-004 | 07 | TC-LES-01-05; không render Archived | BE kiểm tra ở mọi endpoint liên quan |
| LES-004 | 08 | Không có Scheduled Publishing trong DTO/UI | BE từ chối tính năng ngoài scope |
| LES-004 | 09 | TC-LES-10-10; role/owner guard và lỗi 403 | RBAC và IDOR trên lifecycle endpoints |
| LES-005 | 01 | TC-LES-10-07; canDelete=true, confirm Delete | BE kiểm tra không có related data |
| LES-005 | 02 | TC-LES-10-08; disable Delete; stale canDelete gặp 409 | BE rà progress/relation/submission/history |
| LES-005 | 03 | TC-LES-10-05; Archive cần xác nhận và dữ liệu API | Ownership và transaction Archive |
| LES-005 | 04 | TC-LES-01-05; Archived không hiện content/file | Ẩn child theo phạm vi Archive thật |
| LES-005 | 05 | UI không mở child unavailable | BE 4.6/4.7/4.8 chặn Submission mới |
| LES-005 | 06 | TC-LES-10-05; không xóa/reset progress FE | Submission/Grade/history bất biến ở DB |
| LES-005 | 07 | TC-LES-10-06; Restore đúng action | Ownership và transaction Restore |
| LES-005 | 08 | TC-LES-10-06; dùng trạng thái server, Draft không ép Published | Restore toàn bộ child state thật |
| LES-005 | 09 | TC-LES-10-06; progress cũ được đọc lại | Persistence/history thật không bị reset |
| LES-005 | 10 | TC-LES-10-10; guard và contract 401/403 | RBAC/IDOR trực tiếp cho delete/archive/restore |
| LES-005 | 11 | TC-LES-10-08; 409 giữ lesson; không optimistic lifecycle | Transaction và rollback các lỗi thật |

## Truy vết Business Rules

| BR | Ca tương ứng | Bằng chứng FE / giới hạn |
| --- | --- | --- |
| BR-LES-001 | TC-LES-01-01, TC-LES-10-01/09 | Text/code/link và danh sách file MVP; không bắt buộc Video; provider/policy thật pending |
| BR-LES-002 | TC-LES-10-02/11 | Relation optional; Quiz đã thuộc lesson khác bị khóa chọn; BE phải giữ uniqueness |
| BR-LES-003 | TC-LES-01-02/03, TC-LES-10-03 | Manual vs Condition-based; Practice không là điều kiện; BE quyết định kết quả |
| BR-LES-004 | TC-LES-10-01/04/05 | Draft/Published/Archived; không Scheduled |
| BR-LES-005 | TC-LES-10-04, TC-LES-01-04 | Published → Draft; không reset progress FE; DB pending |
| BR-LES-006 | TC-LES-10-07/08 | Eligibility từ canDelete; server 409 vẫn giữ lesson |
| BR-LES-007 | TC-LES-10-05, TC-LES-01-05 | Không render archived/child unavailable; chặn Submission và giữ history cần BE |
| BR-LES-008 | TC-LES-10-06 | Restore phản ánh trạng thái server; child lifecycle cần BE |
| BR-DATA-001 | TC-LES-10-09, TC-LES-01-07 | UI kiểm tra và giữ content; policy/security file thật cần File Storage |

## Các phụ thuộc chưa tích hợp

| Phụ thuộc | Trạng thái / hành động cần làm |
| --- | --- |
| #12 — BE 4.5 | Chưa có contract/nhánh được cung cấp. Review DTO/endpoints cùng BE, sửa adapter duy nhất và fixtures theo contract cuối |
| #4 — BE 4.1 | FE dùng auth/refresh/logout hiện có. Chưa có môi trường auth thật được xác nhận cho test LES; không dùng token mock cho live gate |
| #8 — BE 4.3 | FE dùng API scope lớp hiện có. Membership/ownership thật cần kiểm thử lại cùng #12 |
| File Storage | Chưa chốt provider, quota, MIME, nội dung ZIP, malware và logging; capability lỗi thì upload disable, không giả định thành công |
| 4.6/4.7/4.8 | Chưa có contract activity options/routes/completion/Archive submission scope; UI không tự tạo route giả |
| Nghiệm thu | PR review và live tests cho đầy đủ 18 ca chưa hoàn tất; không tự đóng issue |

## Chạy integration khi BE sẵn sàng

Chỉ chạy trên môi trường test với Teacher và Student Active do người kiểm thử cung cấp. Ca live tạo một lớp riêng có tên `LES integration …` và archive lớp này khi kết thúc; không xóa dữ liệu học tập.

```powershell
$env:ALGOLEARN_LIVE_LESSONS = '1'
$env:ALGOLEARN_LESSON_TEACHER = '<teacher username>'
$env:ALGOLEARN_LESSON_TEACHER_PASSWORD = '<test password>'
$env:ALGOLEARN_LESSON_STUDENT = '<student username>'
$env:ALGOLEARN_LESSON_STUDENT_PASSWORD = '<test password>'
# ALGOLEARN_API_TARGET mặc định http://localhost:8080, đặt lại nếu cần.
npm run test:e2e:lessons
```

Ca live hiện bao phủ authoring → Draft deny → Publish → Manual completion/reload → Draft → Archive/Restore Draft → Publish → Archive/Restore Published → progress vẫn Completed. Phải bổ sung/chạy các ca storage thật, hoạt động thật, hai Student và API IDOR trước nghiệm thu. Không coi một ca live lifecycle là toàn bộ 18 ca tích hợp đã đạt.
