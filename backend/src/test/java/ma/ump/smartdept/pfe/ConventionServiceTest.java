package ma.ump.smartdept.pfe;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.ConventionRequest;
import ma.ump.smartdept.pfe.PfeDtos.ReviewConventionRequest;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;

import static ma.ump.smartdept.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConventionServiceTest {

    @Mock
    private InternshipConventionRepository conventionRepository;
    @Mock
    private PfeProjectRepository projectRepository;
    @Mock
    private NotificationService notifications;

    @InjectMocks
    private ConventionService service;

    private User rania;
    private User chef;

    @BeforeEach
    void setUp() {
        rania = student(12, "Rania", filiere(1, "M2I-ISI"));
        chef = user(2, "Karim", "Chef", Role.CHEF_DEPARTEMENT);
        when(conventionRepository.save(any(InternshipConvention.class))).thenAnswer(i -> {
            InternshipConvention c = i.getArgument(0);
            if (c.getId() == null) c.setId(1L);
            return c;
        });
        when(projectRepository.findByStudentId(anyLong())).thenReturn(Optional.empty());
    }

    private ConventionRequest request(LocalDate start, LocalDate end) {
        return new ConventionRequest("TechNova", "Oujda", null, "Rachid Benali", "r.benali@technova.ma", start, end,
                "Refonte du module de facturation.");
    }

    @Test
    @DisplayName("create - durée de 4 à 26 semaines ; brouillon créé")
    void create() {
        LocalDate start = LocalDate.of(2027, 2, 1);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.create(rania, request(start, start.plusWeeks(2)))).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.create(rania, request(start, start.minusDays(1)))).getStatus());

        var c = service.create(rania, request(start, start.plusWeeks(16)));
        assertEquals(ConventionStatus.DRAFT, c.status());
        assertEquals(16, c.durationWeeks());
    }

    @Test
    @DisplayName("submit puis review : refus sans motif interdit, validation notifiée à l'étudiante")
    void submitAndReview() {
        InternshipConvention c = new InternshipConvention();
        c.setId(1L);
        c.setStudent(rania);
        c.setCompanyName("TechNova");
        c.setStartDate(LocalDate.of(2027, 2, 1));
        c.setEndDate(LocalDate.of(2027, 6, 1));
        when(conventionRepository.findById(1L)).thenReturn(Optional.of(c));

        service.submit(rania, 1L);
        assertEquals(ConventionStatus.SUBMITTED, c.getStatus());
        verify(notifications).notifyManagers(eq(NotificationType.CONVENTION_SUBMITTED), anyString(), anyString(), anyString());

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.review(chef, 1L, new ReviewConventionRequest(false, null))).getStatus());
        service.review(chef, 1L, new ReviewConventionRequest(true, null));
        assertEquals(ConventionStatus.VALIDATED, c.getStatus());
        verify(notifications).notify(eq(rania), eq(NotificationType.CONVENTION_REVIEWED), contains("validée"), anyString(), anyString());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.submit(rania, 1L)).getStatus());
    }
}
