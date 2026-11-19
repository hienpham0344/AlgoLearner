package com.algolearner.platform.problem;
import java.util.List;
import com.algolearner.platform.judge.JudgeGateway.TestCaseSnapshot;
final class ProblemPolicy {
 private ProblemPolicy() { }
 static List<TestCaseSnapshot> publicSamples(List<TestCaseSnapshot> tests) {return tests.stream().filter(TestCaseSnapshot::sample).toList();}
}
