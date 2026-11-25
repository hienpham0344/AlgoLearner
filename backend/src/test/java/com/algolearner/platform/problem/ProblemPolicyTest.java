package com.algolearner.platform.problem;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import com.algolearner.platform.judge.JudgeGateway.TestCaseSnapshot;
class ProblemPolicyTest {
 @Test void publicDetailsExcludeHiddenInputsAndExpectedOutputs() {
  var samples=ProblemPolicy.publicSamples(List.of(new TestCaseSnapshot("shown","shown",true,false),new TestCaseSnapshot("secret","secret",false,false)));
  assertThat(samples).hasSize(1);assertThat(samples.getFirst().input()).isEqualTo("shown");
 }
}
