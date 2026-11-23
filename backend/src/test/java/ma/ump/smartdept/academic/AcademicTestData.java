package ma.ump.smartdept.academic;

import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;

final class AcademicTestData {

    private AcademicTestData() {
    }

    static Room room(long id, String code, int capacity, RoomType type) {
        Room r = new Room();
        r.setId(id);
        r.setCode(code);
        r.setName(code);
        r.setCapacity(capacity);
        r.setType(type);
        r.setActive(true);
        return r;
    }

    static CourseModule module(long id, String code, Filiere f, User teacher, int cm, int td, int tp) {
        CourseModule m = new CourseModule();
        m.setId(id);
        m.setCode(code);
        m.setName("Module " + code);
        m.setFiliere(f);
        m.setSemester("S3");
        m.setTeacher(teacher);
        m.setCoefficient(1);
        m.setCmPerWeek(cm);
        m.setTdPerWeek(td);
        m.setTpPerWeek(tp);
        return m;
    }
}
