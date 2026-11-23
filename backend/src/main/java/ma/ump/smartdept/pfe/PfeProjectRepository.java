package ma.ump.smartdept.pfe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PfeProjectRepository extends JpaRepository<PfeProject, Long> {

    Optional<PfeProject> findBySubjectId(Long subjectId);

    @Query("SELECT p FROM PfeProject p JOIN p.students s WHERE s.id = :studentId")
    Optional<PfeProject> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(p) > 0 FROM PfeProject p JOIN p.students s WHERE s.id = :studentId")
    boolean existsByStudentId(@Param("studentId") Long studentId);

    List<PfeProject> findBySupervisorIdOrderByCreatedAtDesc(Long supervisorId);

    List<PfeProject> findAllByOrderByCreatedAtDesc();

    long countByStatus(ProjectStatus status);
}
