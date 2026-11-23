package ma.ump.smartdept.user;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.RateLimiter;
import ma.ump.smartdept.notification.MailChannel;
import ma.ump.smartdept.notification.MailRouter;
import ma.ump.smartdept.notification.MailTemplates;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.security.AuthUser;
import ma.ump.smartdept.security.JwtService;
import ma.ump.smartdept.user.AccountDtos.*;
import ma.ump.smartdept.user.UserDtos.LoginResponse;
import ma.ump.smartdept.user.UserDtos.UserResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Inscription des étudiants avec confirmation de l'adresse e-mail, renvoi du lien, mot de passe oublié.
 * Les liens contiennent un jeton aléatoire de 256 bits ; seule son empreinte est stockée en base.
 * Les réponses de « mot de passe oublié » et de « renvoi » ne révèlent jamais si un compte existe.
 */
@Service
@Transactional
public class AccountService {

    static final Duration VERIFY_VALIDITY = Duration.ofHours(24);
    static final Duration RESET_VALIDITY = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final FiliereRepository filiereRepository;
    private final UserTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailRouter mailRouter;
    private final NotificationService notifications;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final String frontendUrl;

    public AccountService(UserRepository userRepository, FiliereRepository filiereRepository, UserTokenRepository tokenRepository,
                          PasswordEncoder passwordEncoder, MailRouter mailRouter, NotificationService notifications,
                          JwtService jwtService, RateLimiter rateLimiter, Clock clock,
                          @Value("${app.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.filiereRepository = filiereRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailRouter = mailRouter;
        this.notifications = notifications;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.frontendUrl = frontendUrl;
    }

    public RegisterResponse register(RegisterRequest r, String clientIp) {
        rateLimiter.check("register:" + clientIp, 5, Duration.ofHours(1), "Trop d'inscriptions depuis cette adresse : réessayez dans une heure");
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        checkPassword(r.password());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cette adresse : connectez-vous ou utilisez « Mot de passe oublié »");
        }
        String number = r.studentNumber() == null || r.studentNumber().isBlank() ? null : r.studentNumber().trim().toUpperCase(Locale.ROOT);
        if (number != null && userRepository.existsByStudentNumber(number)) {
            throw ApiException.conflict("Ce numéro d'étudiant est déjà utilisé");
        }
        Filiere filiere = filiereRepository.findById(r.filiereId()).orElseThrow(() -> ApiException.notFound("Filière introuvable"));
        User u = new User();
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        u.setRole(Role.ETUDIANT);
        u.setFiliere(filiere);
        u.setStudentNumber(number);
        u.setSpecialties(r.skills() == null || r.skills().isBlank() ? null : r.skills().trim());
        u.setEnabled(true);
        u.setEmailVerified(false);
        userRepository.save(u);
        MailRouter.Delivery d = sendVerification(u);
        notifications.notifyManagers(NotificationType.ACCOUNT, "Nouvelle inscription",
                u.getFullName() + " (" + filiere.getCode() + ") vient de créer un compte étudiant.", "/admin/utilisateurs");
        return new RegisterResponse("Compte créé. Ouvrez le lien envoyé à " + email + " pour l'activer.", email,
                d.channel().name(), d.reason(), d.channel() == MailChannel.MAILPIT ? mailRouter.mailpitUiUrl() : null);
    }

    /** Confirme l'adresse et connecte directement l'utilisateur. */
    public LoginResponse verifyEmail(String token) {
        UserToken t = consume(token, UserToken.Type.VERIFY_EMAIL, "Lien de confirmation invalide ou expiré : demandez-en un nouveau");
        User u = t.getUser();
        u.setEmailVerified(true);
        u.setLastLoginAt(LocalDateTime.now(clock));
        notifications.notify(u, NotificationType.ACCOUNT, "Bienvenue sur SmartDept",
                "Votre adresse est confirmée. Complétez vos compétences dans « Mon profil » pour recevoir des offres de stage adaptées.", "/profil");
        return new LoginResponse(jwtService.generateToken(AuthUser.of(u)), "Bearer", jwtService.getExpirationSeconds(), UserResponse.of(u));
    }

    public MessageResponse resendVerification(String email, String clientIp) {
        rateLimiter.check("resend:" + clientIp, 5, Duration.ofHours(1), "Trop de demandes : réessayez plus tard");
        MailRouter.Delivery d = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> !u.isEmailVerified())
                .map(this::sendVerification).orElse(null);
        return generic("Si un compte non confirmé existe pour cette adresse, un nouveau lien vient d'être envoyé.", d);
    }

    public MessageResponse forgotPassword(String email, String clientIp) {
        rateLimiter.check("forgot:" + clientIp, 5, Duration.ofHours(1), "Trop de demandes : réessayez plus tard");
        MailRouter.Delivery d = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(User::isEnabled)
                .map(u -> {
                    String token = createToken(u, UserToken.Type.RESET_PASSWORD, RESET_VALIDITY);
                    String url = frontendUrl + "/reinitialiser-mot-de-passe?token=" + token;
                    String msg = "Une demande de nouveau mot de passe a été faite pour votre compte. Le lien est valable 1 heure. "
                            + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : votre mot de passe reste inchangé.";
                    return mailRouter.send(u.getEmail(), "[SmartDept] Nouveau mot de passe",
                            MailTemplates.text(u.getFirstName(), msg, url),
                            MailTemplates.html(u.getFirstName(), "Choisir un nouveau mot de passe", msg, "Choisir un mot de passe", url,
                                    "E-mail de sécurité envoyé à la demande."));
                }).orElse(null);
        return generic("Si un compte existe pour cette adresse, un lien pour choisir un nouveau mot de passe vient d'être envoyé.", d);
    }

    public MessageResponse resetPassword(String token, String password) {
        checkPassword(password);
        UserToken t = consume(token, UserToken.Type.RESET_PASSWORD, "Lien invalide ou expiré : refaites une demande");
        User u = t.getUser();
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setEmailVerified(true);
        rateLimiter.reset("login:" + u.getEmail().toLowerCase(Locale.ROOT));
        notifications.notify(u, NotificationType.ACCOUNT, "Mot de passe modifié",
                "Votre mot de passe vient d'être changé. Si ce n'est pas vous, contactez immédiatement l'administration.", "/profil");
        return new MessageResponse("Mot de passe modifié : vous pouvez vous connecter.", null, null);
    }

    private MailRouter.Delivery sendVerification(User u) {
        String token = createToken(u, UserToken.Type.VERIFY_EMAIL, VERIFY_VALIDITY);
        String url = frontendUrl + "/verifier-email?token=" + token;
        String msg = "Merci pour votre inscription sur SmartDept, la plateforme du département informatique de la FSO Oujda. "
                + "Confirmez votre adresse e-mail pour activer votre compte (lien valable 24 heures).";
        return mailRouter.send(u.getEmail(), "[SmartDept] Confirmez votre adresse e-mail",
                MailTemplates.text(u.getFirstName(), msg, url),
                MailTemplates.html(u.getFirstName(), "Activez votre compte", msg, "Confirmer mon adresse", url,
                        "Vous recevez cet e-mail car cette adresse a été utilisée pour créer un compte SmartDept."));
    }

    String createToken(User u, UserToken.Type type, Duration validity) {
        LocalDateTime now = LocalDateTime.now(clock);
        tokenRepository.invalidate(u.getId(), type, now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UserToken t = new UserToken();
        t.setUser(u);
        t.setType(type);
        t.setTokenHash(sha256(token));
        t.setExpiresAt(now.plus(validity));
        tokenRepository.save(t);
        return token;
    }

    private UserToken consume(String token, UserToken.Type type, String error) {
        UserToken t = tokenRepository.findByHash(sha256(token.trim())).orElseThrow(() -> ApiException.badRequest(error));
        LocalDateTime now = LocalDateTime.now(clock);
        if (t.getType() != type || t.getUsedAt() != null || t.getExpiresAt().isBefore(now)) {
            throw ApiException.badRequest(error);
        }
        t.setUsedAt(now);
        return t;
    }

    static void checkPassword(String p) {
        if (p == null || p.length() < 8 || !p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) {
            throw ApiException.badRequest("Le mot de passe doit contenir au moins 8 caractères, dont une lettre et un chiffre");
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private MessageResponse generic(String message, MailRouter.Delivery d) {
        // Le canal n'est indiqué que s'il s'agit de Mailpit (aide en démonstration), sans confirmer l'existence du compte
        return new MessageResponse(message, null, mailRouter.mailpitUiUrl());
    }
}
