package ma.ump.smartdept.pfe;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Soutenance d'un projet : date, salle, jury (dont un président), puis note et mention. */
@Entity
@Table(name = "pfe_defenses")
public class PfeDefense extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private PfeProject project;

    @Column(name = "defense_date", nullable = false)
    private LocalDate defenseDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 50)
    private String room;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "pfe_defense_jury",
            joinColumns = @JoinColumn(name = "defense_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<User> jury = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "president_id", nullable = false)
    private User president;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefenseStatus status = DefenseStatus.SCHEDULED;

    private Double grade;

    @Column(length = 30)
    private String mention;

    @Column(name = "jury_comment", length = 1000)
    private String juryComment;

    public boolean overlaps(LocalDate date, LocalTime start, LocalTime end) {
        return defenseDate.equals(date) && startTime.isBefore(end) && endTime.isAfter(start);
    }

    /** Mention à partir de la note sur 20. */
    public static String mentionFor(double grade) {
        if (grade >= 16) return "Très bien";
        if (grade >= 14) return "Bien";
        if (grade >= 12) return "Assez bien";
        if (grade >= 10) return "Passable";
        return "Ajourné";
    }

    public PfeProject getProject() {
        return project;
    }

    public void setProject(PfeProject project) {
        this.project = project;
    }

    public LocalDate getDefenseDate() {
        return defenseDate;
    }

    public void setDefenseDate(LocalDate defenseDate) {
        this.defenseDate = defenseDate;
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

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public Set<User> getJury() {
        return jury;
    }

    public void setJury(Set<User> jury) {
        this.jury = jury;
    }

    public User getPresident() {
        return president;
    }

    public void setPresident(User president) {
        this.president = president;
    }

    public DefenseStatus getStatus() {
        return status;
    }

    public void setStatus(DefenseStatus status) {
        this.status = status;
    }

    public Double getGrade() {
        return grade;
    }

    public void setGrade(Double grade) {
        this.grade = grade;
    }

    public String getMention() {
        return mention;
    }

    public void setMention(String mention) {
        this.mention = mention;
    }

    public String getJuryComment() {
        return juryComment;
    }

    public void setJuryComment(String juryComment) {
        this.juryComment = juryComment;
    }
}
