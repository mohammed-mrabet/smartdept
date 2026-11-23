package ma.ump.smartdept.academic;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.academic.AcademicDtos.ExamPlanSlot;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static ma.ump.smartdept.academic.AcademicTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class ExamPlannerTest {

    private final Filiere isi = TestData.filiere(1, "M2I-ISI");
    private final User nadia = TestData.teacher(3, "Nadia", "");
    private final User samir = TestData.teacher(5, "Samir", "");
    private final User youssef = TestData.teacher(4, "Youssef", "");
    private final User hanae = TestData.teacher(6, "Hanae", "");

    @Test
    void oneExamPerDayWithEnoughSeatsAndSupervisors() {
        List<CourseModule> modules = List.of(module(1, "GL501", isi, nadia, 1, 0, 0), module(2, "DEV502", isi, samir, 1, 0, 0),
                module(3, "BD503", isi, youssef, 1, 0, 0));
        List<Room> rooms = List.of(room(3, "S12", 40, RoomType.SALLE), room(4, "S14", 35, RoomType.SALLE), room(6, "LABO-1", 24, RoomType.LABO));
        ExamPlanner.Result r = ExamPlanner.plan(1, 60, modules, rooms, List.of(nadia, samir, youssef, hanae), new Agenda(),
                LocalDate.of(2026, 12, 14), LocalDate.of(2026, 12, 19), 120);
        assertTrue(r.unplaced().isEmpty(), r.unplaced().toString());
        assertEquals(3, r.slots().stream().map(ExamPlanSlot::date).distinct().count(), "un examen par jour");
        for (ExamPlanSlot s : r.slots()) {
            assertEquals(List.of("S12", "S14"), s.roomCodes(), "60 étudiants : deux salles, jamais le laboratoire");
            assertEquals(4, s.supervisorIds().size(), "deux surveillants par salle");
        }
        ExamPlanSlot gl = r.slots().stream().filter(s -> s.moduleCode().equals("GL501")).findFirst().orElseThrow();
        assertEquals(3L, gl.supervisorIds().get(0), "l'enseignant du module surveille en premier");
    }

    @Test
    void avoidsBusyFiliereAndReportsImpossibleCases() {
        Agenda agenda = new Agenda();
        LocalDate day = LocalDate.of(2026, 12, 14);
        agenda.add("F1", day, LocalTime.of(8, 0), LocalTime.of(18, 0));
        List<Room> rooms = List.of(room(3, "S12", 40, RoomType.SALLE));
        ExamPlanner.Result r = ExamPlanner.plan(1, 30, List.of(module(1, "GL501", isi, nadia, 1, 0, 0), module(2, "DEV502", isi, samir, 1, 0, 0)),
                rooms, List.of(nadia, samir), agenda, day, day.plusDays(1), 120);
        assertEquals(1, r.slots().size());
        assertEquals(day.plusDays(1), r.slots().get(0).date());
        assertEquals(1, r.unplaced().size());
    }
}
