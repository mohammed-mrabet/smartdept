package ma.ump.smartdept.offers;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferAlertServiceTest {

    @Mock private OfferAlertRepository alertRepository;
    @Mock private InternshipOfferRepository offerRepository;
    @Mock private OfferBookmarkRepository bookmarkRepository;
    @Mock private NotificationService notifications;

    private OfferAlertService service;
    private User omar;

    @BeforeEach
    void setUp() {
        service = new OfferAlertService(alertRepository, offerRepository, bookmarkRepository, notifications, TestData.CLOCK);
        omar = TestData.student(7, "Omar", TestData.filiere(1, "M2I-ISI"));
    }

    private OfferAlert alert(String keywords, OfferCountry country, OfferKind kind, Integer level) {
        OfferAlert a = new OfferAlert();
        a.setId(1L);
        a.setUser(omar);
        a.setName("Mon alerte");
        a.setKeywords(keywords);
        a.setCountry(country);
        a.setKind(kind);
        a.setStudyLevel(level);
        return a;
    }

    @Test
    void matchesOnEveryCriterion() {
        InternshipOffer pfeMa = OfferTestData.offer(1, "Stage PFE Spring Boot", OfferCountry.MA, OfferKind.PFE, 5, 5, "Java, Spring");
        assertTrue(OfferAlertService.matches(alert("spring", OfferCountry.MA, OfferKind.PFE, 5), pfeMa));
        assertFalse(OfferAlertService.matches(alert("spring", OfferCountry.FR, null, null), pfeMa), "pays différent");
        assertFalse(OfferAlertService.matches(alert(null, null, OfferKind.ALTERNANCE, null), pfeMa), "type différent");
        assertFalse(OfferAlertService.matches(alert(null, null, null, 3), pfeMa), "niveau Bac+3 non accepté");
        assertFalse(OfferAlertService.matches(alert("spring python", null, null, null), pfeMa), "tous les mots-clés sont requis");
        InternshipOffer unknownLevel = OfferTestData.offer(2, "Stage Java", OfferCountry.MA, OfferKind.STAGE, null, null, "Java");
        assertTrue(OfferAlertService.matches(alert(null, null, null, 3), unknownLevel), "niveau non précisé : l'offre est proposée");
    }

    @Test
    void dispatchSendsOneGroupedNotificationPerAlert() {
        InternshipOffer a = OfferTestData.offer(1, "Stage PFE Spring Boot", OfferCountry.MA, OfferKind.PFE, 5, 5, "Spring");
        InternshipOffer b = OfferTestData.offer(2, "PFE Spring Cloud", OfferCountry.MA, OfferKind.PFE, 5, 5, "Spring");
        InternshipOffer c = OfferTestData.offer(3, "Stage Python", OfferCountry.FR, OfferKind.STAGE, 3, 3, "Python");
        when(offerRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(a, b, c));
        OfferAlert alert = alert("spring", OfferCountry.MA, null, null);
        when(alertRepository.findActiveWithUser()).thenReturn(List.of(alert));

        assertEquals(1, service.dispatch(List.of(1L, 2L, 3L)));
        verify(notifications, times(1)).notify(eq(omar), eq(NotificationType.OFFER_ALERT),
                eq("2 nouvelle(s) offre(s) pour « Mon alerte »"), contains("Stage PFE Spring Boot"), eq("/offres?alerte=1"));
        assertEquals(2, alert.getMatchesSent());
        assertNotNull(alert.getLastNotifiedAt());
    }

    @Test
    void noNewOfferNoQuery() {
        assertEquals(0, service.dispatch(List.of()));
        verifyNoInteractions(alertRepository, notifications);
    }

    @Test
    void followUpAfterFourteenDays() {
        OfferBookmark b = new OfferBookmark();
        b.setUser(omar);
        b.setOffer(OfferTestData.offer(1, "Stage Java", OfferCountry.FR, OfferKind.STAGE, 3, 5, "Java"));
        b.setStatus(TrackingStatus.APPLIED);
        b.setAppliedAt(LocalDate.of(2026, 11, 2));
        when(bookmarkRepository.findDueForFollowUp(LocalDate.of(2026, 11, 9))).thenReturn(List.of(b));
        assertEquals(1, service.sendFollowUps());
        assertTrue(b.isFollowUpSent());
        verify(notifications).notify(eq(omar), eq(NotificationType.OFFER_FOLLOW_UP), startsWith("Pensez à relancer"), anyString(), eq("/offres/suivi"));
    }
}
