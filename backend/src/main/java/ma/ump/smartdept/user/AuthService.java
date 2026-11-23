package ma.ump.smartdept.user;

import ma.ump.smartdept.common.ApiException;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Même message pour un e-mail inconnu ou un mauvais mot de passe : on ne révèle pas quels comptes existent. */
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("Ce compte est désactivé : contactez l'administration");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtService.generateToken(AuthUser.of(user));
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), UserResponse.of(user));
    }
}
