package ma.ump.smartdept.publicsite;

import jakarta.persistence.*;
import ma.ump.smartdept.common.BaseEntity;

import java.time.LocalDateTime;

/** Message envoyé depuis le formulaire de contact de la page d'accueil (entreprise, étudiant, visiteur). */
@Entity
@Table(name = "contact_messages")
public class ContactMessage extends BaseEntity {

    public enum Topic { PARTENARIAT, OFFRE_STAGE, INSCRIPTION, AUTRE }

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 150)
    private String organisation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Topic topic;

    @Column(nullable = false, length = 3000)
    private String message;

    @Column(nullable = false)
    private boolean handled;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganisation() {
        return organisation;
    }

    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }
}
