# Test Report sổ điểm lớp học 4.10

Ngày kiểm thử 26/11/2026. Môi trường Windows, Java 21, Maven qua IntelliJ và PostgreSQL 17 tạm. Database dành riêng cho test có hậu tố `_test`. Pool tối đa 4, minimum idle 1, cache Spring test context tối đa 8. Dữ liệu và tệp tạm đặt trong `backend/target`. Các lượt chạy dùng `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD` và đọc kết quả từ Maven Surefire.

## Kết quả theo đợt

Chạy từ `backend`. Lệnh mục tiêu có dạng `mvn -B '-Dspring.test.context.cache.maxSize=8' '-Dtest=<lớp>' test`. Full suite cuối G7 bỏ bộ lọc `-Dtest`.

| Đợt | Phạm vi | Pass | Fail | Error | Skip |
|---|---|---:|---:|---:|---:|
| G1 | Integration riêng và hồi quy Assignment, Quiz | 56 | 0 | 0 | 0 |
| G1 bổ sung | `GradeSourceBatchIntegrationTest`, `AssignmentGradeSourceTest`, `AssignmentCodeJudgeTest`, `AssignmentPersistenceSafetyTest` | 22 | 0 | 0 | 0 |
| G2 | Calculator, scheme, unit/integration truy cập Gradebook | 15 | 0 | 0 | 0 |
| G3 | API, RBAC và DTO Student, cộng nhóm tính điểm và adapter | 22 | 0 | 0 | 0 |
| G4 | Publication, adapter, Gradebook, Assignment, Quiz và Flyway hai track | 87 | 0 | 0 | 0 |
| G5 | Gradebook, Flyway và hồi quy Assignment, Quiz, Notification, Audit, Classroom | 281 | 0 | 0 | 0 |
| G6 | Export qua HTTP và hồi quy Gradebook | 25 | 0 | 0 | 0 |
| G7 mục tiêu | Gradebook, restart, race và migration | 34 | 0 | 0 | 0 |
| G7 mục tiêu cuối | `GradebookAccessIntegrationTest`, gồm hai worker đồng thời và Student vào lớp sau | 14 | 0 | 0 | 0 |
| G7 full suite | Toàn bộ backend, một lượt | 706 | 3 | 3 | 3 |
| G7 OpenAPI | `GradebookAccessIntegrationTest` sau khi bổ sung metadata bảo mật và phản hồi 422 cho scheme preview | 14 | 0 | 0 | 0 |

Lệnh full suite là `mvn -o -B '-Dspring.test.context.cache.maxSize=8' test`. Maven trả exit code 1 do sáu ca baseline trong `DockerSandboxLifecycleTest` trên Windows, gồm ba assertion fail và ba error từ `Files.setPosixFilePermissions`. Không có ca đỏ mới. Ba skip là các ca opt-in `LiveSupabaseFlowTest`, `SupabaseConnectionTest` và `RegistrationBenchmarkTest`. Tổng 715 lấy từ Maven, gồm 11 ca lồng của `CourseRulesTest` xuất hiện trong log console. Xem `backend/target/g7-full-test.log` và các XML trong `backend/target/surefire-reports`.

## Kiểm tra sau review độc lập

Các lượt mục tiêu dùng `mvn -o -B -Dspring.test.context.cache.maxSize=8 -Dtest=… test`, Java 21 và PostgreSQL 17 cách ly, pool tối đa 4 và minimum idle 1. Log nằm trong `backend/target`. Mỗi dòng là một lượt chạy, có thể trùng ca giữa các lượt.

| Mục | Phạm vi | Pass | Fail | Error | Skip | Log |
|---|---|---:|---:|---:|---:|---|
| 1 | Worker, concurrency, scheduler sau restart và Flyway hai track | 20 | 0 | 0 | 0 | `gradebook-fix-01.log` |
| 2 | Đổi scoring policy Assignment qua HTTP và refresh Overall | 1 | 0 | 0 | 0 | `gradebook-fix-02.log` |
| 3 | Ungraded ở Assignment/Quiz và Raw/Weighted | 8 | 0 | 0 | 0 | `gradebook-fix-03.log` |
| 4 | Bảo vệ snapshot khi nguồn bị ẩn và Flyway hai track | 7 | 0 | 0 | 0 | `gradebook-fix-04-final.log` |
| 5 | Audit publish, replay, rollback, race và restart | 3 | 0 | 0 | 0 | `gradebook-fix-05.log` |
| 6 | Nội dung thông báo, người nhận, visibility và tính nguyên tử | 3 | 0 | 0 | 0 | `gradebook-fix-06.log` |
| 7 | HTTP phân quyền, roster, export, dữ liệu public, lịch sử và bộ chọn điểm | 33 | 0 | 0 | 0 | `gradebook-fix-07.log` |

### Lượt full suite duy nhất sau review

Lệnh `mvn -o -B -Dspring.test.context.cache.maxSize=8 test` chạy **728** ca. Kết quả là **697 pass, 4 fail, 24 error, 3 skip**, Maven exit 1. Log `backend/target/gradebook-fix-full.log` ghi 3 fail và 3 error của `DockerSandboxLifecycleTest` trên Windows, 1 fail do fixture audit cũ của `DashboardPersistenceTest` rơi khỏi 10 audit mới nhất, và 21 error do JUnit không dọn được thư mục tạm ngoài workspace. Các lớp Gradebook và migration qua trong lượt này. Điều kiện chỉ có sáu ca Docker đỏ chưa đạt ở lượt full.

Kiểm tra bổ sung có bộ lọc `-Dtest=GradebookAccessIntegrationTest,DashboardPersistenceTest,AvatarMigrationTest,AvatarRemoteStorageTest,AvatarStorageTest,ProfileApiTest,StorageCleanupQueueTest` và `java.io.tmpdir` trong workspace. Fixture Dashboard dùng timestamp từ DB và kiểm tra chính xác ID audit vừa thêm. Bảy lớp chạy **88 pass, 0 fail, 0 error, 0 skip**. Xem `backend/target/gradebook-fix-full-follow-up.log`. Kết quả này xác minh các nhóm bị ảnh hưởng và không thay thế kết quả full suite ở trên.

### Bằng chứng của worker và restart

- **Xử lý lặp sau thành công.** `publishesCurrentWithHiddenFinalAndReplaysSameRequestWithoutChangingVersion` gọi lại processor trên work đã xong và kiểm tra số snapshot. `failedRefreshBacksOffAndDoesNotBlockHealthyWork` cho hai processor tranh cùng thay đổi điểm và kiểm tra không có thông báo trùng.
- **Retry sau lỗi.** `failedRefreshBacksOffAndDoesNotBlockHealthyWork` ép giao dịch ghi lịch sử thất bại, kiểm tra backoff, cho job khỏe hoàn tất, kiểm tra chuyển FAILED và chạy lại sau thao tác đưa job vào hàng đợi.
- **Đọc lại dữ liệu sau restart.** `readsSchemePublicationHistoryAndNotificationOverHttpAfterRestart` mở ứng dụng mới trên cùng schema, kiểm tra scheme, receipt replay, điểm, lịch sử và thông báo qua HTTP. Ca này cũng giữ một job pending trước khi tắt app đầu và chờ scheduler của app sau tự xử lý trước khi đọc điểm đã cập nhật.
## Truy vết SRS

| Test ID dự kiến | Bằng chứng |
|---|---|
| `TC-FR-GRD-001-01` | `GradebookAccessIntegrationTest.teacherSubmissionDetailsIncludeScopedCodeEssayAndQuizAnswers` kiểm tra code, essay và Quiz answers. `studentDtoIsIdenticalAfterChangingHiddenGradeAndTeacherRoutesRejectStudent` kiểm tra dashboard cho owner và ranh giới Student. |
| `TC-FR-GRD-001-02` | `GradebookAccessIntegrationTest.rejectsForeignClassItemAndStudentAndRevokesOldOwnerAfterTransfer` và `GradeSourceBatchIntegrationTest.batchesBothSourcesAndStudentRowsAreStableWhenHiddenGradesChange` kiểm tra sai lớp, sai Student, thay owner và điểm ẩn. |
| `TC-FR-GRD-002-01/02` | `GradebookAccessIntegrationTest.exportsRealCsvAndXlsxWithScopedGradesAndEnforcesLimitsBeforeAttachment` đọc lại tệp CSV/XLSX, kiểm tra dữ liệu, quyền, giới hạn, headers, audit và version. |
| `TC-FR-GRD-003-01/02`, `TC-BR-GRD-001-01` | `GradeCalculatorTest.rawCountsZeroAndSkipsMissingOrZeroMaximumWhileFinalRequiresGrading`, `weightedRenormalizesCurrentAndExcludesLegacyWithoutMaximum`, và `GradebookAccessIntegrationTest.appliesImmutableSchemeAndPreservesItWhenUpdateIsRejected`. |
| `TC-FR-GRD-004-01/02`, `TC-BR-GRD-002-01` | `GradebookSchemeServiceTest.previewDistributesFourDecimalRemainderInStableOrder`, `rejectsStaleVersionAndInvalidWeightsBeforeSavingRevision`, và integration apply scheme. |
| `TC-FR-GRD-005-01/02`, `TC-BR-GRD-003-01` | `GradeCalculatorTest`, `GradeSourceBatchIntegrationTest.distinguishesZeroFromMissingAndUngradedScores`, `GradebookAccessIntegrationTest.studentDtoIsIdenticalAfterChangingHiddenGradeAndTeacherRoutesRejectStudent`. |
| `TC-FR-GRD-006-01/02`, `TC-BR-GRD-004-01` | `GradeCalculatorTest`, `GradebookAccessIntegrationTest.publishesCurrentWithHiddenFinalAndReplaysSameRequestWithoutChangingVersion` và `hiddenLessonItemSuppressesOverallUntilRefreshAndIsExcludedFromNewScheme`. |
| `TC-FR-GRD-007-01/02`, `TC-BR-GRD-005-01` | `GradebookAccessIntegrationTest.publishesCurrentWithHiddenFinalAndReplaysSameRequestWithoutChangingVersion` kiểm tra visibility và nguồn điểm ẩn. |
| `TC-FR-GRD-008-01/02`, `TC-BR-GRD-006-01` | Các ca publish STUDENT/CLASS, retry, stale version, vào lớp sau, và `GradebookAccessIntegrationTest.simultaneousApplyAndPublishOnlyOneCanUseTheSameVersion`. Bổ sung ca roster đổi đồng thời, cùng key khác payload, audit publish/replay và test HTTP sai quyền. |
| `TC-FR-GRD-009-01/02`, `TC-BR-GRD-007-01` | `GradebookAccessIntegrationTest.recalculationUpdatesPublishedGradeHistoryAndNotificationsAtomically` kiểm tra tính lại, preservation của Quiz submission/override, rollback, audit và link. `hiddenOverallNotifiesOnlyStudentWithChangedPublishedFinalWithoutRevealingScores` kiểm tra Final đổi, giữ nguyên, chưa công bố, và thông báo khi Overall đang ẩn. |

### Kiểm thử cắt ngang

- Số query do adapter phát bằng **1** với 1 ô và 40 ô, cho Assignment và Quiz ở cả Teacher và Student. Test `GradeSourceBatchIntegrationTest.bothAudiencesUseOneQueryPerSourceForOneAndFortyCells`.
- Attempt/mẫu số khác nhau và vai trò của override: `GradeSourceBatchIntegrationTest.highestAndLatestChooseDifferentAttemptsAndKeepTheirOwnSnapshotAndOverride`, `batchesBothSourcesAndStudentRowsAreStableWhenHiddenGradesChange`, `AssignmentGradeSourceTest.highestUsesAttemptMaximumAndLatestKeepsTheFirstGradedAttempt` và `AssignmentPersistenceSafetyTest.manualGradeAndOverrideAppendDistinctImmutableEvaluations`.
- Nhiều thế hệ rejudge: `GradeSourceBatchIntegrationTest.pendingAndFailedRejudgeKeepTheLastCompleteEvaluation`, `olderRejudgeJobsFinishingLateDoNotSupersedeTheNewestAppliedGeneration`, `AssignmentCodeJudgeTest.officialRejudgeKeepsCanonicalRequestKeyAndOwnerAcrossGenerations` và `AssignmentPersistenceSafetyTest.sameScoreJudgeGenerationsAppendEvaluationsOnceWithoutRepeatedPolling`.
- Restart HTTP thật: `GradebookRestartIntegrationTest.readsSchemePublicationHistoryAndNotificationOverHttpAfterRestart` dừng app đầu, mở app thứ hai trên cùng schema rồi đọc bằng HTTP loopback. So sánh receipt replay và kiểm tra scheme, Final, history, notification.
- Migration: `BackendMigrationVerificationTest.newAndUpgradedDatabasesValidateAndRepeatWithoutChangingLegacyData` kiểm tra cài mới và nâng cấp trên `migration` cùng `main-auth`. `MainAuthMigrationUpgradeTest` bổ sung nhánh upgrade main-auth.

## Kiểm tra theo review lần cuối

| Mục | Kiểm tra | Pass | Fail | Error | Skip | Log trong `backend/target` |
|---|---|---:|---:|---:|---:|---|
| 1 | Worker thực hiện đủ 8 lần thử. Quiz override qua HTTP hồi phục job FAILED, worker cập nhật Final lên 95%, và enqueue giữ nguyên thời điểm retry của job đang backoff. `/me` cũng hồi phục job snapshot FAILED. | 2 | 0 | 0 | 0 | `gradebook-final-01-utf8.log` |
| 2 | Với `showOverall=false`, chỉ Student có Final đã công bố thay đổi nhận thông báo. Payload không chứa giá trị điểm và `/me` vẫn ẩn Overall. | 1 | 0 | 0 | 0 | `gradebook-final-02.log` |
| 3 | API thông báo trả đúng `actionUrl` của sổ điểm và cập nhật nguyên tử với lịch sử điểm. | 1 | 0 | 0 | 0 | `gradebook-final-03.log` |

Lượt mục tiêu đầu của mục 1 có 2 lỗi khởi tạo context do database WIN1252 không lưu được văn bản tiếng Việt trong migration Flyway. Lượt chạy lại dùng PostgreSQL UTF-8 và ghi kết quả ở bảng trên. SQL trong bài test chỉ đưa thời điểm retry về hiện tại để bỏ thời gian chờ giữa các lần thử. Job FAILED được hồi phục qua HTTP.

### Full suite lần cuối

Chạy đúng một lượt toàn bộ backend bằng `mvn -o -B '-Dspring.test.context.cache.maxSize=8' '-Djava.io.tmpdir=<workspace>/backend/target/gradebook-final-tmp' test`. Dùng Java 21, PostgreSQL 17 với database test UTF-8 riêng, Hikari pool tối đa 4 và minimum idle 1. JVM fork cũng nhận `java.io.tmpdir` trong workspace qua `JAVA_TOOL_OPTIONS`.

Surefire ghi **727 ca, 718 pass, 3 fail, 3 error, 3 skip** trong 6 phút 39 giây. Maven trả exit code 1. Đủ điều kiện review vì cả sáu ca đỏ đều thuộc `DockerSandboxLifecycleTest` và không có ca đỏ khác. `GradebookAccessIntegrationTest` qua 25 ca, `DashboardPersistenceTest` qua 10 ca và `BackendMigrationVerificationTest` qua 4 ca.

- Ba assertion fail gồm `failedVolumeCreationStillRemovesSourceAndAttemptsVolumeCleanup`, `failedVolumeRemovalDoesNotPreventSourceCleanup` và `infrastructureFailurePropagatesWithoutFabricatingLearnerResult`.
- Ba error là ba bộ tham số của `verdictRequiresOomEvidenceRatherThanExitCodeAlone`. Cả sáu ca cùng bắt nguồn từ `UnsupportedOperationException` ở `Files.setPosixFilePermissions` trên Windows. Ba ca assertion nhận ngoại lệ này thay cho lỗi Docker mà ca kiểm thử mong đợi.
- Ba ca opt-in bị bỏ qua gồm `LiveSupabaseFlowTest`, `RegistrationBenchmarkTest` và `SupabaseConnectionTest`.

Xem `backend/target/gradebook-final-full.log` và báo cáo XML của Surefire.

### Phạm vi còn lại

- **Cần xác nhận** việc vẫn gửi `GRADEBOOK_FINAL_CHANGED` khi `showOverall=false`. Quyết định hiện tại giữ thông báo không chứa điểm và `/me` vẫn ẩn Overall/Final. FR-GRD-009 AC-04 và BR-GRD-007 yêu cầu báo cho Student bị ảnh hưởng khi đổi trọng số làm đổi Final đã công bố, nhưng chưa quy định ngoại lệ theo visibility.
- **Cần xác nhận** phạm vi thông báo do override/rejudge. Cùng quy tắc được áp dụng khi hai thao tác này làm đổi Final đã công bố để Student biết kết quả đã cập nhật. Đây là phần mở rộng ngoài AC-04 và BR-GRD-007 về thay đổi trọng số.
- **CHƯA PHỦ — FR-GRD-001 AC-07 (Admin).** Chưa triển khai namespace Gradebook Admin.
- Các ca truy vết theo ID dự kiến trong SRS. `FR-GRD-001 AC-02` có dữ liệu điểm, completion, lượt nộp và nộp trễ được kiểm tra ở adapter. Ca `csvExportAtConfiguredLimitIncludesTheSecondPageAndRejectsOneAdditionalRow` kiểm tra số Student của `/summary` qua hai trang. Chưa kiểm tra riêng các tổng điểm và lượt nộp qua nhiều trang.
- `FR-GRD-002 AC-05` có kiểm tra điểm thực tế trong CSV và XLSX. Ca nhiều batch kiểm tra đúng 101 Student qua hai trang CSV và từ chối hàng thứ 102. Chưa chạy tải ở giới hạn mặc định 10.000. Với `FR-GRD-004 AC-05`, service kiểm tra trọng số khi publish, còn ca tích hợp tập trung vào từ chối scheme sai từ lúc apply.
- Độ trễ scheduler dưới tải chưa có bài đo riêng. Các ca retry và cạnh tranh gọi processor trực tiếp. Ca restart bật scheduler thật.
- Trước khi merge, đối chiếu phiên bản `V202611260002` đến `V202611260005` với `main`. Hai track giữ từng cặp migration giống byte-for-byte.

## File thay đổi theo giai đoạn

Đường dẫn Java tương đối với `backend/src/main/java/com/algolearner/platform` và `backend/src/test/java/com/algolearner/platform`.

| Giai đoạn | File |
|---|---|
| G1 | `assignment/AssignmentGradeSource.java`, `AssignmentGradeSelection.java`, `AssignmentService.java`, `quiz/QuizGradeSource.java`, `grading/GradeSourceResult.java`, `assignment/AssignmentGradeSourceTest.java`, `grading/GradeSourceBatchIntegrationTest.java` |
| G2 | `grading/GradeCalculator.java`, `GradebookDtos.java`, `GradebookRepository.java`, `GradebookSchemeService.java`, `GradeCalculatorTest.java`, `GradebookSchemeServiceTest.java`, `GradebookAccessIntegrationTest.java`, `common/exception/ErrorCode.java` |
| G3 | `grading/GradebookAccess.java`, `GradebookController.java`, `GradebookDtos.java`, `GradebookQueryService.java`, `GradebookReadRepository.java`, `GradebookVisibilityService.java`, `GradebookAccessIntegrationTest.java` |
| G4 | `grading/GradeSourceChanged.java`, `GradebookPublicationRepository.java`, `GradebookPublicationService.java`, `GradebookRefreshRepository.java`, `GradebookRefreshService.java`, `GradebookRefreshWorker.java`, Controller, DTO, Query Service, `AssignmentService.java`, `QuizService.java`, `ErrorCode.java`, integration Gradebook, migration `V202611260002__gradebook_publications.sql` ở hai track |
| G5 | `grading/GradebookHistoryRepository.java`, `GradebookHistoryService.java`, Controller, DTO, Scheme Service, Refresh Service, `audit/AuditService.java`, `notification/NotificationEntity.java`, `NotificationRepository.java`, `NotificationService.java`, test Scheme và Gradebook, migration `V202611260003__gradebook_history_audit_notification.sql` ở hai track |
| G6 | `backend/pom.xml`, `grading/GradebookCsvWriter.java`, `GradebookXlsxWriter.java`, `GradebookExportService.java`, Controller, `ErrorCode.java`, integration Gradebook |
| G7 | `grading/GradebookRestartIntegrationTest.java`, `GradebookAccessIntegrationTest.java`, `GradebookSchemeServiceTest.java`, `support/BackendMigrationVerificationTest.java`, Controller, DTO, Scheme Service, Read Repository, `ErrorCode.java`, contract và Test Report |
