package ma.ump.smartdept.academic;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.academic.AcademicDtos.RiskResponse;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskServiceTest {

    private final User hamza = TestData.student(14, "Hamza", TestData.filiere(1, "M2I-ISI"));

    @Test
    void highRiskWhenGradesAndAbsencesAccumulate() {
        RiskResponse r = RiskService.compute(hamza, 7.0, 2, 3, 10, 1);
        assertEquals(18 + 20 + 12 + 5, r.score());
        assertEquals(RiskLevel.HIGH, r.level());
        assertEquals(70.0, r.attendanceRate());
        assertEquals(4, r.reasons().size());
    }

    @Test
    void lowRiskForAGoodStudent() {
        RiskResponse r = RiskService.compute(hamza, 14.2, 0, 0, 12, 0);
        assertEquals(0, r.score());
        assertEquals(RiskLevel.LOW, r.level());
        assertEquals("Aucun signal d'alerte", r.reasons().get(0));
    }

    @Test
    void mediumRiskAndCappedComponents() {
        assertEquals(RiskLevel.MEDIUM, RiskService.compute(hamza, 9.0, 1, 3, 8, 0).level());   // 6 + 10 + 12 = 28
        RiskResponse capped = RiskService.compute(hamza, 0.0, 9, 20, 20, 9);
        assertEquals(100, capped.score());
        assertTrue(RiskService.localAdvice(capped, "faits").contains("tutorat"));
    }
}
