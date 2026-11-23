package ma.ump.smartdept.user;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.StudyLevel;
import ma.ump.smartdept.user.AccountDtos.RegisterRequest;
import ma.ump.smartdept.user.AccountDtos.RegisterResponse;
import ma.ump.smartdept.user.UserDtos.LoginRequest;
import ma.ump.smartdept.user.UserDtos.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Inscription, confirmation de l'adresse, connexion, mot de passe oublié — sur H2, e-mails désactivés. */
@SpringBootTest
@Transactional
class AccountIntegrationTest {

    @Autowired private AccountService accounts;
    @Autowired private AuthService auth;
    @Autowired private FiliereRepository filiereRepository;
    @Autowired private UserRepository userRepository;

    private Filiere licence;

    @BeforeEach
    void setUp() {
        licence = new Filiere();
        licence.setCode("AC-L3");
        licence.setName("Licence test");
        licence.setLevel("LICENCE");
        licence.setStudyLevel(StudyLevel.BAC_3);
        licence = filiereRepository.save(licence);
    }

    private RegisterRequest request(String email) {
        return new RegisterRequest("Nora", "Alaoui", email, "Motdepasse1", licence.getId(), "etu-77", "PHP, Laravel", true);
    }

    @Test
    void registerVerifyThenLogin() {
        RegisterResponse r = accounts.register(request("Nora.Alaoui@Gmail.com"), "10.0.0.1");
        assertEquals("nora.alaoui@gmail.com", r.email());
        User u = userRepository.findByEmailIgnoreCase("nora.alaoui@gmail.com").orElseThrow();
        assertFalse(u.isEmailVerified());
        assertEquals(Role.ETUDIANT, u.getRole());

        ApiException notVerified = assertThrows(ApiException.class,
                () -> auth.login(new LoginRequest("nora.alaoui@gmail.com", "Motdepasse1")));
        assertEquals(403, notVerified.getStatus().value());

        String token = accounts.createToken(u, UserToken.Type.VERIFY_EMAIL, Duration.ofHours(24));
        LoginResponse session = accounts.verifyEmail(token);
        assertNotNull(session.token());
        assertTrue(u.isEmailVerified());
        assertThrows(ApiException.class, () -> accounts.verifyEmail(token), "lien à usage unique");
        assertNotNull(auth.login(new LoginRequest("nora.alaoui@gmail.com", "Motdepasse1")).token());

        assertEquals(409, assertThrows(ApiException.class,
                () -> accounts.register(request("nora.alaoui@gmail.com"), "10.0.0.2")).getStatus().value(),
                "adresse déjà utilisée");
    }

    @Test
    void weakPasswordAndRateLimit() {
        RegisterRequest weak = new RegisterRequest("A", "B", "weak@gmail.com", "motdepasse", licence.getId(), null, null, true);
        assertEquals(400, assertThrows(ApiException.class, () -> accounts.register(weak, "10.0.0.3")).getStatus().value());
        for (int i = 0; i < 5; i++) accounts.resendVerification("inconnu@gmail.com", "10.0.0.4");
        assertEquals(429, assertThrows(ApiException.class,
                () -> accounts.resendVerification("inconnu@gmail.com", "10.0.0.4")).getStatus().value());
    }

    @Test
    void forgotAndResetPassword() {
        accounts.register(request("reset@gmail.com"), "10.0.0.5");
        User u = userRepository.findByEmailIgnoreCase("reset@gmail.com").orElseThrow();
        assertTrue(accounts.forgotPassword("reset@gmail.com", "10.0.0.6").message().startsWith("Si un compte existe"));
        assertTrue(accounts.forgotPassword("personne@gmail.com", "10.0.0.7").message().startsWith("Si un compte existe"),
                "même réponse pour une adresse inconnue");
        String token = accounts.createToken(u, UserToken.Type.RESET_PASSWORD, Duration.ofHours(1));
        accounts.resetPassword(token, "Nouveau2026");
        assertNotNull(auth.login(new LoginRequest("reset@gmail.com", "Nouveau2026")).token());
        assertThrows(ApiException.class, () -> accounts.resetPassword(token, "Autre2026x"));
    }
}
