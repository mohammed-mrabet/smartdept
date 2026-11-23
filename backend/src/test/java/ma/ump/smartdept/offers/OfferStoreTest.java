package ma.ump.smartdept.offers;

import ma.ump.smartdept.offers.provider.FetchedOffer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfferStoreTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 10, 0);

    @Test
    void classifiesAFetchedOffer() {
        InternshipOffer o = new InternshipOffer();
        FetchedOffer f = FetchedOffer.of("42", "Stage PFE — Développeur Spring Boot (Bac+5)", "Acme", "Remote - Europe",
                OfferCountry.REMOTE, "<p>Stage de 6 mois, <b>Docker</b> et Angular. Full remote.</p>", "https://x.test/42", null, null, null);
        OfferStore.apply(o, f, NOW);
        assertEquals(OfferKind.PFE, o.getKind());
        assertEquals(5, o.getLevelMin());
        assertEquals(6, o.getDurationMonths());
        assertTrue(o.isRemote());
        assertTrue(o.getSkills().contains("Docker"));
        assertFalse(o.getDescription().contains("<p>"));
        assertEquals(LocalDate.of(2026, 11, 23), o.getPublishedAt());
    }

    @Test
    void levelsFromTheApiTakePrecedence() {
        InternshipOffer o = new InternshipOffer();
        OfferStore.apply(o, new FetchedOffer("1", "Alternance développeur", "X", "Paris", OfferCountry.FR, "Bac+5", "https://x", null,
                LocalDate.of(2026, 11, 1), "apprentissage", null, 3, 3), NOW);
        assertEquals(3, o.getLevelMin());
        assertEquals(OfferKind.ALTERNANCE, o.getKind());
    }

    @Test
    void createsNewOffersAndUpdatesKnownOnes() {
        InternshipOfferRepository repo = mock(InternshipOfferRepository.class);
        InternshipOffer known = new InternshipOffer();
        known.setId(1L);
        known.setExternalId("a");
        when(repo.findBySourceAndExternalIdIn(eq(OfferSource.ADZUNA), anySet())).thenReturn(List.of(known));
        AtomicLong ids = new AtomicLong(100);
        when(repo.save(any())).thenAnswer(inv -> {
            InternshipOffer o = inv.getArgument(0);
            if (o.getId() == null) o.setId(ids.incrementAndGet());
            return o;
        });
        OfferStore store = new OfferStore(repo);
        List<FetchedOffer> fetched = List.of(
                FetchedOffer.of("a", "Stage Java", "X", "Paris", OfferCountry.FR, "d", "https://x/a", null, null, null),
                FetchedOffer.of("b", "Stage Python", "Y", "Lyon", OfferCountry.FR, "d", "https://x/b", null, null, null),
                FetchedOffer.of("b", "Doublon", "Y", "Lyon", OfferCountry.FR, "d", "https://x/b", null, null, null),
                FetchedOffer.of(null, "Sans identifiant", "Z", "Lille", OfferCountry.FR, "d", "https://x/c", null, null, null));
        OfferStore.UpsertResult r = store.upsert(OfferSource.ADZUNA, fetched, NOW);
        assertEquals(List.of(101L), r.createdIds());
        assertEquals(1, r.updated());
        verify(repo, times(2)).save(any());
    }
}
