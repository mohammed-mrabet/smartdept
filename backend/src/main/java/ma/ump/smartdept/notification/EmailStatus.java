package ma.ump.smartdept.notification;

/** Suivi de l'envoi de l'e-mail associé à une notification. */
public enum EmailStatus {
    NOT_REQUESTED,  // l'utilisateur a désactivé les e-mails, ou l'envoi est désactivé
    PENDING,        // en file d'attente (envoi asynchrone)
    SENT,
    FAILED
}
