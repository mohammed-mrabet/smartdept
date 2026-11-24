package ma.ump.smartdept.offers;

import ma.ump.smartdept.offers.provider.FetchedOffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Seules les offres du domaine informatique, récentes et avec un lien valide sont enregistrées. */
class OfferScopeTest {

    @ParameterizedTest(name = "« {0} » → {1}")
    @CsvSource(delimiter = '|', value = {
            "Stage PFE — Développeur Full Stack Spring Boot / Angular | true",
            "Stage Data Scientist — NLP et LLM | true",
            "Alternance Administrateur systèmes et réseaux | true",
            "Technicien réseaux - stage BTS | true",
            "Stage Cybersécurité — analyste SOC | true",
            "Prácticas Desarrollador Backend Java | true",
            "Werkstudent Softwareentwicklung Java (m/w/d) | true",
            "Werkstudent:in IT (m/w/d) | true",
            "Business Intelligence Analyst intern | true",
            "Data Engineer - Finance | true",
            "Stage assistant commercial | false",
            "Werkstudent Buchhaltung (w/m/d) | false",
            "Werkstudent (m/w/d) Sales SaaS | false",
            "Head of Marketing & Communications | false",
            "Talent & Culture Intern / Working Student (m/w/d) | false",
            "Senior Web Engineer - Subscriptions | false",
            "PROJECT MANAGER – AI-FIRST DELIVERY (m/w/d) | false",
            "Stage ingénieur mécanique - conception | false",
            "Community manager - réseaux sociaux | false",
            "Chauffeur livreur | false"
    })
    void keepsOnlyComputingOffersForStudents(String title, boolean expected) {
        assertEquals(expected, OfferClassifier.isComputing(title, ""));
    }

    @Test
    void genericTitleNeedsThreeTechnologiesInTheDescription() {
        assertTrue(OfferClassifier.isComputing("Stagiaire PFE", "Application Java Spring Boot, Angular, Docker et PostgreSQL."));
        assertFalse(OfferClassifier.isComputing("Stagiaire PFE", "Suivi budgétaire, Excel, gestion de projet agile."));
    }

    @Test
    void rejectsOffersWithoutValidLinkOrTooOld() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 10, 0);
        FetchedOffer ok = FetchedOffer.of("1", "Stage développeur Java", "X", "Paris", OfferCountry.FR, "", "https://x/1", null, LocalDate.of(2026, 11, 20), null);
        assertTrue(OfferStore.isValid(ok, now));
        assertFalse(OfferStore.isValid(FetchedOffer.of("2", "Stage développeur Java", "X", "Paris", OfferCountry.FR, "", "javascript:alert(1)", null, null, null), now));
        assertFalse(OfferStore.isValid(FetchedOffer.of("3", "Stage développeur Java", "X", "Paris", OfferCountry.FR, "", "https://x/3", null, LocalDate.of(2026, 9, 1), null), now),
                "publiée il y a plus de 45 jours");
        assertFalse(OfferStore.isValid(FetchedOffer.of("4", "Stage comptable", "X", "Paris", OfferCountry.FR, "", "https://x/4", null, null, null), now));
    }
}
