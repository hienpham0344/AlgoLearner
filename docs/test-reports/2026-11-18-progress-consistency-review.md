# Classroom / Announcement / Lesson — đối chiếu AC và tính nhất quán tiến độ

Phạm vi: FR-CLS-001..010, FR-ANN-001..004, FR-LES-001..005. Bản đồ riêng tại `2026-11-18-classroom-lesson-ac-evidence.json`; không ghi đè master traceability hoặc công việc của agent khác.

## Cách xác minh

- Đã so sánh trực tiếp đủ 150 chuỗi AC và `sourceParagraph` với `word/document.xml` trong SRS ver 1.1.docx; đủ 19 FR, không thiếu hoặc trùng AC.
- Gán trạng thái bằng đối chiếu ngữ nghĩa từng AC với assertion. XML chỉ chứng minh invocation đã PASS; tổng số test không chứng minh bao phủ yêu cầu.
- `VERIFIED_LOCAL` là bằng chứng cục bộ, có thể là Mockito hoặc PostgreSQL/MockMvc tùy test được dẫn. `PARTIAL` giữ nguyên phần còn thiếu; `CODE_REVIEW_ONLY` có đường thực thi nhưng chưa có assertion trực tiếp. Không biến thiếu test thành kết luận thiếu triển khai.
- Hai full snapshot `backend-full-default-ye0d2j98` và `backend-full-main-ae1egmsr`: source `57a01398a1c17c3222ae2a030ba70bb2e1834ec3fc47039d39227241adc63ecd`, mỗi track 439 declared / 436 PASS / 3 opt-in skipped. Đây là mốc trung gian, không phải kết quả full sau các wave sửa lỗi cuối.
- Tại checkpoint 150 AC, 92 method được chọn đều có XML PASS; source của các test này và toàn bộ production/test Classroom/Announcement/Lesson trùng byte với full snapshot trên. Assertion cụ thể nằm trong `testEvidence` của JSON.

## Các nhánh nhất quán đã được đối chiếu

`LessonSafetyTest#publishedGradeAndRejudgeRecomputeCompletionWithoutLosingHistory` xác nhận điểm chưa công bố không hoàn thành Lesson; điểm đã công bố đủ ngưỡng thì hoàn thành, rejudge xuống dưới ngưỡng cập nhật lại false và vẫn giữ history. `anyRuleDiffersFromAllAndRuleRevisionsPreserveHistory` xác nhận ALL/ANY, revision idempotency và chặn unlink item đang được rule tham chiếu. Quiz release được nối vào completion trong `QuizIntegrationTest#frozenAutosaveManualReleaseAndOfficialHighestScore`.

Class Practice progress, Public Problem statistics và Dashboard cùng chọn evaluation hợp lệ mới nhất. Rejudge `QUEUED`, `RUNNING`, `INFRASTRUCTURE_ERROR` giữ kết quả official hợp lệ trước đó; verdict chấm cuối như `WRONG_ANSWER` thay thế kết quả. Bằng chứng: `ProblemPracticePersistenceTest#contributionsRolesSnapshotsAndCanonicalJobsWorkWithoutExecutingLearnerSource` và `DashboardPersistenceTest#unfinishedOrFailedRejudgeKeepsPreviousOfficialPublicPracticeResult`.

`LessonLinkedContentSafetyTest` xác nhận: standalone item dùng được; linked Quiz bị ẩn/chặn khi Lesson Archive; Practice vẫn dùng được nếu còn một Lesson Published khác; restore dùng lại attempt/history cũ. Cả submit trực tiếp, timeout worker và Teacher đọc attempt quá hạn đều không tạo official Quiz submission xuyên qua Lesson Archived. Race `committedArchiveWinsOverConcurrentQuizStart` assert Class lock chặn start và sau archive commit không có attempt mới.

Lesson state/link mutations lấy Class `PESSIMISTIC_WRITE` trước child lock; learner write lấy `PESSIMISTIC_READ` trước child. Không nâng read lock sang write khi callback cập nhật completion. Material response dùng `/api/v1/files/{id}/content`, metadata lấy từ managed file READY thay vì URL/tên/kích thước do client khai báo. File binding `requireReady` giữ transaction REQUIRED đến khi reference commit; test riêng `ManagedFileBindingTransactionTest` kiểm tra cleanup không claim file giữa READY check và bind.

Hai snapshot `progress-linked-final-default/main`, cùng source `57a01398`, mỗi track 45 PASS / 0 skipped. Kết quả này có giới hạn cục bộ; không chứng minh Docker judge thật, storage provider thật hoặc ClamAV thật.

## Những điểm không được kết luận thành backend GAP

- BR-ANN-003 (SRS paragraph 5151) yêu cầu Announcement **ghim gần nhất** trước, phù hợp `pinnedAt DESC`; không phải `createdAt` trước.
- An toàn XSS của nội dung Text/Markdown lúc hiển thị cần kiểm tra renderer phía client theo Security Rules. Hai AC hiển thị XSS được giữ `CLIENT_NOT_VERIFIED`; không tự gán lỗi backend chỉ vì backend lưu văn bản.
- `CompletionMode.NONE` là extension legacy ngoài hai mode SRS mô tả. AC yêu cầu có Manual/Condition-based; chưa có bằng chứng extension này làm hai mode bắt buộc sai nên chưa ghi GAP.
- Chưa tìm thấy lỗi backend mới có bằng chứng trong 150 AC. Các khoảng trống dưới đây là assertion còn thiếu, không phải tuyên bố tính năng chưa tồn tại.

## Kiểm tra bổ sung tập trung

`ClassroomAcceptanceSafetyTest.java` mới, không sửa production hoặc test cũ: exact/past code expiry ở hai Join Mode không mutate membership/request; sai role/trạng thái không được join; sai người nhận không accept/decline invitation; nhiều pin theo lần ghim mới nhất; Student/Teacher SUSPENDED hoặc DEACTIVATED rồi ACTIVE giữ membership/progress/history và áp đúng read/write access.

Nhóm mới đã chạy **13 PASS / 0 skipped** trên mỗi track qua wrapper `backend/target/verification/run_focused.py`:

- Default: `classroom-acceptance-default-2de831a1`, source `ca01bca8a7f09a42f9efa37a9e6fd0d89b196955b73b33c25e64bc019c242ff2`.
- Main-auth: `classroom-acceptance-main-2n1senp6`, source `8bc2aa4035ce84bec621b11257ae68550817f74c0b698389932f689f330d0f07`.

Hai source hash khác nhau do wave độc lập đang sửa backend; riêng test mới và các assertion được dẫn trùng byte ở các snapshot tương ứng. Lần chạy ban đầu có một lỗi trong SQL assertion tên cột notification, đã sửa theo `classroom_invitation_id`; hai kết quả trên là lần chạy sau sửa. Không sửa production để làm test pass.

Bản đồ sau nhóm mới: **77 VERIFIED_LOCAL, 49 PARTIAL, 22 CODE_REVIEW_ONLY, 2 CLIENT_NOT_VERIFIED**; tổng **150 AC / 19 FR**, không còn pending. Catalog có 97 method reference, mỗi reference có source line/assertion và XML PASS được quan sát. Các AC đã nâng gồm Class Code expiry, invalid role/status join, invitation recipient, nhiều pin/order, retained Student membership/progress/submission và Teacher inactive read-old/block-submit. Root vẫn cần full verify source cuối.

## Nhóm authorization cuối cho 21 AC còn chỉ đọc code

Thêm riêng `ClassroomAuthorizationAcceptanceTest.java` và `AnnouncementLessonAuthorizationAcceptanceTest.java`, không sửa test cũ hoặc production. Nhóm có 40 invocation: 15 Classroom và 25 Announcement/Lesson. Các request quyền có role, body và resource state hợp lệ; lỗi 403 phải đến từ guard, không nhờ validation payload sai. Mỗi nhánh từ chối đều so sánh persisted row hoặc membership/progress snapshot trước–sau.

Các case bổ sung kiểm trực tiếp: invalid Class create/update, DB chỉ nhận ACTIVE/ARCHIVED, owner hard delete Class rỗng, unauthorized Class archive/delete, nullable/future code-expiry round trip, đóng tham gia giữ membership/read access, unauthorized approval/remove-member, invitation email chưa đăng ký hoặc không phải Student, route bình luận Announcement không tồn tại, upload/metadata attachment bị từ chối, future publishing bị từ chối, pin/unpin trái phép không đổi trạng thái, non-owner Lesson create/update/progress và toàn bộ state/archive/restore/delete guard.

Default run `classroom-authorization-default-73r6aoiz`, source `64b9235c140f39188b1ab075c2b0e19236b6aec1a64c5edd1ffa810769c0888d`: **40 PASS / 0 skipped**. Các boundary thật trả 404 comment route, 415 multipart Announcement, 400 unknown attachment/publishAt fields; không có lỗi production mới cần sửa. Một lỗi compile ban đầu do thứ tự multipart builder trong test đã được sửa riêng trong test trước kết quả PASS này.

Main-auth run `classroom-authorization-main-bk94z9uu` có cùng source `64b9235c140f39188b1ab075c2b0e19236b6aec1a64c5edd1ffa810769c0888d`: **40 PASS / 0 skipped**. Đã đọc đủ 40 testcase XML của mỗi track; 17 method mới đều có hai-track PASS và source trùng byte với snapshot. Không có thay đổi production trong batch này.

Bản đồ sau batch này: **98 VERIFIED_LOCAL, 49 PARTIAL, 2 CLIENT_NOT_VERIFIED, 1 NOT_REQUIRED**; đủ 150 AC / 19 FR, không còn `CODE_REVIEW_ONLY`. `FR-LES-003 AC-09` được ghi `NOT_REQUIRED` vì phần trăm tổng thể Class không bắt buộc trong MVP; không thêm test cho tính năng tùy chọn. Catalog có 114 method reference. Root vẫn cần full verify cuối.

## Assertion backend còn thiếu nên cân nhắc theo rủi ro

- Class invalid create/update và non-owner archive/delete giữ nguyên persisted data; snapshot đầy đủ Submission/Grade/Progress sau Class Archive/leave/remove.
- Lesson unpublish/archive/restore giữ nguyên completion của hai Student độc lập; negative non-owner Lesson state/link/progress routes. Có shared owner guard và state code, nhưng chưa đủ assertion trực tiếp cho mọi AC này.
- Lesson managed material download khi Published so với Draft/Archived; storage error giữ nguyên nội dung/material cũ; success fixtures PPTX/ZIP/image. Các validator rejection đã có test riêng.

UI rendering, wording/nút Accept/Decline và các provider ngoài tiến trình vẫn chưa được chứng nhận. Root cần dùng full verify cuối sau các wave tích hợp để chứng nhận source cuối.
