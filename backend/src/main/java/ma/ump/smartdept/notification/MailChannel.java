package ma.ump.smartdept.notification;

/** Canal réellement utilisé pour un e-mail. */
public enum MailChannel {
    /** Serveur SMTP réel (Gmail, Brevo, serveur de l'université…) : l'e-mail arrive dans la vraie boîte. */
    SMTP,
    /** Mailpit : boîte de test locale (adresses de démonstration, invalides, ou repli si le SMTP échoue). */
    MAILPIT,
    /** Envoi désactivé (tests, MAIL_ENABLED=false). */
    NONE
}
