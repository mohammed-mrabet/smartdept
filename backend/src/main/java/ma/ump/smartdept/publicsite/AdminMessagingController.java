package ma.ump.smartdept.publicsite;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.notification.MailRouter;
import ma.ump.smartdept.notification.MailTemplates;
import ma.ump.smartdept.publicsite.PublicDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Messagerie de l'administration : configuration et test des e-mails, messages reçus par le formulaire de contact. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
@Tag(name = "Administration - messagerie")
public class AdminMessagingController {

    private final MailRouter mailRouter;
    private final PublicService publicService;

    public AdminMessagingController(MailRouter mailRouter, PublicService publicService) {
        this.mailRouter = mailRouter;
        this.publicService = publicService;
    }

    @GetMapping("/mail/status")
    @Operation(summary = "Configuration des e-mails : mode, SMTP réel, Mailpit, domaines de démonstration")
    public MailRouter.Status status() {
        return mailRouter.status();
    }

    @GetMapping("/mail/route")
    @Operation(summary = "Canal qui serait utilisé pour une adresse (sans envoyer)")
    public MailRouter.Route route(@RequestParam String email) {
        return mailRouter.route(email);
    }

    @PostMapping("/mail/test")
    @Operation(summary = "Envoyer un e-mail de test : SMTP réel si l'adresse est valide, sinon Mailpit")
    public MailRouter.Delivery test(@Valid @RequestBody TestMailRequest r) {
        String msg = "Ceci est un e-mail de test envoyé depuis la page d'administration de SmartDept. "
                + "S'il est arrivé dans votre boîte, la configuration SMTP fonctionne.";
        return mailRouter.send(r.to(), "[SmartDept] E-mail de test", MailTemplates.text("", msg, null),
                MailTemplates.html("", "E-mail de test", msg, null, null, "Test de configuration."));
    }

    @GetMapping("/contact-messages")
    public List<ContactResponse> messages() {
        return publicService.messages();
    }

    @PatchMapping("/contact-messages/{id}")
    public ContactResponse handled(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean handled) {
        return publicService.markHandled(id, handled);
    }
}
