package ma.ump.smartdept.ai;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Moteur de texte local, sans dépendance ni service externe (toujours gratuit et disponible) :
 * extraction de mots-clés, similarité entre deux textes, résumé extractif.
 * Utilisé seul quand l'IA est désactivée, et en complément de l'IA sinon.
 */
public final class TextAnalyzer {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            // français
            "a", "au", "aux", "avec", "ce", "ces", "cet", "cette", "dans", "de", "des", "du", "elle", "en", "est", "et",
            "etre", "il", "ils", "je", "la", "le", "les", "leur", "leurs", "lui", "mais", "me", "meme", "mes", "nous",
            "on", "ou", "par", "pas", "pour", "qu", "que", "qui", "sa", "se", "ses", "son", "sont", "sur", "ta", "te",
            "tes", "toi", "ton", "tu", "un", "une", "vos", "votre", "vous", "y", "plus", "afin", "ainsi", "aussi",
            "bien", "comme", "dont", "entre", "faire", "fait", "peut", "permet", "permettre", "permettant", "sera",
            "seront", "tout", "tous", "toutes", "tres", "chaque", "selon", "sans", "sous", "lors", "ensuite", "notamment",
            "etc", "via", "ayant", "avoir", "doit", "doivent", "nos", "notre", "cela", "ceci", "donc", "car", "si",
            "projet", "sujet", "travail", "mise", "place", "realisation", "realiser", "developpement", "developper",
            "application", "systeme", "partir", "objectif", "objectifs", "utilisateur", "utilisateurs", "gestion",
            // anglais
            "the", "and", "of", "to", "in", "for", "on", "with", "an", "is", "are", "be", "by", "this", "that", "from",
            "as", "at", "it", "or", "using", "use", "based"));

    /** Termes courts mais significatifs, conservés malgré leur longueur. */
    private static final Set<String> SHORT_TERMS = Set.of("ia", "ai", "ml", "ui", "ux", "c#", "go", "bi", "r");

    private TextAnalyzer() {
    }

    /** Minuscules, sans accents. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String n = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    /** Mots significatifs (3 lettres et plus, hors mots vides) ; garde "c++", "c#", "node.js". */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : normalize(text).split("[^a-z0-9+#.]+")) {
            String t = raw.replaceAll("^[.]+|[.]+$", "");
            if (t.length() >= 3 && !STOPWORDS.contains(t) && !t.chars().allMatch(Character::isDigit)) {
                out.add(t);
            } else if (SHORT_TERMS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Les n mots les plus fréquents (ordre de première apparition en cas d'égalité). */
    public static List<String> keywords(String text, int n) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (String t : tokens(text)) {
            freq.merge(t, 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Mots communs aux deux textes. */
    public static List<String> commonTerms(String a, String b) {
        Set<String> right = new HashSet<>(tokens(b));
        return tokens(a).stream().distinct().filter(right::contains).toList();
    }

    /**
     * Similarité entre 0 et 1 : part des termes du profil (b) présents dans le texte (a).
     * Adaptée pour comparer un sujet à la liste de spécialités d'un enseignant.
     */
    public static double coverage(String a, String b) {
        Set<String> profile = new LinkedHashSet<>(tokens(b));
        if (profile.isEmpty()) {
            return 0;
        }
        Set<String> text = new HashSet<>(tokens(a));
        long hits = profile.stream().filter(text::contains).count();
        return (double) hits / profile.size();
    }

    /** Résumé extractif : les phrases les plus riches en mots-clés, dans leur ordre d'origine. */
    public static String summarize(String text, int maxSentences) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> sentences = Arrays.stream(text.replaceAll("\\s+", " ").trim().split("(?<=[.!?])\\s+"))
                .filter(s -> s.length() > 20)
                .toList();
        if (sentences.size() <= maxSentences) {
            return String.join(" ", sentences.isEmpty() ? List.of(text.trim()) : sentences);
        }
        Map<String, Integer> freq = new HashMap<>();
        tokens(text).forEach(t -> freq.merge(t, 1, Integer::sum));
        Map<Integer, Double> scores = new HashMap<>();
        for (int i = 0; i < sentences.size(); i++) {
            List<String> words = tokens(sentences.get(i));
            double score = words.stream().mapToInt(w -> freq.getOrDefault(w, 0)).sum() / Math.sqrt(words.size() + 1.0);
            if (i == 0) {
                score *= 1.2;   // la première phrase pose souvent le contexte
            }
            scores.put(i, score);
        }
        return scores.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .limit(maxSentences)
                .map(Map.Entry::getKey)
                .sorted()
                .map(sentences::get)
                .collect(Collectors.joining(" "));
    }

    /** Découpe un texte en phrases utiles (au moins 20 caractères). */
    public static List<String> sentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.replaceAll("\\s+", " ").trim().split("(?<=[.!?])\\s+"))
                .filter(s -> s.length() >= 20).toList();
    }
}
