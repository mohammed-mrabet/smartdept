package ma.ump.smartdept.ai;

import ma.ump.smartdept.ai.AiDtos.*;
import ma.ump.smartdept.pfe.PfeProjectRepository;
import ma.ump.smartdept.pfe.PfeSubjectRepository;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ma.ump.smartdept.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServiceTest {

    @Mock
    private AiClient ai;
    @Mock
    private ChatContextBuilder contextBuilder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PfeSubjectRepository subjectRepository;
    @Mock
    private PfeProjectRepository projectRepository;

    @InjectMocks
    private AiService service;

    private static final String DESCRIPTION = "Le département veut une application de suivi des stages. "
            + "Elle sera construite avec Spring Boot et Angular, en microservices déployés avec Docker.";

    @Test
    @DisplayName("improveSubject - sans IA : description structurée, mots-clés et objectifs locaux")
    void improveSubject_local() {
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.empty());

        SubjectSuggestion s = service.improveSubject(new SubjectDraft("Suivi des stages", DESCRIPTION, null));

        assertEquals("local", s.source());
        assertTrue(s.improvedDescription().startsWith("Contexte : "));
        assertTrue(s.improvedDescription().contains("Objectifs :"));
        assertTrue(s.keywords().contains("Stages"));
        assertEquals(5, s.objectives().size());
        assertEquals(4, s.deliverables().size());
    }

    @Test
    @DisplayName("improveSubject - avec IA : la réponse du modèle est utilisée")
    void improveSubject_ai() {
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("Contexte : texte réécrit par le modèle"));

        SubjectSuggestion s = service.improveSubject(new SubjectDraft("Suivi des stages", DESCRIPTION, null));

        assertEquals("ia", s.source());
        assertEquals("Contexte : texte réécrit par le modèle", s.improvedDescription());
    }

    @Test
    @DisplayName("suggestSupervisors - l'enseignant dont les spécialités recouvrent le sujet arrive en tête")
    void suggestSupervisors() {
        User web = teacher(3, "Nadia", "Spring Boot, Angular, Microservices");
        User data = teacher(4, "Youssef", "Big Data, Spark, SQL");
        when(userRepository.findByRoleInAndEnabledTrue(anyList())).thenReturn(List.of(data, web));
        when(subjectRepository.findBySupervisorId(anyLong())).thenReturn(List.of());
        when(projectRepository.findBySupervisorIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        List<SupervisorSuggestion> out = service.suggestSupervisors(new SubjectDraft("Suivi des stages", DESCRIPTION, null));

        assertEquals("Nadia Prof", out.get(0).teacher().fullName());
        assertTrue(out.get(0).score() > out.get(1).score());
        assertTrue(out.get(0).matchedKeywords().contains("angular"));
    }

    @Test
    @DisplayName("summarize - sans IA : résumé extractif")
    void summarize_local() {
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.empty());

        SummaryResponse r = service.summarize(new SummaryRequest(DESCRIPTION + " Une troisième phrase assez longue ici.", 1));

        assertEquals("local", r.source());
        assertEquals(1, TextAnalyzer.sentences(r.summary()).size());
    }

    @Test
    @DisplayName("chat - sans IA : le thème de la question choisit la réponse")
    void chat_localIntents() {
        User me = user(7, "Omar", "Tazi", Role.ETUDIANT);
        ChatContext ctx = new ChatContext("Omar", Role.ETUDIANT, List.of("fait"), Map.of(
                "soutenance", "Votre soutenance est le 03/12/2026.",
                "jalon", "Votre prochain jalon est « Conception ».",
                "notification", "Vous avez 2 notification(s) non lue(s)."));
        when(contextBuilder.build(7L)).thenReturn(ctx);
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.empty());

        ChatResponse r1 = service.chat(me, new ChatRequest("Quand est ma SOUTENANCE ?"));
        ChatResponse r2 = service.chat(me, new ChatRequest("prochain livrable\nà rendre ?"));
        ChatResponse r3 = service.chat(me, new ChatRequest("Bonjour"));
        ChatResponse r4 = service.chat(me, new ChatRequest("météo demain"));

        assertEquals("Votre soutenance est le 03/12/2026.", r1.answer());
        assertEquals("local", r1.source());
        assertTrue(r2.answer().contains("Conception"));
        assertTrue(r3.answer().startsWith("Bonjour Omar"));
        assertTrue(r4.answer().startsWith("Je n'ai pas compris"));
        assertEquals(3, r1.suggestions().size());
    }
}
