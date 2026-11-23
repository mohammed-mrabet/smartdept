package ma.ump.smartdept.offers;

import ma.ump.smartdept.ai.AiClient;
import ma.ump.smartdept.ai.TextAnalyzer;
import ma.ump.smartdept.offers.OfferDtos.CoverLetterResponse;
import ma.ump.smartdept.offers.OfferDtos.MatchResponse;
import ma.ump.smartdept.user.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * IA appliquée aux offres :
 * - score de correspondance profil / offre (compétences communes, proximité du texte, niveau d'études) ;
 * - lettre de motivation rédigée par le modèle IA, ou modèle de lettre en français, espagnol ou anglais sans clé.
 */
@Service
public class OfferAiService {

    /** Profil de l'étudiant utilisé pour le calcul : niveau (années après le bac) et texte décrivant ses compétences. */
    public record Profile(Integer level, String text, Set<String> skills) {
        public boolean isEmpty() {
            return skills.isEmpty() && text.isBlank();
        }
    }

    private final AiClient ai;

    public OfferAiService(AiClient ai) {
        this.ai = ai;
    }

    public static Profile profile(User u) {
        Integer level = u.getFiliere() == null || u.getFiliere().getStudyLevel() == null ? null
                : u.getFiliere().getStudyLevel().getYears();
        String text = String.join(" ",
                u.getSpecialties() == null ? "" : u.getSpecialties(),
                u.getFiliere() == null ? "" : u.getFiliere().getName(),
                u.getFiliere() == null || u.getFiliere().getDescription() == null ? "" : u.getFiliere().getDescription()).trim();
        return new Profile(level, text, new LinkedHashSet<>(OfferClassifier.skills(text, 40)));
    }

    private static List<String> offerSkills(InternshipOffer o) {
        List<String> s = OfferDtos.skillList(o.getSkills());
        return s.isEmpty() ? OfferClassifier.skills(o.getTitle() + " " + o.getDescription(), 8) : s;
    }

    /** Score de 0 à 100 : 60 % compétences, 20 % proximité du texte, 20 % niveau d'études. */
    public static int score(Profile p, InternshipOffer o) {
        List<String> required = offerSkills(o);
        long common = required.stream().filter(p.skills()::contains).count();
        double skillScore = required.isEmpty() ? 0.4 : (double) common / required.size();
        double text = Math.min(1.0, TextAnalyzer.coverage(p.text(), o.getTitle() + " " + o.getDescription()) * 2);
        boolean levelFits = p.level() == null || o.acceptsLevel(p.level());
        return (int) Math.round(100 * (0.6 * skillScore + 0.2 * text + (levelFits ? 0.2 : 0)));
    }

    public MatchResponse match(User u, InternshipOffer o) {
        Profile p = profile(u);
        List<String> required = offerSkills(o);
        List<String> matched = required.stream().filter(p.skills()::contains).toList();
        List<String> missing = required.stream().filter(s -> !p.skills().contains(s)).toList();
        boolean levelFits = p.level() == null || o.acceptsLevel(p.level());
        int score = score(p, o);
        String verdict = score >= 75 ? "Excellente correspondance" : score >= 50 ? "Bonne correspondance"
                : score >= 30 ? "Correspondance partielle" : "Correspondance faible";
        List<String> advice = new ArrayList<>();
        if (p.isEmpty()) {
            advice.add("Renseignez vos compétences dans « Mon profil » pour un score plus précis.");
        }
        if (!levelFits) {
            advice.add("Cette offre vise " + OfferDtos.levelLabel(o.getLevelMin(), o.getLevelMax())
                    + " ; vous êtes en Bac+" + p.level() + ". Vérifiez auprès de l'entreprise avant de postuler.");
        }
        if (!matched.isEmpty()) {
            advice.add("Mettez en avant vos projets en " + String.join(", ", matched) + " dans le CV et la lettre.");
        }
        if (!missing.isEmpty()) {
            advice.add("Compétences demandées à travailler ou à valoriser : " + String.join(", ", missing.stream().limit(4).toList()) + ".");
        }
        if (o.getDurationMonths() != null) {
            advice.add("Durée annoncée : " + o.getDurationMonths() + " mois — vérifiez qu'elle correspond à votre calendrier de stage.");
        }
        return new MatchResponse(score, verdict, levelFits, matched, missing, advice, "local");
    }

    public CoverLetterResponse coverLetter(User u, InternshipOffer o, String language, String extra) {
        String lang = language != null && !language.isBlank() ? language.toLowerCase()
                : o.getCountry() == OfferCountry.ES ? "es"
                : o.getCountry() == OfferCountry.REMOTE || o.getCountry() == OfferCountry.OTHER ? "en" : "fr";
        Profile p = profile(u);
        String languageName = switch (lang) {
            case "es" -> "espagnol";
            case "en" -> "anglais";
            default -> "français";
        };
        String system = "Tu rédiges des lettres de motivation de stage sobres, précises et sans formule creuse, en "
                + languageName + ". 200 à 260 mots, 4 paragraphes, sans en-tête d'adresse. Ne jamais inventer d'expérience.";
        String prompt = "Étudiant : " + u.getFullName()
                + (u.getFiliere() == null ? "" : ", " + u.getFiliere().getName())
                + (p.level() == null ? "" : " (Bac+" + p.level() + ")")
                + "\nCompétences : " + (p.text().isBlank() ? "non renseignées" : p.text())
                + "\nOffre : " + o.getTitle() + (o.getCompany() == null ? "" : " chez " + o.getCompany())
                + (o.getLocation() == null ? "" : " (" + o.getLocation() + ")")
                + "\nDescription : " + TextAnalyzer.summarize(o.getDescription() == null ? "" : o.getDescription(), 5)
                + (extra == null || extra.isBlank() ? "" : "\nÉléments à intégrer : " + extra.trim());
        return ai.chat(system, prompt)
                .map(text -> new CoverLetterResponse(text.trim(), lang, "ia"))
                .orElseGet(() -> new CoverLetterResponse(template(u, o, p, lang, extra), lang, "local"));
    }

    static String template(User u, InternshipOffer o, Profile p, String lang, String extra) {
        String company = o.getCompany() == null ? "" : o.getCompany();
        List<String> matched = offerSkills(o).stream().filter(p.skills()::contains).toList();
        String skills = matched.isEmpty() ? String.join(", ", p.skills().stream().limit(4).toList()) : String.join(", ", matched);
        String filiere = u.getFiliere() == null ? "" : u.getFiliere().getName();
        String more = extra == null || extra.isBlank() ? "" : "\n\n" + extra.trim();
        return switch (lang) {
            case "es" -> "Estimado/a responsable de selección:\n\n"
                    + "Le escribo para presentar mi candidatura a la oferta « " + o.getTitle() + " »" + (company.isEmpty() ? "" : " en " + company) + ". "
                    + "Actualmente curso " + (filiere.isEmpty() ? "estudios de informática" : filiere) + " en la Facultad de Ciencias de Oujda (Marruecos).\n\n"
                    + (skills.isEmpty() ? "" : "Durante mi formación he trabajado con " + skills + ", competencias que encajan con los requisitos del puesto. ")
                    + "Me motiva especialmente la posibilidad de aplicar estos conocimientos en un entorno profesional y de aprender de su equipo." + more + "\n\n"
                    + "Quedo a su disposición para una entrevista en la fecha que mejor le convenga.\n\n"
                    + "Atentamente,\n" + u.getFullName();
            case "en" -> "Dear Hiring Manager,\n\n"
                    + "I am applying for the position « " + o.getTitle() + " »" + (company.isEmpty() ? "" : " at " + company) + ". "
                    + "I am currently studying " + (filiere.isEmpty() ? "computer science" : filiere) + " at the Faculty of Sciences of Oujda, Morocco.\n\n"
                    + (skills.isEmpty() ? "" : "Through my coursework and projects I have built practical experience with " + skills + ", which matches your requirements. ")
                    + "I am eager to contribute to your team while developing my skills in a professional environment." + more + "\n\n"
                    + "I would welcome the opportunity to discuss my application at your convenience.\n\n"
                    + "Kind regards,\n" + u.getFullName();
            default -> "Madame, Monsieur,\n\n"
                    + "Actuellement en " + (filiere.isEmpty() ? "formation en informatique" : filiere)
                    + (p.level() == null ? "" : " (Bac+" + p.level() + ")") + " à la Faculté des Sciences d'Oujda, "
                    + "je souhaite vous proposer ma candidature pour l'offre « " + o.getTitle() + " »" + (company.isEmpty() ? "" : " au sein de " + company) + ".\n\n"
                    + (skills.isEmpty() ? "" : "Au cours de ma formation et de mes projets, j'ai mis en pratique " + skills
                    + ", des compétences directement utiles pour les missions décrites. ")
                    + "Rigoureux(se) et à l'aise en équipe, je souhaite mettre ces acquis au service d'un projet concret et progresser auprès de vos équipes." + more + "\n\n"
                    + "Je serais heureux(se) de vous présenter ma motivation lors d'un entretien. "
                    + "Je vous prie d'agréer, Madame, Monsieur, l'expression de mes salutations distinguées.\n\n" + u.getFullName();
        };
    }
}
