package ma.ump.smartdept.pfe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.ump.smartdept.pfe.PfeDtos.PfeStats;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pfe")
@Tag(name = "PFE - Tableau de bord")
public class PfeStatsController {

    private final PfeStatsService statsService;
    private final PfeReminderScheduler reminders;

    public PfeStatsController(PfeStatsService statsService, PfeReminderScheduler reminders) {
        this.statsService = statsService;
        this.reminders = reminders;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Indicateurs PFE : sujets, affectations, jalons en retard, soutenances, moyenne")
    public PfeStats stats() {
        return statsService.stats();
    }

    @PostMapping("/reminders/run")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Lancer maintenant les rappels automatiques (sinon : chaque matin à 8 h)")
    public Map<String, Integer> runReminders() {
        return Map.of("sent", reminders.sendReminders());
    }
}
