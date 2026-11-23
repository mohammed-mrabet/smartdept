import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AiStatus, AppNotification, Application, AutoAssignResult, ChatResponse, Convention, ConventionRequest, ConventionStatus,
  Defense, DefenseRequest, Filiere, JurySuggestion, MilestoneStatus, PfeStats, Project, Role, Subject, SubjectRequest,
  SubjectStatus, SubjectSuggestion, SummaryResponse, SupervisorSuggestion, User
} from './models';

function params(obj: Record<string, any>): HttpParams {
  let p = new HttpParams();
  Object.entries(obj).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  });
  return p;
}

/** Tous les appels à l'API REST (le jeton est ajouté par l'intercepteur). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---------- utilisateurs ----------
  me(): Observable<User> { return this.http.get<User>('/api/auth/me'); }
  updateMe(body: any): Observable<User> { return this.http.put<User>('/api/auth/me', body); }
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/me/password', { currentPassword, newPassword });
  }
  users(role?: Role | '', q?: string): Observable<User[]> { return this.http.get<User[]>('/api/users', { params: params({ role, q }) }); }
  createUser(body: any): Observable<User> { return this.http.post<User>('/api/users', body); }
  updateUser(id: number, body: any): Observable<User> { return this.http.put<User>(`/api/users/${id}`, body); }
  setUserEnabled(id: number, value: boolean): Observable<User> {
    return this.http.patch<User>(`/api/users/${id}/enabled`, {}, { params: params({ value }) });
  }
  filieres(): Observable<Filiere[]> { return this.http.get<Filiere[]>('/api/filieres'); }

  // ---------- sujets ----------
  subjects(f: { status?: SubjectStatus | ''; filiereId?: number | null; type?: string; q?: string; mine?: boolean } = {}): Observable<Subject[]> {
    return this.http.get<Subject[]>('/api/pfe/subjects', { params: params(f) });
  }
  subject(id: number): Observable<Subject> { return this.http.get<Subject>(`/api/pfe/subjects/${id}`); }
  createSubject(body: SubjectRequest): Observable<Subject> { return this.http.post<Subject>('/api/pfe/subjects', body); }
  updateSubject(id: number, body: SubjectRequest): Observable<Subject> { return this.http.put<Subject>(`/api/pfe/subjects/${id}`, body); }
  deleteSubject(id: number): Observable<void> { return this.http.delete<void>(`/api/pfe/subjects/${id}`); }
  publishSubject(id: number): Observable<Subject> { return this.http.patch<Subject>(`/api/pfe/subjects/${id}/publish`, {}); }
  rejectSubject(id: number, reason: string): Observable<Subject> { return this.http.patch<Subject>(`/api/pfe/subjects/${id}/reject`, { reason }); }
  archiveSubject(id: number): Observable<Subject> { return this.http.patch<Subject>(`/api/pfe/subjects/${id}/archive`, {}); }
  subjectApplications(id: number): Observable<Application[]> { return this.http.get<Application[]>(`/api/pfe/subjects/${id}/applications`); }

  // ---------- candidatures ----------
  myApplications(): Observable<Application[]> { return this.http.get<Application[]>('/api/pfe/applications/me'); }
  apply(subjectId: number, preferenceRank: number, motivation: string): Observable<Application> {
    return this.http.post<Application>('/api/pfe/applications', { subjectId, preferenceRank, motivation });
  }
  withdraw(id: number): Observable<Application> { return this.http.patch<Application>(`/api/pfe/applications/${id}/withdraw`, {}); }
  acceptApplication(id: number, comment?: string): Observable<Application> {
    return this.http.patch<Application>(`/api/pfe/applications/${id}/accept`, { comment });
  }
  rejectApplication(id: number, comment?: string): Observable<Application> {
    return this.http.patch<Application>(`/api/pfe/applications/${id}/reject`, { comment });
  }
  autoAssign(): Observable<AutoAssignResult> { return this.http.post<AutoAssignResult>('/api/pfe/applications/auto-assign', {}); }

  // ---------- projets et jalons ----------
  projects(): Observable<Project[]> { return this.http.get<Project[]>('/api/pfe/projects'); }
  myProject(): Observable<Project> { return this.http.get<Project>('/api/pfe/projects/me'); }
  project(id: number): Observable<Project> { return this.http.get<Project>(`/api/pfe/projects/${id}`); }
  markReady(id: number): Observable<Project> { return this.http.patch<Project>(`/api/pfe/projects/${id}/ready`, {}); }
  setExternalSupervisor(id: number, name: string, email: string): Observable<Project> {
    return this.http.put<Project>(`/api/pfe/projects/${id}/external-supervisor`, { name, email });
  }
  addMilestone(projectId: number, body: { title: string; description?: string; dueDate: string }): Observable<Project> {
    return this.http.post<Project>(`/api/pfe/projects/${projectId}/milestones`, body);
  }
  deleteMilestone(id: number): Observable<Project> { return this.http.delete<Project>(`/api/pfe/milestones/${id}`); }
  submitMilestone(id: number, deliverableUrl: string, comment: string): Observable<Project> {
    return this.http.post<Project>(`/api/pfe/milestones/${id}/submit`, { deliverableUrl, comment });
  }
  reviewMilestone(id: number, decision: MilestoneStatus, feedback: string, grade: number | null): Observable<Project> {
    return this.http.post<Project>(`/api/pfe/milestones/${id}/review`, { decision, feedback, grade });
  }

  // ---------- soutenances ----------
  defenses(f: { from?: string; to?: string; mine?: boolean } = {}): Observable<Defense[]> {
    return this.http.get<Defense[]>('/api/pfe/defenses', { params: params(f) });
  }
  scheduleDefense(body: DefenseRequest): Observable<Defense> { return this.http.post<Defense>('/api/pfe/defenses', body); }
  updateDefense(id: number, body: DefenseRequest): Observable<Defense> { return this.http.put<Defense>(`/api/pfe/defenses/${id}`, body); }
  cancelDefense(id: number): Observable<Defense> { return this.http.patch<Defense>(`/api/pfe/defenses/${id}/cancel`, {}); }
  gradeDefense(id: number, grade: number, comment: string): Observable<Defense> {
    return this.http.post<Defense>(`/api/pfe/defenses/${id}/grade`, { grade, comment });
  }
  jurySuggestions(projectId: number, date?: string, start?: string, end?: string): Observable<JurySuggestion[]> {
    return this.http.get<JurySuggestion[]>('/api/pfe/defenses/jury-suggestions', { params: params({ projectId, date, start, end }) });
  }

  // ---------- conventions ----------
  conventions(status?: ConventionStatus | ''): Observable<Convention[]> {
    return this.http.get<Convention[]>('/api/pfe/conventions', { params: params({ status }) });
  }
  convention(id: number): Observable<Convention> { return this.http.get<Convention>(`/api/pfe/conventions/${id}`); }
  createConvention(body: ConventionRequest): Observable<Convention> { return this.http.post<Convention>('/api/pfe/conventions', body); }
  updateConvention(id: number, body: ConventionRequest): Observable<Convention> { return this.http.put<Convention>(`/api/pfe/conventions/${id}`, body); }
  submitConvention(id: number): Observable<Convention> { return this.http.patch<Convention>(`/api/pfe/conventions/${id}/submit`, {}); }
  reviewConvention(id: number, approve: boolean, comment: string): Observable<Convention> {
    return this.http.patch<Convention>(`/api/pfe/conventions/${id}/review`, { approve, comment });
  }
  deleteConvention(id: number): Observable<void> { return this.http.delete<void>(`/api/pfe/conventions/${id}`); }

  // ---------- tableau de bord ----------
  stats(): Observable<PfeStats> { return this.http.get<PfeStats>('/api/pfe/stats'); }
  runReminders(): Observable<{ sent: number }> { return this.http.post<{ sent: number }>('/api/pfe/reminders/run', {}); }

  // ---------- notifications ----------
  notifications(unreadOnly = false, limit = 50): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>('/api/notifications', { params: params({ unreadOnly, limit }) });
  }
  unreadCount(): Observable<{ count: number }> { return this.http.get<{ count: number }>('/api/notifications/unread-count'); }
  markRead(id: number): Observable<AppNotification> { return this.http.patch<AppNotification>(`/api/notifications/${id}/read`, {}); }
  markAllRead(): Observable<{ count: number }> { return this.http.patch<{ count: number }>('/api/notifications/read-all', {}); }
  deleteNotification(id: number): Observable<void> { return this.http.delete<void>(`/api/notifications/${id}`); }
  broadcast(role: Role | '', title: string, message: string): Observable<{ recipients: number }> {
    return this.http.post<{ recipients: number }>('/api/notifications/broadcast', { role: role || null, title, message });
  }

  // ---------- IA ----------
  aiStatus(): Observable<AiStatus> { return this.http.get<AiStatus>('/api/ai/status'); }
  improveSubject(title: string, description: string): Observable<SubjectSuggestion> {
    return this.http.post<SubjectSuggestion>('/api/ai/pfe/improve-subject', { title, description });
  }
  suggestSupervisors(title: string, description: string): Observable<SupervisorSuggestion[]> {
    return this.http.post<SupervisorSuggestion[]>('/api/ai/pfe/suggest-supervisors', { title, description });
  }
  summarize(text: string, maxSentences = 4): Observable<SummaryResponse> {
    return this.http.post<SummaryResponse>('/api/ai/summarize', { text, maxSentences });
  }
  chat(message: string): Observable<ChatResponse> { return this.http.post<ChatResponse>('/api/ai/chat', { message }); }
}
