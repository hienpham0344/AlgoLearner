# AlgoLearner - Coding Standards & Engineering Conventions

> Bộ quy chuẩn viết code, tổ chức mã nguồn và quy ước phát triển cho dự án **AlgoLearner**.  
> Tất cả thành viên và AI Assistant phải tuân thủ nghiêm ngặt nhằm duy trì chất lượng mã nguồn cao nhất.

---

## PHẦN 1: QUY CHUẨN BACKEND (JAVA & SPRING BOOT 3)

### 1. Phân Tầng Kiến Trúc & Trách Nhiệm (Layered Architecture)

Dự án áp dụng mô hình phân lớp kết hợp Domain-Driven Design:
```text
[Client / Frontend]
         │ (HTTP REST / JSON)
         ▼
[Controller Layer]       -> Validate dữ liệu đầu vào (@Valid), mapping HTTP status, ủy quyền cho Service.
         │
         ▼
[Service Layer]          -> Chứa 100% Business Logic, quản lý giao dịch (@Transactional), authorization checks.
         │
         ▼
[Repository Layer]       -> Truy vấn cơ sở dữ liệu (Spring Data JPA), viết query tối ưu (tránh N+1 query).
         │
         ▼
[Database / PostgreSQL]
```

#### Quy tắc cấm kỵ (Strict Don'ts):
- **KHÔNG** đặt logic nghiệp vụ (business logic) hoặc validation phức tạp trong Controller.
- **KHÔNG** gọi Repository trực tiếp từ Controller (phải đi qua Service).
- **KHÔNG** trả về JPA Entity trực tiếp qua Controller. Phải map sang DTO Response.
- **KHÔNG** dùng quan hệ nạp tức thời `FetchType.EAGER`. Luôn luôn dùng `FetchType.LAZY` trên `@OneToMany`, `@ManyToOne`, `@ManyToMany`.

---

### 2. Quy Tắc Thiết Kế JPA Entity & Cơ Sở Dữ Liệu

1. **Khóa chính**: Sử dụng `UUID` cho các bảng người dùng, lớp học, bài toán; hoặc `BIGSERIAL / Long` cho các bảng log, thống kê có tần suất insert cực cao.
2. **Khai báo bảng và cột**: Luôn dùng `@Table(name = "table_name")` và `@Column(name = "column_name")` bằng chữ thường dạng snake_case.
3. **Audit Fields**: Mọi bảng quan trọng bắt buộc kế thừa hoặc khai báo `createdAt` và `updatedAt` tự động thông qua JPA Auditing:
   ```java
   @CreatedDate
   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   @LastModifiedDate
   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt;
   ```
4. **Lombok trên Entity**:
   - **ĐƯỢC DÙNG**: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
   - **TUYỆT ĐỐI CẤM**: `@Data` và `@EqualsAndHashCode` mặc định trên JPA Entity vì sẽ gây ra lỗi lazy-loading exception và vòng lặp vô tận khi gọi `hashCode()` trên quan hệ hai chiều.

```java
// Ví dụ Entity chuẩn mực
@Entity
@Table(name = "tbl_classroom")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomEntity extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "class_code", nullable = false, unique = true, length = 20)
    private String classCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private UserEntity teacher;

    @OneToMany(mappedBy = "classroom", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AssignmentEntity> assignments = new ArrayList<>();
}
```

---

### 3. Quy Tắc Service & Transaction

1. Khai báo `@Transactional(readOnly = true)` ở cấp độ class Service để tối ưu bộ nhớ Hibernate session.
2. Khai báo `@Transactional` cụ thể tại các method thực hiện thao tác ghi (Create, Update, Delete).
3. Sử dụng Constructor Injection (khuyên dùng `@RequiredArgsConstructor` của Lombok) thay vì Field Injection (`@Autowired` trên field).

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;
    private final ClassroomMapper classroomMapper;

    @Override
    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request, UUID teacherId) {
        log.info("Creating new classroom: {} for teacher: {}", request.name(), teacherId);
        
        UserEntity teacher = userRepository.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with ID: " + teacherId));
            
        ClassroomEntity classroom = classroomMapper.toEntity(request);
        classroom.setTeacher(teacher);
        
        ClassroomEntity saved = classroomRepository.save(classroom);
        return classroomMapper.toResponse(saved);
    }
}
```

---

### 4. Xử Lý Ngoại Lệ Tập Trung (Centralized Exception Handling)

Tất cả các exception nghiệp vụ kế thừa từ `AppException`:
- `ResourceNotFoundException` -> HTTP 404
- `BadRequestException` -> HTTP 400
- `UnauthorizedException` -> HTTP 401
- `ForbiddenException` -> HTTP 403
- `ConflictException` -> HTTP 409
- `JudgeExecutionException` -> HTTP 422 hoặc 500

Được xử lý tập trung tại `@RestControllerAdvice`:
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ApiErrorResponse error = ApiErrorResponse.builder()
            .success(false)
            .errorCode("RESOURCE_NOT_FOUND")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ValidationError(f.getField(), f.getRejectedValue(), f.getDefaultMessage()))
            .toList();

        ApiErrorResponse error = ApiErrorResponse.builder()
            .success(false)
            .errorCode("VALIDATION_FAILED")
            .message("Invalid request payload")
            .errors(errors)
            .path(request.getRequestURI())
            .timestamp(Instant.now())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

---

## PHẦN 2: QUY CHUẨN FRONTEND (REACT + TYPESCRIPT)

### 1. Nguyên Tắc Cốt Lõi TypeScript & React
1. **Strict Type-Safety**: 
   - `tsconfig.json` bật `"strict": true`, `"noImplicitAny": true`.
   - **TUYỆT ĐỐI CẤM** dùng kiểu dữ liệu `any`. Dùng `unknown` kèm type guards nếu dữ liệu chưa xác định, hoặc khai báo interface cụ thể.
2. **Functional Components**: 100% sử dụng Functional Components kèm React Hooks. Không sử dụng Class Components.
3. **Props Type Definition**: Mọi component phải định nghĩa type/interface rõ ràng cho Props:
   ```typescript
   interface CodeEditorProps {
     initialValue: string;
     language: 'java' | 'python';
     readOnly?: boolean;
     onChange: (value: string) => void;
     onRun?: () => void;
   }

   export const CodeEditor: React.FC<CodeEditorProps> = ({
     initialValue,
     language,
     readOnly = false,
     onChange,
     onRun
   }) => {
     // Component implementation
   };
   ```

---

### 2. Tổ Chức Thư Mục Feature-Driven (Feature-Sliced Directory)

Chia thư mục theo tính năng người dùng, đặt trong `frontend/src/features/`:
```text
features/
└── practice/
    ├── api/                 # API client calls (getProblems, submitCode, runCode)
    ├── components/          # Components chỉ dùng riêng cho Practice (Editor, Console, TestResult)
    ├── hooks/               # Custom hooks (useCodeRunner, useTimer)
    ├── types/               # Type definitions & DTOs riêng cho Practice
    └── pages/               # Trang hiển thị hoàn chỉnh (ProblemDetailPage.tsx)
```

---

### 3. Server State Management với TanStack Query v5

- Dùng TanStack Query cho tất cả các dữ liệu lấy từ API backend (Caching, Auto-retry, Pagination).
- Sử dụng **Query Key Factory** để quản lý key nhất quán, tránh trùng lặp:

```typescript
// features/practice/api/query-keys.ts
export const practiceKeys = {
  all: ['problems'] as const,
  lists: () => [...practiceKeys.all, 'list'] as const,
  list: (filters: ProblemFilterParams) => [...practiceKeys.lists(), filters] as const,
  details: () => [...practiceKeys.all, 'detail'] as const,
  detail: (problemId: string) => [...practiceKeys.details(), problemId] as const,
};
```

---

### 4. Quy Chuẩn Styling với Tailwind CSS & Giao Diện (Anti-Slop)

1. **Hạn chế dùng màu sắc tùy tiện**: Luôn dùng hệ màu theo Token:
   - Màu chủ đạo: `slate` / `zinc` cho nền và text; `indigo` hoặc `blue` cho hành động chính (Primary Action).
   - Màu trạng thái: `emerald` (Accepted/Success), `rose` (Wrong Answer/Error), `amber` (Time Limit/Pending).
2. **Accessibility (WCAG 2.2 AA)**:
   - Các nút bấm, icon button bắt buộc có `aria-label`.
   - Độ tương phản chữ (contrast ratio) tối thiểu đạt 4.5:1.
   - Hỗ trợ đầy đủ focus state rõ ràng (`focus-visible:ring-2 focus-visible:ring-primary`).
3. **Monaco Code Editor**:
   - Tải bất đồng bộ (Lazy Load) qua `React.lazy()` hoặc `@monaco-editor/react`.
   - Tích hợp phím tắt chuẩn (`Ctrl+Enter` để Run code, `Ctrl+Shift+Enter` để Submit).

---

## PHẦN 3: QUY CHUẨN ĐẶT TÊN (NAMING CONVENTIONS)

| Đối tượng | Quy ước đặt tên | Ví dụ cụ thể |
| :--- | :--- | :--- |
| **Java Class / Interface** | `PascalCase` | `ClassroomService`, `JwtTokenProvider`, `SubmissionEntity` |
| **Java Method / Variable** | `camelCase` | `calculateGrade()`, `maxAttempts`, `isAccepted` |
| **Java Constant** | `UPPER_SNAKE_CASE` | `DEFAULT_TIME_LIMIT_MS`, `MAX_OTP_RETRY_COUNT` |
| **Database Table / Column**| `snake_case` | `tbl_quiz_attempt`, `student_id`, `created_at` |
| **React Component** | `PascalCase.tsx` | `ProblemWorkspace.tsx`, `SubmissionHistory.tsx` |
| **React Hook** | `camelCase.ts` (`use...`) | `useAuth.ts`, `useCountdown.ts`, `useJudgeSocket.ts` |
| **TypeScript Type/Interface**| `PascalCase` | `ProblemDto`, `SubmissionResult`, `UserRole` |
| **API Endpoints** | `kebab-case` danh từ | `/api/v1/classrooms/{id}/quizzes` |

---

## PHẦN 4: QUY ƯỚC GIT VÀ CAM KẾT (GIT CONVENTIONS)

Dự án áp dụng chuẩn **Conventional Commits**:

```text
<type>(<scope>): <mô tả ngắn bằng tiếng Anh hoặc tiếng Việt>

[Optional body: Chi tiết lý do thay đổi]

[Optional footer: Liên kết tới SRS FR hoặc Issue ID]
```

### Các Loại Type Quy Định:
- `feat`: Tính năng mới (ví dụ: `feat(judge): add isolated docker sandbox runner`)
- `fix`: Sửa lỗi (ví dụ: `fix(auth): prevent OTP reuse after successful verification`)
- `refactor`: Tái cấu trúc mã nguồn không thay đổi hành vi bên ngoài
- `test`: Thêm hoặc chỉnh sửa unit test, integration test
- `docs`: Cập nhật tài liệu kỹ thuật, SRS, README
- `style`: Chỉnh sửa định dạng, khoảng trắng, import
- `chore`: Cập nhật cấu hình build, dependencies
