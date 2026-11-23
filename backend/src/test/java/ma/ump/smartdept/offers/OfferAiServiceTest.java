package ma.ump.smartdept.offers;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.ai.AiClient;
import ma.ump.smartdept.common.StudyLevel;
import ma.ump.smartdept.offers.OfferDtos.CoverLetterResponse;
import ma.ump.smartdept.offers.OfferDtos.MatchResponse;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OfferAiServiceTest {

    private AiClient ai;
    private OfferAiService service;
    private User omar;

    @BeforeEach
    void setUp() {
        ai = mock(AiClient.class);
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.empty());
        service = new OfferAiService(ai);
        Filiere isi = TestData.filiere(1, "M2I-ISI");
        isi.setStudyLevel(StudyLevel.BAC_5);
        omar = TestData.student(7, "Omar", isi);
        omar.setSpecialties("Java, Spring Boot, Angular, Docker");
    }

    @Test
    void goodMatchWhenSkillsAndLevelFit() {
        InternshipOffer o = OfferTestData.offer(1, "Stage PFE Spring Boot Angular", OfferCountry.MA, OfferKind.PFE, 5, 5, "Java, Spring, Angular, Docker");
        MatchResponse m = service.match(omar, o);
        assertTrue(m.score() >= 75, "score " + m.score());
        assertTrue(m.levelFits());
        assertTrue(m.missingSkills().isEmpty());
    }

    @Test
    void levelMismatchLowersScoreAndAdvises() {
        InternshipOffer o = OfferTestData.offer(2, "Stage technicien réseau", OfferCountry.MA, OfferKind.STAGE, 2, 2, "Réseaux, Linux");
        MatchResponse m = service.match(omar, o);
        assertFalse(m.levelFits());
        assertTrue(m.score() < 30, "score " + m.score());
        assertEquals(java.util.List.of("Réseaux", "Linux"), m.missingSkills());
        assertTrue(m.advice().stream().anyMatch(a -> a.contains("Bac+2")));
    }

    @Test
    void localCoverLetterInTheCountryLanguage() {
        InternshipOffer es = OfferTestData.offer(3, "Prácticas backend Java", OfferCountry.ES, OfferKind.STAGE, 3, 5, "Java, Spring");
        CoverLetterResponse r = service.coverLetter(omar, es, null, null);
        assertEquals("es", r.language());
        assertEquals("local", r.source());
        assertTrue(r.text().startsWith("Estimado/a"));
        assertTrue(r.text().contains("Java, Spring"));

        InternshipOffer fr = OfferTestData.offer(4, "Stage PFE", OfferCountry.FR, OfferKind.PFE, 5, 5, "Docker");
        assertTrue(service.coverLetter(omar, fr, null, "Disponible dès février.").text().contains("Disponible dès février."));
    }

    @Test
    void aiLetterWhenProviderAnswers() {
        when(ai.chat(anyString(), anyString())).thenReturn(Optional.of("Madame, Monsieur, ..."));
        CoverLetterResponse r = service.coverLetter(omar, OfferTestData.offer(5, "Stage", OfferCountry.MA, OfferKind.STAGE, null, null, "Java"), "fr", null);
        assertEquals("ia", r.source());
    }
}
