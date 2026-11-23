package ma.ump.smartdept.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.ump.smartdept.user.UserDtos.UserSummary;

import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    /** source = "ia" (réponse du modèle) ou "local" (moteur intégré). */
    public record AiStatus(boolean enabled, String provider, String model) {
    }

    public record SubjectDraft(
            @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String title,
            @Size(max = 8000) String description,
            Long filiereId) {
    }

    public record SubjectSuggestion(String improvedDescription, List<String> keywords, List<String> objectives,
                                    List<String> deliverables, String source) {
    }

    public record SupervisorSuggestion(UserSummary teacher, String specialties, double score,
                                       List<String> matchedKeywords, long currentProjects, String reason) {
    }

    public record SummaryRequest(@NotBlank(message = "Le texte est obligatoire") @Size(max = 30000) String text,
                                 Integer maxSentences) {
    }

    public record SummaryResponse(String summary, List<String> keywords, String source) {
    }

    public record ChatRequest(@NotBlank(message = "La question est obligatoire") @Size(max = 1000) String message) {
    }

    public record ChatResponse(String answer, String source, List<String> suggestions) {
    }
}
