package ma.ump.smartdept.offers;

import jakarta.validation.constraints.*;
import ma.ump.smartdept.common.StudyLevel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class OfferDtos {

    private OfferDtos() {
    }

    public static List<String> skillList(String skills) {
        return skills == null || skills.isBlank() ? List.of()
                : Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static String levelLabel(Integer min, Integer max) {
        if (min == null) return "Niveau non précisé";
        if (max == null || max.equals(min)) return "Bac+" + min;
        return "Bac+" + min + " à Bac+" + max;
    }

    public record OfferResponse(Long id, OfferSource source, String sourceLabel, String title, String company, String location,
                                OfferCountry country, String countryLabel, String flag, OfferKind kind, String kindLabel,
                                Integer levelMin, Integer levelMax, String levelLabel, boolean remote, List<String> skills,
                                Integer durationMonths, String salary, LocalDate publishedAt, LocalDate expiresAt, String url,
                                boolean internal, String excerpt, String description, String contactEmail, String postedBy,
                                Integer match, TrackingStatus tracking) {

        public static OfferResponse of(InternshipOffer o, boolean full, Integer match, TrackingStatus tracking) {
            String d = o.getDescription() == null ? "" : o.getDescription();
            String excerpt = d.length() > 260 ? d.substring(0, 260).replaceAll("\\s+\\S*$", "") + "…" : d;
            return new OfferResponse(o.getId(), o.getSource(), o.getSource().getLabel(), o.getTitle(), o.getCompany(),
                    o.getLocation(), o.getCountry(), o.getCountry().getLabel(), o.getCountry().getFlag(), o.getKind(),
                    o.getKind().getLabel(), o.getLevelMin(), o.getLevelMax(), OfferDtos.levelLabel(o.getLevelMin(), o.getLevelMax()),
                    o.isRemote(), skillList(o.getSkills()), o.getDurationMonths(), o.getSalary(), o.getPublishedAt(),
                    o.getExpiresAt(), o.getUrl(), o.isInternal(), excerpt, full ? d : null,
                    full ? o.getContactEmail() : null, o.getPostedBy() == null ? null : o.getPostedBy().getFullName(),
                    match, tracking);
        }
    }

    public record Facets(Map<String, Long> countries, Map<String, Long> kinds, Map<String, Long> levels,
                         Map<String, Long> sources, long unknownLevel) implements java.io.Serializable {
    }

    public record OfferPage(List<OfferResponse> items, long total, int page, int size, int totalPages, Facets facets,
                            Integer myLevel) {
    }

    public record OfferRequest(
            @NotBlank(message = "Le titre est obligatoire") @Size(max = 255) String title,
            @NotBlank(message = "L'entreprise est obligatoire") @Size(max = 200) String company,
            @NotBlank(message = "Le lieu est obligatoire") @Size(max = 200) String location,
            @NotNull(message = "Le pays est obligatoire") OfferCountry country,
            @NotNull(message = "Le type est obligatoire") OfferKind kind,
            @Min(2) @Max(5) Integer levelMin,
            @Min(2) @Max(5) Integer levelMax,
            boolean remote,
            @NotBlank(message = "La description est obligatoire") @Size(max = 8000) String description,
            @Size(max = 1000) String url,
            @Email(message = "E-mail de contact invalide") @Size(max = 150) String contactEmail,
            @Min(1) @Max(36) Integer durationMonths,
            @Size(max = 150) String salary,
            LocalDate expiresAt,
            @Size(max = 500) String skills) {
    }

    public record TrackingRequest(@NotNull(message = "Le statut est obligatoire") TrackingStatus status,
                                  @Size(max = 2000) String notes) {
    }

    public record TrackingResponse(Long id, TrackingStatus status, String notes, LocalDate appliedAt,
                                   LocalDateTime updatedAt, OfferResponse offer) {
        public static TrackingResponse of(OfferBookmark b) {
            return new TrackingResponse(b.getId(), b.getStatus(), b.getNotes(), b.getAppliedAt(), b.getUpdatedAt(),
                    OfferResponse.of(b.getOffer(), false, null, b.getStatus()));
        }
    }

    public record AlertRequest(@NotBlank(message = "Donnez un nom à l'alerte") @Size(max = 100) String name,
                               @Size(max = 200) String keywords, OfferCountry country, OfferKind kind,
                               @Min(2) @Max(5) Integer studyLevel, boolean remoteOnly, Boolean active) {
    }

    public record AlertResponse(Long id, String name, String keywords, OfferCountry country, OfferKind kind,
                                Integer studyLevel, boolean remoteOnly, boolean active, LocalDateTime lastNotifiedAt,
                                int matchesSent, LocalDateTime createdAt) {
        public static AlertResponse of(OfferAlert a) {
            return new AlertResponse(a.getId(), a.getName(), a.getKeywords(), a.getCountry(), a.getKind(), a.getStudyLevel(),
                    a.isRemoteOnly(), a.isActive(), a.getLastNotifiedAt(), a.getMatchesSent(), a.getCreatedAt());
        }
    }

    public record RunInfo(LocalDateTime startedAt, boolean success, int fetched, int created, int updated, long durationMs,
                          String message) {
        public static RunInfo of(OfferSyncRun r) {
            return r == null ? null : new RunInfo(r.getStartedAt(), r.isSuccess(), r.getFetched(), r.getCreatedCount(),
                    r.getUpdatedCount(), r.getDurationMs(), r.getMessage());
        }
    }

    public record SourceStatus(OfferSource source, String label, String website, boolean configured, boolean keyless,
                               List<OfferCountry> countries, String configHint, long activeOffers, int minIntervalMinutes,
                               RunInfo lastRun) {
    }

    public record SyncResult(OfferSource source, String label, OfferCountry country, int fetched, int created, int updated,
                             boolean success, String message, long durationMs) {
    }

    public record SyncSummary(List<SyncResult> results, int created, int updated, int alertsSent) {
    }

    public record LiveSearchRequest(@Size(max = 100) String keywords, OfferCountry country, OfferKind kind) {
    }

    public record MatchResponse(int score, String verdict, boolean levelFits, List<String> matchedSkills,
                                List<String> missingSkills, List<String> advice, String source) {
    }

    public record CoverLetterRequest(@Size(max = 10) String language, @Size(max = 1000) String extra) {
    }

    public record CoverLetterResponse(String text, String language, String source) {
    }

    public record NameCount(String name, long count) implements java.io.Serializable {
    }

    public record OfferStats(long total, long newThisWeek, Map<String, Long> byCountry, Map<String, Long> byLevel,
                             Map<String, Long> byKind, Map<String, Long> bySource, List<NameCount> topSkills,
                             List<NameCount> topCompanies, Map<String, Long> tracking) implements java.io.Serializable {
    }

    public record LevelInfo(StudyLevel level, int years, String label, String diplomas) {
        public static LevelInfo of(StudyLevel l) {
            return new LevelInfo(l, l.getYears(), l.getLabel(), l.getDiplomas());
        }
    }
}
