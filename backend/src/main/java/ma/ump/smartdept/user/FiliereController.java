package ma.ump.smartdept.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.user.UserDtos.FiliereRequest;
import ma.ump.smartdept.user.UserDtos.FiliereResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/filieres")
@Tag(name = "Filières")
public class FiliereController {

    private final FiliereService filiereService;

    public FiliereController(FiliereService filiereService) {
        this.filiereService = filiereService;
    }

    @GetMapping
    @Operation(summary = "Liste des filières")
    public List<FiliereResponse> list() {
        return filiereService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF_DEPARTEMENT')")
    @Operation(summary = "Créer une filière")
    public FiliereResponse create(@Valid @RequestBody FiliereRequest request) {
        return filiereService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CHEF_DEPARTEMENT')")
    @Operation(summary = "Modifier une filière")
    public FiliereResponse update(@PathVariable Long id, @Valid @RequestBody FiliereRequest request) {
        return filiereService.update(id, request);
    }
}
