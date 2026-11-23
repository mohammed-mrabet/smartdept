package ma.ump.smartdept.notification;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Envoi des e-mails de notification, en arrière-plan et seulement après la validation
 * de la transaction (pas d'e-mail pour une action annulée).
 * En local, Mailpit intercepte les e-mails : http://localhost:8025
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String frontendUrl;

    public EmailService(NotificationRepository notificationRepository,
                        ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.mail.from}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationRepository.findWithRecipient(event.notificationId()).ifPresent(this::send);
    }

    void send(Notification n) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            n.setEmailStatus(EmailStatus.FAILED);
            notificationRepository.save(n);
            log.warn("Aucun serveur SMTP configuré : e-mail non envoyé (notification {})", n.getId());
            return;
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(n.getRecipient().getEmail());
            helper.setSubject("[SmartDept] " + n.getTitle());
            helper.setText(plainText(n), html(n));
            sender.send(mime);
            n.setEmailStatus(EmailStatus.SENT);
        } catch (Exception e) {
            n.setEmailStatus(EmailStatus.FAILED);
            log.warn("Échec de l'envoi de l'e-mail à {} : {}", n.getRecipient().getEmail(), e.getMessage());
        }
        notificationRepository.save(n);
    }

    String link(Notification n) {
        return frontendUrl + (n.getLink() == null ? "/notifications" : n.getLink());
    }

    String plainText(Notification n) {
        return "Bonjour " + n.getRecipient().getFirstName() + ",\n\n" + n.getMessage()
                + "\n\nOuvrir SmartDept : " + link(n)
                + "\n\nVous recevez cet e-mail car les notifications par e-mail sont activées dans votre profil.";
    }

    String html(Notification n) {
        String esc = HtmlUtils.htmlEscape(n.getMessage()).replace("\n", "<br>");
        return """
                <div style="font-family:Arial,sans-serif;background:#f4f6fb;padding:24px">
                  <div style="max-width:560px;margin:auto;background:#fff;border-radius:12px;overflow:hidden;border:1px solid #e3e8f0">
                    <div style="background:#1e3a8a;color:#fff;padding:16px 24px;font-size:18px;font-weight:bold">SmartDept</div>
                    <div style="padding:24px;color:#1f2937;font-size:15px;line-height:1.5">
                      <p>Bonjour %s,</p>
                      <h2 style="font-size:18px;color:#1e3a8a;margin:16px 0 8px">%s</h2>
                      <p>%s</p>
                      <p style="margin:24px 0"><a href="%s" style="background:#2563eb;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none">Ouvrir dans SmartDept</a></p>
                    </div>
                    <div style="padding:12px 24px;background:#f9fafb;color:#6b7280;font-size:12px">
                      Notification automatique. Vous pouvez désactiver les e-mails dans votre profil.
                    </div>
                  </div>
                </div>
                """.formatted(HtmlUtils.htmlEscape(n.getRecipient().getFirstName()),
                HtmlUtils.htmlEscape(n.getTitle()), esc, link(n));
    }
}
