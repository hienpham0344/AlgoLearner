package com.algolearner.platform.assignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Immutable values detached from editable authoring entities. Correct options never leave the server. */
final class AssignmentSnapshotPolicy {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    record Option(UUID id, String content, boolean correct, int displayOrder) { }
    record Question(UUID id, QuestionType type, String title, String prompt, BigDecimal points,
                    GradingPolicy policy, EssayFormat essayFormat, String explanation, String codeTemplate,
                    String codeLanguage, int displayOrder, List<Option> options, String codeContent, BigDecimal weight, List<BigDecimal> caseWeights) {
        Question { options = List.copyOf(options); caseWeights=caseWeights==null?List.of():List.copyOf(caseWeights); }
        Question(UUID id,QuestionType type,String title,String prompt,BigDecimal points,GradingPolicy policy,EssayFormat essayFormat,
                String explanation,String codeTemplate,String codeLanguage,int displayOrder,List<Option> options,String codeContent) {
            this(id,type,title,prompt,points,policy,essayFormat,explanation,codeTemplate,codeLanguage,displayOrder,options,codeContent,BigDecimal.ONE,List.of());
        }
        List<AssignmentQuestionOptionEntity> gradingOptions() {
            return options.stream().map(o -> AssignmentQuestionOptionEntity.builder().id(o.id())
                    .content(o.content()).isCorrect(o.correct()).displayOrder(o.displayOrder()).build()).toList();
        }
    }
    record Content(Integer durationMinutes, TimeExpiredAction timeoutAction, boolean shuffleQuestions,
                   boolean shuffleAnswers, List<Question> questions, CodeFeedbackLevel codeFeedbackLevel) {
        Content {
            questions = List.copyOf(questions);
            if (codeFeedbackLevel == null) codeFeedbackLevel = CodeFeedbackLevel.PASS_FAIL;
        }
        Content(Integer durationMinutes, TimeExpiredAction timeoutAction, boolean shuffleQuestions,
                boolean shuffleAnswers, List<Question> questions) {
            this(durationMinutes, timeoutAction, shuffleQuestions, shuffleAnswers, questions, CodeFeedbackLevel.PASS_FAIL);
        }
    }
    private AssignmentSnapshotPolicy() { }
    static String encode(Content content) {
        try { return JSON.writeValueAsString(content); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot serialize assignment revision", error); }
    }
    static String forAttempt(String snapshot) {
        var content=decode(snapshot);
        var questions=new java.util.ArrayList<>(content.questions());
        var random=new java.security.SecureRandom();
        if(content.shuffleQuestions())java.util.Collections.shuffle(questions,random);
        var frozen=new java.util.ArrayList<Question>();
        for(var question:questions) {
            var options=new java.util.ArrayList<>(question.options());
            if(content.shuffleAnswers())java.util.Collections.shuffle(options,random);
            var ordered=new java.util.ArrayList<Option>();
            for(var option:options)ordered.add(new Option(option.id(),option.content(),option.correct(),ordered.size()));
            frozen.add(new Question(question.id(),question.type(),question.title(),question.prompt(),question.points(),
                    question.policy(),question.essayFormat(),question.explanation(),question.codeTemplate(),question.codeLanguage(),
                    frozen.size(),ordered,question.codeContent(),question.weight(),question.caseWeights()));
        }
        return encode(new Content(content.durationMinutes(),content.timeoutAction(),content.shuffleQuestions(),content.shuffleAnswers(),frozen,content.codeFeedbackLevel()));
    }
    static com.algolearner.platform.problem.PublicProblemCatalog.ContentSnapshot decodeCode(String content) {
        if(content==null)return null;
        try{return JSON.readValue(content,com.algolearner.platform.problem.PublicProblemCatalog.ContentSnapshot.class);}
        catch(JsonProcessingException failure){throw new IllegalStateException("Cannot read code snapshot",failure);}
    }
    static Content decode(String content) {
        try { return JSON.readValue(content, Content.class); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot read assignment revision", error); }
    }
}
