package ma.ump.smartdept.offers;

/** Origine d'une offre : publiée dans SmartDept ou récupérée sur une API gratuite. */
public enum OfferSource {
    INTERNAL("SmartDept", "https://smartdept.ma"),
    FRANCE_TRAVAIL("France Travail", "https://francetravail.io"),
    LA_BONNE_ALTERNANCE("La bonne alternance", "https://labonnealternance.apprentissage.beta.gouv.fr"),
    ADZUNA("Adzuna", "https://www.adzuna.fr"),
    JOOBLE("Jooble", "https://jooble.org"),
    CAREERJET("Careerjet", "https://www.optioncarriere.ma"),
    INFOJOBS("InfoJobs", "https://www.infojobs.net"),
    REMOTIVE("Remotive", "https://remotive.com"),
    JOBICY("Jobicy", "https://jobicy.com"),
    ARBEITNOW("Arbeitnow", "https://www.arbeitnow.com");

    private final String label;
    private final String website;

    OfferSource(String label, String website) {
        this.label = label;
        this.website = website;
    }

    public String getLabel() {
        return label;
    }

    public String getWebsite() {
        return website;
    }
}
