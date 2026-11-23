package ma.ump.smartdept.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    @Query("""
            select s from ClassSession s join fetch s.module join fetch s.teacher join fetch s.room join fetch s.filiere
            where s.date between :from and :to
              and (:filiereId is null or s.filiere.id = :filiereId)
              and (:teacherId is null or s.teacher.id = :teacherId)
              and (:roomId is null or s.room.id = :roomId)
            order by s.date, s.startTime
            """)
    List<ClassSession> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("filiereId") Long filiereId,
                                   @Param("teacherId") Long teacherId, @Param("roomId") Long roomId);

    /** Séances actives qui chevauchent le créneau (une séance qui finit à 10:30 ne gêne pas celle qui commence à 10:30). */
    @Query("""
            select s from ClassSession s join fetch s.module join fetch s.teacher join fetch s.room join fetch s.filiere
            where s.status = ma.ump.smartdept.academic.SessionStatus.PLANNED and s.date = :date
              and s.startTime < :end and s.endTime > :start and (:excludeId is null or s.id <> :excludeId)
            """)
    List<ClassSession> findOverlapping(@Param("date") LocalDate date, @Param("start") LocalTime start,
                                       @Param("end") LocalTime end, @Param("excludeId") Long excludeId);

    @Query("""
            select s from ClassSession s join fetch s.module join fetch s.room join fetch s.filiere join fetch s.teacher
            where s.status = ma.ump.smartdept.academic.SessionStatus.PLANNED and s.date between :from and :to
            """)
    List<ClassSession> findPlannedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByModuleIdAndStatusAndAttendanceTakenTrue(Long moduleId, SessionStatus status);

    long countByDateAndStatus(LocalDate date, SessionStatus status);
}
