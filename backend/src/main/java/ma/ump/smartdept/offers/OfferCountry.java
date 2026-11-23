package ma.ump.smartdept.offers;

/** Pays couverts par la recherche d'offres. REMOTE = télétravail sans pays imposé. */
public enum OfferCountry {
    FR("France", "🇫🇷"),
    MA("Maroc", "🇲🇦"),
    CA("Canada", "🇨🇦"),
    BE("Belgique", "🇧🇪"),
    ES("Espagne", "🇪🇸"),
    REMOTE("Télétravail", "🌍"),
    OTHER("Autre pays", "🏳");

    private final String label;
    private final String flag;

    OfferCountry(String label, String flag) {
        this.label = label;
        this.flag = flag;
    }

    public String getLabel() {
        return label;
    }

    public String getFlag() {
        return flag;
    }
}
