package ma.ump.smartdept.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client pour toute API de chat compatible OpenAI (/chat/completions) :
 * Ollama en local (gratuit, sans clé), Groq ou Google Gemini (offres gratuites avec clé).
 * En cas d'erreur ou si l'IA est désactivée, renvoie Optional.empty() : l'appelant bascule sur le moteur local.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final RestClient rest;

    public AiClient(@Value("${app.ai.enabled:false}") boolean enabled,
                    @Value("${app.ai.base-url:http://localhost:11434/v1}") String baseUrl,
                    @Value("${app.ai.api-key:}") String apiKey,
                    @Value("${app.ai.model:llama3.2:3b}") String model,
                    @Value("${app.ai.timeout-seconds:60}") int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey.trim());
        }
        this.rest = builder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getModel() {
        return model;
    }

    /** Nom lisible du fournisseur, déduit de l'URL. */
    public String provider() {
        if (!enabled) return "Moteur local";
        if (baseUrl.contains("groq")) return "Groq";
        if (baseUrl.contains("generativelanguage")) return "Google Gemini";
        if (baseUrl.contains("11434") || baseUrl.contains("ollama")) return "Ollama (local)";
        return "API compatible OpenAI";
    }

    /** Envoie un message système + utilisateur et renvoie la réponse texte. */
    @SuppressWarnings("unchecked")
    public Optional<String> chat(String system, String user) {
        if (!enabled) {
            return Optional.empty();
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "max_tokens", 900,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
        try {
            Map<String, Object> response = rest.post().uri("/chat/completions").body(body).retrieve().body(Map.class);
            if (response == null) {
                return Optional.empty();
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            String text = content == null ? "" : content.toString().trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("Appel IA impossible ({}) : bascule sur le moteur local. Cause : {}", provider(), e.getMessage());
            return Optional.empty();
        }
    }
}
