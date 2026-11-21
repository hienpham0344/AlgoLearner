package com.algolearner.platform.assignment;

import com.algolearner.platform.auth.AccountEntity;
import com.algolearner.platform.judge.JudgeGateway;
import com.algolearner.platform.problem.PublicProblemCatalog;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentCodeJudgeTest {
    @Test void runIncludesOnlySampleCasesAndDoesNotPersistOfficialJob() throws Exception {
        var gateway=mock(JudgeGateway.class);var jdbc=mock(JdbcTemplate.class);var json=JsonMapper.builder().findAndAddModules().build();
        var service=new AssignmentCodeJudge(gateway,jdbc,json);
        var student=new AccountEntity();student.setId(UUID.randomUUID());
        var assignment=AssignmentEntity.builder().id(UUID.randomUUID()).build();
        var attempt=AssignmentAttemptEntity.builder().id(UUID.randomUUID()).assignment(assignment).student(student).revisionId(UUID.randomUUID()).build();
        var content=new PublicProblemCatalog.ContentSnapshot(UUID.randomUUID(),"Code","Prompt",Set.of(JudgeGateway.Language.JAVA),
                List.of(new JudgeGateway.TestCaseSnapshot("sample","sampleout",true,true),new JudgeGateway.TestCaseSnapshot("secret","secretout",false,false)),
                new JudgeGateway.Limits(15,2000,65536),JudgeGateway.Feedback.PASS_FAIL);
        var question=new AssignmentSnapshotPolicy.Question(UUID.randomUUID(),QuestionType.CODE,"Code","Prompt",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,null,null,null,"JAVA",0,List.of(),json.writeValueAsString(content));
        service.evaluate(attempt,question,"class Main {}",true);
        var request=ArgumentCaptor.forClass(JudgeGateway.CodeEvaluationRequest.class);verify(gateway).run(request.capture());
        assertThat(request.getValue().testCases()).hasSize(1).allMatch(JudgeGateway.TestCaseSnapshot::sample);
        assertThat(request.getValue().revisionId()).isEqualTo(attempt.getRevisionId());
        verify(gateway,never()).submit(any());verifyNoInteractions(jdbc);
    }
    @Test void officialRejudgeKeepsCanonicalRequestKeyAndOwnerAcrossGenerations() throws Exception {
        var gateway=mock(JudgeGateway.class);var jdbc=mock(JdbcTemplate.class);var json=JsonMapper.builder().findAndAddModules().build();
        var service=new AssignmentCodeJudge(gateway,jdbc,json);
        var student=new AccountEntity();student.setId(UUID.randomUUID());
        var attempt=AssignmentAttemptEntity.builder().id(UUID.randomUUID()).student(student)
                .assignment(AssignmentEntity.builder().id(UUID.randomUUID()).build()).revisionId(UUID.randomUUID()).build();
        var content=new PublicProblemCatalog.ContentSnapshot(UUID.randomUUID(),"Code","Prompt",Set.of(JudgeGateway.Language.JAVA),
                List.of(new JudgeGateway.TestCaseSnapshot("sample","out",true,true)),new JudgeGateway.Limits(15,2000,65536),JudgeGateway.Feedback.PASS_FAIL);
        var question=new AssignmentSnapshotPolicy.Question(UUID.randomUUID(),QuestionType.CODE,"Code","Prompt",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,null,null,null,"JAVA",0,List.of(),json.writeValueAsString(content));
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(attempt.getId()),eq(question.id()))).thenReturn(1L,2L);
        var firstJob=UUID.randomUUID();var secondJob=UUID.randomUUID();
        when(gateway.submit(any())).thenReturn(firstJob,secondJob);
        assertThat(service.evaluate(attempt,question,"class Main {}",false)).isEqualTo(firstJob);
        assertThat(service.evaluate(attempt,question,"class Main {}",false)).isEqualTo(secondJob);
        var captured=ArgumentCaptor.forClass(JudgeGateway.CodeEvaluationRequest.class);verify(gateway,times(2)).submit(captured.capture());
        var requests=captured.getAllValues();
        assertThat(requests).extracting(JudgeGateway.CodeEvaluationRequest::ownerId).containsExactly(student.getId(),student.getId());
        assertThat(requests).extracting(JudgeGateway.CodeEvaluationRequest::requestKey)
                .containsExactly(attempt.getId()+":"+question.id(),attempt.getId()+":"+question.id());
        assertThat(requests).extracting(JudgeGateway.CodeEvaluationRequest::generation).containsExactly(1L,2L);
    }
    @Test void codeTestcaseWeightsDeterminePartialCreditInsteadOfRawPassedCount() {
        var question=new AssignmentSnapshotPolicy.Question(UUID.randomUUID(),QuestionType.CODE,"Code","Prompt",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,null,null,null,"JAVA",0,List.of(),null,BigDecimal.ONE,List.of(BigDecimal.ONE,new BigDecimal("3")));
        var result=new JudgeGateway.EvaluationResult(UUID.randomUUID(),1,JudgeGateway.Verdict.WRONG_ANSWER,1,2,
                List.of(new JudgeGateway.CaseResult(0,JudgeGateway.Verdict.ACCEPTED,null,null,null),
                        new JudgeGateway.CaseResult(1,JudgeGateway.Verdict.WRONG_ANSWER,null,null,null)));
        assertThat(AssignmentCodeJudge.score(question,result)).isEqualByComparingTo("2.5");
    }
    @Test void missingHistoricalTestcaseSnapshotIsRejectedBeforeGatewayCall() {
        var gateway=mock(JudgeGateway.class);var jdbc=mock(JdbcTemplate.class);
        var service=new AssignmentCodeJudge(gateway,jdbc,JsonMapper.builder().build());
        var question=new AssignmentSnapshotPolicy.Question(UUID.randomUUID(),QuestionType.CODE,"Code","Prompt",BigDecimal.TEN,
                GradingPolicy.ALL_OR_NOTHING,null,null,null,"JAVA",0,List.of(),null);
        assertThatThrownBy(()->service.evaluate(new AssignmentAttemptEntity(),question,"source",false))
                .isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
        verifyNoInteractions(gateway,jdbc);
    }
}
