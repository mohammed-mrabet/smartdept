package ma.ump.smartdept.offers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferSyncRunRepository extends JpaRepository<OfferSyncRun, Long> {

    Optional<OfferSyncRun> findTopBySourceOrderByStartedAtDesc(OfferSource source);

    Optional<OfferSyncRun> findTopBySourceAndSuccessTrueOrderByStartedAtDesc(OfferSource source);

    List<OfferSyncRun> findTop30ByOrderByStartedAtDesc();
}
