package ma.ump.smartdept.offers;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offre de stage, d'alternance ou de premier emploi.
 * Une offre externe est identifiée par (source, externalId) : une nouvelle synchronisation la met à jour au lieu de la dupliquer.
 * Les niveaux d'études acceptés sont un intervalle [levelMin, levelMax] en années après le bac (2 à 5) ; null = non précisé.
 */
@Entity
@Table(name = "internship_offers",
        uniqueConstraints = @UniqueConstraint(name = "uq_offer_source_external", columnNames = {"source", "external_id"}))
public class InternshipOffer extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferSource source;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 200)
    private String company;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OfferCountry country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferKind kind;

    @Column(name = "level_min")
    private Integer levelMin;

    @Column(name = "level_max")
    private Integer levelMax;

    @Column(nullable = false)
    private boolean remote;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 1000)
    private String url;

    @Column(length = 150)
    private String salary;

    @Column(length = 500)
    private String skills;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "contact_email", length = 150)
    private String contactEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_id")
    private User postedBy;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    public boolean acceptsLevel(int years) {
        return levelMin == null || (levelMin <= years && (levelMax == null || levelMax >= years));
    }

    public boolean isInternal() {
        return source == OfferSource.INTERNAL;
    }

    public OfferSource getSource() {
        return source;
    }

    public void setSource(OfferSource source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public OfferCountry getCountry() {
        return country;
    }

    public void setCountry(OfferCountry country) {
        this.country = country;
    }

    public OfferKind getKind() {
        return kind;
    }

    public void setKind(OfferKind kind) {
        this.kind = kind;
    }

    public Integer getLevelMin() {
        return levelMin;
    }

    public void setLevelMin(Integer levelMin) {
        this.levelMin = levelMin;
    }

    public Integer getLevelMax() {
        return levelMax;
    }

    public void setLevelMax(Integer levelMax) {
        this.levelMax = levelMax;
    }

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public Integer getDurationMonths() {
        return durationMonths;
    }

    public void setDurationMonths(Integer durationMonths) {
        this.durationMonths = durationMonths;
    }

    public LocalDate getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDate publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public User getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(User postedBy) {
        this.postedBy = postedBy;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
