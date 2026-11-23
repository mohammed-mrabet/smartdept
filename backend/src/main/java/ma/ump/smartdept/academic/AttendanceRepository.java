package ma.ump.smartdept.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("select a from Attendance a join fetch a.student where a.session.id = :sessionId")
    List<Attendance> findBySession(@Param("sessionId") Long sessionId);

    @Query("""
            select a from Attendance a join fetch a.session s join fetch s.module join fetch s.teacher
            where a.student.id = :studentId order by s.date desc, s.startTime desc
            """)
    List<Attendance> findByStudent(@Param("studentId") Long studentId);

    @Query("""
            select a from Attendance a join fetch a.session s join fetch s.module join fetch a.student
            where a.justificationStatus = ma.ump.smartdept.academic.JustificationStatus.PENDING order by a.justifiedAt
            """)
    List<Attendance> findPendingJustifications();

    @Query("""
            select count(a) from Attendance a
            where a.student.id = :studentId and a.session.module.id = :moduleId
              and a.status = ma.ump.smartdept.academic.AttendanceStatus.ABSENT
              and a.justificationStatus <> ma.ump.smartdept.academic.JustificationStatus.ACCEPTED
            """)
    long countUnjustified(@Param("studentId") Long studentId, @Param("moduleId") Long moduleId);

    /** Par étudiant : nombre de séances pointées et nombre d'absences non justifiées. */
    @Query("""
            select a.student.id, count(a),
                   sum(case when a.status = ma.ump.smartdept.academic.AttendanceStatus.ABSENT
                             and a.justificationStatus <> ma.ump.smartdept.academic.JustificationStatus.ACCEPTED then 1 else 0 end)
            from Attendance a group by a.student.id
            """)
    List<Object[]> statsByStudent();
}
