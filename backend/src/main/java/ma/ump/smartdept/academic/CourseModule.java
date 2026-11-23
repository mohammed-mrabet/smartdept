package ma.ump.smartdept.academic;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;

/**
 * Module d'enseignement d'une filière. Le volume hebdomadaire (séances de 2 h de CM, TD et TP)
 * sert à la planification automatique de l'emploi du temps.
 */
@Entity
@Table(name = "course_modules")
public class CourseModule extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filiere_id", nullable = false)
    private Filiere filiere;

    @Column(nullable = false, length = 5)
    private String semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @Column(nullable = false)
    private double coefficient = 1;

    @Column(name = "cm_per_week", nullable = false)
    private int cmPerWeek;

    @Column(name = "td_per_week", nullable = false)
    private int tdPerWeek;

    @Column(name = "tp_per_week", nullable = false)
    private int tpPerWeek;

    @Column(length = 500)
    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Filiere getFiliere() {
        return filiere;
    }

    public void setFiliere(Filiere filiere) {
        this.filiere = filiere;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(double coefficient) {
        this.coefficient = coefficient;
    }

    public int getCmPerWeek() {
        return cmPerWeek;
    }

    public void setCmPerWeek(int cmPerWeek) {
        this.cmPerWeek = cmPerWeek;
    }

    public int getTdPerWeek() {
        return tdPerWeek;
    }

    public void setTdPerWeek(int tdPerWeek) {
        this.tdPerWeek = tdPerWeek;
    }

    public int getTpPerWeek() {
        return tpPerWeek;
    }

    public void setTpPerWeek(int tpPerWeek) {
        this.tpPerWeek = tpPerWeek;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
