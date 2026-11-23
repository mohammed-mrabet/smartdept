package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ma.ump.smartdept.academic.AcademicRules.*;

/**
 * Appel en séance, justificatifs d'absence et avertissements.
 * Au seuil d'absences non justifiées dans un module (3 par défaut), l'étudiant et le chef de département sont prévenus.
 */
@Service
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final TimetableService timetable;
    private final UserRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;
    private final int threshold;

    public AttendanceService(AttendanceRepository attendanceRepository, TimetableService timetable, UserRepository userRepository,
                             NotificationService notifications, Clock clock,
                             @Value("${app.academic.absence-threshold:3}") int threshold) {
        this.attendanceRepository = attendanceRepository;
        this.timetable = timetable;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
        this.threshold = threshold;
    }

    @Transactional(readOnly = true)
    public AttendanceSheet sheet(User me, Long sessionId) {
        ClassSession s = timetable.find(sessionId);
        requireSessionTeacher(me, s);
        Map<Long, Attendance> records = attendanceRepository.findBySession(sessionId).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
        List<AttendanceRow> rows = userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, s.getFiliere().getId()).stream()
                .map(u -> {
                    Attendance a = records.get(u.getId());
                    return new AttendanceRow(u.getId(), u.getFullName(), u.getStudentNumber(),
                            a == null ? AttendanceStatus.PRESENT : a.getStatus(),
                            a == null ? JustificationStatus.NONE : a.getJustificationStatus());
                }).toList();
        return new AttendanceSheet(SessionResponse.of(s), rows, s.isAttendanceTaken());
    }

    public AttendanceSheet save(User me, Long sessionId, AttendanceBatchRequest r) {
        ClassSession s = timetable.find(sessionId);
        requireSessionTeacher(me, s);
        if (s.getStatus() == SessionStatus.CANCELLED) throw ApiException.badRequest("La séance est annulée : pas d'appel");
        if (s.getDate().isAfter(LocalDate.now(clock))) throw ApiException.badRequest("L'appel se fait le jour de la séance ou après");
        Map<Long, User> students = userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, s.getFiliere().getId()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Attendance> records = attendanceRepository.findBySession(sessionId).stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), Function.identity()));
        List<User> newlyAbsent = new ArrayList<>();
        for (AttendanceEntry e : r.entries()) {
            User student = students.get(e.studentId());
            if (student == null) throw ApiException.badRequest("L'étudiant " + e.studentId() + " n'appartient pas à la filière de la séance");
            Attendance a = records.get(e.studentId());
            if (a == null) {
                a = new Attendance();
                a.setSession(s);
                a.setStudent(student);
            }
            boolean wasAbsent = a.getStatus() == AttendanceStatus.ABSENT;
            a.setStatus(e.status());
            if (e.status() == AttendanceStatus.PRESENT) {
                a.setJustificationStatus(JustificationStatus.NONE);
                a.setJustification(null);
            }
            attendanceRepository.save(a);
            if (e.status() == AttendanceStatus.ABSENT && !wasAbsent) newlyAbsent.add(student);
        }
        s.setAttendanceTaken(true);
        attendanceRepository.flush();
        for (User student : newlyAbsent) warnIfNeeded(student, s.getModule());
        return sheet(me, sessionId);
    }

    /** Avertissement au seuil, puis tous les 2 absences supplémentaires. */
    private void warnIfNeeded(User student, CourseModule module) {
        long count = attendanceRepository.countUnjustified(student.getId(), module.getId());
        if (count < threshold || (count - threshold) % 2 != 0) return;
        notifications.notify(student, NotificationType.ABSENCE_WARNING, "Avertissement d'absences : " + module.getCode(),
                "Vous avez " + count + " absences non justifiées en " + module.getName()
                        + ". Au-delà, l'accès à l'examen peut être refusé. Déposez vos justificatifs dans « Mes absences ».", "/absences");
        notifications.notifyManagers(NotificationType.ABSENCE_WARNING, "Absences répétées : " + student.getFullName(),
                student.getFullName() + " cumule " + count + " absences non justifiées en " + module.getCode() + ".", "/suivi-etudiants");
    }

    @Transactional(readOnly = true)
    public List<AbsenceResponse> mine(User me) {
        return forStudent(me.getId());
    }

    @Transactional(readOnly = true)
    public List<AbsenceResponse> forStudent(Long studentId) {
        return attendanceRepository.findByStudent(studentId).stream()
                .filter(a -> a.getStatus() != AttendanceStatus.PRESENT).map(AbsenceResponse::of).toList();
    }

    public AbsenceResponse justify(User me, Long id, JustifyRequest r) {
        Attendance a = find(id);
        if (!a.getStudent().getId().equals(me.getId())) throw ApiException.forbidden("Cette absence ne vous concerne pas");
        if (a.getStatus() == AttendanceStatus.PRESENT) throw ApiException.badRequest("Vous étiez présent(e) à cette séance");
        if (a.getJustificationStatus() == JustificationStatus.ACCEPTED || a.getJustificationStatus() == JustificationStatus.PENDING) {
            throw ApiException.conflict("Un justificatif est déjà " + (a.getJustificationStatus() == JustificationStatus.PENDING ? "en attente" : "accepté"));
        }
        a.setJustification(r.reason().trim());
        a.setJustificationStatus(JustificationStatus.PENDING);
        a.setJustifiedAt(LocalDateTime.now(clock));
        a.setReviewComment(null);
        ClassSession s = a.getSession();
        notifications.notifyManagers(NotificationType.ABSENCE_JUSTIFICATION, "Justificatif à examiner",
                me.getFullName() + " justifie son absence en " + s.getModule().getCode() + " du " + s.getDate().format(DATE) + ".", "/absences");
        return AbsenceResponse.of(a);
    }

    @Transactional(readOnly = true)
    public List<AbsenceResponse> pending(User me) {
        requireManager(me);
        return attendanceRepository.findPendingJustifications().stream().map(AbsenceResponse::of).toList();
    }

    public AbsenceResponse review(User me, Long id, ReviewRequest r) {
        requireManager(me);
        Attendance a = find(id);
        if (a.getJustificationStatus() != JustificationStatus.PENDING) throw ApiException.badRequest("Aucun justificatif en attente pour cette absence");
        if (!r.accept() && (r.comment() == null || r.comment().isBlank())) throw ApiException.badRequest("Indiquez le motif du refus");
        a.setJustificationStatus(r.accept() ? JustificationStatus.ACCEPTED : JustificationStatus.REJECTED);
        a.setReviewComment(r.comment() == null || r.comment().isBlank() ? null : r.comment().trim());
        ClassSession s = a.getSession();
        notifications.notify(a.getStudent(), NotificationType.ABSENCE_JUSTIFICATION,
                r.accept() ? "Justificatif accepté" : "Justificatif refusé",
                "Absence en " + s.getModule().getCode() + " du " + s.getDate().format(DATE) + " : justificatif "
                        + (r.accept() ? "accepté." : "refusé. " + a.getReviewComment()), "/absences");
        return AbsenceResponse.of(a);
    }

    private Attendance find(Long id) {
        return attendanceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Absence introuvable : " + id));
    }

    private static void requireSessionTeacher(User me, ClassSession s) {
        if (!isManager(me) && !s.getTeacher().getId().equals(me.getId())) {
            throw ApiException.forbidden("Seul l'enseignant de la séance (ou le chef de département) fait l'appel");
        }
    }
}
