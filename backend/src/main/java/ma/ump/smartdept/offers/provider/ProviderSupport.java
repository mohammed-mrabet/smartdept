package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferKind;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Petites conversions partagées par les fournisseurs. */
public final class ProviderSupport {

    private ProviderSupport() {
    }

    /** Date à partir des formats rencontrés : ISO, ISO avec heure, epoch en secondes, RFC 1123. */
    public static LocalDate date(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            return Instant.ofEpochSecond(n.longValue()).atZone(ZoneOffset.UTC).toLocalDate();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        try {
            if (s.matches("\\d{9,10}")) return Instant.ofEpochSecond(Long.parseLong(s)).atZone(ZoneOffset.UTC).toLocalDate();
            if (s.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(s);
            if (s.matches("\\d{4}-\\d{2}-\\d{2}[T ].*")) {
                try {
                    return OffsetDateTime.parse(s.replace(' ', 'T')).toLocalDate();
                } catch (RuntimeException e) {
                    return LocalDate.parse(s.substring(0, 10));
                }
            }
            return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)).toLocalDate();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Mots-clés par défaut selon le type recherché et la langue du pays. */
    public static String keywords(String keywords, OfferKind kind, OfferCountry country) {
        if (keywords != null && !keywords.isBlank()) return keywords.trim();
        boolean es = country == OfferCountry.ES;
        boolean en = country == OfferCountry.REMOTE || country == OfferCountry.OTHER;
        if (kind == OfferKind.ALTERNANCE) return es ? "formación dual informática" : en ? "apprenticeship software" : "alternance informatique";
        if (kind == OfferKind.PFE) return es ? "prácticas fin de grado informática" : en ? "final year internship software" : "stage PFE informatique";
        if (kind == OfferKind.EMPLOI) return es ? "desarrollador junior" : en ? "junior developer" : "développeur junior";
        return es ? "prácticas informática" : en ? "software intern" : "stage informatique";
    }

    public static String countryName(OfferCountry c) {
        return switch (c) {
            case FR -> "France";
            case MA -> "Maroc";
            case CA -> "Canada";
            case BE -> "Belgique";
            case ES -> "España";
            default -> "";
        };
    }

    public static String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
