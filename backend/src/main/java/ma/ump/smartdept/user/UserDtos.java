package ma.ump.smartdept.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** DTO du module utilisateurs (records Java : immuables, sans code répétitif). */
public final class UserDtos {

    private UserDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") String email,
            @NotBlank(message = "Le mot de passe est obligatoire") String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {
    }

    public record FiliereSummary(Long id, String code, String name) {
        public static FiliereSummary of(Filiere f) {
            return f == null ? null : new FiliereSummary(f.getId(), f.getCode(), f.getName());
        }
    }

    public record UserResponse(Long id, String email, String firstName, String lastName, String fullName,
                               Role role, boolean enabled, String phone, String studentNumber,
                               FiliereSummary filiere, String specialties, boolean emailNotifications,
                               LocalDateTime lastLoginAt, LocalDateTime createdAt) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getFullName(),
                    u.getRole(), u.isEnabled(), u.getPhone(), u.getStudentNumber(), FiliereSummary.of(u.getFiliere()),
                    u.getSpecialties(), u.isEmailNotifications(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    /** Résumé utilisé dans les autres modules (encadrant, jury, étudiant). */
    public record UserSummary(Long id, String fullName, String email, Role role) {
        public static UserSummary of(User u) {
            return u == null ? null : new UserSummary(u.getId(), u.getFullName(), u.getEmail(), u.getRole());
        }
    }

    public record CreateUserRequest(
            @NotBlank(message = "L'e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 150) String email,
            @NotBlank(message = "Le mot de passe est obligatoire") @Size(min = 8, message = "8 caractères minimum") String password,
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 80) String firstName,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 80) String lastName,
            @NotNull(message = "Le rôle est obligatoire") Role role,
            @Size(max = 30) String phone,
            @Size(max = 30) String studentNumber,
            Long filiereId,
            @Size(max = 500) String specialties) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 80) String firstName,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 80) String lastName,
            @NotNull(message = "Le rôle est obligatoire") Role role,
            @Size(max = 30) String phone,
            @Size(max = 30) String studentNumber,
            Long filiereId,
            @Size(max = 500) String specialties) {
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "Le prénom est obligatoire") @Size(max = 80) String firstName,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 80) String lastName,
            @Size(max = 30) String phone,
            @Size(max = 500) String specialties,
            boolean emailNotifications) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "L'ancien mot de passe est obligatoire") String currentPassword,
            @NotBlank(message = "Le nouveau mot de passe est obligatoire") @Size(min = 8, message = "8 caractères minimum") String newPassword) {
    }

    public record FiliereRequest(
            @NotBlank(message = "Le code est obligatoire") @Size(max = 30) String code,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
            @NotBlank(message = "Le niveau est obligatoire") @Size(max = 30) String level,
            @Size(max = 500) String description,
            ma.ump.smartdept.common.StudyLevel studyLevel) {
    }

    public record FiliereResponse(Long id, String code, String name, String level, String description,
                                  ma.ump.smartdept.common.StudyLevel studyLevel) {
        public static FiliereResponse of(Filiere f) {
            return new FiliereResponse(f.getId(), f.getCode(), f.getName(), f.getLevel(), f.getDescription(), f.getStudyLevel());
        }
    }
}
