# PostgreSQL và Postman smoke — 19/11/2026

## Phạm vi đã chạy

- Source backend SHA: `64b9235c140f39188b1ab075c2b0e19236b6aec1a64c5edd1ffa810769c0888d`, cùng source với gate backend cuối và lượt đo JaCoCo.
- PostgreSQL 16.15 cục bộ, database mới `algolearner_postman_test` trên `127.0.0.1:55432`; backend JAR chạy `internal-auth,test` trên `127.0.0.1:18080`. Không dùng `.env` ứng dụng hoặc DB Supabase.
- Spring Boot/Flyway khởi động thành công, áp **21 migration** đến `V202611180014`; Hibernate `validate` ở cấu hình hiện hành.
- Newman chạy [Postman collection](../postman/AlgoLearner-4.1-4.6-local.postman_collection.json): **44/44 request, 95/95 assertion, 0 failure**. Luồng gồm đăng ký và xác minh OTP Teacher/Student, đăng nhập Teacher/Admin, profile/privacy/dashboard, tạo và tham gia lớp, thông báo, bài học và hoàn thành, tạo/nộp/chấm/công bố điểm bài tập, lịch sử Rejudge `ORIGINAL`, cùng hai kiểm tra Student bị từ chối ở route Teacher.
- SMTP fixture cục bộ chỉ nhận thư test và cấp OTP cho collection; không phát thư ra ngoài.

## Đối chiếu dữ liệu sau request

Truy vấn chỉ đọc trên chính database test trả về: 3 tài khoản, 1 lớp, 1 thông báo, 1 bài học, 1 completion, 1 bài tập, 1 attempt, 1 answer và 2 evaluation generations. Attempt ở trạng thái `GRADED`, `final_score=10.00`. Truy vấn `pg_class` cho bảng nghiệp vụ `tbl_*` cho kết quả **0 bảng thiếu RLS**. Kết quả này xác nhận request đã ghi và đọc dữ liệu PostgreSQL thật trong môi trường test cục bộ.

## Bằng chứng và giới hạn

- Kết quả Newman đã lọc token: `backend/target/verification/postman-local-result.json` (44 items, 95 assertions, 0 failed).
- Log khởi động/migration: `backend/target/verification/postman-backend-test.log` (local ignored artifact). Postman environment chứa mật khẩu Admin test nằm trong `backend/target/verification/`, quyền file `0600`, không đưa vào Git.
- Chưa chạy trên Supabase/staging, chưa thử Gmail/inbox thật, ClamAV engine, Supabase Storage hay Docker worker thật. Collection là smoke các luồng chính, không đại diện mọi endpoint/tổ hợp quyền/AC 4.1–4.6. Full JUnit và coverage cục bộ được ghi riêng ở [báo cáo backend](2026-11-18-backend-implementation-verification.md).
