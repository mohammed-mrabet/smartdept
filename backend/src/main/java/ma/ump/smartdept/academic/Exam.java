package ma.ump.smartdept.academic;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Examen : une ou plusieurs salles (assez de places pour la filière) et des surveillants. */
@Entity
@Table(name = "exams", indexes = @Index(name = "idx_exam_date", columnList = "exam_date"))
public class Exam extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @Column(name = "exam_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 15)
    private ExamType type;

    @ManyToMany
    @JoinTable(name = "exam_rooms", joinColumns = @JoinColumn(name = "exam_id"), inverseJoinColumns = @JoinColumn(name = "room_id"))
    private Set<Room> rooms = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "exam_supervisors", joinColumns = @JoinColumn(name = "exam_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> supervisors = new LinkedHashSet<>();

    @Column(length = 500)
    private String notes;

    public int capacity() {
        return rooms.stream().mapToInt(Room::getCapacity).sum();
    }

    public CourseModule getModule() {
        return module;
    }

    public void setModule(CourseModule module) {
        this.module = module;
    }

    public Filiere getFiliere() {
        return filiere;
    }

    public void setFiliere(Filiere filiere) {
        this.filiere = filiere;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public ExamType getType() {
        return type;
    }

    public void setType(ExamType type) {
        this.type = type;
    }

    public Set<Room> getRooms() {
        return rooms;
    }

    public void setRooms(Set<Room> rooms) {
        this.rooms = rooms;
    }

    public Set<User> getSupervisors() {
        return supervisors;
    }

    public void setSupervisors(Set<User> supervisors) {
        this.supervisors = supervisors;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
