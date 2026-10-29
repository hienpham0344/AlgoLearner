# AlgoLearner Backend - SRS 4.2, mock mode

Implements FR-PRO-001 through FR-PRO-005: five profile APIs and three dashboards.
Requires Java 21+ and Maven 3.9+. No PostgreSQL/Supabase, Docker, database password or JWT secret is required.

## Run

From `backend`:

```powershell
mvn compile
mvn spring-boot:run
```

The default Spring profile is `mock`. You may explicitly set `SPRING_PROFILES_ACTIVE=mock`.
Swagger UI: http://localhost:8080/swagger-ui/index.html. OpenAPI: http://localhost:8080/v3/api-docs.
`.env.example` lists local settings; Spring does not load this file automatically.
Use `./mvn-local.ps1 spring-boot:run` in PowerShell to load `backend/.env` for that invocation.
Existing environment variables take precedence; imported variables are restored afterward.

## Optional Supabase JDBC connection check

Copy `.env.example` to `.env` if missing (do not overwrite an existing file).
Fill `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` with the project's Supabase JDBC settings.
Use `sslmode=require` (or certificate verification) in the URL. Keep the password only in
`DB_PASSWORD`, never in the URL. `.env` is ignored by Git; `.env.example` contains placeholders only.
The loader supports literal `KEY=VALUE` lines, optional surrounding quotes, and full-line comments;
it does not expand variables or support inline comments. Quote values with leading/trailing spaces.

From `backend`, run explicitly:

```powershell
./mvn-local.ps1 '-Dsupabase.check=true' '-Dtest=SupabaseConnectionTest' test
```

With variables already exported (any shell), use:

```text
mvn -Dsupabase.check=true -Dtest=SupabaseConnectionTest test
```

This test uses JDBC directly and executes only `SELECT 1;`, with connection/socket/query timeouts.
It prints `Supabase connection successful` only when the result is 1. Failures report missing
variable names or a sanitized SQLState category, never raw driver errors or credentials.
It requires no schema, Spring context, entities or repositories. Normal builds skip this test;
starting the mock backend never runs it or connects to Supabase.

In Swagger, click **Authorize** and enter `mock-student` without the Bearer prefix.
In Postman, choose Bearer Token and enter the same value, or send:

```http
Authorization: Bearer mock-student
```

| Token | Scenario |
| --- | --- |
| mock-student | Student, public profile, two active classes, practice history |
| mock-student2 | Another Student, private profile, teacher2's class, broken streak |
| mock-empty | Student with no learning data; LEFT memberships are excluded |
| mock-teacher | Teacher owning two active classes and one archived class |
| mock-teacher2 | Another Teacher with a separate class |
| mock-admin | Admin only; cannot access Student/Teacher profile management |
| mock-suspended / mock-deactivated / mock-locked | Authentication denied; suspended/deactivated public profiles hidden |

Mock tokens are development credentials, not signed JWTs. The mock decoder is isolated behind the `mock` profile.
Role and current access status come from AccountRepository, never client-supplied IDs or role claims.

## Call the APIs

All paths below have prefix `/api/v1` and use the existing response envelopes.

| Method | Path | Authentication / body |
| --- | --- | --- |
| GET | /users/me/profile | Student or Teacher |
| PATCH | /users/me/profile | `{"fullName":"Nguyen An","shortBio":"Learning algorithms"}` |
| PUT | /users/me/profile/avatar | multipart/form-data; file field named `file`, actual PNG/JPEG |
| PATCH | /users/me/profile/privacy | `{"isPublic":true}` or false |
| GET | /users/{username}/profile | Public; try student, student2, suspended, deactivated |
| GET | /dashboards/student | mock-student / mock-student2 / mock-empty |
| GET | /dashboards/teacher | mock-teacher / mock-teacher2 |
| GET | /admin/dashboard | mock-admin |

The existing `GET /users/{username}/profile/avatar` supports the avatarUrl returned by profile APIs.
Upload an image first; reading a private image requires the owner's token. This is an auxiliary image route, not a new business feature.

PowerShell examples:

```powershell
$headers = @{ Authorization = 'Bearer mock-student' }
Invoke-RestMethod http://localhost:8080/api/v1/users/me/profile -Headers $headers
Invoke-RestMethod http://localhost:8080/api/v1/users/me/profile -Method Patch -Headers $headers -ContentType application/json -Body '{"fullName":"Nguyen An"}'
Invoke-RestMethod 'http://localhost:8080/api/v1/dashboards/student?previewSize=1&upcomingDays=7' -Headers $headers
```

PATCH preserves omitted fields, clears null/blank text and rejects username/email/role or unknown fields.
Privacy requires a real JSON boolean. Public profiles expose only username/avatarUrl/shortBio/solved/streak.
Missing credentials return 401; Student accessing Admin dashboard returns 403; hidden profiles return 404; invalid fields/queries return 400.

Student/Teacher dashboards accept previewSize=1..20 (default 5) and upcomingDays=1..30 (default 7).
Admin accepts previewSize, from/to UTC instants with `[from,to)` up to 366 days, and interval=day|week|month.
Default charts cover the preceding 30 days; days/months and Monday-start weeks use UTC+7. All response timestamps use UTC Z.

Fixtures cover draft/archived sources, personal deadlines, a newer graded attempt superseding an older pending attempt,
deleted/expired notifications and pending teacher contributions. Counts and charts are derived from source records.
Solved counts distinct public problems with official ACCEPTED submissions. Streak uses actual accepted submission dates in UTC+7,
excluding RUN, class practice and assignments. Initial student results are Solved=2, Current Streak=3, Highest Streak=4, calculated from fixture history.

## Storage boundaries and future integration

Controller -> Service -> ProfileRepository, PracticeRepository, DashboardRepository, AccountRepository interfaces.
MockData supplies shared source fixtures. InMemoryProfileRepository stores immutable profile snapshots using atomic map updates.
InMemoryDashboardRepository filters account/owner scope before sorting and limiting previews.
Services retain validation, role checks, DTO mapping, Solved/Streak calculations and chart bucketing.

Profile writes live in RAM and reset on restart. Avatar files live in `./data/avatars` and are validated and re-encoded.
Upload/repository failures preserve the previous avatar reference; successful updates then remove the old file.
Files survive restart but mock profile links reset; remove disposable avatar files after stopping the app if desired.
There are no database transactions in mock mode.

When the team's schema is ready:

1. Implement the four repository interfaces against the official PostgreSQL/JPA schema, preserving DTOs and Service logic.
2. Preserve visibility, membership/ownership, personal deadlines, latest-attempt selection, official public ACCEPTED filtering and preview ordering.
3. Replace MockAuthenticationConfiguration with a real JwtDecoder validating signature, issuer, audience, expiry and UUID subject; use a real AccountRepository for current restrictions.
4. Configure datasource and real transactions, including profile write locking and avatar commit/rollback cleanup.
5. Read dashboard source data from the respective modules; coordinate schema/migrations with their owners.

spring-data-jpa and jakarta.persistence-api are retained only to compile the team's existing shared BaseAuditableEntity.
The PostgreSQL JDBC driver is available at runtime solely for the opt-in connection check.
No datasource properties, database profile, 4.2 JPA entities/repositories or migrations are added.
The mock profile continues to exclude DataSource and Hibernate auto-configuration.

## Verification

Run `./mvn-local.ps1 clean verify` to build and run the existing unit/API tests.
The Supabase test is skipped unless `-Dsupabase.check=true` is explicitly supplied.
Java 21+ and access to Maven Central (or a populated local Maven cache) are required.

## Refactor file inventory

Modified: pom.xml, .env.example, README.md, application.yml, ApiConfiguration.java,
SecurityConfig.java, AccountAccess.java, ProfileRepository.java, ProfileService.java,
PracticeStatisticsService.java, DashboardRepository.java, DashboardService.java,
and docs/api/API-4.2-user-profile-dashboard.md.

Added: application-mock.yml, AccountRepository.java, MockAuthenticationConfiguration.java,
PracticeRepository.java, profile/mock/MockData.java, profile/mock/InMemoryProfileRepository.java,
and profile/dashboard/InMemoryDashboardRepository.java.

Removed: V202610290001__account_profile_baseline.sql, V202610290002__dashboard_source_baseline.sql,
and the existing 4.2 profile/dashboard/security/schema tests and Mockito test configuration.
Controllers, DTOs, AvatarStorage and shared base classes are preserved. No frontend or other module functionality is changed.
