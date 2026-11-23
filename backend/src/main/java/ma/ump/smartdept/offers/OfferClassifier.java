package ma.ump.smartdept.offers;

import ma.ump.smartdept.ai.TextAnalyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyse le texte brut d'une offre (titre + description, en français, anglais ou espagnol) pour en déduire :
 * le type (PFE, stage, alternance, emploi), les niveaux Bac+2 à Bac+5, les compétences, la durée et le télétravail.
 * Les API gratuites ne fournissent presque jamais ces informations sous forme structurée.
 */
public final class OfferClassifier {

    private OfferClassifier() {
    }

    private static final Pattern PFE = Pattern.compile(
            "\\bpfe\\b|fin d.?[ée]tudes|projet de fin|end.of.studies|final.year (project|internship)|trabajo (de )?fin de (grado|m[áa]ster)");
    private static final Pattern ALTERNANCE = Pattern.compile(
            "alternance|alternant|apprenti|apprentissage|contrat pro|work.study|dual|formaci[óo]n dual");
    private static final Pattern STAGE = Pattern.compile(
            "\\bstages?\\b|stagiaire|intern(ship)?s?\\b|trainee|pr[áa]cticas|becari[oa]|praktikum|werkstudent|co.?op\\b|placement");

    /** Niveau → motifs qui le signalent. L'ordre compte : "bac+5" doit être testé avant "bac+2" pour "bac+2 à bac+5". */
    private static final Map<Integer, Pattern> LEVELS = new LinkedHashMap<>();

    static {
        LEVELS.put(2, Pattern.compile("bac ?\\+ ?2|\\bbts\\b|\\bdut\\b|\\bdeust\\b|\\bdeug\\b|\\bbut\\b ?[12]|t[ée]cnico superior|\\bfp ?ii\\b|ciclo formativo|technicien sup"));
        LEVELS.put(3, Pattern.compile("bac ?\\+ ?3|licence|bachelor|\\bl3\\b|\\bbut\\b ?3|grado|undergraduate|\\bbac\\b ?3"));
        LEVELS.put(4, Pattern.compile("bac ?\\+ ?4|master ?1|\\bm1\\b|premiere annee de master"));
        LEVELS.put(5, Pattern.compile("bac ?\\+ ?5|master ?2|\\bm2\\b|ing[ée]nieur|engineering school|grande [ée]cole|m[áa]ster(?! ?1)(?!'?s)|graduate student|msc\\b|master'?s"));
    }

    private static final Pattern DURATION = Pattern.compile("(\\d{1,2})\\s*(mois|months?|meses|semaines|weeks|semanas)");
    private static final Pattern REMOTE = Pattern.compile("t[ée]l[ée]travail|remote|teletrabajo|hybride|hybrid|home.?office|full.?remote");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern COUNTRY_MA = Pattern.compile("\\b(maroc|morocco|marruecos|casablanca|rabat|tanger|marrakech|fes|agadir|oujda|kenitra|meknes)\\b");
    private static final Pattern COUNTRY_BE = Pattern.compile("\\b(belgi\\w*|bruxelles|brussels|liege|anvers|antwerp|gand|gent|namur|charleroi|louvain)\\b");
    private static final Pattern COUNTRY_CA = Pattern.compile("\\b(canada|quebec|qc|montreal|toronto|ottawa|vancouver|calgary|laval|gatineau|ontario)\\b");
    private static final Pattern COUNTRY_ES = Pattern.compile("\\b(espagne|spain|espana|madrid|barcelona|valencia|sevilla|malaga|bilbao|zaragoza)\\b");
    private static final Pattern COUNTRY_FR = Pattern.compile("\\b(france|paris|lyon|marseille|toulouse|lille|nantes|bordeaux|rennes|strasbourg|montpellier|nice|ile-de-france)\\b|^\\d{2} - |\\(\\d{2}\\)");
    private static final Pattern COUNTRY_REMOTE = Pattern.compile("\\b(remote|anywhere|worldwide|teletravail|europe|emea)\\b");

    /** Vocabulaire technique reconnu (mot normalisé → libellé affiché). */
    private static final Map<String, String> SKILLS = new LinkedHashMap<>();

    static {
        String[][] pairs = {
                {"java", "Java"}, {"spring", "Spring"}, {"angular", "Angular"}, {"react", "React"}, {"vue", "Vue.js"},
                {"typescript", "TypeScript"}, {"javascript", "JavaScript"}, {"node", "Node.js"}, {"python", "Python"},
                {"django", "Django"}, {"flask", "Flask"}, {"php", "PHP"}, {"laravel", "Laravel"}, {"symfony", "Symfony"},
                {".net", ".NET"}, {"c#", "C#"}, {"c++", "C++"}, {"golang", "Go"}, {"kotlin", "Kotlin"}, {"swift", "Swift"},
                {"flutter", "Flutter"}, {"android", "Android"}, {"ios", "iOS"}, {"sql", "SQL"}, {"mysql", "MySQL"},
                {"postgresql", "PostgreSQL"}, {"mongodb", "MongoDB"}, {"oracle", "Oracle"}, {"docker", "Docker"},
                {"kubernetes", "Kubernetes"}, {"jenkins", "Jenkins"}, {"devops", "DevOps"}, {"terraform", "Terraform"},
                {"ansible", "Ansible"}, {"azure", "Azure"}, {"aws", "AWS"}, {"gcp", "GCP"}, {"cloud", "Cloud"},
                {"linux", "Linux"}, {"git", "Git"}, {"machine learning", "Machine learning"}, {"deep learning", "Deep learning"},
                {"nlp", "NLP"}, {"llm", "LLM"}, {"data", "Data"}, {"big data", "Big Data"}, {"spark", "Spark"},
                {"power bi", "Power BI"}, {"tableau", "Tableau"}, {"excel", "Excel"}, {"sap", "SAP"}, {"salesforce", "Salesforce"},
                {"cybersecurite", "Cybersécurité"}, {"securite", "Sécurité"}, {"security", "Sécurité"}, {"reseau", "Réseaux"},
                {"network", "Réseaux"}, {"scrum", "Scrum"}, {"agile", "Agile"}, {"uml", "UML"}, {"api", "API REST"},
                {"microservices", "Microservices"}, {"tests", "Tests"}, {"selenium", "Selenium"}, {"figma", "Figma"}
        };
        for (String[] p : pairs) SKILLS.put(p[0], p[1]);
    }

    public static String lower(String text) {
        return text == null ? "" : TextAnalyzer.normalize(text).toLowerCase(Locale.ROOT);
    }

    /** Type d'offre : l'indice explicite de l'API (ex. "apprentissage") passe avant l'analyse du texte. */
    public static OfferKind kind(String title, String description, String hint) {
        String t = lower(title);
        String all = t + " " + lower(hint) + " " + lower(description);
        if (PFE.matcher(all).find()) return OfferKind.PFE;
        if (ALTERNANCE.matcher(t + " " + lower(hint)).find()) return OfferKind.ALTERNANCE;
        if (STAGE.matcher(t + " " + lower(hint)).find()) return OfferKind.STAGE;
        if (ALTERNANCE.matcher(all).find()) return OfferKind.ALTERNANCE;
        if (STAGE.matcher(all).find()) return OfferKind.STAGE;
        return OfferKind.EMPLOI;
    }

    /** Intervalle de niveaux cités dans le texte, ou null si aucun. Ex. "Bac+3 à Bac+5" → [3, 5]. */
    public static int[] levels(String text) {
        String t = lower(text);
        int min = 0, max = 0;
        for (Map.Entry<Integer, Pattern> e : LEVELS.entrySet()) {
            if (e.getValue().matcher(t).find()) {
                if (min == 0) min = e.getKey();
                max = e.getKey();
            }
        }
        return min == 0 ? null : new int[]{min, max};
    }

    /** Niveau européen (cadre RNCP) → années après le bac : 5 → Bac+2, 6 → Bac+3, 7 → Bac+5. */
    public static Integer levelFromEuropean(String european) {
        if (european == null) return null;
        return switch (european.replaceAll("\\D", "")) {
            case "4" -> null;
            case "5" -> 2;
            case "6" -> 3;
            case "7", "8" -> 5;
            default -> null;
        };
    }

    public static List<String> skills(String text, int max) {
        String t = " " + lower(text).replaceAll("[^a-z0-9+#. ]", " ") + " ";
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> e : SKILLS.entrySet()) {
            String key = e.getKey();
            boolean hit = key.matches("[a-z ]+")
                    ? Pattern.compile("\\b" + Pattern.quote(key) + "s?\\b").matcher(t).find()
                    : t.contains(" " + key + " ") || t.contains(" " + key);
            if (hit && !found.contains(e.getValue())) {
                found.add(e.getValue());
                if (found.size() >= max) break;
            }
        }
        return found;
    }

    /** Durée en mois si elle est indiquée ("6 mois", "24 weeks"). */
    public static Integer durationMonths(String text) {
        Matcher m = DURATION.matcher(lower(text));
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            boolean weeks = m.group(2).startsWith("sem") || m.group(2).startsWith("week");
            int months = weeks ? Math.max(1, Math.round(n / 4.33f)) : n;
            if (months >= 1 && months <= 36) return months;
        }
        return null;
    }

    public static boolean remote(String text) {
        return REMOTE.matcher(lower(text)).find();
    }

    /** Pays d'après un libellé de lieu ("Casablanca, Maroc", "Madrid", "Remote - Europe"…). */
    public static OfferCountry country(String location, OfferCountry fallback) {
        String l = lower(location);
        if (l.isBlank()) return fallback;
        if (COUNTRY_MA.matcher(l).find()) return OfferCountry.MA;
        if (COUNTRY_BE.matcher(l).find()) return OfferCountry.BE;
        if (COUNTRY_CA.matcher(l).find()) return OfferCountry.CA;
        if (COUNTRY_ES.matcher(l).find()) return OfferCountry.ES;
        if (COUNTRY_FR.matcher(l).find()) return OfferCountry.FR;
        if (COUNTRY_REMOTE.matcher(l).find()) return OfferCountry.REMOTE;
        return fallback;
    }

    /** Texte brut à partir d'un HTML simple (les descriptions de Remotive, Jobicy, Arbeitnow sont en HTML). */
    public static String plainText(String html) {
        if (html == null) return null;
        String s = html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</h\\d>", "\n").replaceAll("(?i)<li[^>]*>", "• ");
        s = TAGS.matcher(s).replaceAll(" ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'").replace("&eacute;", "é")
                .replace("&egrave;", "è").replace("&agrave;", "à").replace("&ccedil;", "ç");
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").trim();
        return s.length() > 8000 ? s.substring(0, 8000) + "…" : s;
    }
}
