package ma.ump.smartdept.common;

/** Niveau d'études après le baccalauréat (filières, offres de stage). */
public enum StudyLevel {
    BAC_2(2, "Bac+2", "DUT, BTS, DEUST, DEUG"),
    BAC_3(3, "Bac+3", "Licence, Bachelor"),
    BAC_4(4, "Bac+4", "Master 1"),
    BAC_5(5, "Bac+5", "Master 2, ingénieur");

    private final int years;
    private final String label;
    private final String diplomas;

    StudyLevel(int years, String label, String diplomas) {
        this.years = years;
        this.label = label;
        this.diplomas = diplomas;
    }

    public int getYears() {
        return years;
    }

    public String getLabel() {
        return label;
    }

    public String getDiplomas() {
        return diplomas;
    }

    public static StudyLevel ofYears(Integer years) {
        if (years == null) return null;
        for (StudyLevel l : values()) {
            if (l.years == years) return l;
        }
        return years > 5 ? BAC_5 : null;
    }
}
