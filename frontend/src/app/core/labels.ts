// Libellés français et couleurs des statuts (utilisés par les badges)
import { ApplicationStatus, ConventionStatus, DefenseStatus, MilestoneStatus, ProjectStatus, Role, SubjectStatus, SubjectType } from './models';

type Label = [text: string, color: string];

export const ROLE: Record<Role, string> = {
  ADMIN: 'Administrateur', CHEF_DEPARTEMENT: 'Chef de département', ENSEIGNANT: 'Enseignant', ETUDIANT: 'Étudiant'
};
export const SUBJECT_TYPE: Record<SubjectType, string> = { ACADEMIQUE: 'Académique', ENTREPRISE: 'Entreprise', RECHERCHE: 'Recherche' };
export const SUBJECT_STATUS: Record<SubjectStatus, Label> = {
  PROPOSED: ['À valider', 'amber'], PUBLISHED: ['Publié', 'blue'], REJECTED: ['Refusé', 'red'], ASSIGNED: ['Affecté', 'teal'], ARCHIVED: ['Archivé', 'gray']
};
export const APPLICATION_STATUS: Record<ApplicationStatus, Label> = {
  PENDING: ['En attente', 'amber'], ACCEPTED: ['Acceptée', 'teal'], REJECTED: ['Refusée', 'red'], WITHDRAWN: ['Retirée', 'gray']
};
export const PROJECT_STATUS: Record<ProjectStatus, Label> = {
  IN_PROGRESS: ['En cours', 'blue'], READY_FOR_DEFENSE: ['Prêt à soutenir', 'violet'], DEFENDED: ['Soutenu', 'teal']
};
export const MILESTONE_STATUS: Record<MilestoneStatus, Label> = {
  TODO: ['À rendre', 'gray'], SUBMITTED: ['Rendu, à relire', 'blue'], NEEDS_REVISION: ['À corriger', 'amber'], VALIDATED: ['Validé', 'teal']
};
export const DEFENSE_STATUS: Record<DefenseStatus, Label> = {
  SCHEDULED: ['Programmée', 'blue'], DONE: ['Terminée', 'teal'], CANCELLED: ['Annulée', 'gray']
};
export const CONVENTION_STATUS: Record<ConventionStatus, Label> = {
  DRAFT: ['Brouillon', 'gray'], SUBMITTED: ['Soumise', 'amber'], VALIDATED: ['Validée', 'teal'], REJECTED: ['Refusée', 'red']
};

/** "2026-11-23" → "lun. 23 nov. 2026" */
export function frDate(iso?: string | null, withDay = false): string {
  if (!iso) return '';
  const [y, m, d] = iso.substring(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('fr-FR', withDay
    ? { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }
    : { day: 'numeric', month: 'short', year: 'numeric' });
}

/** "2026-11-23T10:15:00" → "il y a 2 h" */
export function timeAgo(iso: string): string {
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 60) return 'à l’instant';
  if (diff < 3600) return `il y a ${Math.floor(diff / 60)} min`;
  if (diff < 86400) return `il y a ${Math.floor(diff / 3600)} h`;
  if (diff < 86400 * 30) return `il y a ${Math.floor(diff / 86400)} j`;
  return frDate(iso);
}

export function hhmm(t?: string): string {
  return t ? t.substring(0, 5) : '';
}

export function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Nombre de jours entre aujourd'hui et une date (négatif si passée). */
export function daysUntil(iso: string): number {
  const [y, m, d] = iso.split('-').map(Number);
  const target = new Date(y, m - 1, d).getTime();
  const now = new Date(); now.setHours(0, 0, 0, 0);
  return Math.round((target - now.getTime()) / 86400000);
}

/** Message d'erreur lisible à partir d'une réponse HTTP de l'API. */
export function apiError(err: any, fallback = 'Une erreur est survenue.'): string {
  if (err?.status === 0) return 'Serveur injoignable : vérifiez que le backend est démarré.';
  const body = err?.error;
  if (body?.errors) return Object.values(body.errors).join(' · ');
  return body?.message ?? fallback;
}
