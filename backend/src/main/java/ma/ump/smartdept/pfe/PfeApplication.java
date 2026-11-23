package ma.ump.smartdept.pfe;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

/** Candidature d'un étudiant à un sujet, avec son rang de préférence (1 = premier choix). */
@Entity
@Table(name = "pfe_applications",
        uniqueConstraints = @UniqueConstraint(name = "uq_application_subject_student", columnNames = {"subject_id", "student_id"}))
public class PfeApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private PfeSubject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "preference_rank", nullable = false)
    private int preferenceRank;

    @Column(columnDefinition = "TEXT")
    private String motivation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "decision_comment", length = 500)
    private String decisionComment;

    public PfeSubject getSubject() {
        return subject;
    }

    public void setSubject(PfeSubject subject) {
        this.subject = subject;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public int getPreferenceRank() {
        return preferenceRank;
    }

    public void setPreferenceRank(int preferenceRank) {
        this.preferenceRank = preferenceRank;
    }

    public String getMotivation() {
        return motivation;
    }

    public void setMotivation(String motivation) {
        this.motivation = motivation;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }
}
