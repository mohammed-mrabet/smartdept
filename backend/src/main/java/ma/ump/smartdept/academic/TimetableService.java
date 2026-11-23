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

/**
 * Emploi du temps : consultation par semaine (filière, enseignant, salle ou « le mien »), création manuelle ou en série,
 * planification automatique, annulation. Chaque séance est contrôlée : salle, enseignant et filière libres (409),
 * salle assez grande, examens inclus.
 */
@Service
@Transactional
public class TimetableService {

    private final ClassSessionRepository sessionRepository;
    private final ExamRepository examRepository;
    private final CourseModuleRepository moduleRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final FiliereRepository filiereRepository;
    private final ReferenceService references;
    private final NotificationService notifications;
    private final Clock clock;

    public TimetableService(ClassSessionRepository sessionRepository, ExamRepository examRepository,
                            CourseModuleRepository moduleRepository, RoomRepository roomRepository, UserRepository userRepository,
                            FiliereRepository filiereRepository, ReferenceService references, NotificationService notifications,
                            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.examRepository = examRepository;
        this.moduleRepository = moduleRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.filiereRepository = filiereRepository;
        this.references = references;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> week(LocalDate day, Long filiereId, Long teacherId, Long roomId) {
        LocalDate monday = monday(day == null ? LocalDate.now(clock) : day);
        return sessionRepository.findBetween(monday, monday.plusDays(6), filiereId, teacherId, roomId).stream()
                .map(SessionResponse::of).toList();
    }

    /** Étudiant : sa filière ; enseignant : ses séances. */
    @Transactional(readOnly = true)
    public List<SessionResponse> mine(User me, LocalDate day) {
        if (isStudent(me)) {
            if (me.getFiliere() == null) return List.of();
            return week(day, me.getFiliere().getId(), null, null);
        }
        return week(day, null, me.getId(), null);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(Long id) {
        return SessionResponse.of(find(id));
    }

    public List<SessionResponse> create(SessionRequest r) {
        CourseModule module = references.module(r.moduleId());
        User teacher = teacherFor(r.teacherId(), module);
        Room room = references.room(r.roomId());
        checkTimes(r.startTime(), r.endTime());
        int weeks = r.weeks() == null ? 1 : r.weeks();
        int students = students(module.getFiliere().getId());
        checkRoom(room, students);
        // Tout ou rien : la série n'est créée que si aucune semaine n'est en conflit
        List<String> conflicts = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            LocalDate date = r.date().plusWeeks(w);
            conflicts.addAll(conflicts(date, r.startTime(), r.endTime(), teacher.getId(), room.getId(),
                    module.getFiliere().getId(), null).stream().map(c -> date.format(DATE) + " : " + c).toList());
        }
        if (!conflicts.isEmpty()) throw ApiException.conflict("Conflit — " + String.join(" ; ", conflicts));
        List<SessionResponse> created = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            ClassSession s = new ClassSession();
            s.setModule(module);
            s.setTeacher(teacher);
            s.setRoom(room);
            s.setFiliere(module.getFiliere());
            s.setDate(r.date().plusWeeks(w));
            s.setStartTime(r.startTime());
            s.setEndTime(r.endTime());
            s.setType(r.type());
            s.setNotes(blank(r.notes()));
            created.add(SessionResponse.of(sessionRepository.save(s)));
        }
        if (weeks > 1) {
            notifyFiliere(module.getFiliere(), NotificationType.TIMETABLE_PUBLISHED, "Emploi du temps mis à jour",
                    module.getCode() + " " + r.type() + " ajouté chaque semaine (" + weeks + " semaines) à partir du "
                            + r.date().format(DATE) + ", " + slot(r.startTime(), r.endTime()) + ", salle " + room.getCode() + ".",
                    "/emploi-du-temps?semaine=" + r.date());
        }
        return created;
    }

    public SessionResponse update(Long id, SessionRequest r) {
        ClassSession s = find(id);
        CourseModule module = references.module(r.moduleId());
        User teacher = teacherFor(r.teacherId(), module);
        Room room = references.room(r.roomId());
        checkTimes(r.startTime(), r.endTime());
        checkRoom(room, students(module.getFiliere().getId()));
        List<String> conflicts = conflicts(r.date(), r.startTime(), r.endTime(), teacher.getId(), room.getId(),
                module.getFiliere().getId(), id);
        if (!conflicts.isEmpty()) throw ApiException.conflict("Conflit le " + r.date().format(DATE) + " : " + String.join(" ; ", conflicts));
        boolean moved = !s.getDate().equals(r.date()) || !s.getStartTime().equals(r.startTime()) || !s.getRoom().getId().equals(room.getId());
        String before = s.getDate().format(DATE) + " " + slot(s.getStartTime(), s.getEndTime()) + " salle " + s.getRoom().getCode();
        s.setModule(module);
        s.setTeacher(teacher);
        s.setRoom(room);
        s.setFiliere(module.getFiliere());
        s.setDate(r.date());
        s.setStartTime(r.startTime());
        s.setEndTime(r.endTime());
        s.setType(r.type());
        s.setNotes(blank(r.notes()));
        s.setStatus(SessionStatus.PLANNED);
        if (moved && !r.date().isBefore(LocalDate.now(clock))) {
            notifyFiliere(module.getFiliere(), NotificationType.SESSION_CHANGED, "Séance déplacée : " + module.getCode(),
                    "La séance " + module.getCode() + " " + r.type() + " du " + before + " a lieu le " + r.date().format(DATE)
                            + " " + slot(r.startTime(), r.endTime()) + ", salle " + room.getCode() + ".",
                    "/emploi-du-temps?semaine=" + r.date());
        }
        return SessionResponse.of(s);
    }

    public SessionResponse cancel(Long id, String reason) {
        ClassSession s = find(id);
        s.setStatus(SessionStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) s.setNotes(reason.trim());
        if (!s.getDate().isBefore(LocalDate.now(clock))) {
            notifyFiliere(s.getFiliere(), NotificationType.SESSION_CHANGED, "Séance annulée : " + s.getModule().getCode(),
                    s.getModule().getName() + " (" + s.getType() + ") du " + s.getDate().format(DATE) + " "
                            + slot(s.getStartTime(), s.getEndTime()) + " est annulée." + (reason == null || reason.isBlank() ? "" : " Motif : " + reason.trim()),
                    "/emploi-du-temps?semaine=" + s.getDate());
        }
        return SessionResponse.of(s);
    }

    public void delete(Long id) {
        sessionRepository.delete(find(id));
    }

    public ClassSession find(Long id) {
        return sessionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Séance introuvable : " + id));
    }

    /** Liste lisible de tous les conflits du créneau (vide si le créneau est libre). */
    @Transactional(readOnly = true)
    public List<String> conflicts(LocalDate date, LocalTime start, LocalTime end, Long teacherId, Long roomId, Long filiereId, Long excludeId) {
        List<String> out = new ArrayList<>();
        for (ClassSession o : sessionRepository.findOverlapping(date, start, end, excludeId)) {
            String when = slot(o.getStartTime(), o.getEndTime()) + " (" + o.getModule().getCode() + ")";
            if (o.getTeacher().getId().equals(teacherId)) out.add(o.getTeacher().getFullName() + " a déjà cours " + when);
            if (o.getRoom().getId().equals(roomId)) out.add("la salle " + o.getRoom().getCode() + " est occupée " + when);
            if (o.getFiliere().getId().equals(filiereId)) out.add("la filière " + o.getFiliere().getCode() + " a déjà cours " + when);
        }
        for (Exam e : examRepository.findOverlapping(date, start, end, null)) {
            String when = slot(e.getStartTime(), e.getEndTime()) + " (examen " + e.getModule().getCode() + ")";
            if (e.getRooms().stream().anyMatch(r -> r.getId().equals(roomId))) out.add("la salle est réservée " + when);
            if (e.getFiliere().getId().equals(filiereId)) out.add("la filière passe un examen " + when);
            if (e.getSupervisors().stream().anyMatch(u -> u.getId().equals(teacherId))) out.add("l'enseignant surveille un examen " + when);
        }
        return out;
    }

    // ------------------------------------------------------------- planification automatique

    @Transactional(readOnly = true)
    public PlanPreview preview(PlanPreviewRequest r) {
        Filiere f = filiereRepository.findById(r.filiereId()).orElseThrow(() -> ApiException.notFound("Filière introuvable"));
        LocalDate monday = monday(r.weekStart());
        List<CourseModule> modules = moduleRepository.search(f.getId(), null).stream()
                .filter(m -> r.semester() == null || r.semester().equals(m.getSemester())).toList();
        if (modules.isEmpty()) throw ApiException.badRequest("Aucun module pour cette filière" + (r.semester() == null ? "" : " au semestre " + r.semester()));
        Agenda agenda = new Agenda();
        // Les séances déjà planifiées cette semaine restent en place (toutes filières confondues)
        sessionRepository.findPlannedBetween(monday, monday.plusDays(6)).forEach(agenda::addSession);
        examRepository.findBetween(monday, monday.plusDays(6), null).forEach(agenda::addExam);
        int students = students(f.getId());
        TimetablePlanner.Result res = TimetablePlanner.plan(f.getId(), students, modules, roomRepository.findAllByOrderByCodeAsc(),
                agenda, monday);
        Map<String, Integer> perDay = new LinkedHashMap<>();
        for (int d = 0; d < 6; d++) perDay.put(monday.plusDays(d).getDayOfWeek().name(), 0);
        res.slots().forEach(s -> perDay.merge(s.date().getDayOfWeek().name(), 1, Integer::sum));
        return new PlanPreview(f.getId(), monday, students, res.required(), res.slots(), res.unplaced(), perDay);
    }

    /** Crée les séances validées par le chef, répétées sur plusieurs semaines ; un conflit apparu entre-temps ignore la séance. */
    public PlanApplyResult apply(PlanApplyRequest r) {
        Filiere f = filiereRepository.findById(r.filiereId()).orElseThrow(() -> ApiException.notFound("Filière introuvable"));
        int created = 0;
        int skipped = 0;
        List<String> conflicts = new ArrayList<>();
        for (int w = 0; w < r.weeks(); w++) {
            for (SlotInput in : r.slots()) {
                CourseModule m = references.module(in.moduleId());
                if (!m.getFiliere().getId().equals(f.getId())) throw ApiException.badRequest(m.getCode() + " n'appartient pas à la filière " + f.getCode());
                User teacher = teacherFor(in.teacherId(), m);
                Room room = references.room(in.roomId());
                LocalDate date = in.date().plusWeeks(w);
                List<String> c = conflicts(date, in.startTime(), in.endTime(), teacher.getId(), room.getId(), f.getId(), null);
                if (!c.isEmpty()) {
                    skipped++;
                    conflicts.add(date.format(DATE) + " " + m.getCode() + " : " + String.join(", ", c));
                    continue;
                }
                ClassSession s = new ClassSession();
                s.setModule(m);
                s.setTeacher(teacher);
                s.setRoom(room);
                s.setFiliere(f);
                s.setDate(date);
                s.setStartTime(in.startTime());
                s.setEndTime(in.endTime());
                s.setType(in.type());
                sessionRepository.save(s);
                sessionRepository.flush();
                created++;
            }
        }
        if (created > 0) {
            LocalDate first = r.slots().stream().map(SlotInput::date).min(LocalDate::compareTo).orElse(LocalDate.now(clock));
            notifyFiliere(f, NotificationType.TIMETABLE_PUBLISHED, "Nouvel emploi du temps publié",
                    created + " séance(s) planifiée(s) sur " + r.weeks() + " semaine(s) à partir du " + monday(first).format(DATE) + ".",
                    "/emploi-du-temps?semaine=" + monday(first));
        }
        return new PlanApplyResult(created, skipped, conflicts);
    }

    // ------------------------------------------------------------- outils

    private User teacherFor(Long teacherId, CourseModule module) {
        if (teacherId == null) {
            if (module.getTeacher() == null) throw ApiException.badRequest("Le module " + module.getCode() + " n'a pas d'enseignant : précisez teacherId");
            return module.getTeacher();
        }
        User t = userRepository.findById(teacherId).orElseThrow(() -> ApiException.notFound("Enseignant introuvable : " + teacherId));
        if (t.getRole() != Role.ENSEIGNANT && t.getRole() != Role.CHEF_DEPARTEMENT) throw ApiException.badRequest(t.getFullName() + " n'est pas enseignant");
        return t;
    }

    private static void checkRoom(Room room, int students) {
        if (!room.isActive()) throw ApiException.badRequest("La salle " + room.getCode() + " est hors service");
        if (room.getCapacity() < students) {
            throw ApiException.badRequest("La salle " + room.getCode() + " offre " + room.getCapacity() + " places pour " + students + " étudiants");
        }
    }

    int students(Long filiereId) {
        return userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, filiereId).size();
    }

    private void notifyFiliere(Filiere f, NotificationType type, String title, String message, String link) {
        notifications.notifyAll(userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, f.getId()), type, title, message, link);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
