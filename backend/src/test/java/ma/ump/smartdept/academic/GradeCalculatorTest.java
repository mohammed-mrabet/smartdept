package ma.ump.smartdept.academic;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GradeCalculatorTest {

    private static Map<Assessment, Double> g(Double cc, Double exam, Double tp, Double ratt) {
        Map<Assessment, Double> m = new EnumMap<>(Assessment.class);
        if (cc != null) m.put(Assessment.CC, cc);
        if (exam != null) m.put(Assessment.EXAM, exam);
        if (tp != null) m.put(Assessment.TP, tp);
        if (ratt != null) m.put(Assessment.RATTRAPAGE, ratt);
        return m;
    }

    @Test
    void weightsDependOnAvailableComponents() {
        assertEquals(13.6, GradeCalculator.average(g(12.0, 14.0, 15.0, null)));   // 3,6 + 7 + 3
        assertEquals(13.2, GradeCalculator.average(g(12.0, 14.0, null, null)));   // 4,8 + 8,4
        assertEquals(14.3, GradeCalculator.average(g(null, 14.0, 15.0, null)));   // 9,8 + 4,5
        assertEquals(9.0, GradeCalculator.average(g(null, 9.0, null, null)));
        assertNull(GradeCalculator.average(g(15.0, null, 12.0, null)), "sans examen : note incomplète");
    }

    @Test
    void rattrapageReplacesExamOnlyIfBetter() {
        assertEquals(GradeCalculator.average(g(10.0, 12.0, null, null)), GradeCalculator.average(g(10.0, 6.0, null, 12.0)));
        assertEquals(GradeCalculator.average(g(10.0, 12.0, null, null)), GradeCalculator.average(g(10.0, 12.0, null, 8.0)));
    }

    @Test
    void validationAndCompensation() {
        assertEquals("VALIDE", GradeCalculator.status(10.0, 8.0));
        assertEquals("COMPENSE", GradeCalculator.status(8.0, 11.0));
        assertEquals("AJOURNE", GradeCalculator.status(4.5, 12.0), "note éliminatoire sous 5");
        assertEquals("AJOURNE", GradeCalculator.status(8.0, 9.5));
        assertEquals("INCOMPLET", GradeCalculator.status(null, 12.0));
    }
}
