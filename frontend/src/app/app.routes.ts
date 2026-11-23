import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';
import { Shell } from './layout/shell';

// Les URL correspondent aux liens envoyés dans les notifications par le backend
const MANAGERS = ['ADMIN', 'CHEF_DEPARTEMENT'];
const TEACHERS = ['ADMIN', 'CHEF_DEPARTEMENT', 'ENSEIGNANT'];

export const routes: Routes = [
  // ---------- pages publiques (sans connexion)
  { path: '', pathMatch: 'full', loadComponent: () => import('./pages/public/landing').then(m => m.Landing), title: 'SmartDept · Département informatique FSO Oujda' },
  { path: 'connexion', loadComponent: () => import('./pages/login/login').then(m => m.Login), title: 'Connexion · SmartDept' },
  { path: 'inscription', loadComponent: () => import('./pages/public/account-pages').then(m => m.Register), title: 'Inscription · SmartDept' },
  { path: 'verifier-email', loadComponent: () => import('./pages/public/account-pages').then(m => m.VerifyEmail), title: 'Confirmation de l’adresse · SmartDept' },
  { path: 'mot-de-passe-oublie', loadComponent: () => import('./pages/public/account-pages').then(m => m.ForgotPassword), title: 'Mot de passe oublié · SmartDept' },
  { path: 'reinitialiser-mot-de-passe', loadComponent: () => import('./pages/public/account-pages').then(m => m.ResetPassword), title: 'Nouveau mot de passe · SmartDept' },
  // ---------- espace connecté
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: 'tableau-de-bord', loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.Dashboard), title: 'Tableau de bord · SmartDept' },
      { path: 'pfe/sujets', loadComponent: () => import('./pages/pfe/subjects/subject-list').then(m => m.SubjectList), title: 'Sujets de PFE · SmartDept' },
      {
        path: 'pfe/sujets/nouveau', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/pfe/subjects/subject-form').then(m => m.SubjectForm), title: 'Proposer un sujet · SmartDept'
      },
      { path: 'pfe/sujets/:id', loadComponent: () => import('./pages/pfe/subjects/subject-detail').then(m => m.SubjectDetail), title: 'Sujet · SmartDept' },
      {
        path: 'pfe/sujets/:id/modifier', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/pfe/subjects/subject-form').then(m => m.SubjectForm), title: 'Modifier le sujet · SmartDept'
      },
      {
        path: 'pfe/candidatures', canActivate: [roleGuard], data: { roles: ['ETUDIANT'] },
        loadComponent: () => import('./pages/pfe/applications/my-applications').then(m => m.MyApplications), title: 'Mes candidatures · SmartDept'
      },
      {
        path: 'pfe/projets', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/pfe/projects/project-list').then(m => m.ProjectList), title: 'Projets · SmartDept'
      },
      { path: 'pfe/projets/:id', loadComponent: () => import('./pages/pfe/projects/project-detail').then(m => m.ProjectDetail), title: 'Projet · SmartDept' },
      {
        path: 'pfe/mon-projet', canActivate: [roleGuard], data: { roles: ['ETUDIANT'], mine: true },
        loadComponent: () => import('./pages/pfe/projects/project-detail').then(m => m.ProjectDetail), title: 'Mon projet · SmartDept'
      },
      { path: 'pfe/soutenances', loadComponent: () => import('./pages/pfe/defenses/defenses').then(m => m.Defenses), title: 'Soutenances · SmartDept' },
      { path: 'pfe/conventions', loadComponent: () => import('./pages/pfe/conventions/conventions').then(m => m.Conventions), title: 'Conventions de stage · SmartDept' },
      { path: 'pfe/conventions/:id', loadComponent: () => import('./pages/pfe/conventions/convention-detail').then(m => m.ConventionDetail), title: 'Convention · SmartDept' },
      { path: 'notifications', loadComponent: () => import('./pages/notifications/notifications').then(m => m.Notifications), title: 'Notifications · SmartDept' },
      { path: 'profil', loadComponent: () => import('./pages/profile/profile').then(m => m.Profile), title: 'Mon profil · SmartDept' },
      // ---------- offres de stage
      { path: 'offres', loadComponent: () => import('./pages/offers/offers').then(m => m.Offers), title: 'Offres de stage · SmartDept' },
      { path: 'offres/suivi', loadComponent: () => import('./pages/offers/tracking').then(m => m.OfferTracking), title: 'Suivi des candidatures · SmartDept' },
      { path: 'offres/alertes', loadComponent: () => import('./pages/offers/alerts').then(m => m.OfferAlerts), title: 'Mes alertes · SmartDept' },
      {
        path: 'offres/marche', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/offers/market').then(m => m.OfferMarket), title: 'Sources et marché · SmartDept'
      },
      {
        path: 'offres/publier', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/offers/offer-form').then(m => m.OfferForm), title: 'Publier une offre · SmartDept'
      },
      {
        path: 'offres/:id/modifier', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/offers/offer-form').then(m => m.OfferForm), title: 'Modifier une offre · SmartDept'
      },
      // ---------- scolarité
      { path: 'emploi-du-temps', loadComponent: () => import('./pages/academic/timetable').then(m => m.Timetable), title: 'Emploi du temps · SmartDept' },
      {
        path: 'emploi-du-temps/planification', canActivate: [roleGuard], data: { roles: MANAGERS },
        loadComponent: () => import('./pages/academic/planner').then(m => m.Planner), title: 'Planification automatique · SmartDept'
      },
      { path: 'examens', loadComponent: () => import('./pages/academic/exams').then(m => m.Exams), title: 'Examens · SmartDept' },
      { path: 'notes', loadComponent: () => import('./pages/academic/grades').then(m => m.Grades), title: 'Notes · SmartDept' },
      { path: 'absences', loadComponent: () => import('./pages/academic/absences').then(m => m.Absences), title: 'Absences · SmartDept' },
      {
        path: 'suivi-etudiants', canActivate: [roleGuard], data: { roles: TEACHERS },
        loadComponent: () => import('./pages/academic/risks').then(m => m.Risks), title: 'Suivi des étudiants · SmartDept'
      },
      {
        path: 'admin/scolarite', canActivate: [roleGuard], data: { roles: MANAGERS },
        loadComponent: () => import('./pages/academic/referential').then(m => m.Referential), title: 'Salles et modules · SmartDept'
      },
      {
        path: 'admin/utilisateurs', canActivate: [roleGuard], data: { roles: MANAGERS },
        loadComponent: () => import('./pages/admin/users').then(m => m.Users), title: 'Utilisateurs · SmartDept'
      },
      {
        path: 'admin/messagerie', canActivate: [roleGuard], data: { roles: MANAGERS },
        loadComponent: () => import('./pages/admin/messaging').then(m => m.Messaging), title: 'Messagerie · SmartDept'
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
