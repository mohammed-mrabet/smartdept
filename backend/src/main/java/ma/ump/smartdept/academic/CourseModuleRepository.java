package ma.ump.smartdept.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    @Query("""
            select m from CourseModule m join fetch m.filiere left join fetch m.teacher
            where (:filiereId is null or m.filiere.id = :filiereId)
              and (:teacherId is null or m.teacher.id = :teacherId)
            order by m.filiere.code, m.semester, m.code
            """)
    List<CourseModule> search(@Param("filiereId") Long filiereId, @Param("teacherId") Long teacherId);

    boolean existsByCodeIgnoreCase(String code);
}
