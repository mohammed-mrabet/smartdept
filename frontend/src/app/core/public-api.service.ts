import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ContactMessage, ContactTopic, Filiere, LoginResponse, MailDelivery, MailRoute, MailStatus, MessageResponse,
  PublicOffer, PublicStats, RegisterRequest, RegisterResponse
} from './models';

/** Appels sans connexion (accueil, inscription, mot de passe) et messagerie de l'administration. */
@Injectable({ providedIn: 'root' })
export class PublicApi {
  private readonly http = inject(HttpClient);

  stats(): Observable<PublicStats> { return this.http.get<PublicStats>('/api/public/stats'); }
  offers(): Observable<PublicOffer[]> { return this.http.get<PublicOffer[]>('/api/public/offers'); }
  filieres(): Observable<Filiere[]> { return this.http.get<Filiere[]>('/api/public/filieres'); }
  contact(body: { name: string; email: string; organisation?: string; topic: ContactTopic; message: string; website?: string }): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/public/contact', body);
  }

  register(body: RegisterRequest): Observable<RegisterResponse> { return this.http.post<RegisterResponse>('/api/auth/register', body); }
  verifyEmail(token: string): Observable<LoginResponse> { return this.http.post<LoginResponse>('/api/auth/verify-email', { token }); }
  resendVerification(email: string): Observable<MessageResponse> { return this.http.post<MessageResponse>('/api/auth/resend-verification', { email }); }
  forgotPassword(email: string): Observable<MessageResponse> { return this.http.post<MessageResponse>('/api/auth/forgot-password', { email }); }
  resetPassword(token: string, password: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>('/api/auth/reset-password', { token, password });
  }

  mailStatus(): Observable<MailStatus> { return this.http.get<MailStatus>('/api/admin/mail/status'); }
  mailRoute(email: string): Observable<MailRoute> { return this.http.get<MailRoute>('/api/admin/mail/route', { params: { email } }); }
  testMail(to: string): Observable<MailDelivery> { return this.http.post<MailDelivery>('/api/admin/mail/test', { to }); }
  contactMessages(): Observable<ContactMessage[]> { return this.http.get<ContactMessage[]>('/api/admin/contact-messages'); }
  markHandled(id: number, handled: boolean): Observable<ContactMessage> {
    return this.http.patch<ContactMessage>(`/api/admin/contact-messages/${id}`, {}, { params: { handled } });
  }
}

/** Force du mot de passe (0 à 4) — mêmes règles minimales que le backend : 8 caractères, une lettre, un chiffre. */
export function passwordScore(p: string): number {
  if (!p) return 0;
  let s = 0;
  if (p.length >= 8) s++;
  if (/[a-zA-Z]/.test(p) && /\d/.test(p)) s++;
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) s++;
  if (p.length >= 12 || /[^a-zA-Z0-9]/.test(p)) s++;
  return s;
}

export function passwordValid(p: string): boolean {
  return p.length >= 8 && /[a-zA-Z]/.test(p) && /\d/.test(p);
}
