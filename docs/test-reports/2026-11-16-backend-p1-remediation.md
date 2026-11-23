# Khắc phục các đường lỗi P1 backend 4.5–4.6

Ngày: 16/11/2026. Đối chiếu [báo cáo baseline](/home/hien/Project/AlgoLearner/docs/test-reports/2026-11-16-backend-srs-4.1-4.6-audit.md).

Kết quả PostgreSQL và xác minh bổ sung ngày 17/11 được ghi tại [báo cáo xác minh toàn backend](/home/hien/Project/AlgoLearner/docs/test-reports/2026-11-17-backend-full-verification.md).

## Thay đổi

- **AL-01/02:** scope Assignment/Lesson theo lớp trước khi xóa câu hỏi hoặc gỡ liên kết. Teacher sở hữu lớp A không thể dùng ID tài nguyên lớp B.
- **AL-03:** thay phép tìm chuỗi UUID bằng bộ chấm JSON array UUID. Single Choice/True-False yêu cầu đúng một lựa chọn hợp lệ; UUID lặp, sai định dạng hoặc ngoài câu hỏi không được điểm. **AL-16** được sửa cùng bộ chấm: Partial Credit cộng điểm đúng, không xóa điểm khi chọn thêm đáp án sai; làm tròn sau tính tỷ lệ.
- **AL-04:** dùng chung điều kiện ghi cho start/autosave/submit: lớp ACTIVE, Owner ACTIVE, Assignment PUBLISHED, Start Time/deadline hợp lệ và không có Lesson liên kết ARCHIVED. Khóa Assignment rồi Attempt để tuần tự hóa thao tác; close/xóa câu hỏi dùng cùng khóa Assignment.
- **Timeout:** đúng mốc hết giờ server, AUTO_SUBMIT chấm đáp án đã lưu; LOCK giữ bản nháp và không tạo official submission. Autosave quá hạn không ghi payload mới; trạng thái timeout được commit dù response trả TIME_LIMIT_EXCEEDED. Job phía server xử lý cả khi trình duyệt đóng, batch 100, poll mặc định 1000ms; gọi service qua proxy với transaction riêng từng attempt. Mốc submit tự động là lúc hết hạn, tránh biến bài đúng hạn thành Late do job chạy trễ. Không cho đổi duration/timeExpiredAction sau khi đã có attempt để tránh thay đổi timer hồi tố.
- **AL-05:** Student gọi detail trước Start Time nhận ASSIGNMENT_NOT_STARTED; danh sách vẫn cung cấp tên/thời điểm mở, Owner vẫn đọc được.
- **AL-06:** chặn xóa câu hỏi khi Assignment đã có bất kỳ attempt, gồm draft, để ngăn FK cascade xóa câu trả lời lịch sử. Đây là biện pháp bảo vệ xóa; chưa triển khai snapshot/versioning đầy đủ.
- **AL-07:** `/complete` chỉ chấp nhận Lesson PUBLISHED/MANUAL. NONE/AUTO_ITEMS không thể tự đánh dấu hoàn thành. Evaluator AUTO_ITEMS vẫn chưa có.

Không đổi migration, dependency hoặc lockfile; không kết nối DB ứng dụng, không commit/push. Tài liệu chạy backend đã cập nhật hành vi mới.

## Bằng chứng hồi quy

- AssignmentSafetyTest trước sửa: **10 test, 9 failure, 0 error**; xác nhận lỗi đúng nghiệp vụ thay vì lỗi môi trường.
- LessonServiceTest trước sửa: **15 test, 3 failure**; sau sửa **15/15 đạt**.
- ObjectiveQuestionGrader: runner cô lập trước sửa **7 failure/1 pass** trên bộ chấm cũ; sau sửa **8/8 đạt** trên bộ chấm mới. Bộ test này cũng được chạy bằng Maven cùng service thật.
- Scheduler: trước sửa **3 failure** khi method rỗng; sau sửa **4/4 đạt** qua harness cô lập, sau đó được chạy lại trong Maven với dependency thật.
- Khi bổ sung kiểm tra timer bị thay đổi sau start: **18 test, 2 failure, 0 error**; hai failure tương ứng duration và timeout action.
- Có kiểm tra transaction qua Spring TransactionInterceptor: autosave quá hạn gọi commit, không rollback, và không lưu đáp án mới.
- Đã thêm 4 test PostgreSQL có điều kiện cho query timeout/native SQL, derived query Lesson archive, query khóa và bảo toàn câu trả lời lịch sử. Chưa có TEST_DB_URL để chạy các test này.

## Xác minh cuối cùng

- Focused Maven: **65 test đạt**, 0 failure/error/skipped (LessonService, AssignmentService, AssignmentSafety, ObjectiveQuestionGrader, AssignmentTimeoutScheduler).
- Chạy Maven offline `clean verify` với JDK 21 và Mockito premain agent như lệnh trong báo cáo baseline: **BUILD SUCCESS**, **265 test được báo cáo; 212 đạt, 53 skipped, 0 failure/error**. Hoàn tất lúc **23:29:50 ngày 16/11/2026**, giờ Việt Nam.
- 53 skipped gồm 49 test có điều kiện từ baseline và 4 test PostgreSQL mới. Chưa bật TEST_DB_URL hoặc các probe live/benchmark; không coi các test skipped là đã đạt.
- Python launcher: `python3 -B -m unittest discover -s scripts/tests` → **9 tests, OK**.
- `git diff --check` không phát hiện lỗi whitespace. Các liên kết file trong hai báo cáo đã được kiểm tra tồn tại.
- JaCoCo không có execution data trong lượt build sạch này, nên không báo tỷ lệ coverage.
- Log: `/tmp/algolearner-p1-clean-verify.log`, `/tmp/algolearner-p1-final-focused.log`, `/tmp/algolearner-p1-python-tests.log`.

## Phạm vi còn lại

- Chưa xác minh query/locking/concurrency thực tế trên PostgreSQL; các test có điều kiện không thay thế bằng chứng runtime.
- Chưa có snapshot/versioning câu hỏi/testcase, evaluator AUTO_ITEMS, Code Judge và các chức năng thiếu đã liệt kê trong báo cáo baseline.
- Các P2 khác ngoài AL-16 chưa thuộc đợt sửa này: auth namespace, Dashboard/notification, audit, restore Lesson, file storage, Late penalty, grade override/rejudge/publishing và validation cấu hình.
- Các rủi ro cập nhật lớp/Owner đồng thời trong baseline vẫn cần kiểm chứng và xử lý riêng. Chưa thể kết luận toàn bộ backend 4.1–4.6 đạt SRS.
- Khóa ghi theo Assignment ưu tiên tính nhất quán; chưa đo throughput khi nhiều Student autosave/nộp cùng lúc.
