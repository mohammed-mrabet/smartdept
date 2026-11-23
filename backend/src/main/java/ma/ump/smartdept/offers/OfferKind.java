package ma.ump.smartdept.offers;

public enum OfferKind {
    PFE("Stage de fin d'études"),
    STAGE("Stage"),
    ALTERNANCE("Alternance"),
    EMPLOI("Premier emploi");

    private final String label;

    OfferKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
