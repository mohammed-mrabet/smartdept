package ma.ump.smartdept.academic;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

/** Note d'un étudiant pour une composante d'un module (CC, examen, TP, rattrapage). Visible par l'étudiant une fois publiée. */
@Entity
@Table(name = "grades", uniqueConstraints = @UniqueConstraint(name = "uq_grade", columnNames = {"student_id", "module_id", "assessment"}))
public class Grade extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Assessment assessment;

    @Column(name = "grade_value", nullable = false)
    private double value;

    @Column(nullable = false)
    private boolean published;

    @Column(length = 300)
    private String comment;

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public CourseModule getModule() {
        return module;
    }

    public void setModule(CourseModule module) {
        this.module = module;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
