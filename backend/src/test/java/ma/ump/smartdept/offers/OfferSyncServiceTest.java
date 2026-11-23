package ma.ump.smartdept.offers;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.offers.OfferDtos.LiveSearchRequest;
import ma.ump.smartdept.offers.OfferDtos.SyncSummary;
import ma.ump.smartdept.offers.provider.FetchedOffer;
import ma.ump.smartdept.offers.provider.OfferProvider;
import ma.ump.smartdept.offers.provider.OfferQuery;
import ma.ump.smartdept.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfferSyncServiceTest {

    private OfferStore store;
    private OfferAlertService alerts;
    private OfferSyncRunRepository runs;
    private OfferProvider working;
    private OfferProvider failing;
    private OfferProvider notConfigured;
    private OfferSyncService service;

    @BeforeEach
    void setUp() throws Exception {
        store = mock(OfferStore.class);
        alerts = mock(OfferAlertService.class);
        runs = mock(OfferSyncRunRepository.class);
        working = provider(OfferSource.ADZUNA, true, List.of(OfferCountry.FR, OfferCountry.BE));
        failing = provider(OfferSource.JOOBLE, true, List.of(OfferCountry.MA));
        notConfigured = provider(OfferSource.INFOJOBS, false, List.of(OfferCountry.ES));
        when(working.fetch(any())).thenReturn(List.of(FetchedOffer.of("1", "Stage", "X", "Paris", OfferCountry.FR, "d", "https://x", null, null, null)));
        when(failing.fetch(any())).thenThrow(new IllegalStateException("HTTP 503"));
        when(store.upsert(any(), anyList(), any())).thenReturn(new OfferStore.UpsertResult(List.of(10L), 0));
        when(alerts.dispatch(anyCollection())).thenReturn(1);
        service = new OfferSyncService(List.of(working, failing, notConfigured), store, mock(InternshipOfferRepository.class),
                runs, alerts, TestData.CLOCK, new io.micrometer.core.instrument.simple.SimpleMeterRegistry(), 45, 50);
    }

    private static OfferProvider provider(OfferSource source, boolean configured, List<OfferCountry> countries) {
        OfferProvider p = mock(OfferProvider.class);
        when(p.source()).thenReturn(source);
        when(p.isConfigured()).thenReturn(configured);
        when(p.countries()).thenReturn(countries);
        when(p.maxQueriesPerSync()).thenReturn(3);
        when(p.minIntervalMinutes()).thenReturn(0);
        return p;
    }

    @Test
    void oneFailingApiDoesNotStopTheOthers() throws Exception {
        SyncSummary s = service.syncAll(null);
        assertEquals(2, s.results().size(), "l'API non configurée est ignorée");
        assertTrue(s.results().get(0).success());
        assertFalse(s.results().get(1).success());
        assertTrue(s.results().get(1).message().contains("HTTP 503"));
        assertEquals(3, s.created(), "3 requêtes Adzuna (FR, FR, FR…) plafonnées à maxQueriesPerSync");
        assertEquals(1, s.alertsSent());
        verify(notConfigured, never()).fetch(any());
        verify(runs, times(2)).save(any());
    }

    @Test
    void respectsTheMinimumIntervalOfAnApi() throws Exception {
        when(working.minIntervalMinutes()).thenReturn(360);
        service.syncAll(OfferSource.ADZUNA);
        SyncSummary second = service.syncAll(OfferSource.ADZUNA);
        assertTrue(second.results().get(0).message().startsWith("Ignoré"));
        verify(working, times(3)).fetch(any());
    }

    @Test
    void liveSearchTargetsOnlyApisCoveringTheCountry() throws Exception {
        var user = TestData.user(7, "Omar", "Tazi", Role.ETUDIANT);
        SyncSummary s = service.liveSearch(user, new LiveSearchRequest("spring", OfferCountry.BE, OfferKind.PFE));
        assertEquals(1, s.results().size());
        verify(working).fetch(new OfferQuery("spring", OfferCountry.BE, OfferKind.PFE, 50));
        verify(failing, never()).fetch(any());
        assertThrows(ApiException.class, () -> service.liveSearch(user, new LiveSearchRequest("x", null, null)), "30 s entre deux recherches");
    }
}
