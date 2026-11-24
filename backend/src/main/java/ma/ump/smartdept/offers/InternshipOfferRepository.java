package ma.ump.smartdept.offers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InternshipOfferRepository extends JpaRepository<InternshipOffer, Long> {

    Optional<InternshipOffer> findBySourceAndExternalId(OfferSource source, String externalId);

    List<InternshipOffer> findBySourceAndExternalIdIn(OfferSource source, Collection<String> externalIds);

    /**
     * Recherche multicritère. Niveau : l'offre accepte le niveau demandé, ou ne précise pas de niveau si includeUnknown.
     * q est déjà en minuscules et entouré de %.
     */
    @Query("""
            select o from InternshipOffer o
            where o.active = true
              and (:country is null or o.country = :country)
              and (:kind is null or o.kind = :kind)
              and (:source is null or o.source = :source)
              and (:remoteOnly = false or o.remote = true)
              and (:level is null
                   or (o.levelMin is null and :includeUnknown = true)
                   or (o.levelMin <= :level and coalesce(o.levelMax, o.levelMin) >= :level))
              and (:q is null or lower(o.title) like :q or lower(o.company) like :q
                   or lower(o.skills) like :q or lower(o.location) like :q)
            """)
    Page<InternshipOffer> search(@Param("q") String q, @Param("country") OfferCountry country, @Param("kind") OfferKind kind,
                                 @Param("source") OfferSource source, @Param("remoteOnly") boolean remoteOnly,
                                 @Param("level") Integer level, @Param("includeUnknown") boolean includeUnknown, Pageable pageable);

    @Query("select o.country, count(o) from InternshipOffer o where o.active = true group by o.country")
    List<Object[]> countByCountry();

    @Query("select o.kind, count(o) from InternshipOffer o where o.active = true group by o.kind")
    List<Object[]> countByKind();

    @Query("select o.source, count(o) from InternshipOffer o where o.active = true group by o.source")
    List<Object[]> countBySource();

    @Query("select o.levelMin, o.levelMax from InternshipOffer o where o.active = true")
    List<Object[]> levelRanges();

    @Query("select o.company, count(o) from InternshipOffer o where o.active = true and o.company is not null group by o.company order by count(o) desc")
    List<Object[]> topCompanies(Pageable pageable);

    @Query("select o.skills from InternshipOffer o where o.active = true and o.skills is not null")
    List<String> allSkills();

    long countByActiveTrue();

    long countByActiveTrueAndCreatedAtAfter(LocalDateTime after);

    long countBySourceAndActiveTrue(OfferSource source);

    List<InternshipOffer> findTop300ByActiveTrueOrderByPublishedAtDesc();

    List<InternshipOffer> findByActiveTrueAndSourceNot(OfferSource source);

    List<InternshipOffer> findByPostedByIdOrderByCreatedAtDesc(Long userId);

    /** Offres externes non revues depuis la date donnée : retirées de la recherche. */
    @Modifying
    @Query("update InternshipOffer o set o.active = false where o.active = true and o.source <> ma.ump.smartdept.offers.OfferSource.INTERNAL and o.fetchedAt < :before")
    int deactivateStale(@Param("before") LocalDateTime before);

    @Modifying
    @Query("update InternshipOffer o set o.active = false where o.active = true and o.expiresAt < :today")
    int deactivateExpired(@Param("today") LocalDate today);
}
