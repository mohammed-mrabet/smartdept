package ma.ump.smartdept.user;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.RateLimiter;
import ma.ump.smartdept.security.AuthUser;
import ma.ump.smartdept.security.JwtService;
import ma.ump.smartdept.user.UserDtos.LoginRequest;
import ma.ump.smartdept.user.UserDtos.LoginResponse;
import ma.ump.smartdept.user.UserDtos.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    /** Même message pour un e-mail inconnu ou un mauvais mot de passe : on ne révèle pas quels comptes existent. */
    public LoginResponse login(LoginRequest request) {
        // Anti force brute : 10 essais par adresse e-mail toutes les 15 minutes (compteur Redis ou mémoire)
        String key = "login:" + request.email().trim().toLowerCase(java.util.Locale.ROOT);
        rateLimiter.check(key, 10, java.time.Duration.ofMinutes(15), "Trop de tentatives de connexion : réessayez dans 15 minutes");
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Ce compte est désactivé : contactez l'administration");
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("Confirmez votre adresse e-mail : ouvrez le lien reçu lors de l'inscription");
        }
        rateLimiter.reset(key);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtService.generateToken(AuthUser.of(user));
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), UserResponse.of(user));
    }
}
