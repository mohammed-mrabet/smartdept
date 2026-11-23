// Offres de stage : modèles, libellés et appels API
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type OfferCountry = 'FR' | 'MA' | 'CA' | 'BE' | 'ES' | 'REMOTE' | 'OTHER';
export type OfferKind = 'PFE' | 'STAGE' | 'ALTERNANCE' | 'EMPLOI';
export type OfferSource = 'INTERNAL' | 'FRANCE_TRAVAIL' | 'LA_BONNE_ALTERNANCE' | 'ADZUNA' | 'JOOBLE' | 'CAREERJET' | 'INFOJOBS' | 'REMOTIVE' | 'JOBICY' | 'ARBEITNOW';
export type TrackingStatus = 'SAVED' | 'APPLIED' | 'INTERVIEW' | 'ACCEPTED' | 'REJECTED';

export interface Offer {
  id: number; source: OfferSource; sourceLabel: string; title: string; company?: string; location?: string;
  country: OfferCountry; countryLabel: string; flag: string; kind: OfferKind; kindLabel: string;
  levelMin?: number; levelMax?: number; levelLabel: string; remote: boolean; skills: string[]; durationMonths?: number;
  salary?: string; publishedAt?: string; expiresAt?: string; url?: string; internal: boolean; excerpt: string;
  description?: string; contactEmail?: string; postedBy?: string; match?: number; tracking?: TrackingStatus;
}
export interface Facets { countries: Record<string, number>; kinds: Record<string, number>; levels: Record<string, number>; sources: Record<string, number>; unknownLevel: number; }
export interface OfferPage { items: Offer[]; total: number; page: number; size: number; totalPages: number; facets: Facets; myLevel?: number; }
export interface OfferFilters {
  q?: string; country?: OfferCountry | ''; kind?: OfferKind | ''; source?: OfferSource | ''; remote?: boolean; level?: number | null;
  includeUnknown?: boolean; sort?: 'recent' | 'match'; page?: number; size?: number;
}
export interface OfferRequest {
  title: string; company: string; location: string; country: OfferCountry; kind: OfferKind; levelMin: number | null; levelMax: number | null;
  remote: boolean; description: string; url?: string; contactEmail?: string; durationMonths?: number | null; salary?: string; expiresAt?: string | null; skills?: string;
}
export interface Tracking { id: number; status: TrackingStatus; notes?: string; appliedAt?: string; updatedAt: string; offer: Offer; }
export interface Alert { id: number; name: string; keywords?: string; country?: OfferCountry; kind?: OfferKind; studyLevel?: number; remoteOnly: boolean; active: boolean; lastNotifiedAt?: string; matchesSent: number; createdAt: string; }
export interface AlertRequest { name: string; keywords?: string; country?: OfferCountry | null; kind?: OfferKind | null; studyLevel?: number | null; remoteOnly: boolean; active?: boolean; }
export interface RunInfo { startedAt: string; success: boolean; fetched: number; created: number; updated: number; durationMs: number; message?: string; }
export interface SourceStatus { source: OfferSource; label: string; website: string; configured: boolean; keyless: boolean; countries: OfferCountry[]; configHint: string; activeOffers: number; minIntervalMinutes: number; lastRun?: RunInfo; }
export interface SyncResult { source: OfferSource; label: string; fetched: number; created: number; updated: number; success: boolean; message?: string; durationMs: number; }
export interface SyncSummary { results: SyncResult[]; created: number; updated: number; alertsSent: number; }
export interface MatchResult { score: number; verdict: string; levelFits: boolean; matchedSkills: string[]; missingSkills: string[]; advice: string[]; source: string; }
export interface CoverLetter { text: string; language: string; source: 'ia' | 'local'; }
export interface NameCount { name: string; count: number; }
export interface OfferStats { total: number; newThisWeek: number; byCountry: Record<string, number>; byLevel: Record<string, number>; byKind: Record<string, number>; bySource: Record<string, number>; topSkills: NameCount[]; topCompanies: NameCount[]; tracking: Record<string, number>; }

export const COUNTRIES: { code: OfferCountry; label: string; flag: string }[] = [
  { code: 'MA', label: 'Maroc', flag: '🇲🇦' }, { code: 'FR', label: 'France', flag: '🇫🇷' }, { code: 'CA', label: 'Canada', flag: '🇨🇦' },
  { code: 'BE', label: 'Belgique', flag: '🇧🇪' }, { code: 'ES', label: 'Espagne', flag: '🇪🇸' }, { code: 'REMOTE', label: 'Télétravail', flag: '🌍' }
];
export const COUNTRY_LABEL: Record<string, string> = { FR: 'France', MA: 'Maroc', CA: 'Canada', BE: 'Belgique', ES: 'Espagne', REMOTE: 'Télétravail', OTHER: 'Autre pays' };
export const COUNTRY_FLAG: Record<string, string> = { FR: '🇫🇷', MA: '🇲🇦', CA: '🇨🇦', BE: '🇧🇪', ES: '🇪🇸', REMOTE: '🌍', OTHER: '🏳' };
export const KINDS: { code: OfferKind; label: string; icon: string }[] = [
  { code: 'PFE', label: 'PFE', icon: '🎓' }, { code: 'STAGE', label: 'Stage', icon: '💼' },
  { code: 'ALTERNANCE', label: 'Alternance', icon: '🔁' }, { code: 'EMPLOI', label: 'Premier emploi', icon: '🚀' }
];
export const KIND_LABEL: Record<string, string> = { PFE: 'Stage de fin d’études', STAGE: 'Stage', ALTERNANCE: 'Alternance', EMPLOI: 'Premier emploi' };
export const LEVELS = [
  { years: 2, label: 'Bac+2', diplomas: 'DUT · BTS · DEUST' },
  { years: 3, label: 'Bac+3', diplomas: 'Licence · Bachelor' },
  { years: 4, label: 'Bac+4', diplomas: 'Master 1' },
  { years: 5, label: 'Bac+5', diplomas: 'Master 2 · Ingénieur' }
];
export const TRACKING: { code: TrackingStatus; label: string; color: string }[] = [
  { code: 'SAVED', label: 'À postuler', color: 'blue' }, { code: 'APPLIED', label: 'Candidature envoyée', color: 'violet' },
  { code: 'INTERVIEW', label: 'Entretien', color: 'amber' }, { code: 'ACCEPTED', label: 'Acceptée', color: 'teal' },
  { code: 'REJECTED', label: 'Refusée', color: 'red' }
];
export const TRACKING_LABEL: Record<string, string> = Object.fromEntries(TRACKING.map(t => [t.code, t.label]));

/** Couleur stable dérivée du nom de l'entreprise (avatar). */
export function companyColor(name?: string): string {
  const palette = ['#2563eb', '#0f8a7a', '#6d3fc0', '#b45309', '#c0392b', '#0e7490', '#4d7c0f', '#9d174d'];
  let h = 0;
  for (const c of name ?? '?') h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return palette[h % palette.length];
}

export function initials(name?: string): string {
  const words = (name ?? '?').replace(/\(.*?\)/g, '').trim().split(/\s+/).filter(Boolean);
  return ((words[0]?.[0] ?? '?') + (words[1]?.[0] ?? '')).toUpperCase();
}

@Injectable({ providedIn: 'root' })
export class OfferApi {
  private readonly http = inject(HttpClient);

  search(f: OfferFilters): Observable<OfferPage> {
    let p = new HttpParams();
    Object.entries(f).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v)); });
    return this.http.get<OfferPage>('/api/offers', { params: p });
  }
  get(id: number): Observable<Offer> { return this.http.get<Offer>(`/api/offers/${id}`); }
  recommended(limit = 8): Observable<Offer[]> { return this.http.get<Offer[]>('/api/offers/recommended', { params: { limit } }); }
  match(id: number): Observable<MatchResult> { return this.http.get<MatchResult>(`/api/offers/${id}/match`); }
  coverLetter(id: number, language: string, extra: string): Observable<CoverLetter> {
    return this.http.post<CoverLetter>(`/api/offers/${id}/cover-letter`, { language, extra });
  }
  liveSearch(keywords: string, country: OfferCountry | '', kind: OfferKind | ''): Observable<SyncSummary> {
    return this.http.post<SyncSummary>('/api/offers/live-search', { keywords: keywords || null, country: country || null, kind: kind || null });
  }
  mine(): Observable<Offer[]> { return this.http.get<Offer[]>('/api/offers/mine'); }
  create(body: OfferRequest): Observable<Offer> { return this.http.post<Offer>('/api/offers', body); }
  update(id: number, body: OfferRequest): Observable<Offer> { return this.http.put<Offer>(`/api/offers/${id}`, body); }
  close(id: number): Observable<Offer> { return this.http.patch<Offer>(`/api/offers/${id}/close`, {}); }
  remove(id: number): Observable<void> { return this.http.delete<void>(`/api/offers/${id}`); }
  tracking(): Observable<Tracking[]> { return this.http.get<Tracking[]>('/api/offers/tracking'); }
  track(id: number, status: TrackingStatus, notes?: string | null): Observable<Tracking> {
    return this.http.put<Tracking>(`/api/offers/${id}/tracking`, { status, notes });
  }
  untrack(id: number): Observable<void> { return this.http.delete<void>(`/api/offers/${id}/tracking`); }
  alerts(): Observable<Alert[]> { return this.http.get<Alert[]>('/api/offers/alerts'); }
  createAlert(body: AlertRequest): Observable<Alert> { return this.http.post<Alert>('/api/offers/alerts', body); }
  updateAlert(id: number, body: AlertRequest): Observable<Alert> { return this.http.put<Alert>(`/api/offers/alerts/${id}`, body); }
  deleteAlert(id: number): Observable<void> { return this.http.delete<void>(`/api/offers/alerts/${id}`); }
  sources(): Observable<SourceStatus[]> { return this.http.get<SourceStatus[]>('/api/offers/sources'); }
  sync(source?: OfferSource): Observable<SyncSummary> {
    return this.http.post<SyncSummary>('/api/offers/sync', {}, { params: source ? { source } : {} });
  }
  stats(): Observable<OfferStats> { return this.http.get<OfferStats>('/api/offers/stats'); }
}
