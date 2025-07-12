import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./components/auth/login/login.component')
      .then(m => m.LoginComponent)
  },
  {
    path: 'signup',
    loadComponent: () => import('./components/auth/signup/signup.component')
      .then(m => m.SignupComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard/dashboard.component')
      .then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'functions',
    children: [
      {
        path: '',
        loadComponent: () => import('./components/functions/function-list/function-list.component')
          .then(m => m.FunctionListComponent)
      },
      {
        path: ':id',
        loadComponent: () => import('./components/functions/function-detail/function-detail.component')
          .then(m => m.FunctionDetailComponent)
      },
      {
        path: ':appName/:functionName/invoke',
        loadComponent: () => import('./components/functions/function-invoke/function-invoke.component')
          .then(m => m.FunctionInvokeComponent)
      }
    ],
    canActivate: [authGuard]
  },
  {
    path: 'deploy',
    loadComponent: () => import('./components/deployment/deploy-application/deploy-application.component')
      .then(m => m.DeployApplicationComponent),
    canActivate: [authGuard]
  },
  {
    path: 'profile',
    loadComponent: () => import('./components/profile/user-profile/user-profile.component')
      .then(m => m.UserProfileComponent),
    canActivate: [authGuard]
  },
  {
    path: 'admin',
    children: [
      {
        path: 'users',
        loadComponent: () => import('./components/admin/user-management/user-management.component')
          .then(m => m.UserManagementComponent)
      },
      {
        path: 'platform',
        loadComponent: () => import('./components/admin/platform-status/platform-status.component')
          .then(m => m.PlatformStatusComponent)
      }
    ],
    canActivate: [authGuard, adminGuard]
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];