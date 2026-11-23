package ma.ump.smartdept.user;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.UserDtos.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Gestion des comptes (administration) et du profil de la personne connectée. */
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final FiliereRepository filiereRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, FiliereRepository filiereRepository,
                       PasswordEncoder passwordEncoder, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.filiereRepository = filiereRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> search(Role role, String q) {
        String query = q == null || q.isBlank() ? null : q.trim();
        return userRepository.search(role, query).stream().map(UserResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.of(find(id));
    }

    public UserResponse create(CreateUserRequest r) {
        String email = r.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Un compte existe déjà avec l'e-mail " + email);
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(r.password()));
        applyCommon(u, r.firstName(), r.lastName(), r.role(), r.phone(), r.studentNumber(), r.filiereId(), r.specialties());
        User saved = userRepository.save(u);
        notificationService.notify(saved, NotificationType.ACCOUNT, "Bienvenue sur SmartDept",
                "Votre compte " + roleLabel(saved.getRole()) + " a été créé. Connectez-vous avec votre e-mail "
                        + email + " et pensez à changer votre mot de passe dans votre profil.", "/profil");
        return UserResponse.of(saved);
    }

    public UserResponse update(Long id, UpdateUserRequest r) {
        User u = find(id);
        applyCommon(u, r.firstName(), r.lastName(), r.role(), r.phone(), r.studentNumber(), r.filiereId(), r.specialties());
        return UserResponse.of(userRepository.save(u));
    }

    public UserResponse setEnabled(User actor, Long id, boolean enabled) {
        User u = find(id);
        if (u.getId().equals(actor.getId()) && !enabled) {
            throw ApiException.badRequest("Vous ne pouvez pas désactiver votre propre compte");
        }
        u.setEnabled(enabled);
        return UserResponse.of(userRepository.save(u));
    }

    // ---------- profil personnel ----------

    public UserResponse updateProfile(User me, UpdateProfileRequest r) {
        me.setFirstName(r.firstName().trim());
        me.setLastName(r.lastName().trim());
        me.setPhone(blankToNull(r.phone()));
        if (me.getRole() == Role.ENSEIGNANT || me.getRole() == Role.CHEF_DEPARTEMENT) {
            me.setSpecialties(blankToNull(r.specialties()));
        }
        me.setEmailNotifications(r.emailNotifications());
        return UserResponse.of(userRepository.save(me));
    }

    public void changePassword(User me, ChangePasswordRequest r) {
        if (!passwordEncoder.matches(r.currentPassword(), me.getPasswordHash())) {
            throw ApiException.badRequest("L'ancien mot de passe est incorrect");
        }
        if (r.currentPassword().equals(r.newPassword())) {
            throw ApiException.badRequest("Le nouveau mot de passe doit être différent de l'ancien");
        }
        me.setPasswordHash(passwordEncoder.encode(r.newPassword()));
        userRepository.save(me);
    }

    // ---------- règles communes ----------

    private void applyCommon(User u, String firstName, String lastName, Role role, String phone,
                             String studentNumber, Long filiereId, String specialties) {
        u.setFirstName(firstName.trim());
        u.setLastName(lastName.trim());
        u.setRole(role);
        u.setPhone(blankToNull(phone));
        if (role == Role.ETUDIANT) {
            if (filiereId == null) {
                throw ApiException.badRequest("Un étudiant doit être rattaché à une filière");
            }
            String number = blankToNull(studentNumber);
            if (number != null && !number.equals(u.getStudentNumber()) && userRepository.existsByStudentNumber(number)) {
                throw ApiException.conflict("Le numéro étudiant " + number + " est déjà utilisé");
            }
            u.setStudentNumber(number);
            u.setFiliere(filiereRepository.findById(filiereId)
                    .orElseThrow(() -> ApiException.notFound("Filière introuvable : " + filiereId)));
            u.setSpecialties(null);
        } else {
            u.setStudentNumber(null);
            u.setFiliere(null);
            u.setSpecialties(blankToNull(specialties));
        }
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable : " + id));
    }

    static String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "administrateur";
            case CHEF_DEPARTEMENT -> "chef de département";
            case ENSEIGNANT -> "enseignant";
            case ETUDIANT -> "étudiant";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
