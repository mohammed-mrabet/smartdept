// Scolarité : modèles et appels API (emploi du temps, examens, notes, absences, suivi)
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FiliereSummary, UserSummary } from './models';

export type RoomType = 'AMPHI' | 'SALLE' | 'LABO';
export type SessionType = 'CM' | 'TD' | 'TP';
export type SessionStatus = 'PLANNED' | 'CANCELLED';
export type ExamType = 'CC' | 'FINAL' | 'RATTRAPAGE' | 'TP';
export type Assessment = 'CC' | 'EXAM' | 'TP' | 'RATTRAPAGE';
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE';
export type JustificationStatus = 'NONE' | 'PENDING' | 'ACCEPTED' | 'REJECTED';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface Room { id: number; code: string; name: string; capacity: number; type: RoomType; building?: string; active: boolean; }
export interface RoomSummary { id: number; code: string; name: string; capacity: number; type: RoomType; }
export interface CourseModule {
  id: number; code: string; name: string; filiere: FiliereSummary; semester: string; teacher?: UserSummary; coefficient: number;
  cmPerWeek: number; tdPerWeek: number; tpPerWeek: number; description?: string;
}
export interface Session {
  id: number; moduleId: number; moduleCode: string; moduleName: string; teacher: UserSummary; room: RoomSummary; filiere: FiliereSummary;
  date: string; startTime: string; endTime: string; type: SessionType; status: SessionStatus; notes?: string; attendanceTaken: boolean;
}
export interface SessionRequest {
  moduleId: number | null; teacherId?: number | null; roomId: number | null; date: string; startTime: string; endTime: string;
  type: SessionType; notes?: string; weeks?: number | null;
}
export interface PlannedSlot {
  moduleId: number; moduleCode: string; moduleName: string; teacherId: number; teacherName: string; roomId: number; roomCode: string;
  date: string; startTime: string; endTime: string; type: SessionType; reason: string;
}
export interface PlanPreview { filiereId: number; weekStart: string; students: number; required: number; slots: PlannedSlot[]; unplaced: string[]; sessionsPerDay: Record<string, number>; }
export interface PlanApplyResult { created: number; skipped: number; conflicts: string[]; }
export interface Exam {
  id: number; moduleId: number; moduleCode: string; moduleName: string; moduleTeacher?: UserSummary; filiere: FiliereSummary;
  date: string; startTime: string; endTime: string; type: ExamType; rooms: RoomSummary[]; capacity: number; students: number;
  supervisors: UserSummary[]; notes?: string;
}
export interface ExamRequest { moduleId: number | null; date: string; startTime: string; endTime: string; type: ExamType; roomIds: number[]; supervisorIds: number[]; notes?: string; }
export interface ExamPlanSlot {
  moduleId: number; moduleCode: string; moduleName: string; date: string; startTime: string; endTime: string;
  roomIds: number[]; roomCodes: string[]; supervisorIds: number[]; supervisorNames: string[];
}
export interface ExamPlanPreview { filiereId: number; type: ExamType; students: number; slots: ExamPlanSlot[]; unplaced: string[]; }
export interface RosterRow { student: UserSummary; studentNumber?: string; grades: Partial<Record<Assessment, number>>; average?: number; status: string; }
export interface ModuleGrades { module: CourseModule; rows: RosterRow[]; published: Partial<Record<Assessment, boolean>>; classAverage?: number; passRate?: number; min?: number; max?: number; }
export interface TranscriptLine { moduleId: number; code: string; name: string; semester: string; coefficient: number; cc?: number; exam?: number; tp?: number; rattrapage?: number; average?: number; status: string; }
export interface SemesterSummary { semester: string; average?: number; validated: number; total: number; lines: TranscriptLine[]; }
export interface Transcript { student: UserSummary; studentNumber?: string; filiere?: FiliereSummary; semesters: SemesterSummary[]; overallAverage?: number; validatedModules: number; totalModules: number; decision: string; }
export interface AttendanceRow { studentId: number; fullName: string; studentNumber?: string; status: AttendanceStatus; justificationStatus: JustificationStatus; }
export interface AttendanceSheet { session: Session; rows: AttendanceRow[]; taken: boolean; }
export interface Absence {
  id: number; sessionId: number; moduleCode: string; moduleName: string; date: string; startTime: string; endTime: string; teacherName?: string;
  status: AttendanceStatus; justificationStatus: JustificationStatus; justification?: string; reviewComment?: string; justifiedAt?: string; student: UserSummary;
}
export interface Risk {
  student: UserSummary; studentNumber?: string; filiereCode?: string; score: number; level: RiskLevel; average?: number; failingModules: number;
  unjustifiedAbsences: number; attendanceRate?: number; lateMilestones: number; reasons: string[];
}
export interface Overview { sessionsToday: number; sessionsThisWeek: number; examsNext14Days: number; pendingJustifications: number; studentsAtRisk: number; departmentAverage?: number; }

export const SESSION_COLORS: Record<SessionType, string> = { CM: 'cm', TD: 'td', TP: 'tp' };
export const EXAM_LABEL: Record<ExamType, string> = { CC: 'Contrôle continu', FINAL: 'Examen final', RATTRAPAGE: 'Rattrapage', TP: 'Examen de TP' };
export const ASSESSMENTS: { code: Assessment; label: string }[] = [
  { code: 'CC', label: 'Contrôle continu' }, { code: 'EXAM', label: 'Examen' }, { code: 'TP', label: 'TP' }, { code: 'RATTRAPAGE', label: 'Rattrapage' }
];
export const GRADE_STATUS: Record<string, [string, string]> = {
  VALIDE: ['Validé', 'teal'], COMPENSE: ['Compensé', 'blue'], AJOURNE: ['Ajourné', 'red'], INCOMPLET: ['En attente', 'gray']
};
export const JUSTIF: Record<JustificationStatus, [string, string]> = {
  NONE: ['Non justifiée', 'red'], PENDING: ['Justificatif en examen', 'amber'], ACCEPTED: ['Justifiée', 'teal'], REJECTED: ['Justificatif refusé', 'red']
};
export const RISK: Record<RiskLevel, [string, string]> = { LOW: ['Faible', 'teal'], MEDIUM: ['Moyen', 'amber'], HIGH: ['Élevé', 'red'] };
export const DAYS = ['Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi'];

export function mondayOf(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  const shift = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - shift);
  return isoDate(date);
}
export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  return isoDate(new Date(y, m - 1, d + days));
}
export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export function minutes(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}

function params(obj: Record<string, any>): HttpParams {
  let p = new HttpParams();
  Object.entries(obj).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v)); });
  return p;
}

@Injectable({ providedIn: 'root' })
export class AcademicApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/academic';

  rooms(): Observable<Room[]> { return this.http.get<Room[]>(`${this.base}/rooms`); }
  saveRoom(body: any, id?: number): Observable<Room> {
    return id ? this.http.put<Room>(`${this.base}/rooms/${id}`, body) : this.http.post<Room>(`${this.base}/rooms`, body);
  }
  modules(f: { filiereId?: number | null; mine?: boolean } = {}): Observable<CourseModule[]> {
    return this.http.get<CourseModule[]>(`${this.base}/modules`, { params: params(f) });
  }
  saveModule(body: any, id?: number): Observable<CourseModule> {
    return id ? this.http.put<CourseModule>(`${this.base}/modules/${id}`, body) : this.http.post<CourseModule>(`${this.base}/modules`, body);
  }

  sessions(f: { week: string; filiereId?: number | null; teacherId?: number | null; roomId?: number | null }): Observable<Session[]> {
    return this.http.get<Session[]>(`${this.base}/sessions`, { params: params(f) });
  }
  mySessions(week: string): Observable<Session[]> { return this.http.get<Session[]>(`${this.base}/sessions/me`, { params: { week } }); }
  createSession(body: SessionRequest): Observable<Session[]> { return this.http.post<Session[]>(`${this.base}/sessions`, body); }
  updateSession(id: number, body: SessionRequest): Observable<Session> { return this.http.put<Session>(`${this.base}/sessions/${id}`, body); }
  cancelSession(id: number, reason: string): Observable<Session> { return this.http.patch<Session>(`${this.base}/sessions/${id}/cancel`, { reason }); }
  deleteSession(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/sessions/${id}`); }
  previewTimetable(filiereId: number, weekStart: string, semester?: string): Observable<PlanPreview> {
    return this.http.post<PlanPreview>(`${this.base}/planning/timetable/preview`, { filiereId, weekStart, semester: semester || null });
  }
  applyTimetable(filiereId: number, weeks: number, slots: PlannedSlot[]): Observable<PlanApplyResult> {
    return this.http.post<PlanApplyResult>(`${this.base}/planning/timetable/apply`, {
      filiereId, weeks, slots: slots.map(s => ({ moduleId: s.moduleId, teacherId: s.teacherId, roomId: s.roomId, date: s.date, startTime: s.startTime, endTime: s.endTime, type: s.type }))
    });
  }

  exams(f: { from?: string; to?: string; filiereId?: number | null } = {}): Observable<Exam[]> {
    return this.http.get<Exam[]>(`${this.base}/exams`, { params: params(f) });
  }
  myExams(): Observable<Exam[]> { return this.http.get<Exam[]>(`${this.base}/exams/me`); }
  createExam(body: ExamRequest): Observable<Exam> { return this.http.post<Exam>(`${this.base}/exams`, body); }
  deleteExam(id: number): Observable<void> { return this.http.delete<void>(`${this.base}/exams/${id}`); }
  previewExams(body: { filiereId: number; from: string; to: string; semester?: string | null; type: ExamType; durationMinutes: number }): Observable<ExamPlanPreview> {
    return this.http.post<ExamPlanPreview>(`${this.base}/planning/exams/preview`, body);
  }
  applyExams(filiereId: number, type: ExamType, slots: ExamPlanSlot[]): Observable<Exam[]> {
    return this.http.post<Exam[]>(`${this.base}/planning/exams/apply`, { filiereId, type, slots });
  }

  moduleGrades(moduleId: number): Observable<ModuleGrades> { return this.http.get<ModuleGrades>(`${this.base}/grades/modules/${moduleId}`); }
  saveGrades(moduleId: number, assessment: Assessment, entries: { studentId: number; value: number | null }[]): Observable<ModuleGrades> {
    return this.http.put<ModuleGrades>(`${this.base}/grades/modules/${moduleId}`, { assessment, entries });
  }
  publishGrades(moduleId: number, assessment: Assessment): Observable<ModuleGrades> {
    return this.http.post<ModuleGrades>(`${this.base}/grades/modules/${moduleId}/publish`, {}, { params: { assessment } });
  }
  myTranscript(): Observable<Transcript> { return this.http.get<Transcript>(`${this.base}/grades/me`); }
  transcript(studentId: number): Observable<Transcript> { return this.http.get<Transcript>(`${this.base}/grades/students/${studentId}`); }

  attendance(sessionId: number): Observable<AttendanceSheet> { return this.http.get<AttendanceSheet>(`${this.base}/sessions/${sessionId}/attendance`); }
  saveAttendance(sessionId: number, entries: { studentId: number; status: AttendanceStatus }[]): Observable<AttendanceSheet> {
    return this.http.put<AttendanceSheet>(`${this.base}/sessions/${sessionId}/attendance`, { entries });
  }
  myAbsences(): Observable<Absence[]> { return this.http.get<Absence[]>(`${this.base}/absences/me`); }
  justify(id: number, reason: string): Observable<Absence> { return this.http.patch<Absence>(`${this.base}/absences/${id}/justify`, { reason }); }
  pendingJustifications(): Observable<Absence[]> { return this.http.get<Absence[]>(`${this.base}/absences/pending`); }
  review(id: number, accept: boolean, comment: string): Observable<Absence> {
    return this.http.patch<Absence>(`${this.base}/absences/${id}/review`, { accept, comment });
  }

  risks(filiereId?: number | null): Observable<Risk[]> { return this.http.get<Risk[]>(`${this.base}/risks`, { params: params({ filiereId }) }); }
  scanRisks(): Observable<{ high: number; medium: number; notified: number }> { return this.http.post<any>(`${this.base}/risks/scan`, {}); }
  explainRisk(studentId: number): Observable<{ text: string; source: string }> { return this.http.get<any>(`${this.base}/risks/${studentId}/explain`); }
  overview(): Observable<Overview> { return this.http.get<Overview>(`${this.base}/overview`); }
}
