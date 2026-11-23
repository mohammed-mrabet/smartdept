package ma.ump.smartdept.user;

/** Rôles de la plateforme (le préfixe ROLE_ est ajouté pour Spring Security). */
public enum Role {
    ADMIN,
    CHEF_DEPARTEMENT,
    ENSEIGNANT,
    ETUDIANT;

    public boolean isManager() {
        return this == ADMIN || this == CHEF_DEPARTEMENT;
    }
}
