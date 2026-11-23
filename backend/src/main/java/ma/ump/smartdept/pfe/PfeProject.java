package ma.ump.smartdept.pfe;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Projet de PFE : un sujet affecté à 1 ou 2 étudiants, suivi par jalons jusqu'à la soutenance. */
@Entity
@Table(name = "pfe_projects")
public class PfeProject extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false, unique = true)
    private PfeSubject subject;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "pfe_project_students",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id"))
    private Set<User> students = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @Column(name = "external_supervisor_name", length = 150)
    private String externalSupervisorName;

    @Column(name = "external_supervisor_email", length = 150)
    private String externalSupervisorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.IN_PROGRESS;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<PfeMilestone> milestones = new ArrayList<>();

    public boolean hasMember(User user) {
        return students.stream().anyMatch(s -> s.getId().equals(user.getId()));
    }

    /** Pourcentage de jalons validés (0 à 100). */
    public int progress() {
        if (milestones.isEmpty()) {
            return 0;
        }
        long validated = milestones.stream().filter(m -> m.getStatus() == MilestoneStatus.VALIDATED).count();
        return (int) Math.round(100.0 * validated / milestones.size());
    }

    public PfeSubject getSubject() {
        return subject;
    }

    public void setSubject(PfeSubject subject) {
        this.subject = subject;
    }

    public Set<User> getStudents() {
        return students;
    }

    public void setStudents(Set<User> students) {
        this.students = students;
    }

    public User getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(User supervisor) {
        this.supervisor = supervisor;
    }

    public String getExternalSupervisorName() {
        return externalSupervisorName;
    }

    public void setExternalSupervisorName(String externalSupervisorName) {
        this.externalSupervisorName = externalSupervisorName;
    }

    public String getExternalSupervisorEmail() {
        return externalSupervisorEmail;
    }

    public void setExternalSupervisorEmail(String externalSupervisorEmail) {
        this.externalSupervisorEmail = externalSupervisorEmail;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public List<PfeMilestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<PfeMilestone> milestones) {
        this.milestones = milestones;
    }
}
