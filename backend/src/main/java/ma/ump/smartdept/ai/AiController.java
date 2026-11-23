package ma.ump.smartdept.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.ai.AiDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "Assistant IA", description = "IA gratuite (Ollama, Groq, Gemini) avec moteur local de secours")
public class AiController {

    private final AiService aiService;
    private final CurrentUser currentUser;

    public AiController(AiService aiService, CurrentUser currentUser) {
        this.aiService = aiService;
        this.currentUser = currentUser;
    }

    @GetMapping("/status")
    @Operation(summary = "IA active ? Fournisseur et modèle utilisés")
    public AiStatus status() {
        return aiService.status();
    }

    @PostMapping("/pfe/improve-subject")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Améliorer la description d'un sujet de PFE, extraire mots-clés et objectifs")
    public SubjectSuggestion improveSubject(@Valid @RequestBody SubjectDraft draft) {
        return aiService.improveSubject(draft);
    }

    @PostMapping("/pfe/suggest-supervisors")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Encadrants les plus proches d'un sujet (spécialités, historique, charge)")
    public List<SupervisorSuggestion> suggestSupervisors(@Valid @RequestBody SubjectDraft draft) {
        return aiService.suggestSupervisors(draft);
    }

    @PostMapping("/summarize")
    @Operation(summary = "Résumer un texte (rapport, cahier des charges)")
    public SummaryResponse summarize(@Valid @RequestBody SummaryRequest request) {
        return aiService.summarize(request);
    }

    @PostMapping("/chat")
    @Operation(summary = "Chatbot : questions sur mes sujets, candidatures, jalons, soutenances, conventions")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return aiService.chat(currentUser.get(), request);
    }
}
