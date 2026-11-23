package ma.ump.smartdept.pfe;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;

import java.time.LocalDate;

/** Règles transverses du module PFE (droits, année universitaire). */
final class PfeRules {

    private PfeRules() {
    }

    static boolean isManager(User u) {
        return u.getRole() != null && u.getRole().isManager();
    }

    static boolean isTeacher(User u) {
        return u.getRole() == Role.ENSEIGNANT || u.getRole() == Role.CHEF_DEPARTEMENT;
    }

    static void requireManager(User u) {
        if (!isManager(u)) {
            throw ApiException.forbidden("Action réservée au chef de département et à l'administrateur");
        }
    }

    /** L'encadrant du sujet ou un responsable. */
    static void requireSupervisorOrManager(User actor, User supervisor) {
        if (!isManager(actor) && !supervisor.getId().equals(actor.getId())) {
            throw ApiException.forbidden("Action réservée à l'encadrant du sujet ou au chef de département");
        }
    }

    /** Année universitaire en cours : de septembre à août (ex : novembre 2026 → 2026-2027). */
    static String currentAcademicYear(LocalDate today) {
        int start = today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
        return start + "-" + (start + 1);
    }

    static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
