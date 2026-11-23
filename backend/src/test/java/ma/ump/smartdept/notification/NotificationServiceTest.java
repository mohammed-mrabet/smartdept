package ma.ump.smartdept.notification;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Optional;

import static ma.ump.smartdept.TestData.user;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher events;

    private NotificationService service;
    private User omar;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userRepository, events, true);
        omar = user(7, "Omar", "Tazi", Role.ETUDIANT);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            if (n.getId() == null) n.setId(99L);
            return n;
        });
    }

    @Test
    @DisplayName("notify - e-mail en file d'attente et événement publié si l'utilisateur accepte les e-mails")
    void notify_withEmail() {
        Notification n = service.notify(omar, NotificationType.APPLICATION_ACCEPTED, "Titre", "Message", "/pfe/mon-projet");

        assertEquals(EmailStatus.PENDING, n.getEmailStatus());
        assertFalse(n.isRead());
        verify(events).publishEvent(new NotificationCreatedEvent(99L));
    }

    @Test
    @DisplayName("notify - pas d'e-mail si l'utilisateur les a désactivés ; texte trop long tronqué")
    void notify_withoutEmail() {
        omar.setEmailNotifications(false);
        Notification n = service.notify(omar, NotificationType.ANNOUNCEMENT, "T".repeat(250), "M", null);

        assertEquals(EmailStatus.NOT_REQUESTED, n.getEmailStatus());
        assertEquals(200, n.getTitle().length());
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("notifyAll - doublons et valeurs nulles ignorés")
    void notifyAll_dedup() {
        User salma = user(8, "Salma", "Berrada", Role.ETUDIANT);
        int sent = service.notifyAll(Arrays.asList(omar, null, salma, omar), NotificationType.ANNOUNCEMENT, "T", "M", null);

        assertEquals(2, sent);
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("markRead - 403 sur la notification d'un autre utilisateur")
    void markRead_owner() {
        Notification n = new Notification();
        n.setId(5L);
        n.setRecipient(omar);
        when(notificationRepository.findById(5L)).thenReturn(Optional.of(n));
        User salma = user(8, "Salma", "Berrada", Role.ETUDIANT);

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.markRead(salma, 5L)).getStatus());
        assertTrue(service.markRead(omar, 5L).read());
        assertNotNull(n.getReadAt());
    }
}
