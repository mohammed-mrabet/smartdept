// Contrats JSON de l'API SmartDept (mêmes noms que les records Java)
export type Role = 'ADMIN' | 'CHEF_DEPARTEMENT' | 'ENSEIGNANT' | 'ETUDIANT';
export type SubjectType = 'ACADEMIQUE' | 'ENTREPRISE' | 'RECHERCHE';
export type SubjectStatus = 'PROPOSED' | 'PUBLISHED' | 'REJECTED' | 'ASSIGNED' | 'ARCHIVED';
export type ApplicationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
export type ProjectStatus = 'IN_PROGRESS' | 'READY_FOR_DEFENSE' | 'DEFENDED';
export type MilestoneStatus = 'TODO' | 'SUBMITTED' | 'NEEDS_REVISION' | 'VALIDATED';
export type DefenseStatus = 'SCHEDULED' | 'DONE' | 'CANCELLED';
export type ConventionStatus = 'DRAFT' | 'SUBMITTED' | 'VALIDATED' | 'REJECTED';

export interface FiliereSummary { id: number; code: string; name: string; }
export interface Filiere extends FiliereSummary { level: string; description?: string; studyLevel?: 'BAC_2' | 'BAC_3' | 'BAC_4' | 'BAC_5'; }
export interface UserSummary { id: number; fullName: string; email: string; role: Role; }

export interface User {
  id: number; email: string; firstName: string; lastName: string; fullName: string; role: Role; enabled: boolean;
  phone?: string; studentNumber?: string; filiere?: FiliereSummary; specialties?: string;
  emailNotifications: boolean; lastLoginAt?: string; createdAt?: string;
}

export interface LoginResponse { token: string; tokenType: string; expiresInSeconds: number; user: User; }

export interface Subject {
  id: number; title: string; description: string; keywords: string[]; type: SubjectType; companyName?: string;
  maxStudents: number; status: SubjectStatus; rejectionReason?: string; academicYear: string;
  supervisor: UserSummary; filiere: FiliereSummary; pendingApplications: number; acceptedStudents: number; createdAt: string;
}

export interface SubjectRequest {
  title: string; description: string; keywords?: string; type: SubjectType; companyName?: string;
  maxStudents: number; filiereId: number | null; supervisorId?: number | null;
}

export interface Application {
  id: number; subject: { id: number; title: string; status: SubjectStatus; supervisorName: string };
  student: UserSummary; studentNumber?: string; preferenceRank: number; motivation?: string;
  status: ApplicationStatus; decisionComment?: string; createdAt: string;
}

export interface AutoAssignResult { assigned: number; details: string[]; studentsWithoutSubject: UserSummary[]; }

export interface Milestone {
  id: number; title: string; description?: string; dueDate: string; orderIndex: number; status: MilestoneStatus;
  deliverableUrl?: string; submissionComment?: string; submittedAt?: string; feedback?: string; grade?: number; late: boolean;
}

export interface DefenseSummary {
  id: number; defenseDate: string; startTime: string; endTime: string; room: string; status: DefenseStatus; grade?: number; mention?: string;
}

export interface Project {
  id: number; subjectId: number; subjectTitle: string; subjectType: SubjectType; companyName?: string;
  students: UserSummary[]; supervisor: UserSummary; externalSupervisorName?: string; externalSupervisorEmail?: string;
  status: ProjectStatus; progress: number; milestones: Milestone[]; defense?: DefenseSummary; createdAt: string;
}

export interface Defense {
  id: number; projectId: number; subjectTitle: string; students: UserSummary[]; supervisor: UserSummary;
  defenseDate: string; startTime: string; endTime: string; room: string; jury: UserSummary[]; president: UserSummary;
  status: DefenseStatus; grade?: number; mention?: string; juryComment?: string;
}

export interface DefenseRequest {
  projectId: number; defenseDate: string; startTime: string; endTime: string; room: string; juryIds: number[]; presidentId: number | null;
}

export interface JurySuggestion { teacher: UserSummary; score: number; matchedKeywords: string[]; available: boolean; reason: string; }

export interface Convention {
  id: number; student: UserSummary; studentNumber?: string; filiereCode?: string; projectId?: number; projectTitle?: string;
  companyName: string; companyAddress: string; companyEmail?: string; tutorName: string; tutorEmail: string;
  startDate: string; endDate: string; durationWeeks: number; missionDescription: string; status: ConventionStatus;
  reviewComment?: string; createdAt: string;
}

export interface ConventionRequest {
  companyName: string; companyAddress: string; companyEmail?: string; tutorName: string; tutorEmail: string;
  startDate: string; endDate: string; missionDescription: string;
}

export interface PfeStats {
  subjectsByStatus: Record<SubjectStatus, number>; students: number; studentsWithProject: number; studentsWithoutProject: number;
  pendingApplications: number; projectsByStatus: Record<ProjectStatus, number>; lateMilestones: number; submittedMilestones: number;
  defensesScheduled: number; defensesDone: number; averageGrade?: number; conventionsToReview: number;
}

export interface AppNotification {
  id: number; type: string; title: string; message: string; link?: string; read: boolean; readAt?: string;
  emailStatus: 'NOT_REQUESTED' | 'PENDING' | 'SENT' | 'FAILED'; emailChannel?: MailChannel; emailInfo?: string; createdAt: string;
}

// ---------- site public, comptes, messagerie
export type MailChannel = 'SMTP' | 'MAILPIT' | 'NONE';
export interface PublicStats {
  students: number; teachers: number; filieres: number; publishedSubjects: number; activeOffers: number; countries: number;
  defensesScheduled: number; emailsSent: number; offersByCountry: Record<string, number>; offersByLevel: Record<string, number>;
}
export interface PublicOffer {
  id: number; title: string; company: string; location?: string; flag: string; countryLabel: string; kindLabel: string;
  levelLabel: string; remote: boolean; skills: string[]; publishedAt?: string; sourceLabel: string;
}
export type ContactTopic = 'PARTENARIAT' | 'OFFRE_STAGE' | 'INSCRIPTION' | 'AUTRE';
export interface ContactMessage {
  id: number; name: string; email: string; organisation?: string; topic: ContactTopic; message: string; handled: boolean; createdAt: string;
}
export interface RegisterRequest {
  firstName: string; lastName: string; email: string; password: string; filiereId: number | null;
  studentNumber?: string; skills?: string; acceptTerms: boolean;
}
export interface RegisterResponse { message: string; email: string; emailChannel: MailChannel; emailReason: string; mailpitUrl?: string; }
export interface MessageResponse { message: string; emailChannel?: MailChannel; mailpitUrl?: string; }
export interface MailRoute { channel: MailChannel; reason: string; }
export interface MailDelivery { channel: MailChannel; success: boolean; reason: string; error?: string; }
export interface MailStatus {
  enabled: boolean; mode: string; smtpConfigured: boolean; smtpHost: string; smtpPort: number; smtpUser: string; from: string;
  mailpitHost: string; mailpitPort: number; mailpitUiUrl: string; sandboxDomains: string[]; checkMx: boolean;
}

export interface AiStatus { enabled: boolean; provider: string; model?: string; }
export interface SubjectSuggestion { improvedDescription: string; keywords: string[]; objectives: string[]; deliverables: string[]; source: 'ia' | 'local'; }
export interface SupervisorSuggestion { teacher: UserSummary; specialties: string; score: number; matchedKeywords: string[]; currentProjects: number; reason: string; }
export interface ChatResponse { answer: string; source: 'ia' | 'local'; suggestions: string[]; }
export interface SummaryResponse { summary: string; keywords: string[]; source: 'ia' | 'local'; }
