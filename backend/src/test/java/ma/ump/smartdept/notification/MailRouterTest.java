package ma.ump.smartdept.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MailRouterTest {

    private static MailRouter router(String mode, String smtpHost) {
        return new MailRouter(true, mode, "", smtpHost, 587, "moi@gmail.com", "secret", "starttls", "mailpit", 1025,
                "http://localhost:8025", "smartdept.ma,exemple.ma,test", false);
    }

    @Test
    void realAddressesGoThroughTheRealSmtp() {
        MailRouter r = router("auto", "smtp.gmail.com");
        assertEquals(MailChannel.SMTP, r.route("etudiant@gmail.com").channel());
        assertEquals(MailChannel.SMTP, r.route("prenom.nom@ump.ac.ma").channel());
        assertEquals(MailChannel.SMTP, r.route("x@outlook.com").channel());
    }

    @Test
    void demoInvalidOrUnconfiguredGoToMailpit() {
        MailRouter r = router("auto", "smtp.gmail.com");
        assertEquals(MailChannel.MAILPIT, r.route("omar.tazi@etu.smartdept.ma").channel(), "sous-domaine de démonstration");
        assertEquals(MailChannel.MAILPIT, r.route("rh@exemple.ma").channel());
        assertEquals(MailChannel.MAILPIT, r.route("pas-une-adresse").channel());
        assertEquals(MailChannel.MAILPIT, r.route("a@b").channel(), "domaine sans extension");
        assertEquals(MailChannel.MAILPIT, router("auto", "").route("etudiant@gmail.com").channel(), "aucun SMTP réel configuré");
        assertEquals(MailChannel.MAILPIT, router("sandbox", "smtp.gmail.com").route("etudiant@gmail.com").channel());
        assertEquals(MailChannel.SMTP, router("smtp", "smtp.gmail.com").route("x@domaine-inconnu.org").channel());
    }

    @Test
    void statusMasksTheAccountAndDefaultsTheSender() {
        MailRouter.Status s = router("auto", "smtp.gmail.com").status();
        assertTrue(s.smtpConfigured());
        assertEquals("SmartDept <moi@gmail.com>", s.from(), "Gmail impose l'adresse du compte comme expéditeur");
        assertFalse(s.smtpUser().contains("moi@"));
        assertEquals(MailChannel.NONE, new MailRouter(false, "auto", "", "", 587, "", "", "none", "mailpit", 1025, "", "", false)
                .route("x@gmail.com").channel());
    }
}
