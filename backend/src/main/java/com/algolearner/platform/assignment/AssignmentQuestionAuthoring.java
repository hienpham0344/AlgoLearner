package com.algolearner.platform.assignment;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.judge.JudgeGateway.*;
import com.algolearner.platform.problem.PublicProblemCatalog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!mock")
@Transactional(readOnly = true)
public class AssignmentQuestionAuthoring {
    private final AssignmentQuestionRepository questions;
    private final AssignmentQuestionOptionRepository options;
    private final PublicProblemCatalog catalog;
    private final ObjectMapper json;
    public AssignmentQuestionAuthoring(AssignmentQuestionRepository questions, AssignmentQuestionOptionRepository options,
            PublicProblemCatalog catalog, ObjectMapper json) {
        this.questions=questions;this.options=options;this.catalog=catalog;this.json=json;
    }
    static void validate(AssignmentDtos.AddQuestionRequest r) {
        if (r.questionType()==null || r.title()==null || r.title().isBlank() || r.prompt()==null || r.prompt().isBlank()
                || r.points()==null || r.points().signum()<0) invalid();
        if(r.questionWeight()!=null && r.questionWeight().signum()<0)invalid();
        if(r.codeCaseWeights()!=null && r.codeCaseWeights().stream().anyMatch(w->w==null || w.signum()<0))invalid();
        boolean objective = r.questionType()==QuestionType.SINGLE_CHOICE || r.questionType()==QuestionType.TRUE_FALSE || r.questionType()==QuestionType.MULTIPLE_CHOICE;
        var choices = r.options()==null?List.<AssignmentDtos.OptionRequest>of():r.options();
        if (objective) {
            long correct=choices.stream().filter(AssignmentDtos.OptionRequest::isCorrect).count();
            if (choices.size()<2 || choices.stream().anyMatch(o->o.content()==null||o.content().isBlank()) || correct==0
                    || r.questionType()!=QuestionType.MULTIPLE_CHOICE && correct!=1
                    || r.questionType()==QuestionType.TRUE_FALSE && choices.size()!=2) invalid();
        } else if (!choices.isEmpty()) invalid();
        if (r.questionType()==QuestionType.ESSAY && r.essayFormat()==null) invalid();
        if (r.questionType()==QuestionType.CODE && (r.codeLanguage()==null
                || !Set.of("JAVA","PYTHON","java","python").contains(r.codeLanguage()))) invalid();
    }
    @Transactional
    AssignmentQuestionEntity save(UUID teacher, AssignmentEntity assignment, AssignmentQuestionEntity existing,
            AssignmentDtos.AddQuestionRequest request) {
        validate(request);
        var q=existing==null?AssignmentQuestionEntity.builder().id(UUID.randomUUID()).assignment(assignment)
                .displayOrder((int)questions.countByAssignmentId(assignment.getId())).build():existing;
        q.setQuestionType(request.questionType());q.setTitle(request.title().trim());q.setPrompt(request.prompt().trim());
        q.setQuestionWeight(request.questionWeight()==null?java.math.BigDecimal.ONE:request.questionWeight());
        q.setCodeCaseWeights(request.codeCaseWeights()==null?null:List.copyOf(request.codeCaseWeights()));
        q.setPoints(request.points());q.setGradingPolicy(request.gradingPolicy()==null?GradingPolicy.ALL_OR_NOTHING:request.gradingPolicy());
        q.setEssayFormat(request.essayFormat());q.setExplanation(request.explanation());q.setCodeTemplate(request.codeTemplate());q.setCodeLanguage(request.codeLanguage());
        if (request.questionType()==QuestionType.CODE) {
            var content=request.publicProblemId()==null?new PublicProblemCatalog.ContentSnapshot(UUID.randomUUID(),request.title(),request.prompt(),
                    Set.of(Language.valueOf(request.codeLanguage().toUpperCase(java.util.Locale.ROOT))),
                    request.testCases()==null?List.of():request.testCases(),new Limits(10,2000,65536),Feedback.PASS_FAIL)
                    :catalog.contentSnapshot(teacher,request.publicProblemId());
            if(content.testCases().isEmpty() || content.testCases().stream().noneMatch(TestCaseSnapshot::sample)) invalid();
            if(request.codeCaseWeights()!=null && (request.codeCaseWeights().size()!=content.testCases().size()
                    || request.codeCaseWeights().stream().reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).signum()==0))invalid();
            try {q.setCodeContentSnapshot(json.writeValueAsString(content));}
            catch(JsonProcessingException failure){throw new IllegalStateException("Cannot copy code content",failure);}
            q.setPrompt(content.statement());
        } else q.setCodeContentSnapshot(null);
        questions.save(q);
        if(existing!=null)options.deleteAll(options.findByQuestionIdOrderByDisplayOrderAsc(q.getId()));
        int order=0;
        for(var o:request.options()==null?List.<AssignmentDtos.OptionRequest>of():request.options())
            options.save(AssignmentQuestionOptionEntity.builder().id(UUID.randomUUID()).question(q).content(o.content())
                    .isCorrect(o.isCorrect()).displayOrder(order++).build());
        return q;
    }
    private static void invalid(){throw new ApiException(ErrorCode.VALIDATION_FAILED,"Invalid question configuration.");}
}
