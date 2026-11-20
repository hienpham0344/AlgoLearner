# Xác minh backend SRS 4.1–4.6 — 17/11/2026

## Kết luận

**Backend chưa đạt đầy đủ SRS.** Các đường lỗi P1 đã vá có kiểm thử hồi quy, nhưng còn 14 vấn đề P2 trong báo cáo gốc và đã tái hiện thêm hai lỗi đồng thời ở Classroom: **AL-23 (P1)** làm phục hồi Owner cũ, **AL-24 (P2)** vẫn cho tham gia bằng request đã đọc mã lớp cũ. Tổng các lỗi còn được liệt kê: **1 P1 và 15 P2**, ngoài các chức năng chưa triển khai.

Đợt này mở rộng xác minh sang PostgreSQL 16 thật, native SQL, JPA, transaction, concurrency, cả hai track Flyway và luồng JDBC TLS/HTTP-JPA. Không kết nối hoặc ghi dữ liệu vào Supabase ứng dụng; không gửi Gmail thật.

## Kết quả thực chạy

Các lượt chốt sau điều chỉnh fixture đều đã hoàn tất. Gộp suite chính và hai bài opt-in: **273/273 test Java đạt, 0 failure, 0 error, 0 skipped**. Lượt `main-auth` lặp lại 90 test đã nằm trong tập trên, không cộng thêm 90 vào tổng số test duy nhất.

| Hạng mục | Kết quả |
| --- | --- |
| PostgreSQL | 16.15, cluster kiểm thử riêng, chỉ listen `127.0.0.1:55432` |
| Track `db/migration` | 8 migration; validate đạt; migrate lần hai áp dụng 0; 20/20 bảng nghiệp vụ bật RLS |
| Track `db/main-auth` | 9 migration; validate đạt; migrate lần hai áp dụng 0; 23/23 bảng nghiệp vụ bật RLS |
| Suite mặc định + registration benchmark | BUILD SUCCESS; 273 test được báo cáo: 271 đạt, 2 opt-in chưa bật; 0 failure/error |
| API/JPA/concurrency trên `main-auth` | BUILD SUCCESS; 90/90 test đạt, 0 failure/error/skipped sau sửa fixture |
| HTTP/JPA đầy đủ và JDBC TLS opt-in | 1/1 test HTTP/JPA và 1/1 test JDBC TLS đạt, 0 failure/error/skipped; JDBC dùng TLS 1.3 và role test SCRAM riêng |

Hai test có tên `LiveSupabaseFlowTest` và `SupabaseConnectionTest` được chạy với DB test local và TLS. Kết quả của chúng xác minh logic HTTP/JPA/JDBC/TLS, **không chứng minh kết nối Supabase hoặc việc email tới Gmail thật**.

JaCoCo ở lượt suite mặc định dùng execution data mới: **2.381/2.546 dòng (93,52%)**, **946/1.288 nhánh (73,45%)** trên toàn backend được phân tích. Coverage không chứng minh các chức năng thiếu đã tồn tại hoặc mọi FR đã có kiểm thử.

Registration benchmark gồm 5 lượt làm nóng và 30 mẫu qua MockMvc, PostgreSQL local, mail sender giả: p50 **827,18 ms**, p95 **1.134,75 ms**; riêng BCrypt p50 **791,25 ms**, p95 **1.087,26 ms**. Đây là phép đo môi trường hiện tại, không phải kết luận năng lực production hay kiểm tra SMTP thật.

Python launcher: **9 tests, OK**. `git diff --check` không phát hiện lỗi whitespace.

Bằng chứng của các lượt chốt được lưu trong thư mục build bị Git bỏ qua:

- [Suite mặc định](/home/hien/Project/AlgoLearner/backend/target/verification/default-summary.log).
- [90 test trên main-auth](/home/hien/Project/AlgoLearner/backend/target/verification/main-summary.log).
- [Luồng HTTP/JPA opt-in](/home/hien/Project/AlgoLearner/backend/target/verification/live-summary.log).
- [JDBC TLS opt-in](/home/hien/Project/AlgoLearner/backend/target/verification/tls-summary.log).
- [Tổng kết 273 test](/home/hien/Project/AlgoLearner/backend/target/verification/final-test-summary.json).
- [Hai track Flyway](/home/hien/Project/AlgoLearner/backend/target/verification/flyway-both-tracks.log).
- [Python launcher](/home/hien/Project/AlgoLearner/backend/target/verification/python.log).

Các file trong `target` có thể bị xóa khi chạy Maven `clean`; kết quả tổng hợp và phạm vi xác minh được giữ trong báo cáo này.

## Transaction và concurrency của bản vá Assignment

Các test PostgreSQL đã bổ sung:

- `AssignmentPersistenceSafetyTest`: query lấy attempt đúng mốc hết giờ; loại attempt chưa hết hạn/không có timer/đã submit; điều kiện Lesson archive; query pessimistic lock; từ chối xóa câu hỏi và giữ câu trả lời lịch sử.
- `AssignmentTimeoutIntegrationTest`: scheduler dùng đáp án đã lưu và thời điểm hết giờ; chạy lặp không nộp lại; LOCK giữ draft; autosave quá hạn phải commit trạng thái timeout dù trả lỗi, không ghi payload mới. Các lần kiểm tra persisted state dùng transaction mới.
- `AssignmentConcurrencyTest`: hai request start dùng cùng attempt; submit đợi autosave commit và lấy đáp án mới; autosave đợi sau submit bị từ chối và không ghi đè official answer.
- `ClassroomConcurrencyTest` và `ClassroomQueryCountTest`: các tình huống join/invitation/removal và giới hạn query đã có trong repository cũng được đưa vào lượt chạy PostgreSQL.

## Hai lỗi đồng thời đã xác nhận thêm

### AL-23 — P1: cập nhật cũ phục hồi Owner sau khi Admin đã chuyển quyền

Vị trí: [ClassroomService.java:108](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/classroom/ClassroomService.java:108), [ClassroomService.java:281](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/classroom/ClassroomService.java:281).

Lịch đã chạy với transaction và query JPA thật:

1. Teacher A bắt đầu update và tải Classroom đang thuộc A; giữ transaction tại điểm sau query.
2. Admin chuyển Owner sang B; transaction khác kiểm tra Owner B đã commit.
3. Cho update của A tiếp tục commit.
4. Đọc DB bằng transaction mới: Owner trở lại A.

Entity chưa có version hoặc khóa row lớp thống nhất; update entity cũ ghi lại `owner_id`. Hệ quả: thao tác đã bắt đầu trước chuyển quyền có thể hoàn tác việc thu hồi quyền Owner. Cần optimistic version hoặc khóa lớp dùng nhất quán trên mutation. Đây là lỗi đã tái hiện, không còn chỉ là rủi ro suy từ mã.

### AL-24 — P2: request đã đọc mã cũ vẫn join sau khi regenerate commit

Vị trí: [ClassroomService.java:148](/home/hien/Project/AlgoLearner/backend/src/main/java/com/algolearner/platform/classroom/ClassroomService.java:148).

1. Student gọi join bằng mã hiện tại; giữ transaction ngay sau query lấy Classroom, trước pair lock.
2. Teacher regenerate code; transaction khác kiểm tra mã mới đã commit.
3. Cho request join tiếp tục.
4. Request trả `JOINED`; DB có membership `ACTIVE` dù mã dùng để khởi tạo request đã bị thay.

`join()` dùng snapshot Classroom trước khóa và không refresh; regenerate/configure không dùng khóa lớp chung. Cần khóa/đọc lại cấu hình tham gia trong cùng cơ chế đồng bộ, rồi bổ sung regression cho lịch này.

Các probe dùng real Spring repository interceptor với `invocation.proceed()`, latch có timeout, transaction độc lập; không thay query bằng giá trị mock. Schema riêng của probe đã được dọn.

## Các P2 còn lại và mức bằng chứng

| Mã | Trạng thái đã xác minh |
| --- | --- |
| AL-08 | PostgreSQL/service: hai tài khoản có `A.username=B.email`; 5 login với định danh/mật khẩu của A bị từ chối, A không tăng bộ đếm, B tăng 5 và bị khóa |
| AL-09 | PostgreSQL/JPA: DB có Assignment và attempt nhưng Dashboard Assignment/submission vẫn rỗng |
| AL-10 | PostgreSQL/JPA: notification count 1 nhưng Dashboard items 0; DTO đọc notification không có title/link/classroom cho Announcement |
| AL-11 | Mã hiện tại: Dashboard Admin `activities()`/`audits()` vẫn trả rỗng dù có storage audit |
| AL-12 | Mã hiện tại: chuyển Owner chỉ set owner, chưa ghi audit tương ứng |
| AL-13 | Mã hiện tại: restore Lesson luôn đặt PUBLISHED, chưa lưu trạng thái trước archive |
| AL-14 | Mã hiện tại: Lesson material/Essay vẫn nhận metadata URL, chưa có file storage và kiểm tra nội dung tệp theo yêu cầu |
| AL-15 | PostgreSQL/service: title 255 ký tự gây SQLState `22001`; cả announcement và notification bị rollback |
| AL-17 | Service và test hiện tại vẫn áp dụng Late penalty trái FR-ASG-005 AC-04 |
| AL-18 | Mã hiện tại: chấm Essay chưa chặn draft/điểm vượt max; chưa tách đầy đủ Final Grade khỏi tổng tạm |
| AL-19 | Mã hiện tại: override/chấm lại ghi đè giá trị và lý do cuối; chưa có lịch sử đầy đủ |
| AL-20 | PostgreSQL/service: rejudge giữ nguyên điểm 10 vẫn tạo thêm 1 notification và 1 log; chưa thực hiện đánh giá lại qua testcase/judge |
| AL-21 | Mã hiện tại: IMMEDIATE chưa được áp dụng; hiển thị điểm vẫn chỉ dựa `isGradePublished` |
| AL-22 | Mã hiện tại: create chưa kiểm tra đầy đủ duration/maxAttempts/penalty/thứ tự thời gian |

AL-16 Partial Credit đã được sửa và có kiểm thử; không nằm trong 14 P2 còn lại. Xem [báo cáo gốc](/home/hien/Project/AlgoLearner/docs/test-reports/2026-11-16-backend-srs-4.1-4.6-audit.md) để đối chiếu FR/AC và vị trí chi tiết.

## Phần chưa được triển khai

Không coi các mục này là đạt chỉ vì bộ test hiện có chạy thành công:

- Snapshot/versioning câu hỏi, quy tắc chấm và testcase đầy đủ; chặn delete mới bảo vệ một đường mất dữ liệu.
- Evaluator AUTO_ITEMS và API Teacher xem tiến độ từng Student.
- Quiz/Practice links, Code Judge và sao chép Public Problem/testcase độc lập.
- Reorder/update câu hỏi, SCHEDULED, cấp/reset lượt từng Student, Student xem lịch sử attempt.
- Shuffle thực sự, AFTER_SUBMIT/AFTER_DEADLINE và ScoringPolicy.LATEST.

## Điều chỉnh bộ test trong đợt xác minh

- `AssignmentConcurrencyTest` trước đó dùng `callRealMethod()` trên abstract Spring Data repository, gây lỗi harness. Đã thay bằng interceptor trên repository thật; dọn membership trước Classroom để đúng khóa ngoại.
- Track `main-auth` yêu cầu `password_hash NOT NULL`. Mười helper account fixture chỉ dùng actor giả lập đã được bổ sung hash giả không dùng đăng nhập; không đổi schema, không nới assertions hoặc sửa logic production vì lỗi fixture này.
- Runtime/database/log đặt dưới `backend/target/verification`, được Git bỏ qua. Không commit/push; giữ nguyên `outputs/` có từ trước.
- PostgreSQL tạm đã được dừng thành công sau khi hoàn tất các lượt kiểm thử.

## Giới hạn kết luận

- Test đạt không thay thế đánh giá hoàn thành 53 FR của SRS 4.1–4.6, nhất là các chức năng còn thiếu và lỗi đã tái hiện.
- Kiểm tra RLS trên local xác nhận cờ bật trên các bảng; không xác nhận cấu hình quyền/role của Supabase đang triển khai.
- Không xác nhận SMTP/Gmail thật, hạ tầng production, sandbox chưa có triển khai hoặc năng lực chịu tải production.
- Cần ưu tiên AL-23, AL-24 và các P2 còn lại trước khi xác nhận backend hoàn thành.
