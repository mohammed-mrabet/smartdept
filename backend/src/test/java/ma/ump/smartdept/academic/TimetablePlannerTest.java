package ma.ump.smartdept.academic;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.academic.AcademicDtos.PlannedSlot;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ma.ump.smartdept.academic.AcademicTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class TimetablePlannerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);
    private Filiere isi;
    private User nadia;
    private User samir;
    private List<Room> rooms;

    @BeforeEach
    void setUp() {
        isi = TestData.filiere(1, "M2I-ISI");
        nadia = TestData.teacher(3, "Nadia", "Génie logiciel");
        samir = TestData.teacher(5, "Samir", "DevOps");
        rooms = List.of(room(1, "AMPHI-A", 120, RoomType.AMPHI), room(3, "S12", 40, RoomType.SALLE),
                room(6, "LABO-1", 24, RoomType.LABO), room(9, "S-PETITE", 10, RoomType.SALLE));
    }

    @Test
    void placesEveryNeedWithoutAnyConflict() {
        List<CourseModule> modules = List.of(module(1, "GL501", isi, nadia, 1, 1, 1), module(2, "DEV502", isi, samir, 1, 0, 2),
                module(3, "GL502", isi, nadia, 2, 1, 0));
        TimetablePlanner.Result r = TimetablePlanner.plan(1, 20, modules, rooms, new Agenda(), MONDAY);
        assertEquals(9, r.required());
        assertEquals(9, r.slots().size());
        assertTrue(r.unplaced().isEmpty());
        Set<String> used = new HashSet<>();
        for (PlannedSlot s : r.slots()) {
            assertTrue(used.add("F" + s.date() + s.startTime()), "la filière n'a jamais deux cours en même temps");
            assertTrue(used.add("T" + s.teacherId() + s.date() + s.startTime()), "un enseignant n'est pas à deux endroits");
            if (s.type() == SessionType.TP) assertEquals("LABO-1", s.roomCode(), "TP en laboratoire");
            else assertNotEquals("LABO-1", s.roomCode());
            assertNotEquals("S-PETITE", s.roomCode(), "salle trop petite pour 20 étudiants");
            assertNotEquals(DayOfWeek.SUNDAY, s.date().getDayOfWeek());
        }
    }

    @Test
    void spreadsAModuleOverSeveralDays() {
        TimetablePlanner.Result r = TimetablePlanner.plan(1, 20, List.of(module(1, "GL501", isi, nadia, 2, 2, 0)), rooms, new Agenda(), MONDAY);
        long days = r.slots().stream().map(PlannedSlot::date).distinct().count();
        assertTrue(days >= 3, "4 séances réparties sur au moins 3 jours, ici " + days);
    }

    @Test
    void respectsExistingBookingsOfOtherFilieres() {
        Agenda agenda = new Agenda();
        // Nadia enseigne déjà toute la semaine sauf le vendredi matin
        for (int d = 0; d < 6; d++) {
            for (LocalTime[] slot : TimetablePlanner.SLOTS) {
                if (!(d == 4 && slot[0].getHour() < 12)) agenda.add("T3", MONDAY.plusDays(d), slot[0], slot[1]);
            }
        }
        TimetablePlanner.Result r = TimetablePlanner.plan(1, 20, List.of(module(1, "GL501", isi, nadia, 3, 0, 0)), rooms, agenda, MONDAY);
        assertEquals(2, r.slots().size());
        assertTrue(r.slots().stream().allMatch(s -> s.date().getDayOfWeek() == DayOfWeek.FRIDAY));
        assertEquals(1, r.unplaced().size());
    }

    @Test
    void reportsModulesWithoutTeacherOrRoom() {
        TimetablePlanner.Result r = TimetablePlanner.plan(1, 200, List.of(module(1, "GL501", isi, null, 1, 0, 0),
                module(2, "DEV502", isi, samir, 1, 0, 0)), rooms, new Agenda(), MONDAY);
        assertEquals(2, r.unplaced().size());
        assertTrue(r.unplaced().stream().anyMatch(u -> u.startsWith("GL501") && u.contains("aucun enseignant")));
        assertTrue(r.unplaced().stream().anyMatch(u -> u.startsWith("DEV502") && u.contains("aucun créneau")), "aucune salle de 200 places");
    }
}
