package ma.ump.smartdept.academic;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;

import java.time.LocalDate;
import java.time.LocalTime;

/** Séance de cours : un module, un enseignant, une salle, une filière, un créneau. */
@Entity
@Table(name = "class_sessions", indexes = @Index(name = "idx_session_date", columnList = "session_date"))
public class ClassSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @Column(name = "session_date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 5)
    private SessionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SessionStatus status = SessionStatus.PLANNED;

    @Column(length = 500)
    private String notes;

    @Column(name = "attendance_taken", nullable = false)
    private boolean attendanceTaken;

    public boolean overlaps(LocalDate d, LocalTime start, LocalTime end) {
        return date.equals(d) && startTime.isBefore(end) && endTime.isAfter(start);
    }

    public CourseModule getModule() {
        return module;
    }

    public void setModule(CourseModule module) {
        this.module = module;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
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

    public SessionType getType() {
        return type;
    }

    public void setType(SessionType type) {
        this.type = type;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isAttendanceTaken() {
        return attendanceTaken;
    }

    public void setAttendanceTaken(boolean attendanceTaken) {
        this.attendanceTaken = attendanceTaken;
    }
}
