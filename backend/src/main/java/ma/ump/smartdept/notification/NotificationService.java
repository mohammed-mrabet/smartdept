package ma.ump.smartdept.notification;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationDtos.NotificationResponse;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Point d'entrée unique des notifications.
 * Chaque appel à {@link #notify} enregistre la notification (cloche de l'application) puis,
 * si l'utilisateur accepte les e-mails, déclenche l'envoi asynchrone après la validation de la transaction.
 */
@Service
@Transactional
public class NotificationService {

    static final int MAX_LIST = 100;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final boolean mailEnabled;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ApplicationEventPublisher events,
                               @Value("${app.mail.enabled:false}") boolean mailEnabled) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.mailEnabled = mailEnabled;
    }

    // ---------- création ----------

    public Notification notify(User recipient, NotificationType type, String title, String message, String link) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setType(type);
        n.setTitle(truncate(title, 200));
        n.setMessage(truncate(message, 1000));
        n.setLink(link);
        boolean sendEmail = mailEnabled && recipient.isEnabled() && recipient.isEmailNotifications();
        n.setEmailStatus(sendEmail ? EmailStatus.PENDING : EmailStatus.NOT_REQUESTED);
        Notification saved = notificationRepository.save(n);
        if (sendEmail) {
            events.publishEvent(new NotificationCreatedEvent(saved.getId()));
        }
        return saved;
    }

    /** Notifie plusieurs personnes (doublons et valeurs nulles ignorés). */
    public int notifyAll(Collection<User> recipients, NotificationType type, String title, String message, String link) {
        Map<Long, User> unique = new LinkedHashMap<>();
        recipients.stream().filter(Objects::nonNull).forEach(u -> unique.putIfAbsent(u.getId(), u));
        unique.values().forEach(u -> notify(u, type, title, message, link));
        return unique.size();
    }

    /** Notifie le chef de département et les administrateurs. */
    public int notifyManagers(NotificationType type, String title, String message, String link) {
        return notifyAll(userRepository.findByRoleInAndEnabledTrue(List.of(Role.CHEF_DEPARTEMENT, Role.ADMIN)),
                type, title, message, link);
    }

    /** Annonce à tous les utilisateurs actifs d'un rôle, ou à tout le monde si role est null. */
    public int broadcast(Role role, String title, String message, String link) {
        List<User> recipients = role == null
                ? userRepository.findByRoleInAndEnabledTrue(List.of(Role.values()))
                : userRepository.findByRoleAndEnabledTrueOrderByLastNameAsc(role);
        return notifyAll(recipients, NotificationType.ANNOUNCEMENT, title, message, link);
    }

    // ---------- consultation par le destinataire ----------

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User me, boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIST)));
        List<Notification> list = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(me.getId(), page)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(me.getId(), page);
        return list.stream().map(NotificationResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User me) {
        return notificationRepository.countByRecipientIdAndReadFalse(me.getId());
    }

    public NotificationResponse markRead(User me, Long id) {
        Notification n = findOwned(me, id);
        n.markRead();
        return NotificationResponse.of(notificationRepository.save(n));
    }

    public int markAllRead(User me) {
        return notificationRepository.markAllRead(me.getId(), LocalDateTime.now());
    }

    public void delete(User me, Long id) {
        notificationRepository.delete(findOwned(me, id));
    }

    private Notification findOwned(User me, Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification introuvable : " + id));
        if (!n.getRecipient().getId().equals(me.getId())) {
            throw ApiException.forbidden("Cette notification ne vous est pas adressée");
        }
        return n;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
