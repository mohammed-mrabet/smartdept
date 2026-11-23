package ma.ump.smartdept.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextAnalyzerTest {

    private static final String SUBJECT = "Détection de fraudes bancaires par Machine Learning. Le système analyse les "
            + "transactions en temps réel avec Kafka et Spring Boot. Les modèles de machine learning sont entraînés en "
            + "Python. Le déploiement se fait avec Docker.";

    @Test
    @DisplayName("normalize : minuscules et sans accents")
    void normalize() {
        assertEquals("detection reseau ete", TextAnalyzer.normalize("Détection RÉSEAU été"));
        assertEquals("", TextAnalyzer.normalize(null));
    }

    @Test
    @DisplayName("tokens : mots vides retirés, termes techniques conservés (C++, C#, node.js, IA)")
    void tokens() {
        List<String> t = TextAnalyzer.tokens("Le projet avec C++ et C# sous node.js pour l'IA");
        assertTrue(t.containsAll(List.of("c++", "c#", "node.js", "ia")));
        assertFalse(t.contains("le"));
        assertFalse(t.contains("projet"), "mot générique filtré");
    }

    @Test
    @DisplayName("keywords : les termes les plus fréquents d'abord")
    void keywords() {
        List<String> k = TextAnalyzer.keywords(SUBJECT, 3);
        assertEquals(List.of("machine", "learning"), k.subList(0, 2));
        assertEquals(3, k.size());
    }

    @Test
    @DisplayName("coverage : part du profil de l'enseignant présente dans le sujet")
    void coverage() {
        assertEquals(0.6, TextAnalyzer.coverage(SUBJECT, "Machine learning, Python, Data science"), 0.001);
        assertEquals(0.0, TextAnalyzer.coverage(SUBJECT, "Réseaux, Sécurité"), 0.001);
        assertEquals(0.0, TextAnalyzer.coverage(SUBJECT, ""), 0.001);
    }

    @Test
    @DisplayName("summarize : nombre de phrases respecté et ordre d'origine conservé")
    void summarize() {
        String summary = TextAnalyzer.summarize(SUBJECT, 2);
        assertEquals(2, TextAnalyzer.sentences(summary).size());
        assertTrue(summary.startsWith("Détection de fraudes"));
        assertEquals("", TextAnalyzer.summarize("  ", 3));
    }
}
