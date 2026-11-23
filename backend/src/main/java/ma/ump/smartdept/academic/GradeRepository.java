package ma.ump.smartdept.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GradeRepository extends JpaRepository<Grade, Long> {

    @Query("select g from Grade g join fetch g.student where g.module.id = :moduleId")
    List<Grade> findByModule(@Param("moduleId") Long moduleId);

    @Query("select g from Grade g join fetch g.module m join fetch m.filiere where g.student.id = :studentId and (:publishedOnly = false or g.published = true)")
    List<Grade> findByStudent(@Param("studentId") Long studentId, @Param("publishedOnly") boolean publishedOnly);

    @Query("select g from Grade g join fetch g.module m join fetch g.student where g.published = true")
    List<Grade> findAllPublished();

    List<Grade> findByModuleIdAndAssessment(Long moduleId, Assessment assessment);
}
