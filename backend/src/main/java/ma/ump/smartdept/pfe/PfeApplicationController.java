package ma.ump.smartdept.pfe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pfe/applications")
@Tag(name = "PFE - Candidatures", description = "Choix des étudiants (3 maximum, classés) et affectation")
public class PfeApplicationController {

    private final PfeApplicationService applicationService;
    private final CurrentUser currentUser;

    public PfeApplicationController(PfeApplicationService applicationService, CurrentUser currentUser) {
        this.applicationService = applicationService;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Mes candidatures")
    public List<ApplicationResponse> mine() {
        return applicationService.mine(currentUser.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Candidater à un sujet avec un rang de préférence (1 à 3)")
    public ApplicationResponse apply(@Valid @RequestBody ApplicationRequest request) {
        return applicationService.apply(currentUser.get(), request);
    }

    @PatchMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Retirer ma candidature")
    public ApplicationResponse withdraw(@PathVariable Long id) {
        return applicationService.withdraw(currentUser.get(), id);
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Accepter une candidature : crée le projet et ses jalons")
    public ApplicationResponse accept(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return applicationService.accept(currentUser.get(), id, request);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Refuser une candidature")
    public ApplicationResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return applicationService.reject(currentUser.get(), id, request);
    }

    @PostMapping("/auto-assign")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Affectation automatique : rang 1 d'abord, puis 2 et 3, premier arrivé premier servi")
    public AutoAssignResult autoAssign() {
        return applicationService.autoAssign(currentUser.get());
    }
}
