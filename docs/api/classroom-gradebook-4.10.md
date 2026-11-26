# API sổ điểm lớp học 4.10

Base path `/api/v1/classrooms/{classId}/gradebook`. Các mốc thời gian dùng ISO-8601 UTC với hậu tố `Z`. HTTP JSON dùng `ApiResponse<T>`, `PageResponse<T>` hoặc `ApiErrorResponse`. Pagination bắt đầu từ `page=0`, mặc định `size=20`, tối đa `100`. Roster sắp theo Student UUID, bài nộp theo thời điểm nộp giảm dần rồi attempt UUID, và lịch sử theo ID giảm dần.

## Phân quyền

| Vai trò | Đọc | Ghi và xuất |
|---|---|---|
| Teacher, owner hiện tại | Dashboard, chi tiết Student đang là thành viên, bài nộp của item trong lớp, scheme và lịch sử | Scheme, visibility và publication yêu cầu lớp `ACTIVE`. CSV/XLSX theo quyền đọc của owner |
| Teacher khác | HTTP 403 | HTTP 403 |
| Student có membership `ACTIVE` | `GET /me` lấy Student từ principal | HTTP 403 |
| Student đã rời lớp | HTTP 403 | HTTP 403 |
| Admin | Namespace quản trị Gradebook nằm ngoài đợt này | `FR-GRD-001 AC-07` chưa phủ |

## Endpoint

| Method | Path | Payload hoặc kết quả |
|---|---|---|
| GET | base path | `PageResponse<TeacherGradebookRow>` với danh tính Student, các ô điểm và Overall |
| GET | `/summary` | Số Student, item, ô graded/completed, lượt nộp, lượt trễ và trung bình Current |
| GET | `/students/{studentId}` | `TeacherGradebookRow` của Student trong lớp |
| GET | `/items/{itemId}/submissions?sourceType=ASSIGNMENT\|QUIZ` | Trang attempt đã nộp với `submittedAt`, `late`, `status` và `answers` gồm code, essay, file hoặc tùy chọn nếu có |
| GET | `/scheme` | Scheme active, revision và gradebook version. Mode và revision là null khi chưa áp dụng |
| POST | `/scheme-preview` | `{"items":[{"sourceType":"QUIZ","sourceId":"<UUID>"}]}` trả danh sách item với trọng số chia đều |
| PUT | `/scheme` | `mode`, `expectedVersion`, `items`, `reason`. Mỗi item có `sourceType`, `sourceId`, `weight`, `required` |
| PUT | `/visibility` | `{"showOverall":true,"expectedVersion":2}` trả visibility và version |
| GET | `/me` | `{"items":[...],"overall":null}`. Điểm và mẫu số trong `items` thuộc nguồn đã công bố và đang hiển thị. Có thể kèm `overallStatus: UPDATING` trong lúc tính lại |
| POST | `/publications` | Header `Idempotency-Key`, body `scope`, `studentId` với scope `STUDENT`, `expectedVersion`. Trả HTTP 201 hoặc 200 khi replay |
| GET | `/history` | Trang lịch sử có actor, Student, revision, lý do, trạng thái trước/sau và thời điểm |
| GET | `/export?format=csv\|xlsx` | Tệp tải xuống, `Cache-Control: no-store`, `X-Gradebook-Scheme-Revision` nếu có revision |

### Ví dụ áp dụng Weighted

```json
{
  "mode": "WEIGHTED",
  "expectedVersion": 0,
  "reason": "Initial scheme",
  "items": [
    {"sourceType": "ASSIGNMENT", "sourceId": "1a9ad105-b7dd-4e29-81c2-c1ceb99d07cc", "weight": 40, "required": true},
    {"sourceType": "QUIZ", "sourceId": "592f0708-6bba-4550-8c83-21f871eec311", "weight": 60, "required": true}
  ]
}
```

Trọng số nhận tới 4 chữ số thập phân, tổng `100.0000` cho Weighted. Khi chia đều, phân bổ phần dư `0.0001` theo thứ tự cố định từ loại nguồn và UUID. `required` mặc định là true. Raw dùng trọng số 0 trong biểu diễn scheme. Version tăng khi scheme, publication hoặc giá trị hiển thị thay đổi. Apply tạo revision bất biến.

## Điểm và publication

| Trạng thái | Kết quả |
|---|---|
| `GRADED` | Có điểm, kể cả 0. Nếu Assignment legacy thiếu snapshot, mẫu số là null |
| `UNGRADED` | Item được cấu hình không tính điểm |
| `PENDING` | Đã nộp và còn chờ điểm hợp lệ |
| `NOT_SUBMITTED` | Chưa có lượt nộp chính thức |

- Assignment dùng chính sách `HIGHEST` theo tỷ lệ điểm trên snapshot, hoặc `LATEST` theo lượt đã chấm mới nhất. Điểm hiệu lực của evaluation bao gồm override. Nộp trễ ghi riêng và giữ nguyên điểm theo `BR-ASG-006`. CODE chỉ tính qua evaluation hoàn tất. `RUN` đứng ngoài sổ điểm. Rejudge chờ/lỗi giữ evaluation chính thức trước đó.
- Quiz dùng điểm cao nhất thang 100 và override. Teacher khớp `QuizService.result`. Gradebook Student chọn override mới nhất trong số attempt đã công bố để việc chỉnh override ẩn giữ nguyên DTO công khai. API Quiz hiện hữu dùng `latestGrade` như trước.
- Weighted tính `Σ(earned/possible × weight) ÷ Σ(weight hợp lệ) × 100`. Ví dụ điểm `8/10` trọng số 40 và `50/100` trọng số 60 cho `62%`.
- Raw dùng giả định tạm đã duyệt cho FR-GRD-003. Ví dụ `8/10` và `50/100` cho `earnedPoints=58`, `possiblePoints=110`, `percentage=52.7273`.
- Điểm `0/10` hợp lệ đưa 10 vào mẫu số. `null`, Ungraded, legacy thiếu mẫu số và item có mẫu số `0` đứng ngoài tử và mẫu. Tổng mẫu số 0 cho kết quả null. Item bắt buộc có mẫu số 0 vẫn cần bản ghi chấm hợp lệ. Nếu còn item bắt buộc chưa xử lý thì Final là null. `EXCUSED` và Class-wide Pass/Fail nằm ngoài MVP.
- Apply từ chối từng item Ungraded với `GRADEBOOK_ITEM_UNGRADED`. Nếu item thuộc revision hiện hành chuyển sang Ungraded, phép tính bỏ mục đó khỏi điểm, mẫu số và điều kiện Final.
- Item ngoài lớp, đã xóa hoặc bị ẩn do chỉ liên kết với lesson chưa công bố/đã archive đứng ngoài revision mới. Snapshot đã công bố được giữ trong lịch sử. Hard delete lớp có revision trả `409 CLASS_HAS_RETAINED_DATA`.

Theo FR-GRD-008, “Việc công bố Grade phải tuân thủ cấu hình hiển thị của Class và các quy tắc công bố điểm riêng của Assignment hoặc Quiz.” AC-04 giới hạn Student ở Grade của chính mình đã được phép công bố. AC-05 giữ nguyên visibility khi công bố. AC-10 loại yêu cầu không hợp lệ. BR-GRD-006 cho phép hai phạm vi Student và Class. FR-GRD-006 quy định riêng điều kiện xác định Final.

**Cần xác nhận** với nhóm nghiệp vụ về việc thu hồi quyền xem Overall đã công bố. Hiện tại, nếu bất kỳ nguồn nào đã góp vào snapshot không còn hiển thị, `/me` trả `overall: null` và `overallStatus: UPDATING`, đồng thời yêu cầu refresh. Snapshot cũ thiếu danh sách nguồn cũng đi qua luồng này. Snapshot tính lại chỉ dùng điểm công khai hiện tại. Nếu nguồn bị ẩn vẫn là mục bắt buộc, Final tiếp tục để trống đến khi điều kiện Final được đáp ứng.

Publication lấy điểm nguồn đã công bố. Có thể công bố Current với `finalGrade=null`, ví dụ `80/100` đã công bố và một Quiz bắt buộc còn ẩn. Khi nguồn cung cấp điểm còn thiếu, worker có thể tạo Final hợp lệ. Khi `showOverall=false`, `/me` trả `overall=null` dù có publication. Publish scope `CLASS` chốt roster đang hoạt động và trả `publishedCount` cùng `ineligibleCount`. Student vào sau cần đợt công bố mới.

```json
{"scope":"CLASS","expectedVersion":3}
```

Publish thành công ghi audit trong cùng giao dịch, gồm scope, lớp, actor, publication ID và metadata request. Replay cùng actor, lớp, key và payload trả đúng receipt đã lưu và không ghi thêm audit. Key trùng với payload khác trả 409. Yêu cầu mới mang version cũ trả 409. Publication scope Student có thêm `studentId` và yêu cầu membership còn hoạt động.

## Lịch sử, worker và export

- Apply scheme tính lại đồng bộ. Lịch sử và audit có actor, revision, lý do, điểm cũ/mới và metadata của lớp. Audit ghi địa chỉ từ request và User-Agent khi có. Bản lưu kết quả hiện hành đổi cùng giao dịch ghi lịch sử và thông báo.
- Thay đổi điểm nguồn ghi yêu cầu refresh cùng transaction. Scheduler chạy fixed-delay `app.gradebook.refresh.delay-ms=2000` với `app.gradebook.refresh.batch-size=100`. Khóa theo lớp, gradebook, rồi hàng đợi với `SKIP LOCKED`. Queue tồn tại sau restart. Mục tiêu độ trễ là **tối đa khoảng 4 giây** với queue nằm trong một batch, chu kỳ xử lý không quá 2 giây và lớp không bị khóa kéo dài. Backlog, khóa và lỗi cần retry có thể kéo dài thời gian. Profile `test` dùng gọi processor đồng bộ.

Refresh thất bại được lên lịch thử lại sau 5 giây, tăng gấp đôi và tối đa 300 giây. Lần thất bại thứ 8 đặt `failed_at`. Log có trường số `terminalFailureCount` để tổng hợp số chuyển sang FAILED. Gauge số job đang FAILED có thể lấy bằng `SELECT count(*) FROM tbl_gradebook_refresh WHERE failed_at IS NOT NULL`. Cột `last_error` lưu mã lỗi cố định.

Khi một thay đổi nguồn điểm hoặc yêu cầu làm mới snapshot từ `/me` enqueue lại job FAILED, backend xóa `failed_at` và `last_error`, đặt `attempt_count=0` và lên lịch xử lý ngay. Job đang chờ retry giữ nguyên lịch backoff và số lần thử.

- Notification `GRADEBOOK_FINAL_CHANGED` trả `actionUrl` là `/classrooms/{id}/gradebook`, với `id` là UUID của lớp. Frontend cần đăng ký route này để mở sổ điểm từ thông báo. Hệ thống ghi một notification theo Student và revision kết quả khi một Final đã công bố đổi sang điểm Final hợp lệ khác. Điểm mới chưa đủ điều kiện Final và phần trăm Final giữ nguyên không phát loại thông báo này. Notification cấp item giữ theo luồng nguồn.
- **Cần xác nhận** việc gửi khi `showOverall=false`. Theo quyết định hiện tại, thông báo vẫn được gửi khi Final đã công bố thực sự đổi. FR-GRD-009 AC-04 và BR-GRD-007 yêu cầu thông báo cho Student bị ảnh hưởng khi thay đổi trọng số làm đổi Final đã công bố, nhưng chưa quy định ngoại lệ theo visibility. `/me` tiếp tục ẩn Overall/Final. Tiêu đề, nội dung và metadata thông báo không chứa điểm cũ, điểm mới, phần trăm hoặc chênh lệch.
- **Cần xác nhận** phạm vi override/rejudge. Hai thao tác này cũng có thể phát thông báo nếu Final đã công bố thực sự đổi, nhằm báo cho Student khi kết quả đã công bố được cập nhật. Đây là phần mở rộng ngoài AC-04 và BR-GRD-007, vốn quy định luồng thay đổi trọng số.
- Export dùng snapshot giao dịch và revision của dashboard Teacher, gồm điểm chưa công bố và cột publication từng ô. Giới hạn mặc định `app.gradebook.export.max-students=10000` và `app.gradebook.export.max-items=200`. CSV bảo vệ ô văn bản mở đầu bằng `=`, `+`, `-`, `@` hoặc ký tự điều khiển. XLSX ghi điểm kiểu số. Audit ghi scope, định dạng, revision và kích thước, còn lịch sử điểm giữ nguyên.
- Apache Commons CSV **1.14.1** ghi RFC 4180, Apache POI **5.5.1** dùng SXSSF với cửa sổ hàng và tệp tạm. Các phiên bản tương thích Java 21. POI 5.5.x cung cấp API cần dùng và còn được duy trì cho lỗi nghiêm trọng. [Commons CSV](https://commons.apache.org/proper/commons-csv/changes.html) · [Apache POI](https://poi.apache.org/versioning.html)

## Mã lỗi

| HTTP | Mã hoặc điều kiện |
|---|---|
| 400 | `VALIDATION_FAILED`, body hoặc query sai, scope và key không hợp lệ |
| 401 | `UNAUTHORIZED_ACCESS` |
| 403 | `FORBIDDEN_OPERATION`, gồm sai vai trò, sai owner hoặc tham chiếu ngoài lớp |
| 404 | `GRADEBOOK_NOT_FOUND` hoặc lớp không tồn tại |
| 409 | `GRADEBOOK_VERSION_CONFLICT`, `GRADEBOOK_IDEMPOTENCY_CONFLICT` |
| 422 | `GRADEBOOK_INVALID_WEIGHTS`, `GRADEBOOK_ITEM_HIDDEN`, `GRADEBOOK_ITEM_UNGRADED`, `GRADEBOOK_PUBLICATION_NOT_READY`, `GRADEBOOK_EXPORT_LIMIT_EXCEEDED` |

Trước khi merge, đối chiếu `V202611260002` và `V202611260003` với `main`, đồng thời giữ hai bản migration `db/migration` và `db/main-auth` giống nhau.
