package ma.ump.smartdept.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.security.CurrentUser;
import ma.ump.smartdept.user.UserDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Connexion et profil de la personne connectée")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, UserService userService, CurrentUser currentUser) {
        this.authService = authService;
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter : renvoie le jeton JWT à mettre dans Authorize")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Mon profil")
    public UserResponse me() {
        return UserResponse.of(currentUser.get());
    }

    @PutMapping("/me")
    @Operation(summary = "Modifier mon profil et mes préférences de notification")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUser.get(), request);
    }

    @PostMapping("/me/password")
    @Operation(summary = "Changer mon mot de passe")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.get(), request);
        return ResponseEntity.noContent().build();
    }
}
