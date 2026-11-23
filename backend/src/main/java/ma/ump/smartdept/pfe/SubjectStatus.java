package ma.ump.smartdept.pfe;

/** Cycle de vie d'un sujet : proposé, publié par le chef, puis affecté. */
public enum SubjectStatus {
    PROPOSED,   // proposé par un enseignant, en attente de validation
    PUBLISHED,  // validé : ouvert aux candidatures
    REJECTED,   // refusé par le chef (motif obligatoire)
    ASSIGNED,   // complet : tous les étudiants sont affectés
    ARCHIVED
}
