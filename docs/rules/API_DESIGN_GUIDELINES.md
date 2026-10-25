# AlgoLearner - API Design Guidelines

> Single Source of Truth cho toàn bộ thiết kế RESTful API trong hệ thống **AlgoLearner**.  
> Áp dụng nghiêm ngặt cho Spring Boot Backend và React Frontend.

---

## 1. Nguyên tắc Cốt lõi (Core Principles)

1. **RESTful & Resource-Oriented**: Đặt tên URI theo danh từ số nhiều (`/users`, `/classrooms`, `/problems`), không dùng động từ trong URI (ngoại trừ các endpoint hành động đặc thù như `/judge/run` hoặc `/auth/login`).
2. **Contract-First với OpenAPI 3**: Mọi endpoint bắt buộc có tài liệu Swagger/OpenAPI hoàn chỉnh với DTO Request/Response tường minh và mô tả status code.
3. **Format Thời gian Thống nhất**: 
   - Backend & Database lưu trữ chuẩn **UTC**.
   - Toàn bộ chuỗi ngày tháng trong Request/Response body sử dụng định dạng **ISO-8601 UTC**: `YYYY-MM-DDTHH:mm:ss'Z'` (ví dụ: `2026-10-25T14:30:00Z`).
   - Frontend chịu trách nhiệm chuyển đổi sang múi giờ hiển thị của người dùng (mặc định UTC+7 cho Việt Nam theo SRS 2.4).
4. **Không làm lộ Entity ra ngoài**: 100% endpoint giao tiếp qua DTO (Data Transfer Object). Tuyệt đối cấm trả về JPA Entity trực tiếp.

---

## 2. Cấu trúc URI & Phiên bản (URI Versioning)

- **Base URL**: `/api/v1`
- **Quy ước đặt tên**: Sử dụng chữ thường, ngăn cách bằng dấu gạch ngang (kebab-case) nếu nhiều từ, danh từ số nhiều cho collection.

```text
GET    /api/v1/classrooms                         # Danh sách lớp học
POST   /api/v1/classrooms                         # Tạo mới lớp học
GET    /api/v1/classrooms/{classId}               # Chi tiết một lớp học
PUT    /api/v1/classrooms/{classId}               # Cập nhật toàn bộ thông tin lớp học
PATCH  /api/v1/classrooms/{classId}               # Cập nhật một phần thông tin
DELETE /api/v1/classrooms/{classId}               # Xóa hoặc lưu trữ lớp học

# Nested Resources (Quan hệ phụ thuộc)
GET    /api/v1/classrooms/{classId}/assignments   # Danh sách bài tập của lớp
POST   /api/v1/classrooms/{classId}/assignments   # Tạo bài tập trong lớp
GET    /api/v1/classrooms/{classId}/assignments/{assignmentId}

# Hành động đặc thù (Action Endpoints)
POST   /api/v1/auth/register                      # Đăng ký tài khoản
POST   /api/v1/auth/login                         # Đăng nhập
POST   /api/v1/auth/refresh-token                 # Cấp mới access token
POST   /api/v1/auth/verify-otp                    # Xác thực mã OTP qua email
POST   /api/v1/judge/run                          # Chạy thử code không chấm điểm (Sandbox)
POST   /api/v1/judge/submit                       # Nộp bài chính thức vào hàng đợi chấm
```

---

## 3. Quy chuẩn Phương thức HTTP (HTTP Methods)

| Method | Mục đích | Yêu cầu Idempotent? | Status Code thành công thông dụng |
| :--- | :--- | :--- | :--- |
| **GET** | Truy vấn dữ liệu, không làm thay đổi trạng thái hệ thống | Có (Idempotent) | `200 OK` |
| **POST** | Tạo mới tài nguyên, hoặc thực hiện hành động tính toán/xác thực | Không | `201 Created` (tạo mới) hoặc `200 OK` (xử lý) |
| **PUT** | Thay thế/cập nhật toàn bộ tài nguyên hiện có | Có (Idempotent) | `200 OK` |
| **PATCH** | Cập nhật một số trường cụ thể của tài nguyên | Không bắt buộc | `200 OK` |
| **DELETE** | Xóa tài nguyên (hoặc Soft Delete) | Có (Idempotent) | `204 No Content` hoặc `200 OK` |

---

## 4. Chuẩn Hóa Envelope Response (Standard Response Wrapper)

Tất cả các response trả về từ backend (thành công hoặc thất bại) phải tuân theo cấu trúc JSON chuẩn mực.

### 4.1. Response Thành Công Cơ Bản (`ApiResponse<T>`)
```json
{
  "success": true,
  "message": "Classroom created successfully",
  "data": {
    "id": "c1f7b8a2-9345-4a67-8d01-e738927164b1",
    "name": "Data Structures & Algorithms - Fall 2026",
    "classCode": "DSA2026",
    "createdAt": "2026-10-25T08:00:00Z"
  },
  "timestamp": "2026-10-25T08:00:01Z"
}
```

### 4.2. Response Phân Trang (`PageResponse<T>`)
Đối với các endpoint danh sách có phân trang (Pagination & Sorting):

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "p001",
        "title": "Two Sum",
        "difficulty": "EASY",
        "acceptanceRate": 49.5
      }
    ],
    "pagination": {
      "page": 0,
      "size": 20,
      "totalElements": 150,
      "totalPages": 8,
      "isFirst": true,
      "isLast": false
    }
  },
  "timestamp": "2026-10-25T08:00:01Z"
}
```

* **Query Parameters chuẩn**:
  - `page`: Số trang (0-indexed, mặc định `0`).
  - `size`: Số phần tử trên mỗi trang (mặc định `20`, tối đa `100`).
  - `sort`: Tiêu chí sắp xếp, ví dụ: `sort=createdAt,desc` hoặc `sort=difficulty,asc`.

### 4.3. Response Lỗi Chuẩn Hóa (`ApiErrorResponse`)
Khi có lỗi (4xx, 5xx), backend trả về định dạng nhất quán giúp frontend bắt lỗi và hiển thị thân thiện:

```json
{
  "success": false,
  "errorCode": "VALIDATION_FAILED",
  "message": "Invalid request parameters",
  "errors": [
    {
      "field": "email",
      "rejectedValue": "invalid-email",
      "message": "Email must be in a valid format"
    },
    {
      "field": "password",
      "rejectedValue": null,
      "message": "Password must contain at least 8 characters including uppercase, lowercase, and numbers"
    }
  ],
  "timestamp": "2026-10-25T08:00:01Z",
  "path": "/api/v1/auth/register"
}
```

---

## 5. Bảng Mã Lỗi HTTP & Error Codes

| HTTP Status | Trường hợp sử dụng | ErrorCode mẫu |
| :--- | :--- | :--- |
| **`200 OK`** | Yêu cầu GET, PUT, PATCH thành công | - |
| **`201 Created`** | Tạo mới tài nguyên thành công (POST) | - |
| **`204 No Content`** | Xóa thành công, không có nội dung trả về | - |
| **`400 Bad Request`** | Dữ liệu gửi lên sai định dạng, vi phạm validation | `VALIDATION_FAILED`, `INVALID_REQUEST_BODY` |
| **`401 Unauthorized`** | Thiếu hoặc JWT Token hết hạn/không hợp lệ | `TOKEN_EXPIRED`, `UNAUTHORIZED_ACCESS` |
| **`403 Forbidden`** | Đã đăng nhập nhưng không đủ quyền (VD: Sinh viên sửa bài tập) | `FORBIDDEN_OPERATION`, `NOT_CLASS_OWNER` |
| **`404 Not Found`** | Tài nguyên không tồn tại | `CLASS_NOT_FOUND`, `USER_NOT_FOUND`, `PROBLEM_NOT_FOUND` |
| **`409 Conflict`** | Xung đột dữ liệu (Email đã đăng ký, mã lớp trùng lặp) | `EMAIL_ALREADY_EXISTS`, `DUPLICATE_CLASS_CODE` |
| **`422 Unprocessable`**| Dữ liệu đúng cú pháp nhưng sai logic nghiệp vụ (Nộp bài sau deadline) | `SUBMISSION_AFTER_DEADLINE`, `EXCEEDED_ATTEMPTS` |
| **`429 Too Many Requests`** | Vượt quá giới hạn gọi API (Rate limit cho OTP hoặc Judge RUN) | `RATE_LIMIT_EXCEEDED` |
| **`500 Internal Error`** | Lỗi server không mong muốn (Sandbox sập, DB timeout) | `INTERNAL_SERVER_ERROR`, `SANDBOX_ERROR` |

---

## 6. Xác Thực & Headers (Headers & Security)

### 6.1. Request Headers
Mọi request xác thực phải gửi kèm:
```http
Authorization: Bearer <access_token>
Content-Type: application/json
Accept: application/json
X-Request-Id: <uuidv4>  # Tùy chọn: Dùng để tracing request qua log
```

### 6.2. Cơ chế Access Token & Refresh Token (SRS 4.1)
- `Access Token`: Thời hạn ngắn (15-30 phút), chứa `userId`, `email`, `roles`.
- `Refresh Token`: Thời hạn dài (7-30 ngày), lưu an toàn trong database / HttpOnly Cookie để thu hồi khi cần (`REVOKED`).
- Khi Access Token hết hạn, frontend tự động gọi `POST /api/v1/auth/refresh-token` thông qua Axios Interceptor trước khi retry request ban đầu.

---

## 7. Quy định Tài liệu Hóa với OpenAPI / Swagger

Tất cả các Controller trong Spring Boot bắt buộc phải khai báo các annotation của thư viện `springdoc-openapi`:

```java
@Tag(name = "Classroom Management", description = "Endpoints phục vụ tạo, cập nhật và quản lý lớp học")
@RestController
@RequestMapping("/api/v1/classrooms")
public class ClassroomController {

    @Operation(summary = "Tạo lớp học mới", description = "Chỉ dành cho tài khoản có quyền TEACHER")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tạo lớp học thành công", 
            content = @Content(schema = @Schema(implementation = ClassroomDetailResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dữ liệu nhập không hợp lệ"),
        @ApiResponse(responseCode = "403", description = "Không có quyền Teacher")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ClassroomDetailResponse>> createClass(
            @Valid @RequestBody CreateClassroomRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ...
    }
}
```
