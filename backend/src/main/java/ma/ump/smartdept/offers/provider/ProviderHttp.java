package ma.ump.smartdept.offers.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Appels HTTP communs aux fournisseurs : délais courts, JSON lu en Map, paramètres encodés en UTF-8. */
@Component
public class ProviderHttp {

    public static final String USER_AGENT = "SmartDept/1.0 (plateforme pedagogique FSO Oujda)";

    private final RestClient rest;

    public ProviderHttp(@Value("${app.offers.http-timeout-seconds:15}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.rest = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", USER_AGENT).build();
    }

    public static URI url(String base, Map<String, ?> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((k, v) -> {
            if (v != null && !String.valueOf(v).isBlank()) {
                query.add(encode(k) + "=" + encode(String.valueOf(v)));
            }
        });
        String q = query.toString();
        return URI.create(q.isEmpty() ? base : base + (base.contains("?") ? "&" : "?") + q);
    }

    public static Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    public static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(URI uri, Map<String, String> headers) {
        RestClient.RequestHeadersSpec<?> spec = rest.get().uri(uri).accept(MediaType.APPLICATION_JSON);
        headers.forEach(spec::header);
        Object body = spec.retrieve().body(Object.class);
        return wrap(body);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> postJson(URI uri, Object payload, Map<String, String> headers) {
        RestClient.RequestBodySpec spec = rest.post().uri(uri).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        headers.forEach(spec::header);
        return wrap(spec.body(payload).retrieve().body(Object.class));
    }

    /** POST application/x-www-form-urlencoded (jeton OAuth2). */
    public Map<String, Object> postForm(URI uri, Map<String, String> form) {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(encode(k) + "=" + encode(v)));
        return wrap(rest.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(body.toString()).retrieve().body(Object.class));
    }

    /** Certaines API renvoient directement un tableau : on l'enveloppe pour un accès uniforme. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> wrap(Object body) {
        if (body instanceof Map<?, ?> m) return (Map<String, Object>) m;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("items", body);
        return map;
    }
}
