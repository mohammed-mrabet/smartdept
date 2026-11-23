package ma.ump.smartdept.academic;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

import java.time.LocalDateTime;

/** Présence d'un étudiant à une séance ; une absence peut être justifiée puis validée par le chef de département. */
@Entity
@Table(name = "attendances", uniqueConstraints = @UniqueConstraint(name = "uq_attendance", columnNames = {"session_id", "student_id"}))
public class Attendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "justification_status", nullable = false, length = 10)
    private JustificationStatus justificationStatus = JustificationStatus.NONE;

    @Column(length = 1000)
    private String justification;

    @Column(name = "review_comment", length = 300)
    private String reviewComment;

    @Column(name = "justified_at")
    private LocalDateTime justifiedAt;

    public boolean isUnjustifiedAbsence() {
        return status == AttendanceStatus.ABSENT && justificationStatus != JustificationStatus.ACCEPTED;
    }

    public ClassSession getSession() {
        return session;
    }

    public void setSession(ClassSession session) {
        this.session = session;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public JustificationStatus getJustificationStatus() {
        return justificationStatus;
    }

    public void setJustificationStatus(JustificationStatus justificationStatus) {
        this.justificationStatus = justificationStatus;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public LocalDateTime getJustifiedAt() {
        return justifiedAt;
    }

    public void setJustifiedAt(LocalDateTime justifiedAt) {
        this.justifiedAt = justifiedAt;
    }
}
