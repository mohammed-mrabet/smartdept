package ma.ump.smartdept.pfe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PfeApplicationRepository extends JpaRepository<PfeApplication, Long> {

    List<PfeApplication> findByStudentIdOrderByPreferenceRankAsc(Long studentId);

    List<PfeApplication> findBySubjectIdOrderByPreferenceRankAscCreatedAtAsc(Long subjectId);

    List<PfeApplication> findByStudentIdAndStatus(Long studentId, ApplicationStatus status);

    List<PfeApplication> findBySubjectIdAndStatus(Long subjectId, ApplicationStatus status);

    /** Candidatures en attente, par rang puis par ordre d'arrivée (affectation automatique). */
    List<PfeApplication> findByStatusAndSubjectStatusInOrderByPreferenceRankAscCreatedAtAscIdAsc(
            ApplicationStatus status, Collection<SubjectStatus> subjectStatuses);

    boolean existsBySubjectIdAndStudentId(Long subjectId, Long studentId);

    long countBySubjectIdAndStatus(Long subjectId, ApplicationStatus status);

    long countByStatus(ApplicationStatus status);
}
