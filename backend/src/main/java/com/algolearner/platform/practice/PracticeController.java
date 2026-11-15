package com.algolearner.platform.practice;

import com.algolearner.platform.common.dto.ApiResponse;
import com.algolearner.platform.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static com.algolearner.platform.practice.PracticeDtos.*;

@RestController
@Profile("!mock | (mock & integration)")
@RequestMapping("/api/v1/classrooms/{classroomId}/practices")
@Tag(name = "Classroom Practice", description = "SRS 4.8 in-class code practice: teacher management and student RUN/SUBMIT")
@SecurityRequirement(name = "bearerAuth")
public class PracticeController {
    private final PracticeService service;

    public PracticeController(PracticeService service) { this.service = service; }

    // ---- teacher
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a practice (owner Teacher only, starts as DRAFT)")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the classroom owner")})
    public ApiResponse<PracticeDetail> create(Authentication auth, @PathVariable UUID classroomId, @Valid @RequestBody CreatePractice input) {
        return ApiResponse.success(service.create(actor(auth), classroomId, input));
    }

    @PatchMapping("/{practiceId}")
    @Operation(summary = "Update practice configuration (owner Teacher only)")
    public ApiResponse<PracticeDetail> update(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                              @Valid @RequestBody UpdatePractice input) {
        return ApiResponse.success(service.update(actor(auth), classroomId, practiceId, input));
    }

    @PostMapping("/{practiceId}/status")
    @Operation(summary = "Change status: DRAFT -> PUBLISHED -> ARCHIVED (owner Teacher only)")
    public ApiResponse<PracticeDetail> changeStatus(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                                    @Valid @RequestBody ChangeStatus input) {
        return ApiResponse.success(service.changeStatus(actor(auth), classroomId, practiceId, input));
    }

    @DeleteMapping("/{practiceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a DRAFT practice (owner Teacher only)")
    public void delete(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId) {
        service.delete(actor(auth), classroomId, practiceId);
    }

    @GetMapping("/{practiceId}/testcases")
    @Operation(summary = "List all testcases including hidden ones (owner Teacher only)")
    public ApiResponse<List<TeacherTestcase>> testcases(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId) {
        return ApiResponse.success(service.listTestcases(actor(auth), classroomId, practiceId));
    }

    @PostMapping("/{practiceId}/testcases") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a testcase. If submissions already used the current testcase version, a new version is created. "
            + "rejudge=true flags older-version submissions PENDING_REJUDGE.")
    public ApiResponse<TeacherTestcase> addTestcase(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                                    @RequestParam(defaultValue = "false") boolean rejudge,
                                                    @Valid @RequestBody TestcaseInput input) {
        return ApiResponse.success(service.addTestcase(actor(auth), classroomId, practiceId, input, rejudge));
    }

    @PutMapping("/{practiceId}/testcases/{testcaseId}")
    @Operation(summary = "Edit a testcase of the current version (versioned like add)")
    public ApiResponse<TeacherTestcase> updateTestcase(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                                       @PathVariable UUID testcaseId, @RequestParam(defaultValue = "false") boolean rejudge,
                                                       @Valid @RequestBody TestcaseInput input) {
        return ApiResponse.success(service.updateTestcase(actor(auth), classroomId, practiceId, testcaseId, input, rejudge));
    }

    @DeleteMapping("/{practiceId}/testcases/{testcaseId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a testcase from the current version (versioned like add)")
    public void deleteTestcase(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId, @PathVariable UUID testcaseId,
                               @RequestParam(defaultValue = "false") boolean rejudge) {
        service.deleteTestcase(actor(auth), classroomId, practiceId, testcaseId, rejudge);
    }

    @GetMapping("/{practiceId}/progress")
    @Operation(summary = "Completion status of every active student (owner Teacher only)")
    public PageResponse<ProgressView> progress(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                               @PageableDefault(size = 20, sort = "joinedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.progress(actor(auth), classroomId, practiceId, pageable);
    }

    @GetMapping("/{practiceId}/submissions")
    @Operation(summary = "Submission history of the whole class, optionally for one student (owner Teacher only)")
    public PageResponse<SubmissionView> classSubmissions(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                                         @RequestParam(required = false) UUID studentId,
                                                         @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.classSubmissions(actor(auth), classroomId, practiceId, studentId, pageable);
    }

    // ---- shared reads
    @GetMapping
    @Operation(summary = "List practices: all for the owner Teacher, PUBLISHED only for active students")
    public PageResponse<PracticeSummary> list(Authentication auth, @PathVariable UUID classroomId,
                                              @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(actor(auth), classroomId, pageable);
    }

    @GetMapping("/{practiceId}")
    @Operation(summary = "Practice detail with sample testcases only; hidden testcases are never returned")
    public ApiResponse<PracticeDetail> get(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId) {
        return ApiResponse.success(service.get(actor(auth), classroomId, practiceId));
    }

    // ---- student
    @PostMapping("/{practiceId}/run")
    @Operation(summary = "RUN on sample testcases or a custom input (max 64 KB): not persisted, no completion change, 5 per minute per user")
    @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Execution feedback"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Code judge unavailable")})
    public ApiResponse<RunResponse> run(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                        @Valid @RequestBody RunInput input) {
        return ApiResponse.success(service.run(actor(auth), classroomId, practiceId, input));
    }

    @PostMapping("/{practiceId}/submit") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "SUBMIT: judged on all testcases of the current version, stored, completes the practice on the first ACCEPTED. "
            + "Unlimited attempts, no deadline. A judge outage returns 503 and stores nothing.")
    public ApiResponse<SubmissionView> submit(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                              @Valid @RequestBody CodeRequest input) {
        return ApiResponse.success(service.submit(actor(auth), classroomId, practiceId, input));
    }

    @GetMapping("/{practiceId}/submissions/me")
    @Operation(summary = "The caller's own submission history")
    public PageResponse<SubmissionView> mySubmissions(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                                      @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.mySubmissions(actor(auth), classroomId, practiceId, pageable);
    }

    @GetMapping("/{practiceId}/submissions/{submissionId}")
    @Operation(summary = "One submission: Teacher sees any, a student only their own (403 otherwise)")
    public ApiResponse<SubmissionView> submission(Authentication auth, @PathVariable UUID classroomId, @PathVariable UUID practiceId,
                                                  @PathVariable UUID submissionId) {
        return ApiResponse.success(service.submission(actor(auth), classroomId, practiceId, submissionId));
    }

    private UUID actor(Authentication authentication) { return UUID.fromString(authentication.getName()); }
}
