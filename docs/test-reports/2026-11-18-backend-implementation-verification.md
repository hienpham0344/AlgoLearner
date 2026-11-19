# Backend 4.1–4.6: triển khai và xác minh

Ngày 18/11/2026. Phạm vi: 53 FR của backend 4.1–4.6 và những dependency cần cho Dashboard, Lesson completion, Code Question. Không kết luận hoàn tất toàn bộ các module 4.7–4.16.

**Kết quả cục bộ cuối:** hai track `db/migration` và `db/main-auth` cùng source SHA `64b9235c140f39188b1ab075c2b0e19236b6aec1a64c5edd1ffa810769c0888d`; mỗi track **543 PASS, 3 opt-in skip, 0 failure/error**, `mvn verify` và đóng gói JAR thành công. Không có test DB bắt buộc nào bị skip.

**Mức nghiệm thu SRS:** 253 AC có assertion cục bộ trực tiếp; 107 AC mới được chứng minh một phần; 7 AC về client chưa xác minh; 5 mục được SRS miễn yêu cầu trong MVP. Không còn AC bắt buộc chỉ dừng ở đọc code. Không suy rằng toàn bộ 372 AC đã hoàn tất từ kết quả Maven.

## Những phần đã sửa

- Account/identity: resolver chung, ambiguity dữ liệu cũ không làm tăng nhầm counter hoặc gửi nhầm OTP; định danh bất biến. Kiểm thử thêm role injection, public Admin registration, consent, Pending retention, khóa đúng 15 phút, OTP REGISTER và password/session atomicity.
- Classroom/Announcement: khóa aggregate và kiểm tra Owner/trạng thái tại thao tác; bảo vệ chuyển quyền, code rotation, join/invitation, archive và quyền ghi. Audit cùng transaction; notification count/list cùng scope, title hợp lệ và chống trùng event.
- File/Avatar: private Storage, metadata/fileId, validation cấu trúc và malware scan, quyền đọc mỗi lần; không có public blob URL. Recovery/cleanup lọc eligibility trước LIMIT, kiểm lại tham chiếu dưới khóa; transaction bind giữ khóa tới commit. Avatar dùng CAS, migration opt-in và durable cleanup queue với lease/retry/DLQ.
- Assignment: revision/attempt snapshot bất biến, soft-retire câu hỏi, clone độc lập giữ settings; typed/versioned/idempotent autosave; quota/grant/reset và timeout. Lượt LOCK bắt đầu đúng thời điểm grant vẫn cần quyền mới sau timeout kế tiếp. Cho Late giữ timer riêng; không cho Late dùng cutoff sớm hơn deadline/timer, chặn đổi policy làm lùi timestamp trước lúc bắt đầu.
- Scheduled/visibility: future publish lưu SCHEDULED thật, activation đúng Start Time, batch không bị kẹt sau class/owner inactive. Student trước giờ mở chỉ nhận metadata cho phép; Dashboard che deadline, có Scheduled không deadline và Teacher vẫn thấy bài đang mở cho Late.
- Grading/Rejudge: weighted objective/Code, Essay chờ chấm, evaluation/release/override history. Ba mức Code feedback theo policy đã snapshot và mức hạn chế hiện hành; own history đọc được sau Closed, polling có kiểm parent/release. Notification chỉ đến đúng Student khi công bố kết quả/điểm thay đổi.
- Rejudge theo lựa chọn người dùng: `ORIGINAL` mặc định hoặc `LATEST_PUBLISHED`. Giữ official submission/source/ngôn ngữ/điểm tối đa/trọng số câu hỏi gốc; context testcase/trọng số testcase/giới hạn mới thuộc revision đã publish và được lưu theo generation. Working edits không dùng cho Rejudge. Pending/infra không thay kết quả chính thức cũ.
- Judge: API chỉ ghi job; worker riêng biên dịch/chạy Java/Python trong Docker. RUN sample/rate-limit, không official. Typed JobReference không lộ source/IO; port rejudge riêng bảo vệ identity/generation. Sửa stdin, OOM evidence và cleanup tài nguyên; public feedback không lộ hidden IO ngoài quyền cấu hình.
- Lesson và dependency: ALL/ANY completion theo submission/điểm công bố, lịch sử và restore; Quiz/Practice liên kết Lesson archived bị chặn cả đường làm bài/timer/resume. Class lock giữ trạng thái ổn định với archive/link changes. Course/contribution/violation và Dashboard có nguồn dữ liệu thật theo role; latest valid rejudge cập nhật solved/streak, pending/infra giữ kết quả hợp lệ cũ.

## Bằng chứng kiểm thử

JSON/log/Surefire XML ở `backend/target/verification`. JSON lưu source SHA, snapshot, selector, track, phase và kết quả. Test giữa các batch có trùng nhau; **không cộng các batch thành tổng test riêng biệt**.

| Phạm vi | Lệnh hoặc bằng chứng | Kết quả quan sát |
| --- | --- | --- |
| Full backend default | `backend-acceptance-final-default.json` | 543 PASS, 3 opt-in skip; verify + JAR |
| Full backend main-auth | `backend-acceptance-final-main.json` | 543 PASS, 3 opt-in skip; verify + JAR |
| Source và artifact | SHA hai track trùng workspace hiện tại; ZIP integrity của JAR | đạt; JAR 70,632,821 bytes/track |
| Flyway new/upgrade/repeat | `BackendMigrationVerificationTest`, trong full suite | 4 PASS, gồm hai track và giữ dữ liệu legacy |
| Cặp migration mới | 13 cặp `V20261118*.sql` | giống byte giữa hai track |
| Frontend unit/build | `npm test`, `npm run build` | 22 PASS; build thành công, còn cảnh báo bundle >500 kB |
| Browser HTTP fixture | `npm run test:e2e -- e2e/backend-source.spec.ts` | 3 PASS trong14.2s ở lượt chạy lại |
| Launcher Python | `python3 -B -m unittest discover -s scripts/tests` | 9 PASS |
| Registration benchmark | `registration-benchmark-local.json` | 1 PASS; 30 mẫu sau5warmup; HTTP p50=217.31ms/p95=230.14ms, SMTP mock |
| Diff whitespace | `git diff --check` | đạt |

Ba opt-in skip là `LiveSupabaseFlowTest`, `SupabaseConnectionTest`, `RegistrationBenchmarkTest`. Benchmark đã chạy riêng như bảng trên; hai kiểm thử Supabase chưa chạy vì chưa xác nhận project staging. Benchmark này không chứng minh Gmail thật hoặc tải100VU.

### Đo coverage bổ sung ngày 18/11/2026

Chạy lại đúng source SHA `64b9235c140f39188b1ab075c2b0e19236b6aec1a64c5edd1ffa810769c0888d` trên PostgreSQL test cục bộ với JaCoCo 0.8.12, track mặc định: **546 test, 543 PASS, 3 opt-in skip, 0 failure/error, BUILD SUCCESS**. Bằng chứng: `backend/target/verification/backend-coverage-current/coverage.log` và `target/site/jacoco/jacoco.xml` trong cùng snapshot. Đây là lượt đo riêng vì runner full gate trước đó tắt JaCoCo để tránh overhead.

| Phạm vi package | Line coverage | Branch coverage |
| --- | ---: | ---: |
| 4.1 `auth`, `admin` | 597/648 = 92,1% | 255/376 = 67,8% |
| 4.2 `profile`, `profile/dashboard` | 560/594 = 94,3% | 394/503 = 78,3% |
| 4.3 `classroom` | 281/293 = 95,9% | 105/132 = 79,5% |
| 4.4 `announcement` | 100/103 = 97,1% | 17/20 = 85,0% |
| 4.5 `lesson` | 350/370 = 94,6% | 134/198 = 67,7% |
| 4.6 `assignment` | 1320/1389 = 95,0% | 913/1211 = 75,4% |
| **Tổng package 4.1–4.6 ở trên** | **3208/3397 = 94,4%** | **1818/2440 = 74,5%** |
| **Toàn backend** | **4587/5094 = 90,0%** | **2617/3746 = 69,9%** |

Tỷ lệ theo package không gồm dependency `storage`, `judge`, `notification` và các package khác; hàng toàn backend gồm chúng. Coverage đo dòng/nhánh thực thi, không thay cho nghiệm thu SRS. Không có lượt kiểm thử Postman hoặc kiểm chứng truy vấn trên Supabase/staging thật; integration tests dùng PostgreSQL 16 cục bộ và chạy repository/JDBC/HTTP thực với một số external service fixture.

Các lỗi quan trọng đã có regression RED/GREEN: identity ambiguity, ownership/code race, notification title/count, PDF blacklist bypass, file recovery/cleanup starvation và binding race, avatar compensation, Judge stdin/OOM/cleanup, rejudge official identity/version, linked archive/timer/resume, Code compile budget, Late/Scheduled/metadata/feedback/history, clone settings và grant tại cùng timestamp. Các lỗi compile/test setup, SQL fixture, target Flyway và expectation cũ sai nghiệp vụ không được tính là RED production.

Lượt browser đầu có 2 PASS/1 timeout khi chạy cùng Maven; giữ nguyên assertion/code và chạy lại sau tác vụ Maven thì 3 PASS. Các lượt full trung gian thất bại bởi expectation Late cũ đã được ghi trong báo cáo Assignment; không dùng chúng làm gate cuối.

## Truy vết và review

[Ma trận 53 FR /372 AC](2026-11-18-backend-traceability.json) giữ nguyên văn AC/source paragraph, exact test/assertion/code, evidence package và lý do giới hạn. Đã kiểm tra độc lập **424 test reference** của253 AC VERIFIED_LOCAL đều có XML PASS trên cả hai full run cuối, không thiếu reference.

Ba evidence package: [Auth/Profile95 AC](2026-11-18-auth-profile-ac-evidence.json), [Classroom/Announcement/Lesson150 AC](2026-11-18-classroom-lesson-ac-evidence.json), [Assignment127 AC](2026-11-18-assignment-ac-evidence.json). `PARTIAL` vẫn có nhánh, tổ hợp hoặc phần end-to-end chưa được assertion chứng minh đầy đủ; không đồng nghĩa mặc nhiên là thiếu code, cũng không được coi là đã nghiệm thu toàn bộ.

Review độc lập: [Storage/Judge/Course/exception](2026-11-18-root-modules-review.md), [Assignment fixwave](2026-11-18-assignment-ac-fix-review.md), [Judge/Dashboard seams](2026-11-18-judge-dashboard-ac-fix-review.md), [review tích hợp cuối](2026-11-18-final-backend-integration-review.md). Không còn P1/P2 đã biết trong phạm vi review; đây là gate cục bộ, không phải chứng nhận production.

## Quyết định và giới hạn

- Giữ dirty workspace; không commit/push hoặc auto-clean bằng chứng. Không chạy migration trên DB ứng dụng.
- Dùng hai track Flyway hiện hữu; audit legacy được giữ/adopt với provenance, không xóa. Case collision cùng namespace phải dừng để operator đối chiếu, không tự chọn tài khoản hoặc đổi định danh.
- Dữ liệu cũ không có snapshot phải giữ `LEGACY_MISSING`/provenance; không tạo lịch sử giả hoặc tính lại điểm khi migration. File URL legacy cần upload lại, không tự tải URL bên ngoài.
- Active fixed Admin có ba quyền grading qua namespace riêng; không trở thành Teacher/thành viên lớp. SRS chưa định nghĩa grant theo từng lớp; granular grants cần contract riêng.
- Attempt/revision và kết quả trước được giữ khi sửa nội dung/rejudge. Chính sách feedback hiện hành chỉ có thể hạn chế mức tiết lộ so với snapshot ban đầu. Cảnh báo/xác nhận khi sửa testcase ở UI vẫn chưa được xác minh.
- Snapshot/target test riêng, hai build slot và tám Spring context tránh source đang sửa hoặc cạn tài nguyên gây kết quả sai. Build lỗi trước test không được tính là RED nghiệp vụ.
- PDFBox3.0.8 được thêm sau regression bypass kiểm tra PDF; không nâng dependency khác chỉ để qua test.
- Scheduler pool bốn thread giúp job IO không giữ toàn bộ lịch timeout. Quyền/deadline vẫn kiểm tra ở service; tải trên cấu hình triển khai thật chưa đo.
- Cleanup không xóa key đang dùng, remote IO ngoài DB transaction, retry hết giữ FAILED để operator xử lý. Không tự xóa avatar local gốc sau migration.

## Còn cần trước nghiệm thu đầy đủ

Còn107 AC PARTIAL và7 AC client chưa xác minh; chi tiết từng phần thiếu nằm trong ma trận. Chưa kiểm chứng Supabase Storage/DB staging, ClamAV engine, Docker sandbox thật, Gmail/inbox hoặc tải100VU. DB/SMTP ứng dụng hiện có chưa được xác nhận là staging; chưa dùng để ghi dữ liệu hoặc phát email.

Cần project staging riêng và credential server qua biến môi trường/file Git ignore, không gửi secret trong chat. Sau khi có môi trường và quyền:

1. Backup, đối chiếu đúng lịch sử Flyway; nâng cấp bản sao/staging, kiểm row counts/điểm/history/membership/provenance.
2. Bucket private +clamd: upload sạch/nhiễm, outage, privacy và recovery/cleanup bằng dịch vụ thật.
3. Worker Linux với image digest cố định: Java/Python stdin, AC/WA/CE/TLE/MLE/output limit, network/PID/CPU/RAM và cleanup khi crash.
4. Gmail với inbox test được phép, OTP một lần/hết hạn; đo tải trên cấu hình mục tiêu.
5. Kiểm tra UI/rendering/confirmation và các scenario PARTIAL còn lại; chuyển avatar theo batch opt-in sau backup, giữ file gốc tới nghiệm thu.

Chưa đủ bằng chứng để tuyên bố hoàn tất toàn bộ372 AC hoặc production readiness.
