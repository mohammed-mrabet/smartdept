package ma.ump.smartdept.offers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class OfferClassifierTest {

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource(delimiter = '|', value = {
            "Stage PFE développeur Java | PFE",
            "Stage de fin d'études - Data engineer | PFE",
            "Alternance développeur full stack | ALTERNANCE",
            "Apprenti(e) administrateur systèmes | ALTERNANCE",
            "Software Engineering Intern | STAGE",
            "Prácticas desarrollador backend | STAGE",
            "Stagiaire support informatique | STAGE",
            "Développeur Java confirmé | EMPLOI"
    })
    void detectsKind(String title, OfferKind expected) {
        assertEquals(expected, OfferClassifier.kind(title, "", null));
    }

    @Test
    @DisplayName("l'indice fourni par l'API l'emporte : contrat d'apprentissage → alternance")
    void kindHintFromApi() {
        assertEquals(OfferKind.ALTERNANCE, OfferClassifier.kind("Développeur web", "Rejoignez-nous", "Contrat apprentissage"));
        assertEquals(OfferKind.STAGE, OfferClassifier.kind("Développeur web", "", "internship"));
    }

    @Test
    @DisplayName("niveaux Bac+2 à Bac+5 reconnus en français, anglais et espagnol")
    void detectsLevels() {
        assertArrayEquals(new int[]{3, 5}, OfferClassifier.levels("Profil : Bac+3 à Bac+5 en informatique"));
        assertArrayEquals(new int[]{4, 4}, OfferClassifier.levels("Étudiant en Master 1"));
        assertArrayEquals(new int[]{5, 5}, OfferClassifier.levels("Élève ingénieur ou Master 2"));
        assertArrayEquals(new int[]{2, 2}, OfferClassifier.levels("Ouvert aux BTS SIO et DUT"));
        assertArrayEquals(new int[]{3, 3}, OfferClassifier.levels("Bachelor student"));
        assertArrayEquals(new int[]{2, 2}, OfferClassifier.levels("Técnico superior en sistemas"));
        assertNull(OfferClassifier.levels("Aucune précision sur le diplôme"));
    }

    @Test
    void europeanLevel() {
        assertEquals(2, OfferClassifier.levelFromEuropean("5"));
        assertEquals(3, OfferClassifier.levelFromEuropean("6"));
        assertEquals(5, OfferClassifier.levelFromEuropean("7"));
        assertNull(OfferClassifier.levelFromEuropean("4"));
    }

    @Test
    void detectsSkillsDurationAndRemote() {
        String text = "Stage de 6 mois : microservices Spring Boot, Angular, Docker et Kubernetes. Télétravail 2 jours.";
        assertTrue(OfferClassifier.skills(text, 8).containsAll(java.util.List.of("Spring", "Angular", "Docker", "Kubernetes", "Microservices")));
        assertEquals(6, OfferClassifier.durationMonths(text));
        assertEquals(6, OfferClassifier.durationMonths("24 weeks internship"));
        assertTrue(OfferClassifier.remote(text));
        assertFalse(OfferClassifier.remote("Sur site à Oujda"));
    }

    @Test
    void detectsCountry() {
        assertEquals(OfferCountry.MA, OfferClassifier.country("Casablanca, Grand Casablanca", OfferCountry.OTHER));
        assertEquals(OfferCountry.ES, OfferClassifier.country("Madrid", OfferCountry.OTHER));
        assertEquals(OfferCountry.CA, OfferClassifier.country("Montréal, QC", OfferCountry.OTHER));
        assertEquals(OfferCountry.BE, OfferClassifier.country("Bruxelles", OfferCountry.OTHER));
        assertEquals(OfferCountry.FR, OfferClassifier.country("69 - Lyon 3e", OfferCountry.OTHER));
        assertEquals(OfferCountry.REMOTE, OfferClassifier.country("Worldwide", OfferCountry.OTHER));
        assertEquals(OfferCountry.OTHER, OfferClassifier.country("Berlin", OfferCountry.OTHER));
    }

    @Test
    void stripsHtml() {
        assertEquals("Missions :\n• Java\n• Angular", OfferClassifier.plainText("<p>Missions :</p><ul><li>Java</li><li>Angular</li></ul>").replace(" \n", "\n").trim()
                .replaceAll("\\n\\s+", "\n"));
    }
}
