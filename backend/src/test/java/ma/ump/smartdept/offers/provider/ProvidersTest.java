package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferKind;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lecture des réponses JSON (exemples conformes aux documentations des API) sans appel réseau. */
class ProvidersTest {

    private final ProviderHttp http = mock(ProviderHttp.class);

    @Test
    void adzunaMapsResultsAndBuildsCountryUrl() {
        when(http.get(any(URI.class), anyMap())).thenReturn(Map.of("results", List.of(Map.of(
                "id", "4711", "title", "Stage développeur Java", "description", "Stage de 6 mois",
                "company", Map.of("display_name", "Acme"), "location", Map.of("display_name", "Bruxelles"),
                "created", "2026-11-20T08:00:00Z", "redirect_url", "https://www.adzuna.be/details/4711",
                "salary_min", 1200, "salary_max", 1500))));
        AdzunaProvider p = new AdzunaProvider(http, "id", "key");
        List<FetchedOffer> list = p.fetch(new OfferQuery(null, OfferCountry.BE, OfferKind.STAGE, 20));
        assertEquals(1, list.size());
        FetchedOffer f = list.get(0);
        assertEquals("4711", f.externalId());
        assertEquals("Acme", f.company());
        assertEquals(OfferCountry.BE, f.country());
        assertEquals("1200 - 1500", f.salary());
        assertEquals(LocalDate.of(2026, 11, 20), f.publishedAt());
        verify(http).get(argThat(u -> u.toString().startsWith("https://api.adzuna.com/v1/api/jobs/be/search/1?")
                && u.toString().contains("what=stage%20informatique")), anyMap());
        assertTrue(p.fetch(new OfferQuery(null, OfferCountry.MA, OfferKind.STAGE, 20)).isEmpty(), "Adzuna ne couvre pas le Maroc");
    }

    @Test
    void joobleUsesTheKeyOfTheCountry() {
        when(http.postJson(any(URI.class), any(), anyMap())).thenReturn(Map.of("totalCount", 1, "jobs", List.of(Map.of(
                "id", 123, "title", "Stage PFE Data", "location", "Casablanca", "snippet", "Bac+5",
                "link", "https://ma.jooble.org/desc/123", "company", "DataCo", "updated", "2026-11-21T10:00:00.0000000"))));
        JoobleProvider p = new JoobleProvider(http, "MA:cle-maroc, FR:cle-france", "https://jooble.org/api/{key}");
        assertEquals(List.of(OfferCountry.FR, OfferCountry.MA), p.countries());
        List<FetchedOffer> list = p.fetch(new OfferQuery("data", OfferCountry.MA, OfferKind.PFE, 20));
        assertEquals("123", list.get(0).externalId());
        assertEquals(LocalDate.of(2026, 11, 21), list.get(0).publishedAt());
        verify(http).postJson(eq(URI.create("https://jooble.org/api/cle-maroc")),
                argThat(b -> ((Map<?, ?>) b).get("location").equals("Maroc") && ((Map<?, ?>) b).get("keywords").equals("data")), anyMap());
        assertTrue(p.fetch(new OfferQuery(null, OfferCountry.ES, OfferKind.STAGE, 20)).isEmpty());
        assertFalse(new JoobleProvider(http, "", "x").isConfigured());
    }

    @Test
    void franceTravailRequestsATokenThenSearches() {
        when(http.postForm(any(URI.class), anyMap())).thenReturn(Map.of("access_token", "jeton", "expires_in", 1499));
        when(http.get(any(URI.class), eq(Map.of("Authorization", "Bearer jeton")))).thenReturn(Map.of("resultats", List.of(Map.of(
                "id", "190XYZ", "intitule", "Développeur Java (H/F)", "description", "Contrat d'apprentissage",
                "dateCreation", "2026-11-19T09:12:00.000Z", "lieuTravail", Map.of("libelle", "75 - Paris 15e"),
                "entreprise", Map.of("nom", "Société X"), "natureContrat", "Contrat apprentissage"))));
        FranceTravailProvider p = new FranceTravailProvider(http, "client", "secret");
        List<FetchedOffer> list = p.fetch(new OfferQuery(null, OfferCountry.FR, OfferKind.ALTERNANCE, 50));
        assertEquals("https://candidat.francetravail.fr/offres/recherche/detail/190XYZ", list.get(0).url());
        assertEquals("Contrat apprentissage", list.get(0).kindHint());
        p.fetch(new OfferQuery(null, OfferCountry.FR, OfferKind.STAGE, 50));
        verify(http, times(1)).postForm(any(URI.class), anyMap()); // jeton réutilisé
        verify(http).get(argThat(u -> u.toString().contains("natureContrat=E2") && u.toString().contains("range=0-49")), anyMap());
    }

    @Test
    void remotiveAndArbeitnowNeedNoKey() {
        when(http.get(argThat(u -> u != null && u.getHost().equals("remotive.com")), anyMap())).thenReturn(Map.of("jobs", List.of(Map.of(
                "id", 99, "url", "https://remotive.com/remote-jobs/software-dev/intern-99", "title", "Software Engineering Intern",
                "company_name", "Remote Co", "publication_date", "2026-11-18T12:00:00", "job_type", "internship",
                "candidate_required_location", "Europe", "description", "<p>Python</p>"))));
        RemotiveProvider remotive = new RemotiveProvider(http);
        assertTrue(remotive.isConfigured());
        assertEquals(360, remotive.minIntervalMinutes());
        FetchedOffer f = remotive.fetch(new OfferQuery(null, OfferCountry.REMOTE, OfferKind.STAGE, 50)).get(0);
        assertEquals("Python", f.description());
        assertEquals(OfferCountry.REMOTE, f.country());

        when(http.get(argThat(u -> u != null && u.getHost().equals("www.arbeitnow.com")), anyMap())).thenReturn(Map.of("data", List.of(
                Map.of("slug", "java-dev", "title", "Java Developer Intern", "company_name", "A", "description", "Spring",
                        "remote", true, "url", "https://www.arbeitnow.com/jobs/java-dev", "tags", List.of("java"),
                        "job_types", List.of("internship"), "location", "Berlin", "created_at", 1763640000),
                Map.of("slug", "cook", "title", "Koch", "company_name", "B", "description", "Küche", "remote", false,
                        "url", "https://www.arbeitnow.com/jobs/cook", "tags", List.of(), "job_types", List.of(), "location", "Berlin", "created_at", 1763640000))));
        List<FetchedOffer> it = new ArbeitnowProvider(http).fetch(new OfferQuery(null, OfferCountry.REMOTE, OfferKind.STAGE, 50));
        assertEquals(1, it.size(), "seules les offres informatiques sont gardées");
        assertEquals(OfferCountry.REMOTE, it.get(0).country());
    }

    @Test
    void careerjetUsesBasicAuthAndInternshipFilter() {
        when(http.get(any(URI.class), anyMap())).thenReturn(Map.of("type", "JOBS", "jobs", List.of(Map.of(
                "title", "Stagiaire développeur", "company", "Oujda Tech", "locations", "Oujda", "date", "Fri, 20 Nov 2026 10:00:00 GMT",
                "url", "https://www.optioncarriere.ma/jobad/abc", "description", "Stage PFE"))));
        CareerjetProvider p = new CareerjetProvider(http, "cle", "203.0.113.10");
        FetchedOffer f = p.fetch(new OfferQuery(null, OfferCountry.MA, OfferKind.STAGE, 20)).get(0);
        assertEquals(LocalDate.of(2026, 11, 20), f.publishedAt());
        assertNotNull(f.externalId());
        verify(http).get(argThat(u -> u.toString().contains("locale_code=fr_MA") && u.toString().contains("contract_type=i")
                && u.toString().contains("user_ip=203.0.113.10")), argThat(h -> h.get("Authorization").startsWith("Basic ")));
    }

    @Test
    void parsesDates() {
        assertEquals(LocalDate.of(2026, 11, 20), ProviderSupport.date("2026-11-20"));
        assertEquals(LocalDate.of(2026, 11, 20), ProviderSupport.date("2026-11-20T23:30:00+00:00"));
        assertEquals(LocalDate.of(2026, 11, 20), ProviderSupport.date("2026-11-20 08:00:00"));
        assertNull(ProviderSupport.date("pas une date"));
        assertEquals("stage%20informatique%20%26%20data", ProviderHttp.encode("stage informatique & data"));
    }
}
