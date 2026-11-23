package ma.ump.smartdept.offers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OfferAlertRepository extends JpaRepository<OfferAlert, Long> {

    List<OfferAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select a from OfferAlert a join fetch a.user u where a.active = true and u.enabled = true")
    List<OfferAlert> findActiveWithUser();
}
