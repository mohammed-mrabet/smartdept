package ma.ump.smartdept.offers;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;

import java.time.LocalDateTime;

/** Journal des synchronisations (une ligne par fournisseur et par passage). */
@Entity
@Table(name = "offer_sync_runs")
public class OfferSyncRun extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OfferSource source;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(nullable = false)
    private int fetched;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String message;

    public OfferSource getSource() {
        return source;
    }

    public void setSource(OfferSource source) {
        this.source = source;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getFetched() {
        return fetched;
    }

    public void setFetched(int fetched) {
        this.fetched = fetched;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
