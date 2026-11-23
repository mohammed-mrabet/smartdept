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
@RequestMapping("/api/pfe/subjects")
@Tag(name = "PFE - Sujets", description = "Proposition, validation, publication et archivage des sujets")
public class PfeSubjectController {

    private final PfeSubjectService subjectService;
    private final PfeApplicationService applicationService;
    private final CurrentUser currentUser;

    public PfeSubjectController(PfeSubjectService subjectService, PfeApplicationService applicationService,
                                CurrentUser currentUser) {
        this.subjectService = subjectService;
        this.applicationService = applicationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Catalogue des sujets (filtré selon le rôle) ; mine=true : mes sujets")
    public List<SubjectResponse> search(@RequestParam(required = false) SubjectStatus status,
                                        @RequestParam(required = false) Long filiereId,
                                        @RequestParam(required = false) Long supervisorId,
                                        @RequestParam(required = false) SubjectType type,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "false") boolean mine) {
        return subjectService.search(currentUser.get(), status, filiereId, supervisorId, type, q, mine);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un sujet")
    public SubjectResponse get(@PathVariable Long id) {
        return subjectService.get(currentUser.get(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Proposer un sujet (publié directement s'il vient du chef)")
    public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
        return subjectService.create(currentUser.get(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Modifier un sujet (encadrant avant publication, chef ensuite)")
    public SubjectResponse update(@PathVariable Long id, @Valid @RequestBody SubjectRequest request) {
        return subjectService.update(currentUser.get(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Supprimer un sujet non publié")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subjectService.delete(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Valider et publier un sujet (notifie l'encadrant et les étudiants de la filière)")
    public SubjectResponse publish(@PathVariable Long id) {
        return subjectService.publish(currentUser.get(), id);
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Refuser un sujet avec un motif")
    public SubjectResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return subjectService.reject(currentUser.get(), id, request);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Archiver un sujet sans étudiant affecté (les candidatures en attente sont annulées)")
    public SubjectResponse archive(@PathVariable Long id) {
        return subjectService.archive(currentUser.get(), id);
    }

    @GetMapping("/{id}/applications")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Candidatures reçues pour un sujet (encadrant ou chef)")
    public List<ApplicationResponse> applications(@PathVariable Long id) {
        return applicationService.forSubject(currentUser.get(), id);
    }
}
