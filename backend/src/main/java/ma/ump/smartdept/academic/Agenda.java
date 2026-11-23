package ma.ump.smartdept.academic;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Occupation des ressources (enseignant, salle, filière) utilisée par les planificateurs.
 * Clés : "T12" (enseignant 12), "R3" (salle 3), "F1" (filière 1).
 */
public class Agenda {

    private record Busy(LocalDate date, LocalTime start, LocalTime end) {
    }

    private final Map<String, List<Busy>> busy = new HashMap<>();

    public void add(String key, LocalDate date, LocalTime start, LocalTime end) {
        busy.computeIfAbsent(key, k -> new ArrayList<>()).add(new Busy(date, start, end));
    }

    public boolean isFree(String key, LocalDate date, LocalTime start, LocalTime end) {
        for (Busy b : busy.getOrDefault(key, List.of())) {
            if (b.date().equals(date) && b.start().isBefore(end) && b.end().isAfter(start)) return false;
        }
        return true;
    }

    public long count(String key, LocalDate date) {
        return busy.getOrDefault(key, List.of()).stream().filter(b -> b.date().equals(date)).count();
    }

    public boolean adjacent(String key, LocalDate date, LocalTime start, LocalTime end) {
        for (Busy b : busy.getOrDefault(key, List.of())) {
            if (b.date().equals(date) && (Math.abs(b.end().toSecondOfDay() - start.toSecondOfDay()) <= 1800
                    || Math.abs(end.toSecondOfDay() - b.start().toSecondOfDay()) <= 1800)) return true;
        }
        return false;
    }

    public void addSession(ClassSession s) {
        add("T" + s.getTeacher().getId(), s.getDate(), s.getStartTime(), s.getEndTime());
        add("R" + s.getRoom().getId(), s.getDate(), s.getStartTime(), s.getEndTime());
        add("F" + s.getFiliere().getId(), s.getDate(), s.getStartTime(), s.getEndTime());
    }

    public void addExam(Exam e) {
        add("F" + e.getFiliere().getId(), e.getDate(), e.getStartTime(), e.getEndTime());
        e.getRooms().forEach(r -> add("R" + r.getId(), e.getDate(), e.getStartTime(), e.getEndTime()));
        e.getSupervisors().forEach(u -> add("T" + u.getId(), e.getDate(), e.getStartTime(), e.getEndTime()));
    }
}
