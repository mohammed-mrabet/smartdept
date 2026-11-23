package ma.ump.smartdept.security;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Donne l'entité User de la personne connectée (utilisé par les contrôleurs). */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser principal)) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentification requise");
        }
        return userRepository.findWithFiliere(principal.id())
                .orElseThrow(() -> ApiException.notFound("Utilisateur connecté introuvable"));
    }
}
