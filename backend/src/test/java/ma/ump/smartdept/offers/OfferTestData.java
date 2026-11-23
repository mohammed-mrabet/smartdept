package ma.ump.smartdept.offers;

import java.time.LocalDate;

final class OfferTestData {

    private OfferTestData() {
    }

    static InternshipOffer offer(long id, String title, OfferCountry country, OfferKind kind, Integer min, Integer max, String skills) {
        InternshipOffer o = new InternshipOffer();
        o.setId(id);
        o.setSource(OfferSource.INTERNAL);
        o.setExternalId("t-" + id);
        o.setTitle(title);
        o.setCompany("Entreprise " + id);
        o.setLocation(country.getLabel());
        o.setCountry(country);
        o.setKind(kind);
        o.setLevelMin(min);
        o.setLevelMax(max);
        o.setSkills(skills);
        o.setDescription(title + " — missions : " + skills);
        o.setPublishedAt(LocalDate.of(2026, 11, 20));
        o.setActive(true);
        return o;
    }
}
