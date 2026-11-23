package ma.ump.smartdept.pfe;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Jalon d'un projet : un livrable à rendre à une date, puis validé ou renvoyé par l'encadrant. */
@Entity
@Table(name = "pfe_milestones")
public class PfeMilestone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private PfeProject project;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.TODO;

    /** Lien vers le livrable (dépôt Git, document partagé...). */
    @Column(name = "deliverable_url", length = 500)
    private String deliverableUrl;

    @Column(name = "submission_comment", length = 1000)
    private String submissionComment;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(length = 1000)
    private String feedback;

    private Double grade;

    public boolean isLate(LocalDate today) {
        return status != MilestoneStatus.VALIDATED && status != MilestoneStatus.SUBMITTED && dueDate.isBefore(today);
    }

    public PfeProject getProject() {
        return project;
    }

    public void setProject(PfeProject project) {
        this.project = project;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public void setStatus(MilestoneStatus status) {
        this.status = status;
    }

    public String getDeliverableUrl() {
        return deliverableUrl;
    }

    public void setDeliverableUrl(String deliverableUrl) {
        this.deliverableUrl = deliverableUrl;
    }

    public String getSubmissionComment() {
        return submissionComment;
    }

    public void setSubmissionComment(String submissionComment) {
        this.submissionComment = submissionComment;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Double getGrade() {
        return grade;
    }

    public void setGrade(Double grade) {
        this.grade = grade;
    }
}
