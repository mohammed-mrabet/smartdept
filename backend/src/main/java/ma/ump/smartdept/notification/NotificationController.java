package ma.ump.smartdept.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.notification.NotificationDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notifications de l'application (cloche) et annonces")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Mes notifications, les plus récentes d'abord")
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(defaultValue = "50") int limit) {
        return notificationService.list(currentUser.get(), unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues (badge de la cloche)")
    public UnreadCount unreadCount() {
        return new UnreadCount(notificationService.unreadCount(currentUser.get()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(currentUser.get(), id);
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu")
    public UnreadCount markAllRead() {
        notificationService.markAllRead(currentUser.get());
        return new UnreadCount(0);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une notification")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notificationService.delete(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF_DEPARTEMENT')")
    @Operation(summary = "Envoyer une annonce à un rôle (ou à tous) : application + e-mail")
    public BroadcastResult broadcast(@Valid @RequestBody BroadcastRequest request) {
        return new BroadcastResult(notificationService.broadcast(request.role(), request.title(), request.message(), request.link()));
    }
}
