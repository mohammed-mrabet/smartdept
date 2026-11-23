import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';
import { Shell } from './layout/shell';

// Les URL correspondent aux liens envoyés dans les notifications par le backend
const MANAGERS = ['ADMIN', 'CHEF_DEPARTEMENT'];
const TEACHERS = ['ADMIN', 'CHEF_DEPARTEMENT', 'ENSEIGNANT'];

export const routes: Routes = [
  { path: 'connexion', loadComponent: () => import('./pages/login/login').then(m => m.Login), title: 'Connexion · SmartDept' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-de-bord' },
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
      {
        path: 'admin/utilisateurs', canActivate: [roleGuard], data: { roles: MANAGERS },
        loadComponent: () => import('./pages/admin/users').then(m => m.Users), title: 'Utilisateurs · SmartDept'
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
