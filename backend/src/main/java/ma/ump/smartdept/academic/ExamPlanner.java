package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.ExamPlanSlot;
import ma.ump.smartdept.user.User;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Calendrier d'examens automatique pour une filière : au plus un examen par jour, répartis régulièrement sur la période,
 * salles cumulées jusqu'à couvrir l'effectif, deux surveillants par salle choisis parmi les enseignants libres
 * (l'enseignant du module d'abord, puis les moins sollicités).
 */
public final class ExamPlanner {

    public record Result(List<ExamPlanSlot> slots, List<String> unplaced) {
    }

    private ExamPlanner() {
    }

    public static Result plan(long filiereId, int students, List<CourseModule> modules, List<Room> rooms, List<User> teachers,
                              Agenda agenda, LocalDate from, LocalDate to, int durationMinutes) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) days.add(d);
        }
        List<CourseModule> ordered = new ArrayList<>(modules);
        ordered.sort(Comparator.comparingDouble(CourseModule::getCoefficient).reversed().thenComparing(CourseModule::getCode));
        List<ExamPlanSlot> slots = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        if (days.isEmpty()) {
            ordered.forEach(m -> unplaced.add(m.getCode() + " : période vide"));
            return new Result(slots, unplaced);
        }
        Map<Long, Integer> duty = new HashMap<>();
        Set<LocalDate> usedDays = new HashSet<>();
        String f = "F" + filiereId;
        double step = Math.max(1.0, days.size() / (double) Math.max(1, ordered.size()));
        int index = 0;
        for (CourseModule m : ordered) {
            int target = (int) Math.min(days.size() - 1, Math.round(index * step));
            index++;
            boolean done = false;
            // On part du jour idéal puis on s'en écarte progressivement
            for (int offset = 0; offset < days.size() * 2 && !done; offset++) {
                int i = target + (offset % 2 == 0 ? offset / 2 : -(offset / 2 + 1));
                if (i < 0 || i >= days.size()) continue;
                LocalDate date = days.get(i);
                if (usedDays.contains(date)) continue;
                for (LocalTime start : List.of(LocalTime.of(9, 0), LocalTime.of(14, 0))) {
                    LocalTime end = start.plusMinutes(durationMinutes);
                    if (!agenda.isFree(f, date, start, end)) continue;
                    List<Room> chosen = pickRooms(rooms, students, agenda, date, start, end);
                    if (chosen == null) continue;
                    List<User> supervisors = pickSupervisors(teachers, m, chosen.size() * 2, duty, agenda, date, start, end);
                    if (supervisors.size() < chosen.size()) continue;
                    slots.add(new ExamPlanSlot(m.getId(), m.getCode(), m.getName(), date, start, end,
                            chosen.stream().map(Room::getId).toList(), chosen.stream().map(Room::getCode).toList(),
                            supervisors.stream().map(User::getId).toList(), supervisors.stream().map(User::getFullName).toList()));
                    agenda.add(f, date, start, end);
                    chosen.forEach(r -> agenda.add("R" + r.getId(), date, start, end));
                    supervisors.forEach(u -> {
                        agenda.add("T" + u.getId(), date, start, end);
                        duty.merge(u.getId(), 1, Integer::sum);
                    });
                    usedDays.add(date);
                    done = true;
                    break;
                }
            }
            if (!done) unplaced.add(m.getCode() + " : pas de jour libre avec assez de places et de surveillants");
        }
        slots.sort(Comparator.comparing(ExamPlanSlot::date).thenComparing(ExamPlanSlot::startTime));
        return new Result(slots, unplaced);
    }

    /** Salles libres (hors laboratoires), les plus grandes d'abord, jusqu'à couvrir l'effectif. */
    static List<Room> pickRooms(List<Room> rooms, int students, Agenda agenda, LocalDate date, LocalTime start, LocalTime end) {
        List<Room> free = rooms.stream().filter(Room::isActive).filter(r -> r.getType() != RoomType.LABO)
                .filter(r -> agenda.isFree("R" + r.getId(), date, start, end))
                .sorted(Comparator.comparingInt(Room::getCapacity).reversed().thenComparing(Room::getCode)).toList();
        // Une seule salle suffit ? on prend la plus petite qui convient
        Optional<Room> single = free.stream().filter(r -> r.getCapacity() >= students).min(Comparator.comparingInt(Room::getCapacity));
        if (single.isPresent()) return List.of(single.get());
        List<Room> chosen = new ArrayList<>();
        int seats = 0;
        for (Room r : free) {
            chosen.add(r);
            seats += r.getCapacity();
            if (seats >= students) return chosen;
        }
        return null;
    }

    static List<User> pickSupervisors(List<User> teachers, CourseModule m, int wanted, Map<Long, Integer> duty, Agenda agenda,
                                      LocalDate date, LocalTime start, LocalTime end) {
        List<User> free = new ArrayList<>(teachers.stream().filter(t -> agenda.isFree("T" + t.getId(), date, start, end)).toList());
        free.sort(Comparator.comparing((User t) -> m.getTeacher() != null && t.getId().equals(m.getTeacher().getId()) ? 0 : 1)
                .thenComparing(t -> duty.getOrDefault(t.getId(), 0))
                .thenComparing(User::getLastName));
        return free.stream().limit(wanted).toList();
    }
}
