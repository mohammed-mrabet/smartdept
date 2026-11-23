package ma.ump.smartdept.notification;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Choisit le serveur d'envoi de chaque e-mail :
 * - SMTP réel (Gmail, Brevo, serveur de l'université…) pour une adresse valide dont le domaine reçoit du courrier ;
 * - Mailpit (boîte de test) pour une adresse invalide, un domaine de démonstration (@smartdept.ma, @exemple.ma…)
 *   ou un domaine sans serveur de messagerie (enregistrement MX absent) ;
 * - Mailpit aussi en secours si le SMTP réel refuse l'envoi (mot de passe d'application faux, quota atteint…).
 * Mode : app.mail.mode = auto (défaut), sandbox (tout vers Mailpit) ou smtp (tout vers le SMTP réel).
 */
@Component
public class MailRouter {

    private static final Logger log = LoggerFactory.getLogger(MailRouter.class);
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    public record Route(MailChannel channel, String reason) {
    }

    public record Delivery(MailChannel channel, boolean success, String reason, String error) {
    }

    public record Status(boolean enabled, String mode, boolean smtpConfigured, String smtpHost, int smtpPort, String smtpUser,
                         String from, String mailpitHost, int mailpitPort, String mailpitUiUrl, List<String> sandboxDomains,
                         boolean checkMx) {
    }

    private final boolean enabled;
    private final String mode;
    private final String from;
    private final JavaMailSenderImpl smtp;
    private final JavaMailSenderImpl mailpit;
    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String mailpitHost;
    private final int mailpitPort;
    private final String mailpitUiUrl;
    private final List<String> sandboxDomains;
    private final boolean checkMx;
    private final Map<String, Boolean> mxCache = new ConcurrentHashMap<>();

    public MailRouter(@Value("${app.mail.enabled:true}") boolean enabled,
                      @Value("${app.mail.mode:auto}") String mode,
                      @Value("${app.mail.from:}") String from,
                      @Value("${app.mail.smtp.host:}") String smtpHost,
                      @Value("${app.mail.smtp.port:587}") int smtpPort,
                      @Value("${app.mail.smtp.username:}") String smtpUser,
                      @Value("${app.mail.smtp.password:}") String smtpPassword,
                      @Value("${app.mail.smtp.security:starttls}") String security,
                      @Value("${app.mail.mailpit.host:localhost}") String mailpitHost,
                      @Value("${app.mail.mailpit.port:1025}") int mailpitPort,
                      @Value("${app.mail.mailpit.ui-url:http://localhost:8025}") String mailpitUiUrl,
                      @Value("${app.mail.sandbox-domains:smartdept.ma,smartdept.local,exemple.ma,exemple.fr,exemple.be,exemple.ca,example.com,example.es,example.ca,test,local,localhost,invalid}") String sandboxDomains,
                      @Value("${app.mail.check-mx:true}") boolean checkMx) {
        this.enabled = enabled;
        this.mode = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        this.smtpHost = smtpHost == null ? "" : smtpHost.trim();
        this.smtpPort = smtpPort;
        this.smtpUser = smtpUser == null ? "" : smtpUser.trim();
        // Gmail et Brevo exigent que l'expéditeur corresponde au compte authentifié (ou à un expéditeur validé)
        this.from = from != null && !from.isBlank() ? from.trim()
                : !this.smtpUser.isEmpty() && this.smtpUser.contains("@") ? "SmartDept <" + this.smtpUser + ">" : "SmartDept <no-reply@smartdept.local>";
        this.mailpitHost = mailpitHost;
        this.mailpitPort = mailpitPort;
        this.mailpitUiUrl = mailpitUiUrl;
        this.sandboxDomains = Arrays.stream(sandboxDomains.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
        this.checkMx = checkMx;
        this.smtp = this.smtpHost.isEmpty() ? null : sender(this.smtpHost, smtpPort, this.smtpUser, smtpPassword, security);
        this.mailpit = sender(mailpitHost, mailpitPort, "", "", "none");
    }

    private static JavaMailSenderImpl sender(String host, int port, String user, String password, String security) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(host);
        s.setPort(port);
        s.setDefaultEncoding("UTF-8");
        Properties p = s.getJavaMailProperties();
        boolean auth = user != null && !user.isBlank();
        if (auth) {
            s.setUsername(user);
            s.setPassword(password);
        }
        p.put("mail.smtp.auth", String.valueOf(auth));
        String sec = security == null ? "" : security.toLowerCase(Locale.ROOT);
        p.put("mail.smtp.starttls.enable", String.valueOf(sec.equals("starttls")));
        p.put("mail.smtp.starttls.required", String.valueOf(sec.equals("starttls")));
        p.put("mail.smtp.ssl.enable", String.valueOf(sec.equals("ssl")));
        p.put("mail.smtp.connectiontimeout", "8000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return s;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String mailpitUiUrl() {
        return mailpitUiUrl;
    }

    public Status status() {
        String masked = smtpUser.isEmpty() ? "" : smtpUser.replaceAll("(?<=.{2}).(?=[^@]*@)", "•");
        return new Status(enabled, mode, smtp != null, smtpHost, smtpPort, masked, from, mailpitHost, mailpitPort, mailpitUiUrl,
                sandboxDomains, checkMx);
    }

    /** Décide du canal sans rien envoyer (affiché sur la page d'administration). */
    public Route route(String email) {
        if (!enabled) return new Route(MailChannel.NONE, "Envoi d'e-mails désactivé (MAIL_ENABLED=false)");
        String address = email == null ? "" : email.trim();
        if (mode.equals("sandbox")) return new Route(MailChannel.MAILPIT, "Mode test : tous les e-mails vont dans Mailpit");
        if (!EMAIL.matcher(address).matches()) return new Route(MailChannel.MAILPIT, "Adresse e-mail invalide");
        String domain = address.substring(address.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        for (String d : sandboxDomains) {
            if (domain.equals(d) || domain.endsWith("." + d)) {
                return new Route(MailChannel.MAILPIT, "Domaine de démonstration (" + domain + ")");
            }
        }
        if (smtp == null) return new Route(MailChannel.MAILPIT, "Aucun serveur SMTP réel configuré (SMTP_HOST)");
        if (mode.equals("smtp")) return new Route(MailChannel.SMTP, "Mode SMTP : envoi réel");
        if (checkMx && !acceptsMail(domain)) {
            return new Route(MailChannel.MAILPIT, "Le domaine " + domain + " ne reçoit pas d'e-mails (aucun serveur MX)");
        }
        return new Route(MailChannel.SMTP, "Adresse valide : envoi réel par " + smtpHost);
    }

    /** Envoie l'e-mail par le canal choisi ; si le SMTP réel échoue, l'e-mail est déposé dans Mailpit. */
    public Delivery send(String to, String subject, String text, String html) {
        Route route = route(to);
        if (route.channel() == MailChannel.NONE) return new Delivery(MailChannel.NONE, false, route.reason(), null);
        if (route.channel() == MailChannel.SMTP) {
            try {
                deliver(smtp, to, subject, text, html);
                return new Delivery(MailChannel.SMTP, true, route.reason(), null);
            } catch (Exception e) {
                log.warn("SMTP réel en échec pour {} ({}) : repli sur Mailpit", to, e.getMessage());
                try {
                    deliver(mailpit, to, "[non délivré] " + subject, text, html);
                    return new Delivery(MailChannel.MAILPIT, true, "Le SMTP réel a refusé l'envoi : copie déposée dans Mailpit", shorten(e));
                } catch (Exception e2) {
                    return new Delivery(MailChannel.SMTP, false, route.reason(), shorten(e));
                }
            }
        }
        try {
            deliver(mailpit, to, subject, text, html);
            return new Delivery(MailChannel.MAILPIT, true, route.reason(), null);
        } catch (Exception e) {
            log.warn("Mailpit injoignable ({}:{}) : {}", mailpitHost, mailpitPort, e.getMessage());
            return new Delivery(MailChannel.MAILPIT, false, route.reason(), shorten(e));
        }
    }

    private void deliver(JavaMailSenderImpl sender, String to, String subject, String text, String html) throws Exception {
        MimeMessage mime = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to.trim());
        helper.setSubject(subject);
        helper.setText(text, html);
        sender.send(mime);
    }

    /** Le domaine a-t-il un serveur de messagerie (MX, ou à défaut une adresse A) ? Résultat mis en cache. */
    boolean acceptsMail(String domain) {
        return mxCache.computeIfAbsent(domain, d -> {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                env.put("com.sun.jndi.dns.timeout.initial", "2000");
                env.put("com.sun.jndi.dns.timeout.retries", "1");
                DirContext ctx = new InitialDirContext(env);
                Attributes attrs = ctx.getAttributes(d, new String[]{"MX", "A"});
                Attribute mx = attrs.get("MX");
                Attribute a = attrs.get("A");
                ctx.close();
                return (mx != null && mx.size() > 0) || (a != null && a.size() > 0);
            } catch (javax.naming.NameNotFoundException e) {
                return false;
            } catch (Exception e) {
                // DNS injoignable (réseau fermé) : on ne bloque pas l'envoi
                return true;
            }
        });
    }

    private static String shorten(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 240 ? m.substring(0, 240) : m;
    }
}
