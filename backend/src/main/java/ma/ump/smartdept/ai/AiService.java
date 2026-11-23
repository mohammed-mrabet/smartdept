package ma.ump.smartdept.ai;

import ma.ump.smartdept.ai.AiDtos.*;
import ma.ump.smartdept.pfe.PfeProjectRepository;
import ma.ump.smartdept.pfe.PfeSubject;
import ma.ump.smartdept.pfe.PfeSubjectRepository;
import ma.ump.smartdept.pfe.ProjectStatus;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserDtos.UserSummary;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assistant IA. Chaque fonction a deux modes :
 * - "ia" : réponse d'un modèle de langage (Ollama, Groq, Gemini) si configuré ;
 * - "local" : moteur intégré (TextAnalyzer et règles), toujours disponible et gratuit.
 */
@Service
public class AiService {

    private final AiClient ai;
    private final ChatContextBuilder contextBuilder;
    private final UserRepository userRepository;
    private final PfeSubjectRepository subjectRepository;
    private final PfeProjectRepository projectRepository;

    public AiService(AiClient ai, ChatContextBuilder contextBuilder, UserRepository userRepository,
                     PfeSubjectRepository subjectRepository, PfeProjectRepository projectRepository) {
        this.ai = ai;
        this.contextBuilder = contextBuilder;
        this.userRepository = userRepository;
        this.subjectRepository = subjectRepository;
        this.projectRepository = projectRepository;
    }

    public AiStatus status() {
        return new AiStatus(ai.isEnabled(), ai.provider(), ai.isEnabled() ? ai.getModel() : null);
    }

    // ---------------------------------------------------------------- sujets de PFE

    public SubjectSuggestion improveSubject(SubjectDraft draft) {
        String description = draft.description() == null ? "" : draft.description().trim();
        String text = draft.title() + ". " + description;
        List<String> keywords = TextAnalyzer.keywords(text, 8).stream().map(AiService::capitalize).toList();
        List<String> objectives = localObjectives(keywords);
        List<String> deliverables = List.of("Cahier des charges", "Dossier de conception (UML, architecture)",
                "Code source versionné (Git) et documentation", "Rapport final et présentation de soutenance");

        String system = """
                Tu es enseignant-chercheur en informatique et tu aides un collègue à rédiger un sujet de PFE de Master.
                Réécris la description en français, claire et professionnelle, 220 mots maximum, avec exactement ces
                rubriques sur des lignes séparées : Contexte, Objectifs (3 à 5 tirets), Travail demandé, Technologies envisagées.
                N'invente pas d'entreprise ni de chiffres. Pas de markdown gras, pas d'introduction ni de conclusion.""";
        Optional<String> answer = ai.chat(system, "Titre : " + draft.title() + "\nDescription actuelle : "
                + (description.isEmpty() ? "(vide)" : description));
        if (answer.isPresent()) {
            return new SubjectSuggestion(answer.get(), keywords, objectives, deliverables, "ia");
        }
        return new SubjectSuggestion(localDescription(draft.title(), description, keywords, objectives), keywords,
                objectives, deliverables, "local");
    }

    static List<String> localObjectives(List<String> keywords) {
        String tech = keywords.isEmpty() ? "les technologies adaptées" : String.join(", ", keywords.subList(0, Math.min(3, keywords.size())));
        return List.of(
                "Analyser l'existant et formaliser les besoins (cas d'utilisation, user stories)",
                "Concevoir l'architecture et le modèle de données (UML)",
                "Développer une solution fonctionnelle autour de : " + tech,
                "Tester (tests unitaires et d'intégration) et automatiser le déploiement (CI/CD, Docker)",
                "Évaluer les résultats et rédiger la documentation");
    }

    static String localDescription(String title, String description, List<String> keywords, List<String> objectives) {
        List<String> sentences = TextAnalyzer.sentences(description);
        String context = sentences.isEmpty()
                ? "Ce projet porte sur « " + title + " »."
                : String.join(" ", sentences.subList(0, Math.min(2, sentences.size())));
        StringBuilder sb = new StringBuilder();
        sb.append("Contexte : ").append(context).append("\n\nObjectifs :\n");
        objectives.forEach(o -> sb.append("- ").append(o).append("\n"));
        sb.append("\nTravail demandé : étude de l'existant, conception, réalisation itérative (sprints Scrum), "
                + "tests et démonstration finale.");
        if (!keywords.isEmpty()) {
            sb.append("\n\nTechnologies envisagées : ").append(String.join(", ", keywords)).append('.');
        }
        return sb.toString();
    }

    /**
     * Encadrants les plus proches du sujet : spécialités (70 %) et sujets déjà encadrés (30 %),
     * avec une légère pénalité pour les enseignants qui encadrent déjà beaucoup de projets.
     */
    @Transactional(readOnly = true)
    public List<SupervisorSuggestion> suggestSupervisors(SubjectDraft draft) {
        String text = draft.title() + " " + (draft.description() == null ? "" : draft.description());
        List<User> teachers = userRepository.findByRoleInAndEnabledTrue(List.of(Role.ENSEIGNANT, Role.CHEF_DEPARTEMENT));
        List<SupervisorSuggestion> out = new ArrayList<>();
        for (User t : teachers) {
            String specialties = t.getSpecialties() == null ? "" : t.getSpecialties();
            String history = subjectRepository.findBySupervisorId(t.getId()).stream()
                    .map(s -> s.getTitle() + " " + Optional.ofNullable(s.getKeywords()).orElse(""))
                    .collect(Collectors.joining(" "));
            double score = 0.7 * TextAnalyzer.coverage(text, specialties) + 0.3 * TextAnalyzer.coverage(text, history);
            long load = projectRepository.findBySupervisorIdOrderByCreatedAtDesc(t.getId()).stream()
                    .filter(p -> p.getStatus() != ProjectStatus.DEFENDED).count();
            double finalScore = Math.max(0, score - 0.03 * load);
            List<String> matched = TextAnalyzer.commonTerms(specialties + " " + history, text).stream().limit(6).toList();
            String reason = matched.isEmpty() ? "Pas de spécialité en commun"
                    : "En commun : " + String.join(", ", matched) + (load > 0 ? " ; encadre déjà " + load + " projet(s)" : "");
            out.add(new SupervisorSuggestion(UserSummary.of(t), specialties, round(finalScore), matched, load, reason));
        }
        out.sort(Comparator.comparingDouble(SupervisorSuggestion::score).reversed());
        return out.stream().limit(5).toList();
    }

    // ---------------------------------------------------------------- résumé

    public SummaryResponse summarize(SummaryRequest r) {
        int max = r.maxSentences() == null ? 4 : Math.max(1, Math.min(r.maxSentences(), 10));
        List<String> keywords = TextAnalyzer.keywords(r.text(), 8).stream().map(AiService::capitalize).toList();
        Optional<String> answer = ai.chat(
                "Tu résumes des documents académiques (rapports de PFE, cahiers des charges) en français, en "
                        + max + " phrases maximum, fidèlement, sans rien inventer.",
                r.text().length() > 12000 ? r.text().substring(0, 12000) : r.text());
        return answer.map(s -> new SummaryResponse(s, keywords, "ia"))
                .orElseGet(() -> new SummaryResponse(TextAnalyzer.summarize(r.text(), max), keywords, "local"));
    }

    // ---------------------------------------------------------------- chatbot

    public ChatResponse chat(User me, ChatRequest r) {
        ChatContext ctx = contextBuilder.build(me.getId());
        List<String> suggestions = suggestionsFor(ctx.role());
        String system = """
                Tu es l'assistant de SmartDept, la plateforme du département informatique (PFE, soutenances, conventions).
                Réponds en français, en 4 phrases maximum, avec bienveillance. Utilise UNIQUEMENT les faits ci-dessous ;
                si l'information n'y est pas, dis-le et indique la page de l'application à consulter.
                Pages : PFE › Sujets, PFE › Mes candidatures, PFE › Mon projet, PFE › Projets, PFE › Soutenances,
                PFE › Convention de stage, Notifications (cloche), Profil.
                Faits :
                """ + String.join("\n", ctx.facts());
        Optional<String> answer = ai.chat(system, r.message());
        return answer.map(a -> new ChatResponse(a, "ia", suggestions))
                .orElseGet(() -> new ChatResponse(localAnswer(ctx, r.message()), "local", suggestions));
    }

    /** Réponse sans IA : détection du thème de la question par mots-clés. */
    static String localAnswer(ChatContext ctx, String message) {
        String q = TextAnalyzer.normalize(message).replaceAll("\\s+", " ");
        List<String> parts = new ArrayList<>();
        if (q.matches(".*(soutenance|jury|soutenir|defense).*")) parts.add(ctx.topics().get("soutenance"));
        if (q.matches(".*(jalon|livrable|rendre|deadline|echeance|depot|deposer|rapport).*")) parts.add(ctx.topics().get("jalon"));
        if (q.matches(".*(candidat|postul|choix|affect).*")) parts.add(ctx.topics().get("candidature"));
        if (q.matches(".*(sujet|proposer|theme).*")) parts.add(ctx.topics().get("sujet"));
        if (q.matches(".*(convention|stage|entreprise).*")) parts.add(ctx.topics().get("convention"));
        if (q.matches(".*(notif|message|alerte|mail).*")) parts.add(ctx.topics().get("notification"));
        parts.removeIf(Objects::isNull);
        if (!parts.isEmpty()) {
            return String.join("\n\n", new LinkedHashSet<>(parts));
        }
        if (q.matches(".*(bonjour|salut|hello|bonsoir|coucou).*")) {
            return "Bonjour " + ctx.firstName() + " ! Je peux vous renseigner sur vos sujets, candidatures, jalons, "
                    + "soutenances, conventions de stage et notifications.";
        }
        return "Je n'ai pas compris votre question. Je peux répondre sur : sujets de PFE, candidatures, jalons et livrables, "
                + "soutenances, conventions de stage et notifications. Exemple : « Quand est ma soutenance ? »";
    }

    static List<String> suggestionsFor(Role role) {
        return switch (role) {
            case ETUDIANT -> List.of("Quel est mon prochain jalon ?", "Où en sont mes candidatures ?", "Quand est ma soutenance ?");
            case ENSEIGNANT -> List.of("Ai-je des livrables à relire ?", "Dans quels jurys suis-je ?", "Comment proposer un sujet ?");
            default -> List.of("Combien de sujets sont à valider ?", "Où en sont les candidatures ?", "Quelles soutenances sont prévues ?");
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** Utilisé par le service des soutenances. */
    public static String subjectText(PfeSubject s) {
        return s.getTitle() + " " + Optional.ofNullable(s.getKeywords()).orElse("") + " " + s.getDescription();
    }
}
