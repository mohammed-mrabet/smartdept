package ma.ump.smartdept.pfe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface PfeDefenseRepository extends JpaRepository<PfeDefense, Long> {

    Optional<PfeDefense> findByProjectId(Long projectId);

    @Query("""
            SELECT d FROM PfeDefense d
            WHERE d.defenseDate BETWEEN :from AND :to
            ORDER BY d.defenseDate ASC, d.startTime ASC
            """)
    List<PfeDefense> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Soutenances programmées qui chevauchent le créneau (salle ou jury en conflit). */
    @Query("""
            SELECT d FROM PfeDefense d
            WHERE d.status = :status
              AND d.defenseDate = :date
              AND d.startTime < :end
              AND d.endTime > :start
              AND (:excludeId IS NULL OR d.id <> :excludeId)
            """)
    List<PfeDefense> findOverlapping(@Param("date") LocalDate date,
                                     @Param("start") LocalTime start,
                                     @Param("end") LocalTime end,
                                     @Param("excludeId") Long excludeId,
                                     @Param("status") DefenseStatus status);

    @Query("SELECT d FROM PfeDefense d JOIN d.jury j WHERE j.id = :memberId ORDER BY d.defenseDate ASC, d.startTime ASC")
    List<PfeDefense> findByJuryMember(@Param("memberId") Long memberId);

    long countByStatus(DefenseStatus status);

    @Query("SELECT AVG(d.grade) FROM PfeDefense d WHERE d.status = :status AND d.grade IS NOT NULL")
    Double averageGrade(@Param("status") DefenseStatus status);
}
