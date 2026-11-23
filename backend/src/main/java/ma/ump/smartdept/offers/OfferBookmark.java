package ma.ump.smartdept.offers;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;
import ma.ump.smartdept.user.User;

import java.time.LocalDate;

/** Offre suivie par un étudiant (favori puis candidature externe : postulé, entretien, réponse). */
@Entity
@Table(name = "offer_bookmarks",
        uniqueConstraints = @UniqueConstraint(name = "uq_bookmark_user_offer", columnNames = {"user_id", "offer_id"}))
public class OfferBookmark extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private InternshipOffer offer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrackingStatus status = TrackingStatus.SAVED;

    @Column(length = 2000)
    private String notes;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Column(name = "follow_up_sent", nullable = false)
    private boolean followUpSent;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public InternshipOffer getOffer() {
        return offer;
    }

    public void setOffer(InternshipOffer offer) {
        this.offer = offer;
    }

    public TrackingStatus getStatus() {
        return status;
    }

    public void setStatus(TrackingStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = appliedAt;
    }

    public boolean isFollowUpSent() {
        return followUpSent;
    }

    public void setFollowUpSent(boolean followUpSent) {
        this.followUpSent = followUpSent;
    }
}
