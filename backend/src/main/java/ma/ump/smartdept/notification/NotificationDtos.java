package ma.ump.smartdept.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.ump.smartdept.user.Role;

import java.time.LocalDateTime;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(Long id, NotificationType type, String title, String message, String link,
                                       boolean read, LocalDateTime readAt, EmailStatus emailStatus,
                                       LocalDateTime createdAt) {
        public static NotificationResponse of(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                    n.isRead(), n.getReadAt(), n.getEmailStatus(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long count) {
    }

    /** Annonce envoyée par le chef ou l'admin à un rôle (ou à tous si role est vide). */
    public record BroadcastRequest(
            Role role,
            @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String title,
            @NotBlank(message = "Le message est obligatoire") @Size(max = 1000) String message,
            @Size(max = 255) String link) {
    }

    public record BroadcastResult(int recipients) {
    }
}
