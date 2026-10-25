# AlgoLearner - Security & Sandbox Execution Rules

> Bộ quy tắc bảo mật toàn diện cho **AlgoLearner**, tập trung vào cơ chế cô lập thực thi mã nguồn người dùng (**Code Judge Sandbox**), kiểm soát phân quyền (**RBAC & IDOR**), và phòng chống lỗ hổng bảo mật Web.

---

## 1. Cơ Chế Cô Lập Thực Thi Mã Nguồn (Code Judge Sandbox)

Vì hệ thống cho phép sinh viên và người dùng nộp mã nguồn Java và Python để biên dịch và chạy trên máy chủ, đây là bề mặt tấn công nguy hiểm nhất (Remote Code Execution, Resource Exhaustion, Data Exfiltration).

### 1.1. Các Ràng Buộc Bắt Buộc Khi Thực Thi Code (SRS 4.9 & 5.2 - 5.3)

| Tiêu chí | Giới hạn bắt buộc | Biện pháp bảo vệ kỹ thuật |
| :--- | :--- | :--- |
| **Network (Mạng)** | **Không có mạng (`--network none`)** | Cắt đứt toàn bộ kết nối Internet và mạng nội bộ, ngăn chặn SSRF, botnet, crypto-mining. |
| **CPU Time** | Mặc định **2.0 giây** (tối đa 5.0s) | Kills process ngay lập tức nếu vượt quá (trả về `Time Limit Exceeded - TLE`). |
| **Memory (RAM)** | Mặc định **256 MB** (tối đa 512MB) | Giới hạn qua cgroups/container flags (trả về `Memory Limit Exceeded - MLE`). |
| **Process / Thread** | Tối đa **32 processes** (`--pids-limit=32`) | Ngăn chặn tấn công Fork Bomb làm tê liệt máy chủ. |
| **File System** | **Read-Only Root Filesystem** | Mount hệ điều hành dưới dạng Read-Only. Thư mục biên dịch chạy trên `tmpfs` tạm thời và bị xóa sạch ngay sau khi chạy xong. |
| **User Privileges** | **Non-root user** (`nobody` / UID 10001) | Tuyệt đối cấm chạy tiến trình judge bằng quyền `root`. |
| **Linux Capabilities**| **Drop ALL capabilities** | Thêm cờ `--cap-drop=ALL` và `--security-opt=no-new-privileges:true`. |

### 1.2. Phân Định Giữa RUN và SUBMIT (SRS 4.8 & 4.9)
1. **RUN (Chạy thử nghiệm)**:
   - Chỉ chạy với các Sample Test Cases công khai do người dùng nhập hoặc đề bài cung cấp.
   - Trả kết quả tức thời (`Stdout`, `Stderr`, `ExecutionTime`, `MemoryUsed`).
   - **KHÔNG** tạo bản ghi Official Submission trong cơ sở dữ liệu.
   - Áp dụng Rate Limit: Tối đa 5 lần RUN / phút cho mỗi người dùng.
2. **SUBMIT (Nộp bài chính thức)**:
   - Tạo bản ghi Submission ở trạng thái `PENDING / QUEUED`.
   - Chạy trên toàn bộ bộ Test Cases (bao gồm Hidden Test Cases).
   - Snapshot bộ testcase tại thời điểm nộp để đảm bảo tính toàn vẹn (khi giáo viên sửa test sau này không làm sai lệch lịch sử nộp).
   - Cập nhật kết quả: `ACCEPTED`, `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`, `COMPILATION_ERROR`, `RUNTIME_ERROR`.

---

## 2. Kiểm Soát Quyền Truy Cập & Phòng Chống IDOR (RBAC)

1. **Nguyên tắc Đặc quyền Tối thiểu (Principle of Least Privilege)**:
   - `Student`: Chỉ có quyền đọc thông tin lớp học mình đã tham gia, nộp bài, và **chỉ được xem điểm/bài nộp của chính mình**.
   - `Teacher`: Chỉ có quyền tạo, sửa, xóa bài tập, quiz, và chấm điểm trong **các lớp do chính mình tạo** hoặc được chuyển quyền sở hữu (`owner`).
   - `Admin`: Sử dụng bảng quản trị riêng biệt. Không tham gia vào vai trò Student hay Teacher.
2. **Phòng chống Lỗ hổng Tham chiếu Trực tiếp Đối tượng (IDOR)**:
   - Tuyệt đối cấm việc chỉ dùng ID từ URL mà không kiểm tra quyền sở hữu.
   - Mọi thao tác cập nhật/xóa tài nguyên trong lớp học bắt buộc phải xác minh:
   ```java
   // Luôn kiểm tra quyền sở hữu ở tầng Service:
   ClassroomEntity classroom = classroomRepository.findById(classId)
       .orElseThrow(() -> new ResourceNotFoundException("Classroom not found"));

   if (!classroom.getTeacher().getId().equals(currentUser.getId())) {
       throw new ForbiddenException("You do not have permission to modify this classroom");
   }
   ```
3. **Bảo vệ Endpoint bằng Spring Security**:
   - Dùng `@PreAuthorize("hasRole('TEACHER')")` hoặc `@PreAuthorize("hasRole('ADMIN')")` trên các Controller method tương ứng.

---

## 3. Bảo Vệ Dữ Liệu & Phòng Chống Lỗ Hổng Web (OWASP Top 10)

### 3.1. Chống SQL Injection (SQLi)
- 100% truy vấn cơ sở dữ liệu phải dùng Spring Data JPA hoặc `@Query` với tham số định danh (`:paramName`).
- **TUYỆT ĐỐI CẤM** việc ghép chuỗi SQL động (String concatenation).

### 3.2. Chống XSS (Cross-Site Scripting)
- Nội dung mô tả đề bài, bài giảng, và thông báo lớp học hỗ trợ định dạng Markdown.
- Ở phía Frontend, khi render HTML từ Markdown, bắt buộc phải sử dụng bộ lọc làm sạch **`DOMPurify`** để loại bỏ tất cả các thẻ `<script>`, `onerror`, `onload`:
  ```typescript
  import DOMPurify from 'dompurify';
  
  const sanitizedContent = DOMPurify.sanitize(rawHtmlContent);
  ```

### 3.3. Bảo Vệ Mật Khẩu & Xác Thực OTP (SRS 4.1)
- Mật khẩu người dùng bắt buộc mã hóa bằng **BCrypt** (độ mạnh `strength = 12`).
- Mã OTP gửi qua email:
  - Sinh ngẫu nhiên mã số 6 chữ số có độ bảo mật cao (`SecureRandom`).
  - Hạn sử dụng nghiêm ngặt: **5 phút**.
  - Rate limit: Tối đa 1 lần yêu cầu gửi OTP mỗi 60 giây.
  - Khóa tạm thời nếu nhập sai OTP quá 5 lần liên tiếp.
  - Ngay sau khi xác thực thành công, mã OTP phải bị đánh dấu là đã sử dụng (`USED`) hoặc xóa khỏi cache để chống tấn công phát lại (Replay Attack).

### 3.4. Ghi Nhật Ký Kiểm Toán (Audit Logging - SRS 4.16)
Mọi hành động nhạy cảm phải được ghi nhận vào bảng `tbl_audit_log` với các thông tin:
- `userId`, `userRole` thực hiện.
- `action` (ví dụ: `ADMIN_LOCK_USER`, `TEACHER_UPDATE_GRADE`, `SUBMISSION_REJUDGE`).
- `targetId` (ID của user/bài nộp bị tác động).
- `ipAddress`, `userAgent`.
- `timestamp` (UTC).
- Tuyệt đối không bao giờ ghi mật khẩu thô, mã OTP, hoặc secret keys vào file log.
