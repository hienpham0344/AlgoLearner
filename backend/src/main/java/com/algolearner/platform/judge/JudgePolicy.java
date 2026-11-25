package com.algolearner.platform.judge;

import static com.algolearner.platform.judge.JudgeGateway.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Comparator contract TOKEN_WHITESPACE_V1: whitespace tokens, case-sensitive, no numeric tolerance. */
final class JudgePolicy {
    private JudgePolicy() { }
    static boolean matches(String expected, String actual) {
        return Arrays.equals(tokens(expected), tokens(actual));
    }
    private static String[] tokens(String text) { return text.isBlank() ? new String[0] : text.strip().split("\\s+"); }
    static CaseResult visibleCase(int index, TestCaseSnapshot test, Verdict verdict, String output, Feedback feedback) {
        if (feedback == Feedback.PASS_FAIL) return null;
        boolean details = feedback == Feedback.FULL_DETAILS && (test.sample() || test.allowDetails());
        return new CaseResult(index, verdict, details ? test.input() : null,
                details ? test.expectedOutput() : null, details ? output : null);
    }
    static void validate(CodeEvaluationRequest r) {
        if (r.ownerId()==null || r.resourceId()==null || r.revisionId()==null || r.scope()==null || r.language()==null || r.feedback()==null
                || r.requestKey()==null || r.requestKey().isBlank() || r.requestKey().length()>128 || r.generation()<0
                || r.source()==null || r.source().isBlank() || bytes(r.source())>65536
                || r.testCases().isEmpty() || r.testCases().size()>100 || r.limits()==null
                || r.limits().compileSeconds()<1 || r.limits().compileSeconds()>10
                || r.limits().runtimeMillis()<1 || r.limits().runtimeMillis()>5000
                || r.limits().outputBytes()<1 || r.limits().outputBytes()>65536) throw new IllegalArgumentException("Invalid judge request limits");
        long total=0;
        for(var t:r.testCases()) {
            if(t==null || t.input()==null || t.expectedOutput()==null || bytes(t.input())>65536 || bytes(t.expectedOutput())>65536)
                throw new IllegalArgumentException("Invalid testcase limits");
            total+=bytes(t.input())+bytes(t.expectedOutput());
        }
        if(total>1048576) throw new IllegalArgumentException("Testcase snapshot too large");
    }
    private static int bytes(String s) { return s.getBytes(StandardCharsets.UTF_8).length; }
}
