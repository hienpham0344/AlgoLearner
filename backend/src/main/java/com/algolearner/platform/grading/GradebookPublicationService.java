package com.algolearner.platform.grading;

import com.algolearner.platform.audit.AuditService;
import com.algolearner.platform.audit.AuditService.RequestContext;
import com.algolearner.platform.common.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.grading.GradebookDtos.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@RequiredArgsConstructor
public class GradebookPublicationService {
    record Result(GradePublicationResponse response, boolean replay) { }
    private record PublicationAudit(PublicationScope scope, UUID publicationId, UUID studentId,
                                    UUID schemeRevisionId, int publishedCount, int ineligibleCount) { }
    private final GradebookAccess access;
    private final GradebookSchemeService schemes;
    private final GradebookReadRepository reads;
    private final GradebookQueryService queries;
    private final GradeCalculator calculator;
    private final GradebookPublicationRepository publications;
    private final Clock clock;
    private final AuditService audit;
    private final ObjectMapper json;

    @Transactional
    public Result publish(Authentication principal, UUID classroomId, String key, GradePublicationRequest request) {
        return publish(principal, classroomId, key, request, RequestContext.EMPTY);
    }

    @Transactional
    public Result publish(Authentication principal, UUID classroomId, String key,
                          GradePublicationRequest request, RequestContext context) {
        if (key == null || key.isBlank() || key.length() > 128 ||
                (request.scope() == PublicationScope.STUDENT) != (request.studentId() != null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid publication scope or idempotency key");
        }
        var actor = access.requireTeacherWrite(principal, classroomId);
        var gradebook = schemes.lock(classroomId);
        var receipt = publications.receipt(gradebook.getId(), key);
        if (receipt.isPresent()) {
            var previous = receipt.get();
            if (!previous.actor().equals(actor) || !previous.request().equals(request)) {
                throw new ApiException(ErrorCode.GRADEBOOK_IDEMPOTENCY_CONFLICT);
            }
            return new Result(previous.response(), true);
        }
        GradebookSchemeService.requireVersion(gradebook, request.expectedVersion());
        var scheme = schemes.active(gradebook);
        if (scheme.mode() == null) throw new ApiException(ErrorCode.GRADEBOOK_PUBLICATION_NOT_READY, "Apply a grade scheme before publishing");
        GradebookSchemeService.requireValidWeights(scheme.mode(), scheme.items());
        var outcomes = new LinkedHashMap<UUID, GradeCalculator.Calculation>();
        if (request.scope() == PublicationScope.STUDENT) {
            access.requireActiveStudentInClass(classroomId, request.studentId());
            outcomes.putAll(calculate(classroomId, scheme, List.of(request.studentId())));
        } else {
            var next = PageRequest.of(0, 100);
            for (;;) {
                var roster = reads.roster(classroomId, next);
                outcomes.putAll(calculate(classroomId, scheme, roster.stream().map(StudentIdentity::studentId).toList()));
                if (!roster.hasNext()) break;
                next = next.next();
            }
        }
        int targeted = outcomes.size();
        outcomes.values().removeIf(grade -> grade.overall().current() == null);
        if (outcomes.isEmpty()) throw new ApiException(ErrorCode.GRADEBOOK_PUBLICATION_NOT_READY);
        var response = new GradePublicationResponse(UUID.randomUUID(), scheme.revisionId(), gradebook.getVersion() + 1,
                outcomes.size(), targeted - outcomes.size(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        publications.insertReceipt(gradebook.getId(), key, new GradebookPublicationRepository.Receipt(actor, request, response));
        outcomes.forEach((student, overall) -> publications.append(gradebook.getId(), student, response.publicationId(),
                scheme.revisionId(), overall));
        publications.advanceVersion(gradebook.getId());
        var metadata = new PublicationAudit(request.scope(), response.publicationId(), request.studentId(),
                response.schemeRevisionId(), response.publishedCount(), response.ineligibleCount());
        audit.recordGradebook(classroomId, gradebook.getId(), actor, "GRADEBOOK_PUBLISHED", null,
                json.valueToTree(response).toString(), null, json.valueToTree(metadata).toString(), context);
        return new Result(response, false);
    }

    Map<UUID, GradeCalculator.Calculation> calculate(UUID classroomId, SchemeView scheme, List<UUID> students) {
        if (students.isEmpty()) return Map.of();
        var ungraded = new java.util.HashSet<>(reads.ungradedItems(classroomId));
        var eligible = scheme.items().stream().filter(item -> !ungraded.contains(item.reference())).toList();
        var configured = eligible.stream().map(item -> new GradeItem(item.sourceType(), item.sourceId(), null)).toList();
        var results = queries.load(classroomId, configured, students, GradeSourceResult.Audience.STUDENT)
                .stream().collect(Collectors.groupingBy(GradeSourceResult::studentId));
        var byStudent = new LinkedHashMap<UUID, GradeCalculator.Calculation>();
        for (var student : students) {
            byStudent.put(student, calculator.calculateWithContributors(scheme.mode(), eligible, results.getOrDefault(student, List.of())));
        }
        return byStudent;
    }
}
