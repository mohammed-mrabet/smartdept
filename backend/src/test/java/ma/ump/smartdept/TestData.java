package ma.ump.smartdept;

import ma.ump.smartdept.pfe.*;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Fabrique d'objets pour les tests unitaires (sans base de données). */
public final class TestData {

    /** "Aujourd'hui" pour tous les tests : lundi 23 novembre 2026. */
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);

    private TestData() {
    }

    public static Filiere filiere(long id, String code) {
        Filiere f = new Filiere();
        f.setId(id);
        f.setCode(code);
        f.setName("Filière " + code);
        f.setLevel("MASTER");
        return f;
    }

    public static User user(long id, String first, String last, Role role) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(first.toLowerCase() + "@test.ma");
        u.setPasswordHash("hash");
        u.setRole(role);
        return u;
    }

    public static User student(long id, String first, Filiere f) {
        User u = user(id, first, "Etudiant", Role.ETUDIANT);
        u.setFiliere(f);
        return u;
    }

    public static User teacher(long id, String first, String specialties) {
        User u = user(id, first, "Prof", Role.ENSEIGNANT);
        u.setSpecialties(specialties);
        return u;
    }

    public static PfeSubject subject(long id, String title, User supervisor, Filiere f, SubjectStatus status, int max) {
        PfeSubject s = new PfeSubject();
        s.setId(id);
        s.setTitle(title);
        s.setDescription("Description détaillée du sujet " + title + " pour les tests unitaires du module PFE.");
        s.setKeywords("Spring Boot, Angular");
        s.setType(SubjectType.ACADEMIQUE);
        s.setStatus(status);
        s.setMaxStudents(max);
        s.setSupervisor(supervisor);
        s.setFiliere(f);
        s.setAcademicYear("2026-2027");
        return s;
    }

    public static PfeApplication application(long id, PfeSubject s, User student, int rank) {
        PfeApplication a = new PfeApplication();
        a.setId(id);
        a.setSubject(s);
        a.setStudent(student);
        a.setPreferenceRank(rank);
        return a;
    }

    public static PfeProject project(long id, PfeSubject s, User... students) {
        PfeProject p = new PfeProject();
        p.setId(id);
        p.setSubject(s);
        p.setSupervisor(s.getSupervisor());
        for (User st : students) {
            p.getStudents().add(st);
        }
        return p;
    }

    public static PfeMilestone milestone(long id, PfeProject p, String title, MilestoneStatus status, LocalDate due) {
        PfeMilestone m = new PfeMilestone();
        m.setId(id);
        m.setProject(p);
        m.setTitle(title);
        m.setStatus(status);
        m.setDueDate(due);
        m.setOrderIndex(p.getMilestones().size() + 1);
        p.getMilestones().add(m);
        return m;
    }
}
