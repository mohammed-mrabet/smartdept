package ma.ump.smartdept.pfe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InternshipConventionRepository extends JpaRepository<InternshipConvention, Long> {

    List<InternshipConvention> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<InternshipConvention> findByStatusOrderByCreatedAtAsc(ConventionStatus status);

    List<InternshipConvention> findAllByOrderByCreatedAtDesc();

    long countByStatus(ConventionStatus status);
}
