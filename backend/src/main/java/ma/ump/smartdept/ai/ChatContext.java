package ma.ump.smartdept.ai;

import ma.ump.smartdept.user.Role;

import java.util.List;
import java.util.Map;

/**
 * Faits sur la personne connectée, préparés pour le chatbot.
 * topics : réponse prête par thème (soutenance, jalon, candidature, sujet, convention, notification).
 */
public record ChatContext(String firstName, Role role, List<String> facts, Map<String, String> topics) {
}
