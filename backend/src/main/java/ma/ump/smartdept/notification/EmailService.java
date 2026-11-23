package ma.ump.smartdept.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoi des e-mails de notification, en arrière-plan et seulement après la validation de la transaction.
 * Le canal (SMTP réel ou Mailpit) est choisi par MailRouter et enregistré sur la notification.
 */
@Service
public class EmailService {

    private final NotificationRepository notificationRepository;
    private final MailRouter mailRouter;
    private final String frontendUrl;

    public EmailService(NotificationRepository notificationRepository, MailRouter mailRouter,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.notificationRepository = notificationRepository;
        this.mailRouter = mailRouter;
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationRepository.findWithRecipient(event.notificationId()).ifPresent(this::send);
    }

    void send(Notification n) {
        String url = link(n);
        MailRouter.Delivery d = mailRouter.send(n.getRecipient().getEmail(), "[SmartDept] " + n.getTitle(),
                MailTemplates.text(n.getRecipient().getFirstName(), n.getMessage(), "Ouvrir SmartDept : " + url),
                MailTemplates.html(n.getRecipient().getFirstName(), n.getTitle(), n.getMessage(), "Ouvrir dans SmartDept", url,
                        "Notification automatique. Vous pouvez désactiver les e-mails dans votre profil."));
        n.setEmailStatus(d.success() ? EmailStatus.SENT : EmailStatus.FAILED);
        n.setEmailChannel(d.channel());
        String info = d.error() == null ? d.reason() : d.reason() + " — " + d.error();
        n.setEmailInfo(info == null ? null : info.length() > 255 ? info.substring(0, 255) : info);
        notificationRepository.save(n);
    }

    String link(Notification n) {
        return frontendUrl + (n.getLink() == null ? "/notifications" : n.getLink());
    }
}
