package ma.ump.smartdept.pfe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pfe/conventions")
@Tag(name = "PFE - Conventions de stage")
public class ConventionController {

    private final ConventionService conventionService;
    private final CurrentUser currentUser;

    public ConventionController(ConventionService conventionService, CurrentUser currentUser) {
        this.conventionService = conventionService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ETUDIANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Étudiant : mes conventions ; chef : toutes (filtre status)")
    public List<ConventionResponse> list(@RequestParam(required = false) ConventionStatus status) {
        return conventionService.list(currentUser.get(), status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une convention (version imprimable côté interface)")
    public ConventionResponse get(@PathVariable Long id) {
        return conventionService.get(currentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Créer un brouillon de convention (stage de 4 à 26 semaines)")
    public ConventionResponse create(@Valid @RequestBody ConventionRequest request) {
        return conventionService.create(currentUser.get(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Modifier un brouillon ou une convention refusée")
    public ConventionResponse update(@PathVariable Long id, @Valid @RequestBody ConventionRequest request) {
        return conventionService.update(currentUser.get(), id, request);
    }

    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Soumettre au chef de département")
    public ConventionResponse submit(@PathVariable Long id) {
        return conventionService.submit(currentUser.get(), id);
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Valider ou refuser (motif obligatoire en cas de refus)")
    public ConventionResponse review(@PathVariable Long id, @Valid @RequestBody ReviewConventionRequest request) {
        return conventionService.review(currentUser.get(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Supprimer un brouillon")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        conventionService.delete(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
