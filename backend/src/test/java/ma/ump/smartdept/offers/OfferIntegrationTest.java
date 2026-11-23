package ma.ump.smartdept.offers;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.StudyLevel;
import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.offers.OfferDtos.*;
import ma.ump.smartdept.offers.provider.FetchedOffer;
import ma.ump.smartdept.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Offres sur une vraie base H2 : enregistrement des offres d'API, recherche multicritère, offres internes, suivi, alertes. */
@SpringBootTest
@Transactional
class OfferIntegrationTest {

    @Autowired private OfferService service;
    @Autowired private OfferStore store;
    @Autowired private OfferAlertService alertService;
    @Autowired private FiliereRepository filiereRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User chef;
    private User omar;
    private User nora;

    @BeforeEach
    void setUp() {
        Filiere master = filiere("OF-M2", StudyLevel.BAC_5);
        Filiere licence = filiere("OF-L3", StudyLevel.BAC_3);
        chef = save("of.chef@test.ma", "Karim", Role.CHEF_DEPARTEMENT, null, null);
        omar = save("of.omar@test.ma", "Omar", Role.ETUDIANT, master, "Java, Spring Boot, Angular");
        nora = save("of.nora@test.ma", "Nora", Role.ETUDIANT, licence, "PHP, Laravel");
        LocalDateTime now = LocalDateTime.now();
        store.upsert(OfferSource.ADZUNA, List.of(
                FetchedOffer.of("a1", "Stage PFE Spring Boot (Bac+5)", "Acme", "Lyon", OfferCountry.FR, "Stage de 6 mois Java Spring Angular", "https://x/a1", null, LocalDate.now().minusDays(1), null),
                FetchedOffer.of("a2", "Stage développeur PHP Laravel - Licence", "Beta", "Bruxelles", OfferCountry.BE, "Stage de 3 mois", "https://x/a2", null, LocalDate.now().minusDays(2), null),
                FetchedOffer.of("a3", "Alternance développeur", "Gamma", "Madrid", OfferCountry.ES, "Contrat d'apprentissage Bac+3 à Bac+5", "https://x/a3", null, LocalDate.now().minusDays(3), "apprentissage")), now);
    }

    private Filiere filiere(String code, StudyLevel level) {
        Filiere f = new Filiere();
        f.setCode(code);
        f.setName(code);
        f.setLevel("X");
        f.setStudyLevel(level);
        return filiereRepository.save(f);
    }

    private User save(String email, String first, Role role, Filiere f, String skills) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setFirstName(first);
        u.setLastName("Test");
        u.setRole(role);
        u.setFiliere(f);
        u.setSpecialties(skills);
        u.setStudentNumber(role == Role.ETUDIANT ? email : null);
        return userRepository.save(u);
    }

    @Test
    void searchByLevelCountryKindAndText() {
        assertEquals(3, service.search(omar, null, null, null, null, false, null, true, "recent", 0, 20).total());
        OfferPage bac3 = service.search(nora, null, null, null, null, false, 3, false, "recent", 0, 20);
        assertEquals(List.of("Stage développeur PHP Laravel - Licence", "Alternance développeur"),
                bac3.items().stream().map(OfferResponse::title).toList());
        assertEquals(1, service.search(omar, null, OfferCountry.ES, OfferKind.ALTERNANCE, null, false, null, true, "recent", 0, 20).total());
        assertEquals(1, service.search(omar, "spring", null, null, null, false, null, true, "recent", 0, 20).total());
        OfferPage byMatch = service.search(omar, null, null, null, null, false, null, true, "match", 0, 20);
        assertEquals("Stage PFE Spring Boot (Bac+5)", byMatch.items().get(0).title(), "meilleur score pour le profil Java/Spring");
        assertEquals(5, byMatch.myLevel());
        assertEquals(1L, byMatch.facets().countries().get("FR"));
        assertEquals(2L, byMatch.facets().levels().get("BAC_5"), "PFE Bac+5 et alternance Bac+3 à Bac+5");
    }

    @Test
    void internalOfferNotifiesStudentsOfTheRightLevelAndAlerts() {
        service.createAlert(omar, new AlertRequest("PFE au Maroc", "kubernetes", OfferCountry.MA, null, null, false, true));
        OfferResponse created = service.create(chef, new OfferRequest("Stage PFE DevOps Kubernetes", "Partenaire fictif", "Oujda",
                OfferCountry.MA, OfferKind.PFE, 5, 5, false, "Industrialisation Kubernetes, 6 mois.", null, "rh@partenaire.exemple.ma",
                null, null, null, null));
        assertNotNull(created);
        assertTrue(created.internal());
        assertEquals(List.of("Kubernetes", "DevOps"), created.skills());
        assertEquals(6, created.durationMonths(), "durée détectée dans la description");
        var notes = notificationRepository.findAll();
        assertTrue(notes.stream().anyMatch(n -> n.getType() == NotificationType.OFFER_PUBLISHED && n.getRecipient().getId().equals(omar.getId())));
        assertTrue(notes.stream().noneMatch(n -> n.getType() == NotificationType.OFFER_PUBLISHED && n.getRecipient().getId().equals(nora.getId())),
                "Nora (Bac+3) n'est pas visée par un PFE Bac+5");
        assertTrue(notes.stream().anyMatch(n -> n.getType() == NotificationType.OFFER_ALERT && n.getRecipient().getId().equals(omar.getId())));
        assertThrows(ApiException.class, () -> service.create(omar, new OfferRequest("x", "y", "z", OfferCountry.MA, OfferKind.STAGE,
                null, null, false, "d", null, "a@b.ma", null, null, null, null)), "un étudiant ne publie pas d'offre");
    }

    @Test
    void trackingBoard() {
        Long id = service.search(omar, "spring", null, null, null, false, null, true, "recent", 0, 20).items().get(0).id();
        TrackingResponse saved = service.track(omar, id, new TrackingRequest(TrackingStatus.SAVED, null));
        assertNull(saved.appliedAt());
        TrackingResponse applied = service.track(omar, id, new TrackingRequest(TrackingStatus.APPLIED, "CV envoyé"));
        assertEquals(LocalDate.now(), applied.appliedAt());
        assertEquals(TrackingStatus.APPLIED, service.get(omar, id).tracking());
        assertEquals(1, service.tracking(omar).size());
        assertEquals(1L, service.stats().tracking().get("APPLIED"));
        service.untrack(omar, id);
        assertTrue(service.tracking(omar).isEmpty());
        assertEquals(10, service.sources().size(), "offres internes + 9 API");
    }
}
