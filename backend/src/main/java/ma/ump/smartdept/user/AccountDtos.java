package ma.ump.smartdept.user;

import jakarta.validation.constraints.*;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 80) String firstName,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 80) String lastName,
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 150) String email,
            @NotBlank(message = "Le mot de passe est obligatoire") @Size(min = 8, max = 100, message = "8 caractères minimum") String password,
            @NotNull(message = "Choisissez votre filière") Long filiereId,
            @Size(max = 30) String studentNumber,
            @Size(max = 500) String skills,
            @AssertTrue(message = "Acceptez les conditions d'utilisation") boolean acceptTerms) {
    }

    /** emailChannel = SMTP (vraie boîte) ou MAILPIT (boîte de test) ; mailpitUrl renseignée dans ce dernier cas. */
    public record RegisterResponse(String message, String email, String emailChannel, String emailReason, String mailpitUrl) {
    }

    public record TokenRequest(@NotBlank(message = "Lien invalide") @Size(max = 200) String token) {
    }

    public record EmailRequest(@NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") String email) {
    }

    public record ResetPasswordRequest(@NotBlank(message = "Lien invalide") @Size(max = 200) String token,
                                       @NotBlank @Size(min = 8, max = 100, message = "8 caractères minimum") String password) {
    }

    public record MessageResponse(String message, String emailChannel, String mailpitUrl) {
    }
}
