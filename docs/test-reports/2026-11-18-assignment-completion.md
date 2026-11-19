# Assignment T05–T08: bằng chứng hoàn thiện 2026-11-18

Phạm vi: Assignment 4.6 và các dependency thật cho Lesson/Judge; không kết luận toàn bộ 53 FR.

## Hành vi đã sửa và kiểm chứng

- Xóa câu hỏi là soft-retire: giữ entity/options/FK và đáp án lịch sử; chỉ authoring/revision mới loại câu hỏi. Published v1 và attempt đã start tiếp tục dùng snapshot cũ để autosave/chấm điểm; v2 loại câu hỏi đã retire.
- Khóa Judge canonical giữ `ownerId` + `attemptId:questionId` qua các lần rejudge; `generation` riêng. Regression kiểm request của producer; JdbcJudgeGateway do root sở hữu.
- Code result được apply atomic với `applied_at`, điểm và evaluation; polling chỉ chọn latest ready generation chưa apply, bỏ queued/running/infra. Attempt còn Essay chờ chấm không chặn các kết quả mới.
- Mỗi code generation tạo evaluation bất biến kể cả điểm bằng nhau; parts giữ job ID, generation và verdict, không giữ hidden input/output.
- Notification rejudge chỉ phát khi công bố kết quả chính thức có điểm/verdict thay đổi. Không phát khi chưa release, kết quả không đổi hoặc lặp release.
- Teacher mutations kiểm trạng thái lớp/Owner sau Class READ lock; archive giữ read access cho submissions/evaluation history.

## Quyền Admin được chốt

SRS FR-ASG-016 AC-06/AC-11 và FR-ASG-017 AC-01 nói Admin trong phạm vi quyền nhưng không định nghĩa granular grants. Coordinator đã chấp thuận active fixed `ADMIN` role cấp đúng ba quyền trong namespace quản trị riêng; không thêm generic ACL, không thêm Admin làm Teacher hoặc member.

Base `/api/v1/admin/classrooms/{classId}/assignments/{assignmentId}`:

- `POST /rejudge` với `{reason}`.
- `GET /submissions/{attemptId}/evaluations`.
- `PATCH /questions/{questionId}/code-case-weights` với `{weights, reason}`.

Mutation khóa actor account, kiểm active role/restriction, reason 1–500 ký tự, Class ACTIVE và Owner ACTIVE. Lịch sử cho phép đọc khi archive. Audit giữ actor role thật `ADMIN`; Teacher Owner có endpoint testcase weights cùng suffix trong namespace lớp học. Đổi weights chỉ sửa working question; Owner publish revision mới cho future attempts, không thay snapshot cũ.

## RED/GREEN đã quan sát

Evidence JSON/raw log đặt tại `backend/target/verification/` (Git ignored), có source SHA và source snapshot riêng:

| Evidence | Kết quả | Lỗi được chứng minh |
|---|---|---|
| `assignment-retirement-red.json` | 30 tests, 1 failure | Guard chặn retire sau khi Student start |
| `assignment-retirement-completion.json` | 91 tests PASS | Soft-retire, options/FK/history, old/new revision, canonical key |
| `assignment-final-gaps-red.json` | 9 tests, 3 failures, 0 errors | Notify sớm; stale polling candidates; same-score generation mất evaluation |
| `assignment-final-gaps-green.json` | 33 tests PASS | Ba regression trên đã sửa |
| `assignment-verdict-publication-red.json` | 1 test, 1 failure | Verdict đổi nhưng điểm giữ nguyên không notify |
| `assignment-completion-final-default.json` | 95 tests PASS | Gồm verdict đổi ở câu hỏi 0 điểm |
| `assignment-archive-guard-red.json` | 1 test, 1 failure | Owner tạo Assignment trong lớp archived |
| `assignment-admin-authorization-red.json` | 3 tests, 3 failures, 0 errors | Thiếu các route Admin được SRS yêu cầu |
| `assignment-admin-authorization-green3.json` | 3 tests PASS | Admin/Owner/other Teacher/Student, disabled Admin, forged role, reason/scope/weights, archive, frozen weights |

Các lượt `assignment-admin-authorization-green` và `green2` chưa PASS: sửa fixture history URL, clone Class ACTIVE và managed Classroom @Version; không dùng các lỗi fixture này làm RED nghiệp vụ.

Kiểm tra cuối:

| Evidence | Kết quả | Track/phạm vi |
|---|---|---|
| `assignment-final-default.json` | 99 tests PASS, 0 skipped | migration toàn Assignment |
| `assignment-final-main.json` | 99 tests PASS, 0 skipped | main-auth toàn Assignment |

Hai track cùng source SHA-256 `7782cb5e9bb3c8b6f9168f66b3fc099240808b720b71a09d772507133e21f3f3`.
`git diff --check` trong phạm vi Assignment/README không phát hiện whitespace errors.


## Phạm vi chưa xác minh

- Coordinator chạy full backend hai track trên source freeze; các batch ở đây chỉ chứng minh Assignment và ObjectiveQuestionGrader.
- PostgreSQL localhost dedicated test, không production. Không chứng minh Docker sandbox thật hoặc live external storage/SMTP.
- Migration 180005/180006 có hai bản tương ứng; full new/upgrade/repeat reconciliation do T14 coordinator xác minh.

## Formal AC fixwave bổ sung

Đã đối chiếu 17 FR / 127 AC thật trong SRS và ghi từng criterion, named assertion, code,
evidence, trạng thái và giới hạn tại `2026-11-18-assignment-ac-evidence.json`.
Không dùng tổng số test để suy ra hoàn tất các tiêu chí client/external.

- Direct Code dùng compile budget 10 giây, đi qua JdbcJudgeGateway thật; RUN chỉ sample,
  sau RUN/autosave official count vẫn 0, Submit mới tạo canonical official submission.
- Late được phép không còn bị Deadline khóa start/timer; Late không bị trừ điểm.
  Không cho Late vẫn lấy cutoff sớm hơn Deadline/duration. Không đổi Late policy sau
  elapsed deadline khi có attempt để tránh backdate submittedAt trước startedAt.
- Publish future lưu Scheduled và freeze revision; server scheduler/đường start kích hoạt
  đúng giờ. Student trước start chỉ nhận title/start và identifier/status; configs bị ẩn.
  Query lọc Class/Owner Active trước LIMIT100 để tránh starvation do lịch bị blocked.
- Feedback đủ PASS_FAIL/TESTCASE_STATUS/FULL_DETAILS; giữ alias SUMMARY/DETAILED.
  Teacher policy nằm trong snapshot, current policy chỉ hạ mức disclosure; hidden IO
  cần allowDetails. Own scoped result API chỉ cho official job đã có trong published evaluation.
- Student đọc own official history/detail khi Closed, loại draft/IDOR và áp release policy;
  nội dung cũ dùng attempt snapshot, không dùng authoring hiện tại.
- Clone giữ Start/Deadline và cấu hình (kể cả AFTER_DEADLINE), copy question/testcase độc lập,
  không copy learner history, trạng thái Draft.
- Rejudge nhận ORIGINAL (mặc định) hoặc LATEST_PUBLISHED theo lựa chọn người dùng.
  Selected revision bất biến giữ testcase/case weights/limits; original source/language,
  question maximum/weight và canonical submission giữ nguyên. Working edits chưa publish
  không được dùng. Mỗi generation lưu revision_id, evaluation parts lưu judgeRevisionId;
  pending result không đổi official pointer. Migration 180014 cho cả hai track thêm
  ba feedback levels, Scheduled index và immutable per-generation context.
- LOCK timeout sau một granted attempt bắt đầu đúng timestamp grant vẫn khóa đúng;
  old startedAt trước grant được bỏ qua, startedAt bằng/sau grant vẫn yêu cầu grant mới.

Bằng chứng RED bổ sung thực tế: `assignment-formal-ac-red` (7/7 hành vi lỗi),
`assignment-scheduled-redaction-red`, `assignment-schedule-starvation-red`,
`assignment-clone-settings-red`, `assignment-late-policy-red`, `assignment-rejudge-target-red`,
`assignment-lock-grant-instant-red` (mỗi lượt sau là 1 testcase failure đúng nghiệp vụ).
Hai lượt wave-green đầu lỗi fixture audit TEXT/UUID/target string, không được coi là gate pass.
Final113 phát hiện một expectation Late cũ trái SRS; đã sửa thành Late=true và submittedAt=serverNow.

Kiểm tra cuối fixwave: `assignment-formal-final2-default.json` và
`assignment-formal-final2-main.json`, mỗi track **114 tests PASS, 0 failures/errors/skipped**,
cùng SHA-256 `aa7963c64ed12fa0003ab81af394453466c6fec1335c305acdc60670f5de9234`.
15 testcase acceptance mới bao gồm actual PostgreSQL/JdbcJudge enqueue, trusted worker
result fixture, publication/history/scope/weights và concurrency bên cạnh các bộ đã có.
`git diff --check` trong scope không có whitespace error; migration180014 hai track giống nhau.

Review độc lập tại `2026-11-18-assignment-ac-fix-review.md` không còn P1/P2 trong phạm vi đã đọc.
Không chứng minh Docker thực thi source, live Supabase/ClamAV/SMTP hoặc client UI.
FR-ASG-007 AC-05 vẫn CLIENT_NOT_VERIFIED: chưa có UI warning/confirmation evidence và chưa
có server confirmation handshake khi sửa testcase đã dùng. Những AC thiếu assertion riêng
giữ PARTIAL/CODE_REVIEW_ONLY trong JSON; không tuyên bố toàn bộ 127 AC đã hoàn tất.

## Batch test-only xác minh 6 AC backend còn thiếu assertion

`AssignmentAuthorizationAcceptanceTest` thêm 5 testcase cho đúng 6 criterion:
FR-ASG-006 AC-02, FR-ASG-009 AC-08, FR-ASG-011 AC-06/07, FR-ASG-013 AC-03,
FR-ASG-015 AC-01. Kiểm tra service/HTTP cùng PostgreSQL: Draft không tạo attempt;
Teacher khác không đổi allowance/audit; Student/Teacher khác không đổi hai shuffle flags;
IMMEDIATE tự công bố evaluation/điểm objective; Teacher authoring và frozen revision/attempt
giữ đúng ba Essay format kể cả sau khi working question được sửa.

`assignment-authorization-ac-default2.json` và `assignment-authorization-ac-main2.json`
mỗi track **5/5 PASS**, 0 failure/error/skipped, cùng source SHA-256
`35f461c842c6d2400ab3a79c7e5f7d05c068006f5f2a0e653e66c9bf48a6d399`.
Hai lượt đầu lỗi fixture username dài hơn giới hạn 50 ký tự, đã rút prefix fixture;
không dùng các lỗi SQL22001 đó làm RED nghiệp vụ. Batch chỉ thêm test và evidence,
không thay production code/dependency/migration.

JSON 127 AC chỉ nâng đúng sáu criterion trên sang VERIFIED_LOCAL:
93 VERIFIED_LOCAL, 28 PARTIAL, 1 CODE_REVIEW_ONLY, 5 CLIENT_NOT_VERIFIED.
Các giới hạn UI/external và những criterion khác giữ nguyên; full-suite gate mới do coordinator chạy riêng.
