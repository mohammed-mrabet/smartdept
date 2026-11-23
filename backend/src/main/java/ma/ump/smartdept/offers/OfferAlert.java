package ma.ump.smartdept.offers;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

import java.time.LocalDateTime;

/** Recherche enregistrée : l'utilisateur est notifié (application + e-mail) quand de nouvelles offres y correspondent. */
@Entity
@Table(name = "offer_alerts")
public class OfferAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String keywords;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private OfferCountry country;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OfferKind kind;

    @Column(name = "study_level")
    private Integer studyLevel;

    @Column(name = "remote_only", nullable = false)
    private boolean remoteOnly;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "matches_sent", nullable = false)
    private int matchesSent;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
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

    public Integer getStudyLevel() {
        return studyLevel;
    }

    public void setStudyLevel(Integer studyLevel) {
        this.studyLevel = studyLevel;
    }

    public boolean isRemoteOnly() {
        return remoteOnly;
    }

    public void setRemoteOnly(boolean remoteOnly) {
        this.remoteOnly = remoteOnly;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getLastNotifiedAt() {
        return lastNotifiedAt;
    }

    public void setLastNotifiedAt(LocalDateTime lastNotifiedAt) {
        this.lastNotifiedAt = lastNotifiedAt;
    }

    public int getMatchesSent() {
        return matchesSent;
    }

    public void setMatchesSent(int matchesSent) {
        this.matchesSent = matchesSent;
    }
}
