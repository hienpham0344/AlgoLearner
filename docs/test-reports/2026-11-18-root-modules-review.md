# Review độc lập: Storage, Judge, Course và exception/migration

Ngày: 2026-11-18. Phạm vi: `storage/*`, `judge/*`, `course/*`, `ApiExceptionHandler`, migration/test liên quan. Đối chiếu AGENTS.md, kế hoạch triển khai 2026-11-18, quy chuẩn API/Coding/Security và các mục SRS về file, Judge, Course. Không sửa production, không chạy toàn bộ Maven suite trong lượt review này.

Kết luận sau re-review: cả 5 phát hiện đã được sửa trong source; không còn P1/P2 đã biết trong phạm vi đã đọc. Binding có RED/GREEN PostgreSQL; Judge có RED/GREEN command fixture. Final suite hai track và runtime bên ngoài vẫn do coordinator xác minh riêng.

## Phát hiện có hành động cụ thể

### 🔴 P1 — Mất khóa giữa xác nhận file READY và commit tham chiếu

- Vị trí: `backend/src/main/java/com/algolearner/platform/storage/ManagedFileService.java:58`; class annotation tại dòng 19.
- Trước sửa, `requireReady` kế thừa `NOT_SUPPORTED`, tạm ngưng transaction của Lesson/Assignment. `FileRecordService.requireReady` mở/commit transaction riêng, nhả khóa file trước khi caller lưu tham chiếu.
- Trigger: file READY chưa được gắn, hơn 24 giờ; binder kiểm tra READY rồi chưa commit reference; cleanup lấy được file lock và chuyển DELETING. Nó có thể xóa blob dù binder sau đó ghi reference; FK chỉ bảo vệ row, không bảo vệ blob bên ngoài DB.
- Bằng chứng: `ManagedFileBindingTransactionTest.cleanupCannotClaimFileBetweenReadyCheckAndReferenceCommit`, PostgreSQL thật + Spring proxy, `file-binding-red-2.json`: 1 test, 1 failure, 0 error/skip; cleanup trả `ProcessingTicket` trong khi kết quả đúng phải là null sau commit reference. SHA `bb5ad9d86708981b7bdda400c6a9c582fffbb5bb4c9d2a0a8a99d64970219fed`.
- Sửa được review: method override `@Transactional(REQUIRED)` để file lock tham gia transaction caller, không thêm remote IO vào transaction. `file-binding-green.json`: 8 tests PASS, 0 skip (binding 1 + HTTP 3 + maintenance 4), SHA `12ffccdd47c9f574d4c8da8ea13f7376d31bcb8dee15a44518038245d2d394f6`.
- Đính chính: hai `lockReadable` hiện dùng shared lock nên nhận định tự-deadlock ban đầu đã được rút; phát hiện được xác nhận là race mất file lock.

### 🔴 P1 — Docker RUN thiếu stdin mở cho testcase

- Vị trí: `backend/src/main/java/com/algolearner/platform/judge/DockerSandbox.java:83` (`baseCommand`) và dòng 120 (ghi stdin).
- Trigger: chương trình đọc `input()` hoặc `Scanner` với testcase có input. Lệnh `docker run` thiếu `-i`/`--interactive`, nên việc ghi vào stdin của CLI không giữ stdin của container; chương trình có thể đọc EOF và nhận verdict sai.
- Căn cứ: command thực tế trong source và [Docker CLI: Keep STDIN open](https://docs.docker.com/reference/cli/docker/container/run/#interactive).
- `judge-stdin-red.json`: 1 test, 1 failure, 0 error/skip, SHA `e343975a48835f2305b548e23150ed72d14c04c036d11cdaac927a9d1a4f43df`. Đã review source thêm `--interactive`, không TTY. `judge-stdin-lifecycle-green.json`: 16 tests PASS, 0 skip, SHA `7782cb5e9bb3c8b6f9168f66b3fc099240808b720b71a09d772507133e21f3f3`. Vẫn cần smoke test Java/Python với stdin thật trên worker khi có Docker.

### 🟡 P2 — Link material mới trỏ endpoint không tồn tại

- Vị trí: `backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:203`, đối chiếu `storage/FileController.java:43`.
- Trước sửa, material trả `/api/v1/files/{id}/download`, trong khi controller chỉ cung cấp `/content`; client dùng `fileUrl` sẽ nhận 404.
- Đã review source sửa thành `/content`. Test hiện có trước sửa chỉ kiểm tra loại bỏ URL không tin cậy; coordinator/Lesson owner chịu trách nhiệm verification của nhánh này.

### 🟡 P2 — Lỗi Docker control làm rò rỉ source tạm

- Vị trí: `backend/src/main/java/com/algolearner/platform/judge/DockerSandbox.java:31` và dòng 67.
- Trước sửa, tạo volume diễn ra trước try/finally; volume-create thất bại để lại thư mục/source. Ngoài ra volume-rm ném lỗi sẽ bỏ qua xóa source.
- Đã review patch bao cả ghi source/tạo volume trong try; cleanup volume/source độc lập, giữ primary exception và suppressed cleanup error. Fixture kiểm tra cả create/rm failure và source directory trống.

### 🟡 P2 — Gán MLE cho mọi exit 137

- Vị trí: `backend/src/main/java/com/algolearner/platform/judge/DockerSandbox.java:56` và dòng 155.
- Trước sửa, `os._exit(137)` hoặc self-SIGKILL cũng thành MEMORY_LIMIT_EXCEEDED dù chưa vượt RAM; trái ý nghĩa verdict FR-JDG-004/005.
- Đã review patch giữ container đến khi inspect `.State.OOMKilled`, chỉ dùng bằng chứng OOM để gán MLE; exit 137 không OOM là RUNTIME_ERROR, inspect lỗi trở thành infrastructure failure. Container được xóa ở finally.
- `judge-lifecycle-final.json`: 15 tests PASS, 0 skip, gồm 6 lifecycle/verdict fixture và 9 policy/persistence/privacy tests; không phải bằng chứng container thật.

## Các điểm đã rà soát, chưa thấy P1/P2 bổ sung

- Storage candidate queries lọc eligibility/reference trước LIMIT; claim kiểm tra lại state/lease/reference. Upload metadata PENDING, scan trước READY, recovery kiểm size/checksum, key mới không tái sử dụng. Thay đổi REQUIRED phía trên là điều kiện để binding/cleanup chia sẻ khóa đúng.
- Avatar cleanup dùng queue bền vững, lease token, retry có giới hạn và FAILED; active-key guard phù hợp với key UUID ngẫu nhiên không được API tái gán. Không suy ra an toàn nếu tương lai cho phép tái sử dụng key.
- Supabase adapter xác minh bucket private, không upsert, không trả provider detail/credential; Clamd fail-closed. Không dùng provider thật trong review.
- Judge gateway tách RUN/sample/rate limit khỏi official SUBMIT; reuse official submission cho các generation cùng request key, từ chối sửa payload bất biến; scoring projection bỏ IO, public projection lọc feedback.
- Course giới hạn draft author/Admin review, khóa publication transition, chỉ cung cấp own progress, published units không sửa qua API hiện có. Phạm vi tối thiểu cho Dashboard; không kết luận hoàn tất toàn bộ SRS 4.11.
- Exception handler không ghi message/stack trace có thể chứa credential/source. Các migration mới 180001–180013 hiện có ở hai track giống byte; bảng mới liên quan bật RLS/revoke browser roles, file/course FKs có index trực tiếp hoặc prefix của unique index.

## Giới hạn bằng chứng

- Hai lượt đầu của binding test gặp lỗi chuẩn bị DB trước ApplicationContext; không tính là RED nghiệp vụ. Chỉ `file-binding-red-2` dùng PostgreSQL test đúng của đợt triển khai được tính.
- Review test migration kiểm tra new/upgrade/repeat và bảo toàn fixture legacy; không tự chạy lại toàn bộ suite hoặc chứng minh nâng cấp staging thật. Coordinator chạy final hai track.
- Chưa xác minh Docker runtime/daemon, Supabase Storage, ClamAV engine hoặc staging thật. Binding/stdin đã GREEN; final quality gate còn suite hai track của coordinator. Báo cáo này không chứng nhận production readiness.
