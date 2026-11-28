# Tài khoản database cho backend

Profile `separated-db` tách credential Flyway (`MIGRATION_DB_USERNAME`,
`MIGRATION_DB_PASSWORD`) khỏi credential API/worker (`DB_USERNAME`, `DB_PASSWORD`).
Không đưa secret vào repository.

Triển khai trên schema ứng dụng riêng:

1. DBA tạo login `algolearner_runtime` với `NOSUPERUSER NOCREATEDB NOCREATEROLE
   NOREPLICATION NOBYPASSRLS`, đặt mật khẩu qua secret manager. Không cho role này
   làm chủ schema/table hoặc kế thừa role owner.
2. Chạy Flyway bằng migration owner, rồi chạy `provision-runtime-role.sql` bằng
   chính owner trong đúng `search_path`. Chạy lại script sau mỗi đợt migration
   có bảng/view/sequence mới, trước khi chuyển traffic sang phiên bản mới.
3. Khởi động API/worker với profile `internal-auth,separated-db`, dùng runtime
   login cho datasource. Không cấp credential runtime cho browser/Supabase client.

Script chỉ cấp DML cho bảng ứng dụng `tbl_*`, SELECT cho view `vw_*`, quyền dùng
sequence; không cấp DDL/TRUNCATE hay quyền sửa/xóa Audit Log. FORCE RLS cho phép
hai role backend tin cậy (runtime và migration owner). Đây là ranh giới giữa
backend và client DB; quyền từng người dùng vẫn được kiểm tra tại Service,
không phải policy RLS theo JWT. Không chạy script trên schema dùng chung với
ứng dụng khác. Credential/role production chưa được thay đổi bởi code này.

# Ảnh báo cáo

Ảnh mới tối đa 5 MB (có thể giảm bằng `app.problems.report-image-max-bytes`),
phải qua kiểm tra định dạng và malware scanner trước khi ghi private storage.
Cấu hình storage/scanner dùng chung với module file. Database chỉ ghi UUID
object; ảnh BYTEA cũ vẫn đọc được, không tự động xóa hoặc di chuyển dữ liệu cũ.
Nếu transaction rollback, backend xóa object vừa tạo. Nếu storage cũng lỗi
trong bước cleanup, có cảnh báo kèm file UUID để đối soát object mồ côi; cần
đối soát bucket định kỳ, nhất là sau khi process bị dừng đột ngột.

Giới hạn báo cáo: 5/phút, 10 OPEN/IN_REVIEW mỗi tài khoản, một báo cáo chưa
đóng cho mỗi bài. Báo cáo đã RESOLVED/REJECTED không mở lại; cập nhật cùng trạng
thái là idempotent. OPEN có thể chuyển thẳng sang trạng thái kết thúc.
Giới hạn 5 SUBMIT đang QUEUED/RUNNING áp dụng cho lượt nộp mới; retry cùng
idempotency key trả job cũ, rejudge tin cậy không bị chặn bởi quota người học.
