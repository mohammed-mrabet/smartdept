package ma.ump.smartdept.pfe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PfeMilestoneRepository extends JpaRepository<PfeMilestone, Long> {

    /** Jalons non rendus dont l'échéance tombe entre deux dates (rappels automatiques). */
    List<PfeMilestone> findByStatusInAndDueDateBetween(Collection<MilestoneStatus> statuses, LocalDate from, LocalDate to);

    long countByStatus(MilestoneStatus status);

    /** Livrables en attente de relecture pour un encadrant. */
    long countByProjectSupervisorIdAndStatus(Long supervisorId, MilestoneStatus status);
}
