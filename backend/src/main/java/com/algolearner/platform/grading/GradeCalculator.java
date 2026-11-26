package com.algolearner.platform.grading;

import com.algolearner.platform.grading.GradeSchemeRevisionEntity.Mode;
import com.algolearner.platform.grading.GradebookDtos.GradeTotals;
import com.algolearner.platform.grading.GradebookDtos.ItemReference;
import com.algolearner.platform.grading.GradebookDtos.Overall;
import com.algolearner.platform.grading.GradebookDtos.SchemeItem;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GradeCalculator {
    record Calculation(Overall overall, Set<ItemReference> contributors) { }

    /** Calculates a single student's grades for the supplied scheme. */
    public Overall calculate(Mode mode, List<SchemeItem> items, List<GradeSourceResult> grades) {
        return calculateWithContributors(mode, items, grades).overall();
    }

    Calculation calculateWithContributors(Mode mode, List<SchemeItem> items, List<GradeSourceResult> grades) {
        if (mode == null) return new Calculation(new Overall(null, null), Set.of());

        var byItem = new HashMap<ItemReference, GradeSourceResult>();
        for (var grade : grades) {
            byItem.put(new ItemReference(grade.sourceType(), grade.sourceId()), grade);
        }
        var earned = BigDecimal.ZERO;
        var possible = BigDecimal.ZERO;
        boolean finalReady = true;
        var contributors = new HashSet<ItemReference>();
        var finalRequirements = new HashSet<ItemReference>();
        for (var item : items) {
            var grade = byItem.get(item.reference());
            if (grade != null && grade.status() == GradeSourceResult.Status.UNGRADED) continue;
            if (grade == null || grade.status() != GradeSourceResult.Status.GRADED
                    || grade.earnedPoints() == null || grade.possiblePoints() == null) {
                finalReady &= !item.required();
                continue;
            }
            if (item.required()) finalRequirements.add(item.reference());
            if (grade.possiblePoints().signum() == 0) continue;
            if (mode == Mode.WEIGHTED) {
                if (item.weight().signum() == 0) continue;
                earned = earned.add(grade.earnedPoints().multiply(item.weight())
                        .divide(grade.possiblePoints(), MathContext.DECIMAL128));
                possible = possible.add(item.weight());
            } else {
                // Approved provisional interpretation of FR-GRD-003: sum actual earned and possible points.
                earned = earned.add(grade.earnedPoints());
                possible = possible.add(grade.possiblePoints());
            }
            contributors.add(item.reference());
        }
        if (possible.signum() == 0) return new Calculation(new Overall(null, null), Set.of());
        var percentage = earned.movePointRight(2).divide(possible, 4, RoundingMode.HALF_UP);
        var totals = new GradeTotals(earned.setScale(4, RoundingMode.HALF_UP), possible, percentage);
        if (finalReady) contributors.addAll(finalRequirements);
        return new Calculation(new Overall(totals, finalReady ? totals : null), Set.copyOf(contributors));
    }
}
