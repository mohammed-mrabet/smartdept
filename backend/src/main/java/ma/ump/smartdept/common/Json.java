package ma.ump.smartdept.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lecture tolérante d'un JSON désérialisé en Map / List (réponses des API externes).
 * Un chemin "a.b.c" renvoie null au lieu d'une exception si un maillon manque.
 */
public final class Json {

    private Json() {
    }

    public static Object get(Object node, String path) {
        Object current = node;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    public static String str(Object node, String path) {
        Object v = get(node, path);
        if (v == null) return null;
        if (v instanceof List<?> list) {
            return list.isEmpty() ? null : String.valueOf(list.get(0));
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    public static Double num(Object node, String path) {
        Object v = get(node, path);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean bool(Object node, String path) {
        Object v = get(node, path);
        return v instanceof Boolean b ? b : v != null && "true".equalsIgnoreCase(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object node, String path) {
        Object v = path.isEmpty() ? node : get(node, path);
        return v instanceof List<?> l ? (List<Object>) l : Collections.emptyList();
    }

    /** Premier chemin non vide parmi plusieurs (les API nomment parfois différemment le même champ). */
    public static String first(Object node, String... paths) {
        for (String p : paths) {
            String v = str(node, p);
            if (v != null) return v;
        }
        return null;
    }
}
