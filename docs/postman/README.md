# Postman smoke 4.1–4.6

[Collection v2.1](AlgoLearner-4.1-4.6-local.postman_collection.json) chạy 44 request trên backend thật với PostgreSQL test riêng. Chạy bằng Postman hoặc Newman. Đây là smoke test, không thay toàn bộ ma trận SRS.

Collection cần backend `http://127.0.0.1:18080` với profile `internal-auth,test` và database PostgreSQL dành riêng cho test (tên `_test`), cùng SMTP fixture cục bộ từ [`local_smtp.py`](../../backend/scripts/postman/local_smtp.py) ở `127.0.0.1:1025` và OTP lookup `127.0.0.1:1026`. Profile `test` chỉ để cho phép URL PostgreSQL local trong khi cấu hình bình thường yêu cầu Supabase/TLS. Tắt SMTP auth và STARTTLS cho fixture; không dùng `.env` ứng dụng hoặc Gmail thật.

Tạo Postman environment riêng, **không commit** file có mật khẩu/token, với các biến `runId`, `adminEmail`, `adminUsername`, `adminPassword`, `teacherEmail`, `teacherUsername`, `studentEmail`, `studentUsername`, `userPassword`. Admin phải được bootstrap trên cùng DB bằng `ADMIN_BOOTSTRAP_*` trùng các giá trị environment. Email/username Teacher và Student cần mới cho mỗi lượt chạy. `userPassword` phải đáp ứng chính sách 4.1. Collection lấy OTP qua fixture, lưu Bearer token trong biến của lượt chạy, rồi kiểm tra profile, dashboard, lớp, thông báo, Lesson, Assignment, RBAC và chấm điểm.

Ví dụ với environment file Git ignore đã chuẩn bị và Newman được cài riêng ngoài repo:

```bash
newman run docs/postman/AlgoLearner-4.1-4.6-local.postman_collection.json \
  -e backend/target/verification/postman-local.postman_environment.json
```

Bằng chứng lượt chạy ngày 19-11-2026: [báo cáo DB/Postman](../test-reports/2026-11-19-postman-db-smoke.md). Không chạy collection trên production; nó tạo tài khoản, lớp, bài học và bài tập.
