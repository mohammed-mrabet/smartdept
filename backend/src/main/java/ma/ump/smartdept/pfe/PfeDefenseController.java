package ma.ump.smartdept.pfe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/pfe/defenses")
@Tag(name = "PFE - Soutenances", description = "Programmation, jury, conflits, note et mention")
public class PfeDefenseController {

    private final PfeDefenseService defenseService;
    private final CurrentUser currentUser;

    public PfeDefenseController(PfeDefenseService defenseService, CurrentUser currentUser) {
        this.defenseService = defenseService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Calendrier des soutenances (mine=true : celles qui me concernent)")
    public List<DefenseResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "false") boolean mine) {
        return defenseService.list(currentUser.get(), from, to, mine);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une soutenance")
    public DefenseResponse get(@PathVariable Long id) {
        return defenseService.get(currentUser.get(), id);
    }

    @GetMapping("/jury-suggestions")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Jury suggéré : spécialités proches du sujet, disponibilité, charge")
    public List<JurySuggestion> jurySuggestions(@RequestParam Long projectId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end) {
        return defenseService.suggestJury(currentUser.get(), projectId, date, start, end);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Programmer une soutenance (409 si la salle ou un membre du jury est déjà pris)")
    public DefenseResponse schedule(@Valid @RequestBody DefenseRequest request) {
        return defenseService.schedule(currentUser.get(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Modifier une soutenance programmée")
    public DefenseResponse update(@PathVariable Long id, @Valid @RequestBody DefenseRequest request) {
        return defenseService.update(currentUser.get(), id, request);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Annuler une soutenance")
    public DefenseResponse cancel(@PathVariable Long id) {
        return defenseService.cancel(currentUser.get(), id);
    }

    @PostMapping("/{id}/grade")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Saisir la note finale (président du jury ou chef) : mention calculée")
    public DefenseResponse grade(@PathVariable Long id, @Valid @RequestBody GradeRequest request) {
        return defenseService.grade(currentUser.get(), id, request);
    }
}
