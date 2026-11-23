package ma.ump.smartdept.academic;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

final class AcademicRules {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private AcademicRules() {
    }

    static boolean isManager(User u) {
        return u.getRole() != null && u.getRole().isManager();
    }

    static void requireManager(User u) {
        if (!isManager(u)) throw ApiException.forbidden("Action réservée au chef de département et à l'administrateur");
    }

    static void requireTeacherOf(User u, CourseModule m) {
        boolean teacher = m.getTeacher() != null && m.getTeacher().getId().equals(u.getId());
        if (!teacher && !isManager(u)) {
            throw ApiException.forbidden("Action réservée à l'enseignant du module " + m.getCode() + " et au chef de département");
        }
    }

    static boolean isStudent(User u) {
        return u.getRole() == Role.ETUDIANT;
    }

    static LocalDate monday(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static void checkTimes(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("L'heure de fin (" + end.format(TIME) + ") doit suivre l'heure de début (" + start.format(TIME) + ")");
        }
        if (start.isBefore(LocalTime.of(8, 0)) || end.isAfter(LocalTime.of(19, 0))) {
            throw ApiException.badRequest("Les créneaux sont compris entre 08:00 et 19:00");
        }
    }

    static String slot(LocalTime start, LocalTime end) {
        return start.format(TIME) + "-" + end.format(TIME);
    }
}
