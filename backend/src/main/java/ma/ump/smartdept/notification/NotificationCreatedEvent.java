package ma.ump.smartdept.notification;

/** Publié à la création d'une notification qui doit aussi partir par e-mail. */
public record NotificationCreatedEvent(Long notificationId) {
}
