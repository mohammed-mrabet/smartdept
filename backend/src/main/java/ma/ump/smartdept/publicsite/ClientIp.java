package ma.ump.smartdept.publicsite;

import jakarta.servlet.http.HttpServletRequest;

/** Adresse du visiteur, y compris derrière le proxy nginx (en-tête X-Forwarded-For). */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
