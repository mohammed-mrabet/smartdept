package ma.ump.smartdept.pfe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pfe")
@Tag(name = "PFE - Projets et jalons", description = "Suivi des projets affectés : livrables, validation, avancement")
public class PfeProjectController {

    private final PfeProjectService projectService;
    private final CurrentUser currentUser;

    public PfeProjectController(PfeProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    @GetMapping("/projects")
    @Operation(summary = "Projets visibles (chef : tous ; enseignant : encadrés ; étudiant : le sien)")
    public List<ProjectResponse> list() {
        return projectService.list(currentUser.get());
    }

    @GetMapping("/projects/me")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Mon projet de PFE")
    public ProjectResponse mine() {
        return projectService.mine(currentUser.get());
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "Détail d'un projet avec jalons et soutenance")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.get(currentUser.get(), id);
    }

    @PutMapping("/projects/{id}/external-supervisor")
    @Operation(summary = "Renseigner l'encadrant en entreprise")
    public ProjectResponse setExternalSupervisor(@PathVariable Long id, @Valid @RequestBody ExternalSupervisorRequest request) {
        return projectService.setExternalSupervisor(currentUser.get(), id, request);
    }

    @PatchMapping("/projects/{id}/ready")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Déclarer le projet prêt pour la soutenance (tous les jalons validés)")
    public ProjectResponse markReady(@PathVariable Long id) {
        return projectService.markReady(currentUser.get(), id);
    }

    @PostMapping("/projects/{id}/milestones")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Ajouter un jalon")
    public ProjectResponse addMilestone(@PathVariable Long id, @Valid @RequestBody MilestoneRequest request) {
        return projectService.addMilestone(currentUser.get(), id, request);
    }

    @PutMapping("/milestones/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Modifier un jalon (titre, échéance)")
    public ProjectResponse updateMilestone(@PathVariable Long id, @Valid @RequestBody MilestoneRequest request) {
        return projectService.updateMilestone(currentUser.get(), id, request);
    }

    @DeleteMapping("/milestones/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Supprimer un jalon pas encore rendu")
    public ProjectResponse deleteMilestone(@PathVariable Long id) {
        return projectService.deleteMilestone(currentUser.get(), id);
    }

    @PostMapping("/milestones/{id}/submit")
    @PreAuthorize("hasRole('ETUDIANT')")
    @Operation(summary = "Déposer le lien du livrable (notifie l'encadrant)")
    public ProjectResponse submit(@PathVariable Long id, @Valid @RequestBody SubmitMilestoneRequest request) {
        return projectService.submitMilestone(currentUser.get(), id, request);
    }

    @PostMapping("/milestones/{id}/review")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Valider un livrable ou demander une révision (notifie les étudiants)")
    public ProjectResponse review(@PathVariable Long id, @Valid @RequestBody ReviewMilestoneRequest request) {
        return projectService.reviewMilestone(currentUser.get(), id, request);
    }
}
