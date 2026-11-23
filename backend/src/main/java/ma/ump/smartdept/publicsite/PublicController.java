package ma.ump.smartdept.publicsite;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.ump.smartdept.publicsite.PublicDtos.*;
import ma.ump.smartdept.user.FiliereService;
import ma.ump.smartdept.user.UserDtos.FiliereResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Points d'accès sans connexion, utilisés par la page d'accueil et le formulaire d'inscription. */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Accueil : chiffres clés, dernières offres, filières, contact")
public class PublicController {

    private final PublicService service;
    private final FiliereService filiereService;

    public PublicController(PublicService service, FiliereService filiereService) {
        this.service = service;
        this.filiereService = filiereService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Chiffres clés de la plateforme (cache 10 min)")
    public PublicStats stats() {
        return service.stats();
    }

    @GetMapping("/offers")
    @Operation(summary = "Dernières offres de stage (aperçu sans coordonnées)")
    public List<PublicOffer> offers() {
        return service.latestOffers();
    }

    @GetMapping("/filieres")
    public List<FiliereResponse> filieres() {
        return filiereService.list();
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envoyer un message au département (5 messages par heure et par adresse IP)")
    public Map<String, String> contact(@Valid @RequestBody ContactRequest r, HttpServletRequest request) {
        service.contact(r, ClientIp.of(request));
        return Map.of("message", "Message envoyé : un accusé de réception vous a été adressé par e-mail.");
    }
}
