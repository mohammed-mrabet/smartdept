package ma.ump.smartdept.pfe;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.ReviewMilestoneRequest;
import ma.ump.smartdept.pfe.PfeDtos.SubmitMilestoneRequest;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static ma.ump.smartdept.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PfeProjectServiceTest {

    @Mock
    private PfeProjectRepository projectRepository;
    @Mock
    private PfeMilestoneRepository milestoneRepository;
    @Mock
    private PfeDefenseRepository defenseRepository;
    @Mock
    private NotificationService notifications;

    private PfeProjectService service;
    private User youssef;
    private User omar;
    private PfeProject project;
    private PfeMilestone cdc;
    private PfeMilestone conception;

    @BeforeEach
    void setUp() {
        service = new PfeProjectService(projectRepository, milestoneRepository, defenseRepository, notifications, TestData.CLOCK);
        Filiere isi = filiere(1, "M2I-ISI");
        youssef = teacher(4, "Youssef", "SQL");
        omar = student(7, "Omar", isi);
        project = project(1, subject(2, "Entrepôt de données", youssef, isi, SubjectStatus.ASSIGNED, 1), omar);
        cdc = milestone(1, project, "Cahier des charges", MilestoneStatus.VALIDATED, TestData.TODAY.minusDays(20));
        conception = milestone(2, project, "Conception", MilestoneStatus.TODO, TestData.TODAY.minusDays(1));
        when(milestoneRepository.findById(2L)).thenReturn(Optional.of(conception));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(defenseRepository.findByProjectId(anyLong())).thenReturn(Optional.empty());
        when(projectRepository.save(any(PfeProject.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("submit - l'étudiant dépose : statut SUBMITTED, encadrant notifié du retard")
    void submit() {
        var r = service.submitMilestone(omar, 2L, new SubmitMilestoneRequest("https://github.com/omar/entrepot", "v1"));

        assertEquals(MilestoneStatus.SUBMITTED, conception.getStatus());
        assertNotNull(conception.getSubmittedAt());
        assertEquals(50, r.progress());
        verify(notifications).notify(eq(youssef), eq(NotificationType.MILESTONE_SUBMITTED), anyString(), contains("en retard"), anyString());
    }

    @Test
    @DisplayName("submit - 403 pour une personne extérieure au projet")
    void submit_notMember() {
        User salma = student(8, "Salma", filiere(1, "M2I-ISI"));
        ApiException ex = assertThrows(ApiException.class,
                () -> service.submitMilestone(salma, 2L, new SubmitMilestoneRequest("https://x.org", null)));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("review - révision sans commentaire refusée ; validation notifiée aux étudiants")
    void review() {
        conception.setStatus(MilestoneStatus.SUBMITTED);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class,
                () -> service.reviewMilestone(youssef, 2L, new ReviewMilestoneRequest(MilestoneStatus.NEEDS_REVISION, " ", null))).getStatus());

        var r = service.reviewMilestone(youssef, 2L, new ReviewMilestoneRequest(MilestoneStatus.VALIDATED, "Bien", 15.0));

        assertEquals(MilestoneStatus.VALIDATED, conception.getStatus());
        assertEquals(15.0, conception.getGrade());
        assertEquals(100, r.progress());
        verify(notifications).notifyAll(anyCollection(), eq(NotificationType.MILESTONE_REVIEWED), contains("validé"), contains("15.0/20"), anyString());
    }

    @Test
    @DisplayName("markReady - 409 tant qu'un jalon n'est pas validé, puis READY_FOR_DEFENSE")
    void markReady() {
        ApiException ex = assertThrows(ApiException.class, () -> service.markReady(youssef, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().startsWith("1 jalon"));

        conception.setStatus(MilestoneStatus.VALIDATED);
        assertEquals(ProjectStatus.READY_FOR_DEFENSE, service.markReady(youssef, 1L).status());
        verify(notifications).notifyManagers(eq(NotificationType.PROJECT_READY), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("isLate - un jalon non rendu après son échéance est en retard")
    void late() {
        assertTrue(conception.isLate(TestData.TODAY));
        assertFalse(cdc.isLate(TestData.TODAY));
    }
}
