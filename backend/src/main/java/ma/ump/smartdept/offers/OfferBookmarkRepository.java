package ma.ump.smartdept.offers;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OfferBookmarkRepository extends JpaRepository<OfferBookmark, Long> {

    Optional<OfferBookmark> findByUserIdAndOfferId(Long userId, Long offerId);

    @Query("select b from OfferBookmark b join fetch b.offer where b.user.id = :userId order by b.updatedAt desc")
    List<OfferBookmark> findForUser(@Param("userId") Long userId);

    @Query("select b.offer.id, b.status from OfferBookmark b where b.user.id = :userId")
    List<Object[]> statusesForUser(@Param("userId") Long userId);

    @Query("select b.status, count(b) from OfferBookmark b group by b.status")
    List<Object[]> countByStatus();

    @Query("select b from OfferBookmark b join fetch b.offer join fetch b.user where b.status = ma.ump.smartdept.offers.TrackingStatus.APPLIED and b.appliedAt <= :before and b.followUpSent = false")
    List<OfferBookmark> findDueForFollowUp(@Param("before") LocalDate before);
}
