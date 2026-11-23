package ma.ump.smartdept.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /** Charge aussi le destinataire (utilisé hors transaction par l'envoi d'e-mail asynchrone). */
    @Query("SELECT n FROM Notification n JOIN FETCH n.recipient WHERE n.id = :id")
    Optional<Notification> findWithRecipient(@Param("id") Long id);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);

    /** Évite d'envoyer deux fois le même rappel (même destinataire, même lien, même jour). */
    boolean existsByRecipientIdAndTypeAndLinkAndCreatedAtAfter(Long recipientId, NotificationType type, String link,
                                                               LocalDateTime after);
}
