package com.algolearner.platform.assignment;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;

final class AssignmentAnswerPolicy {
    private AssignmentAnswerPolicy() { }
    static String selection(AssignmentDtos.SaveDraftAnswerRequest request, AssignmentSnapshotPolicy.Question question) {
        if(question==null)return request.selectedOptionIds(); // Explicit legacy path; never invent original options.
        List<UUID> ids=request.selectedOptions();
        if(ids==null && request.selectedOptionIds()!=null) {
            try {ids=JsonMapper.builder().build().readValue(request.selectedOptionIds(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<UUID>>() { });}
            catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw invalid();}
        }
        if(ids==null)ids=List.of();
        var type=question.type();
        boolean objective=type==QuestionType.SINGLE_CHOICE || type==QuestionType.TRUE_FALSE || type==QuestionType.MULTIPLE_CHOICE;
        if(objective) {
            if(ids.size()!=new java.util.HashSet<>(ids).size() || type!=QuestionType.MULTIPLE_CHOICE && ids.size()>1
                    || ids.stream().anyMatch(id->question.options().stream().noneMatch(o->o.id().equals(id)))
                    || request.textAnswer()!=null || request.fileId()!=null || request.codeSubmission()!=null)throw invalid();
        } else {
            if(!ids.isEmpty())throw invalid();
            if(type==QuestionType.ESSAY && (request.codeSubmission()!=null
                    || question.essayFormat()==EssayFormat.TEXT_ONLY && request.fileId()!=null
                    || question.essayFormat()==EssayFormat.FILE_ONLY && request.textAnswer()!=null))throw invalid();
            if(type==QuestionType.CODE && (request.textAnswer()!=null || request.fileId()!=null))throw invalid();
        }
        return ids.stream().map(id->"\""+id+"\"").collect(java.util.stream.Collectors.joining(",","[","]"));
    }
    private static ApiException invalid(){return new ApiException(ErrorCode.VALIDATION_FAILED,"Answer does not match the captured question.");}
}
