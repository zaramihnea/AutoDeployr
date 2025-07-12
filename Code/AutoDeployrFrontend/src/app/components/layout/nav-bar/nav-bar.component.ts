import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { User } from '../../../models/user.model';

@Component({
  selector: 'app-nav-bar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <nav class="bg-white shadow">
      <div class="max-w-7xl mx-auto px-2 sm:px-6 lg:px-8">
        <div class="relative flex justify-between h-16">
          <!-- Mobile menu button -->
          <div class="absolute inset-y-0 left-0 flex items-center sm:hidden">
            <button type="button" (click)="toggleMobileMenu()" 
                    class="inline-flex items-center justify-center p-2 rounded-md text-gray-400 hover:text-gray-500 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-indigo-500" 
                    aria-controls="mobile-menu" aria-expanded="false">
              <span class="sr-only">Open main menu</span>
              <!-- Icon when menu is closed -->
              <svg *ngIf="!isMobileMenuOpen" class="block h-6 w-6" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
              <!-- Icon when menu is open -->
              <svg *ngIf="isMobileMenuOpen" class="block h-6 w-6" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          
          <div class="flex-1 flex items-center justify-center sm:items-stretch sm:justify-start">
            <div class="flex-shrink-0 flex items-center">
              <a routerLink="/">
                <span class="text-xl font-bold text-indigo-600">AutoDeployr</span>
              </a>
            </div>
            <div class="hidden sm:ml-6 sm:flex sm:space-x-8">
              <!-- Current: "border-indigo-500 text-gray-900", Default: "border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700" -->
              <a routerLink="/dashboard" routerLinkActive="border-indigo-500 text-gray-900" 
                 [routerLinkActiveOptions]="{exact: true}"
                 class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium">
                Dashboard
              </a>
              <a routerLink="/functions" routerLinkActive="border-indigo-500 text-gray-900"
                 class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium">
                Functions
              </a>
              <a routerLink="/deploy" routerLinkActive="border-indigo-500 text-gray-900"
                 class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium">
                Deploy
              </a>
              <a *ngIf="isAdmin()" routerLink="/admin/platform" routerLinkActive="border-indigo-500 text-gray-900"
                 class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium">
                Admin
              </a>
            </div>
          </div>
          
          <!-- User dropdown -->
          <div class="absolute inset-y-0 right-0 flex items-center pr-2 sm:static sm:inset-auto sm:ml-6 sm:pr-0">
            <div class="ml-3 relative">
              <div>
                <button type="button" (click)="toggleUserMenu()" 
                        class="bg-white rounded-full flex text-sm focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500" 
                        id="user-menu-button" aria-expanded="false" aria-haspopup="true">
                  <span class="sr-only">Open user menu</span>
                  <div class="h-8 w-8 rounded-full bg-indigo-100 flex items-center justify-center">
                    <span class="font-medium text-indigo-800">{{ getUserInitials() }}</span>
                  </div>
                </button>
              </div>
              <div *ngIf="isUserMenuOpen" 
                   class="origin-top-right absolute right-0 mt-2 w-48 rounded-md shadow-lg py-1 bg-white ring-1 ring-black ring-opacity-5 focus:outline-none" 
                   role="menu" aria-orientation="vertical" aria-labelledby="user-menu-button" tabindex="-1">
                <a *ngIf="currentUser" class="block px-4 py-2 text-sm text-gray-700 font-medium border-b">
                  {{ currentUser.username }}
                </a>
                <a routerLink="/profile" (click)="isUserMenuOpen = false"
                   class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100" role="menuitem" tabindex="-1">
                  Your Profile
                </a>
                <a href="javascript:void(0)" (click)="logout()" 
                   class="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-100" role="menuitem" tabindex="-1">
                  Sign out
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Mobile menu, show/hide based on menu state -->
      <div *ngIf="isMobileMenuOpen" class="sm:hidden" id="mobile-menu">
        <div class="pt-2 pb-4 space-y-1">
          <a routerLink="/dashboard" (click)="isMobileMenuOpen = false" routerLinkActive="bg-indigo-50 border-indigo-500 text-indigo-700"
             [routerLinkActiveOptions]="{exact: true}"
             class="border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
            Dashboard
          </a>
          <a routerLink="/functions" (click)="isMobileMenuOpen = false" routerLinkActive="bg-indigo-50 border-indigo-500 text-indigo-700"
             class="border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
            Functions
          </a>
          <a routerLink="/deploy" (click)="isMobileMenuOpen = false" routerLinkActive="bg-indigo-50 border-indigo-500 text-indigo-700"
             class="border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
            Deploy
          </a>
          <a *ngIf="isAdmin()" routerLink="/admin/platform" (click)="isMobileMenuOpen = false" routerLinkActive="bg-indigo-50 border-indigo-500 text-indigo-700"
             class="border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
            Admin
          </a>
          <a routerLink="/profile" (click)="isMobileMenuOpen = false" routerLinkActive="bg-indigo-50 border-indigo-500 text-indigo-700"
             class="border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
            Profile
          </a>
          <a href="javascript:void(0)" (click)="logout()" 
             class="border-transparent text-gray-500 hover:bg-gray-50 hover:border-gray-300 hover:text-gray-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium">
            Sign out
          </a>
        </div>
      </div>
    </nav>
  `,
  styles: []
})
export class NavBarComponent implements OnInit {
  isMobileMenuOpen = false;
  isUserMenuOpen = false;
  currentUser: User | null = null;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      this.currentUser = user;
    });
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
    if (this.isMobileMenuOpen) {
      this.isUserMenuOpen = false;
    }
  }

  toggleUserMenu() {
    this.isUserMenuOpen = !this.isUserMenuOpen;
  }

  logout() {
    this.authService.logout();
    this.isMobileMenuOpen = false;
    this.isUserMenuOpen = false;
  }

  getUserInitials(): string {
    if (!this.currentUser) return '?';
    
    const firstInitial = this.currentUser.firstName?.charAt(0) || this.currentUser.username.charAt(0);
    const lastInitial = this.currentUser.lastName?.charAt(0) || '';
    
    return (firstInitial + lastInitial).toUpperCase();
  }

  isAdmin(): boolean {
    return this.authService.hasRole('ROLE_ADMIN');
  }
}
export default NavBarComponent;