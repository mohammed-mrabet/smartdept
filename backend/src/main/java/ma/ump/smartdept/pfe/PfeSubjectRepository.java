package ma.ump.smartdept.pfe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PfeSubjectRepository extends JpaRepository<PfeSubject, Long> {

    @Query("""
            SELECT s FROM PfeSubject s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:filiereId IS NULL OR s.filiere.id = :filiereId)
              AND (:supervisorId IS NULL OR s.supervisor.id = :supervisorId)
              AND (:type IS NULL OR s.type = :type)
              AND (:q IS NULL
                   OR LOWER(s.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.keywords) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY s.createdAt DESC
            """)
    List<PfeSubject> search(@Param("status") SubjectStatus status,
                            @Param("filiereId") Long filiereId,
                            @Param("supervisorId") Long supervisorId,
                            @Param("type") SubjectType type,
                            @Param("q") String q);

    List<PfeSubject> findBySupervisorId(Long supervisorId);

    List<PfeSubject> findByStatus(SubjectStatus status);

    long countByStatus(SubjectStatus status);
}
