package ma.ump.smartdept.publicsite;

import jakarta.validation.constraints.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PublicDtos {

    private PublicDtos() {
    }

    public record PublicStats(long students, long teachers, long filieres, long publishedSubjects, long activeOffers,
                              long countries, long defensesScheduled, long emailsSent, Map<String, Long> offersByCountry,
                              Map<String, Long> offersByLevel) implements Serializable {
    }

    public record PublicOffer(Long id, String title, String company, String location, String flag, String countryLabel,
                              String kindLabel, String levelLabel, boolean remote, List<String> skills, LocalDate publishedAt,
                              String sourceLabel) implements Serializable {
    }

    public record ContactRequest(@NotBlank(message = "Votre nom est obligatoire") @Size(max = 120) String name,
                                 @NotBlank(message = "Votre e-mail est obligatoire") @Email(message = "E-mail invalide") @Size(max = 150) String email,
                                 @Size(max = 150) String organisation,
                                 @NotNull(message = "Choisissez un sujet") ContactMessage.Topic topic,
                                 @NotBlank(message = "Écrivez votre message") @Size(min = 10, max = 3000, message = "10 à 3000 caractères") String message,
                                 /** Champ piège invisible : rempli uniquement par les robots. */
                                 @Size(max = 0, message = "Message refusé") String website) {
    }

    public record ContactResponse(Long id, String name, String email, String organisation, ContactMessage.Topic topic,
                                  String message, boolean handled, LocalDateTime createdAt) {
        public static ContactResponse of(ContactMessage m) {
            return new ContactResponse(m.getId(), m.getName(), m.getEmail(), m.getOrganisation(), m.getTopic(), m.getMessage(),
                    m.isHandled(), m.getCreatedAt());
        }
    }

    public record TestMailRequest(@NotBlank(message = "Indiquez une adresse") @Size(max = 150) String to) {
    }
}
