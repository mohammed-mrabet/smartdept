package ma.ump.smartdept.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.ump.smartdept.publicsite.ClientIp;
import ma.ump.smartdept.user.AccountDtos.*;
import ma.ump.smartdept.user.UserDtos.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Inscription et mot de passe")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inscription étudiant : un lien de confirmation est envoyé par e-mail (vraie boîte ou Mailpit)")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest r, HttpServletRequest request) {
        return accountService.register(r, ClientIp.of(request));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Confirmer l'adresse e-mail avec le jeton reçu ; renvoie une session")
    public LoginResponse verify(@Valid @RequestBody TokenRequest r) {
        return accountService.verifyEmail(r.token());
    }

    @PostMapping("/resend-verification")
    public MessageResponse resend(@Valid @RequestBody EmailRequest r, HttpServletRequest request) {
        return accountService.resendVerification(r.email(), ClientIp.of(request));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Recevoir un lien pour choisir un nouveau mot de passe (valable 1 heure)")
    public MessageResponse forgot(@Valid @RequestBody EmailRequest r, HttpServletRequest request) {
        return accountService.forgotPassword(r.email(), ClientIp.of(request));
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest r) {
        return accountService.resetPassword(r.token(), r.password());
    }
}
