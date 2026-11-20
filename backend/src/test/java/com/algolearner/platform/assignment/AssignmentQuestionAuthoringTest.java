package com.algolearner.platform.assignment;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AssignmentQuestionAuthoringTest {
    @Test void singleChoiceRequiresExactlyOneCorrectOption() {
        var request=new AssignmentDtos.AddQuestionRequest(QuestionType.SINGLE_CHOICE,"Q","Prompt",BigDecimal.TEN,
                null,null,null,null,null,List.of(new AssignmentDtos.OptionRequest("A",true),new AssignmentDtos.OptionRequest("B",true)));
        assertThatThrownBy(()->AssignmentQuestionAuthoring.validate(request)).isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
    }
    @Test void essayRequiresAnExplicitFormat() {
        var request=new AssignmentDtos.AddQuestionRequest(QuestionType.ESSAY,"Q","Prompt",BigDecimal.TEN,null,null,null,null,null,List.of());
        assertThatThrownBy(()->AssignmentQuestionAuthoring.validate(request)).isInstanceOf(com.algolearner.platform.common.exception.ApiException.class);
    }
    @Test void multipleChoiceAllowsSeveralCorrectOptions() {
        var request=new AssignmentDtos.AddQuestionRequest(QuestionType.MULTIPLE_CHOICE,"Q","Prompt",BigDecimal.TEN,
                null,null,null,null,null,List.of(new AssignmentDtos.OptionRequest("A",true),new AssignmentDtos.OptionRequest("B",true)));
        assertThatCode(()->AssignmentQuestionAuthoring.validate(request)).doesNotThrowAnyException();
    }
}
