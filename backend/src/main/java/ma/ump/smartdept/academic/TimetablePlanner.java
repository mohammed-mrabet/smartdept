package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.PlannedSlot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Planification automatique d'une semaine type pour une filière : algorithme glouton à contraintes.
 * Contraintes dures : enseignant, salle et filière libres ; salle assez grande ; TP en laboratoire, CM hors laboratoire.
 * Préférences (coût à minimiser) : répartir un module sur plusieurs jours, équilibrer les journées, CM le matin,
 * éviter la fin d'après-midi et le samedi, regrouper les cours d'un enseignant, limiter les places vides.
 * Les besoins les plus contraints (TP, enseignants les plus chargés) sont placés en premier.
 */
public final class TimetablePlanner {

    public static final List<LocalTime[]> SLOTS = List.of(
            new LocalTime[]{LocalTime.of(8, 30), LocalTime.of(10, 30)},
            new LocalTime[]{LocalTime.of(10, 45), LocalTime.of(12, 45)},
            new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(16, 0)},
            new LocalTime[]{LocalTime.of(16, 15), LocalTime.of(18, 15)});

    public record Need(CourseModule module, SessionType type) {
    }

    public record Result(List<PlannedSlot> slots, List<String> unplaced, int required) {
    }

    private TimetablePlanner() {
    }

    public static Result plan(long filiereId, int students, List<CourseModule> modules, List<Room> rooms,
                              Agenda agenda, LocalDate monday) {
        List<Need> needs = new ArrayList<>();
        Map<Long, Integer> teacherLoad = new HashMap<>();
        for (CourseModule m : modules) {
            for (int i = 0; i < m.getTpPerWeek(); i++) needs.add(new Need(m, SessionType.TP));
            for (int i = 0; i < m.getCmPerWeek(); i++) needs.add(new Need(m, SessionType.CM));
            for (int i = 0; i < m.getTdPerWeek(); i++) needs.add(new Need(m, SessionType.TD));
            if (m.getTeacher() != null) {
                teacherLoad.merge(m.getTeacher().getId(), m.getCmPerWeek() + m.getTdPerWeek() + m.getTpPerWeek(), Integer::sum);
            }
        }
        needs.sort(Comparator
                .comparingInt((Need n) -> n.type() == SessionType.TP ? 0 : n.type() == SessionType.CM ? 1 : 2)
                .thenComparing(n -> -teacherLoad.getOrDefault(n.module().getTeacher() == null ? -1L : n.module().getTeacher().getId(), 0))
                .thenComparing(n -> n.module().getCode()));

        boolean hasLab = rooms.stream().anyMatch(r -> r.isActive() && r.getType() == RoomType.LABO);
        List<PlannedSlot> placed = new ArrayList<>();
        List<String> unplaced = new ArrayList<>();
        Map<String, Integer> moduleDays = new HashMap<>();
        String f = "F" + filiereId;

        for (Need need : needs) {
            CourseModule m = need.module();
            if (m.getTeacher() == null) {
                unplaced.add(m.getCode() + " " + need.type() + " : aucun enseignant affecté au module");
                continue;
            }
            String t = "T" + m.getTeacher().getId();
            double bestCost = Double.MAX_VALUE;
            PlannedSlot best = null;
            for (int d = 0; d < 6; d++) {
                LocalDate date = monday.plusDays(d);
                int maxSlot = date.getDayOfWeek() == DayOfWeek.SATURDAY ? 2 : SLOTS.size();
                for (int s = 0; s < maxSlot; s++) {
                    LocalTime start = SLOTS.get(s)[0];
                    LocalTime end = SLOTS.get(s)[1];
                    if (!agenda.isFree(t, date, start, end) || !agenda.isFree(f, date, start, end)) continue;
                    Room room = pickRoom(rooms, need.type(), students, hasLab, agenda, date, start, end);
                    if (room == null) continue;
                    double cost = 0;
                    cost += 6 * moduleDays.getOrDefault(m.getId() + ":" + date, 0);
                    cost += 2 * agenda.count(f, date);
                    cost += s == 3 ? 3 : s == 2 ? 1 : 0;
                    cost += date.getDayOfWeek() == DayOfWeek.SATURDAY ? 4 : 0;
                    cost += need.type() == SessionType.CM && s >= 2 ? 2 : 0;
                    cost -= agenda.adjacent(t, date, start, end) ? 1 : 0;
                    cost += (room.getCapacity() - students) / 100.0;
                    cost += d * 0.01; // départage stable : le plus tôt dans la semaine
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new PlannedSlot(m.getId(), m.getCode(), m.getName(), m.getTeacher().getId(),
                                m.getTeacher().getFullName(), room.getId(), room.getCode(), date, start, end, need.type(),
                                reason(need, room, students));
                    }
                }
            }
            if (best == null) {
                unplaced.add(m.getCode() + " " + need.type() + " : aucun créneau où l'enseignant, la filière et une salle adaptée sont libres");
                continue;
            }
            placed.add(best);
            agenda.add(t, best.date(), best.startTime(), best.endTime());
            agenda.add(f, best.date(), best.startTime(), best.endTime());
            agenda.add("R" + best.roomId(), best.date(), best.startTime(), best.endTime());
            moduleDays.merge(m.getId() + ":" + best.date(), 1, Integer::sum);
        }
        placed.sort(Comparator.comparing(PlannedSlot::date).thenComparing(PlannedSlot::startTime));
        return new Result(placed, unplaced, needs.size());
    }

    /** Plus petite salle libre et adaptée (TP en laboratoire s'il en existe un, CM et TD hors laboratoire). */
    static Room pickRoom(List<Room> rooms, SessionType type, int students, boolean hasLab, Agenda agenda,
                         LocalDate date, LocalTime start, LocalTime end) {
        return rooms.stream()
                .filter(Room::isActive)
                .filter(r -> r.getCapacity() >= students)
                .filter(r -> type == SessionType.TP && hasLab ? r.getType() == RoomType.LABO : r.getType() != RoomType.LABO)
                .filter(r -> agenda.isFree("R" + r.getId(), date, start, end))
                .min(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getCode))
                .orElse(null);
    }

    private static String reason(Need need, Room room, int students) {
        return need.type() + " · salle " + room.getCode() + " (" + room.getCapacity() + " places pour " + students + " étudiants)";
    }
}
