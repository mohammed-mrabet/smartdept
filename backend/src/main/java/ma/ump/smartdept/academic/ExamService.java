package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static ma.ump.smartdept.academic.AcademicRules.*;

/** Calendrier des examens : contrôles de salles, d'effectif, de filière et de surveillants ; planification automatique. */
@Service
@Transactional
public class ExamService {

    private final ExamRepository examRepository;
    private final ClassSessionRepository sessionRepository;
    private final CourseModuleRepository moduleRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final FiliereRepository filiereRepository;
    private final ReferenceService references;
    private final NotificationService notifications;
    private final Clock clock;

    public ExamService(ExamRepository examRepository, ClassSessionRepository sessionRepository, CourseModuleRepository moduleRepository,
                       RoomRepository roomRepository, UserRepository userRepository, FiliereRepository filiereRepository,
                       ReferenceService references, NotificationService notifications, Clock clock) {
        this.examRepository = examRepository;
        this.sessionRepository = sessionRepository;
        this.moduleRepository = moduleRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.filiereRepository = filiereRepository;
        this.references = references;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> list(LocalDate from, LocalDate to, Long filiereId) {
        LocalDate start = from == null ? LocalDate.now(clock).minusMonths(1) : from;
        LocalDate end = to == null ? start.plusMonths(6) : to;
        return examRepository.findBetween(start, end, filiereId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> mine(User me) {
        LocalDate from = LocalDate.now(clock).minusDays(7);
        LocalDate to = from.plusMonths(6);
        if (isStudent(me)) {
            return me.getFiliere() == null ? List.of() : list(from, to, me.getFiliere().getId());
        }
        return examRepository.findForTeacher(me.getId(), from, to).stream().map(this::response).toList();
    }

    public ExamResponse create(ExamRequest r) {
        Exam e = new Exam();
        apply(e, r, null);
        examRepository.save(e);
        notifyStudents(e, "Examen programmé : " + e.getModule().getCode(), false);
        return response(e);
    }

    public ExamResponse update(Long id, ExamRequest r) {
        Exam e = find(id);
        apply(e, r, id);
        notifyStudents(e, "Examen modifié : " + e.getModule().getCode(), true);
        return response(e);
    }

    public void delete(Long id) {
        Exam e = find(id);
        if (!e.getDate().isBefore(LocalDate.now(clock))) {
            notifications.notifyAll(students(e.getFiliere().getId()), NotificationType.EXAM_SCHEDULED, "Examen annulé : " + e.getModule().getCode(),
                    "L'examen " + e.getModule().getName() + " du " + e.getDate().format(DATE) + " est annulé.", "/examens");
        }
        examRepository.delete(e);
    }

    public Exam find(Long id) {
        return examRepository.findById(id).orElseThrow(() -> ApiException.notFound("Examen introuvable : " + id));
    }

    private void apply(Exam e, ExamRequest r, Long excludeId) {
        CourseModule m = references.module(r.moduleId());
        checkTimes(r.startTime(), r.endTime());
        Set<Room> rooms = new LinkedHashSet<>();
        for (Long id : new LinkedHashSet<>(r.roomIds())) {
            Room room = references.room(id);
            if (!room.isActive()) throw ApiException.badRequest("La salle " + room.getCode() + " est hors service");
            rooms.add(room);
        }
        Set<User> supervisors = new LinkedHashSet<>();
        for (Long id : r.supervisorIds() == null ? List.<Long>of() : new LinkedHashSet<>(r.supervisorIds())) {
            User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Surveillant introuvable : " + id));
            if (u.getRole() == Role.ETUDIANT) throw ApiException.badRequest(u.getFullName() + " ne peut pas surveiller un examen");
            supervisors.add(u);
        }
        int students = students(m.getFiliere().getId()).size();
        int seats = rooms.stream().mapToInt(Room::getCapacity).sum();
        if (seats < students) throw ApiException.badRequest("Les salles offrent " + seats + " places pour " + students + " étudiants : ajoutez une salle");
        List<String> conflicts = conflicts(r.date(), r.startTime(), r.endTime(), m.getFiliere().getId(), rooms, supervisors, excludeId);
        if (!conflicts.isEmpty()) throw ApiException.conflict("Conflit le " + r.date().format(DATE) + " : " + String.join(" ; ", conflicts));
        e.setModule(m);
        e.setFiliere(m.getFiliere());
        e.setDate(r.date());
        e.setStartTime(r.startTime());
        e.setEndTime(r.endTime());
        e.setType(r.type());
        e.getRooms().clear();
        e.getRooms().addAll(rooms);
        e.getSupervisors().clear();
        e.getSupervisors().addAll(supervisors);
        e.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
    }

    List<String> conflicts(LocalDate date, LocalTime start, LocalTime end, Long filiereId, Set<Room> rooms, Set<User> supervisors, Long excludeId) {
        List<String> out = new ArrayList<>();
        for (Exam o : examRepository.findOverlapping(date, start, end, excludeId)) {
            String when = slot(o.getStartTime(), o.getEndTime()) + " (examen " + o.getModule().getCode() + ")";
            if (o.getFiliere().getId().equals(filiereId)) out.add("la filière a déjà un examen " + when);
            for (Room r : rooms) if (o.getRooms().stream().anyMatch(x -> x.getId().equals(r.getId()))) out.add("la salle " + r.getCode() + " est réservée " + when);
            for (User u : supervisors) if (o.getSupervisors().stream().anyMatch(x -> x.getId().equals(u.getId()))) out.add(u.getFullName() + " surveille déjà " + when);
        }
        for (ClassSession s : sessionRepository.findOverlapping(date, start, end, null)) {
            String when = slot(s.getStartTime(), s.getEndTime()) + " (cours " + s.getModule().getCode() + ")";
            if (s.getFiliere().getId().equals(filiereId)) out.add("la filière a cours " + when);
            for (Room r : rooms) if (s.getRoom().getId().equals(r.getId())) out.add("la salle " + r.getCode() + " est occupée " + when);
            for (User u : supervisors) if (s.getTeacher().getId().equals(u.getId())) out.add(u.getFullName() + " a cours " + when);
        }
        return out;
    }

    // ------------------------------------------------------------- planification automatique

    @Transactional(readOnly = true)
    public ExamPlanPreview preview(ExamPlanRequest r) {
        if (r.to().isBefore(r.from())) throw ApiException.badRequest("La fin de période doit suivre son début");
        Filiere f = filiereRepository.findById(r.filiereId()).orElseThrow(() -> ApiException.notFound("Filière introuvable"));
        ExamType type = r.type() == null ? ExamType.FINAL : r.type();
        List<CourseModule> modules = moduleRepository.search(f.getId(), null).stream()
                .filter(m -> r.semester() == null || r.semester().equals(m.getSemester()))
                .filter(m -> !examRepository.existsByModuleIdAndType(m.getId(), type)).toList();
        Agenda agenda = new Agenda();
        sessionRepository.findPlannedBetween(r.from(), r.to()).forEach(agenda::addSession);
        examRepository.findBetween(r.from(), r.to(), null).forEach(agenda::addExam);
        List<User> teachers = userRepository.findByRoleInAndEnabledTrue(List.of(Role.ENSEIGNANT, Role.CHEF_DEPARTEMENT));
        int students = students(f.getId()).size();
        ExamPlanner.Result res = ExamPlanner.plan(f.getId(), students, modules, roomRepository.findAllByOrderByCodeAsc(),
                teachers, agenda, r.from(), r.to(), r.durationMinutes() == null ? 120 : r.durationMinutes());
        return new ExamPlanPreview(f.getId(), type, students, res.slots(), res.unplaced());
    }

    public List<ExamResponse> applyPlan(ExamPlanApplyRequest r) {
        List<ExamResponse> created = new ArrayList<>();
        for (ExamPlanSlot s : r.slots()) {
            Exam e = new Exam();
            apply(e, new ExamRequest(s.moduleId(), s.date(), s.startTime(), s.endTime(), r.type(), s.roomIds(), s.supervisorIds(), null), null);
            examRepository.save(e);
            created.add(response(e));
        }
        Filiere f = filiereRepository.findById(r.filiereId()).orElseThrow(() -> ApiException.notFound("Filière introuvable"));
        notifications.notifyAll(students(f.getId()), NotificationType.EXAM_SCHEDULED, "Calendrier des examens publié",
                created.size() + " examen(s) programmé(s) du " + created.stream().map(ExamResponse::date).min(LocalDate::compareTo).map(d -> d.format(DATE)).orElse("")
                        + " au " + created.stream().map(ExamResponse::date).max(LocalDate::compareTo).map(d -> d.format(DATE)).orElse("") + ".",
                "/examens");
        // Les surveillants sont prévenus de leurs convocations
        Map<User, Integer> duty = new LinkedHashMap<>();
        for (ExamPlanSlot s : r.slots()) {
            for (Long id : s.supervisorIds()) userRepository.findById(id).ifPresent(u -> duty.merge(u, 1, Integer::sum));
        }
        duty.forEach((u, n) -> notifications.notify(u, NotificationType.EXAM_SCHEDULED, "Convocation de surveillance",
                "Vous surveillez " + n + " examen(s) de la filière " + f.getCode() + ".", "/examens"));
        return created;
    }

    private List<User> students(Long filiereId) {
        return userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, filiereId);
    }

    private ExamResponse response(Exam e) {
        return ExamResponse.of(e, students(e.getFiliere().getId()).size());
    }

    private void notifyStudents(Exam e, String title, boolean changed) {
        if (e.getDate().isBefore(LocalDate.now(clock))) return;
        String rooms = String.join(", ", e.getRooms().stream().map(Room::getCode).toList());
        notifications.notifyAll(students(e.getFiliere().getId()), NotificationType.EXAM_SCHEDULED, title,
                e.getModule().getName() + " (" + e.getType() + ") le " + e.getDate().format(DATE) + " de "
                        + slot(e.getStartTime(), e.getEndTime()) + ", salle(s) " + rooms + (changed ? " (nouvel horaire)." : "."),
                "/examens");
    }
}
