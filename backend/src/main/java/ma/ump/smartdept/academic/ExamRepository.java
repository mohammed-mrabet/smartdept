package ma.ump.smartdept.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    @Query("""
            select distinct e from Exam e join fetch e.module m join fetch e.filiere left join fetch m.teacher
            where e.date between :from and :to and (:filiereId is null or e.filiere.id = :filiereId)
            order by e.date, e.startTime
            """)
    List<Exam> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("filiereId") Long filiereId);

    @Query("""
            select distinct e from Exam e join fetch e.module join fetch e.filiere
            where e.date = :date and e.startTime < :end and e.endTime > :start and (:excludeId is null or e.id <> :excludeId)
            """)
    List<Exam> findOverlapping(@Param("date") LocalDate date, @Param("start") LocalTime start,
                               @Param("end") LocalTime end, @Param("excludeId") Long excludeId);

    @Query("""
            select distinct e from Exam e join fetch e.module m join fetch e.filiere left join e.supervisors s
            where e.date between :from and :to and (m.teacher.id = :teacherId or s.id = :teacherId)
            order by e.date, e.startTime
            """)
    List<Exam> findForTeacher(@Param("teacherId") Long teacherId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByModuleIdAndType(Long moduleId, ExamType type);
}
