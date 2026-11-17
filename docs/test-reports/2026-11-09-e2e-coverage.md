# Kiểm thử frontend/backend ngày 09-11-2026 (cập nhật 10-11)

Phạm vi: mã tại `main` commit `af05458`, cộng các thay đổi frontend 4.3 chưa commit trong worktree. `outputs/` không bị chỉnh sửa.

## Kết quả thực chạy

- Frontend `npm run build`: thành công; Vite cảnh báo bundle JS 551,25 kB vượt ngưỡng 500 kB.
- Frontend `npm test`: 6 file test qua. Chạy từng file bằng `node` cho thấy 18 test case qua; Node 22 `node --test tests/*.test.mjs` chỉ hiển thị tổng theo file.
- Playwright trên Google Chrome 156: 13/13 kịch bản qua, gồm 12 kịch bản prototype/API giả lập và 1 kịch bản nối Spring Boot thật. Đã kiểm tra chuyển tài khoản Teacher trong cùng SPA không dùng lại hồ sơ cũ và HTTP 401 kết thúc phiên React; cả hai lỗi đều được tái hiện trước khi sửa.
- Playwright V8 function coverage trong lần chạy cuối: 636/1209 hàm, 52,61% trên 78 module `frontend/src` được nạp trong 12 kịch bản có thu coverage. Kịch bản Spring Boot thật không thu V8 coverage. Đây không phải line/branch coverage và không chứng minh mọi nghiệp vụ đã được kiểm tra.
- Backend `mvn clean verify` bằng Temurin 21.0.13 và Maven 3.9.9 cài cục bộ trong `/tmp`: BUILD SUCCESS; 107 test được phát hiện, 106 qua, 1 `SupabaseConnectionTest` bỏ qua.
- JaCoCo 0.8.13 chạy trên backend test: toàn backend 700/760 dòng (92,11%), 422/537 nhánh (78,58%); package `classroom` 175/200 dòng (87,50%), 78/120 nhánh (65,00%).

## Kiểm tra API thật

`mock,dev` mặc định không khởi động được khi có 4.3: `application-mock.yml` loại DataSource/JPA nên thiếu `ClassroomRepository`. Đã thêm script `backend/scripts/run-local-integration.sh` dùng profile `mock,dev,integration`, H2 test classpath, JPA auditing và seed tài khoản test. Với server này, `curl` nhận HTTP 200 cho `/api/v1/users/me/profile` và `/api/v1/classrooms`. Playwright **không giả lập API** đã gọi thật để Teacher đọc/sửa hồ sơ, tạo lớp (201), Student đọc hồ sơ và tham gia lớp bằng class code (200); Student không thấy class code của Teacher.

Giới hạn: profile/dashboard 4.2 vẫn dựa trên `MockData` in-memory, trong khi lớp 4.3 dùng H2 JPA. Vì vậy luồng cục bộ xác nhận request/response và thao tác UI nối thành công, nhưng chưa xác nhận đồng bộ dữ liệu dashboard với lớp mới tạo. Chưa chạy PostgreSQL/Flyway/JWT production; việc nối trong môi trường production **chưa được xác minh**. Backend 4.1 chưa hoàn thành cũng là điều kiện phụ thuộc cho đăng nhập production.

Frontend `classroom-api.test.mjs` đã đối chiếu method/path của cả 20 phương thức API client với `ClassroomController`, nhưng sử dụng Axios adapter giả lập. Backend `ClassroomApiTest` hiện gọi 17/20 endpoint qua MockMvc; kịch bản HTTP thật mới bao phủ tạo lớp và tham gia lớp. Các thao tác 4.3 khác chưa có E2E với backend thật.

## Cách chạy lại

```bash
cd frontend
npm test
npm run test:e2e:coverage
npm run build

cd ../backend
JAVA_HOME=/path/to/jdk21 /path/to/mvn clean verify
JAVA_HOME=/path/to/jdk21 /path/to/mvn org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report

# Terminal riêng: chạy backend H2 cục bộ, chỉ dùng tài khoản test
MVN_BIN=/path/to/mvn ./scripts/run-local-integration.sh

# Khi backend H2 đã mở cổng 18080
cd ../frontend
ALGOLEARN_LIVE_BACKEND=1 ALGOLEARN_API_TARGET=http://127.0.0.1:18080 npm run test:e2e:coverage
```

Playwright cần Google Chrome tại `/usr/bin/google-chrome`, hoặc đặt `PLAYWRIGHT_CHROME_PATH`. Báo cáo JaCoCo được tạo tại `backend/target/site/jacoco/` và không commit.
