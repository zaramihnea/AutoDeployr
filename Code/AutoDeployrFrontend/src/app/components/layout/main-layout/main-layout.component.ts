import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { NavBarComponent } from '../nav-bar/nav-bar.component';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, NavBarComponent],
  template: `
    <div class="min-h-screen bg-gray-50 flex flex-col">
      <!-- Navigation bar is visible only when user is authenticated -->
      <app-nav-bar *ngIf="authService.isAuthenticated$ | async"></app-nav-bar>
      
      <!-- Page content -->
      <main class="flex-grow">
        <router-outlet></router-outlet>
      </main>
      
      <!-- Footer -->
      <footer class="bg-white border-t">
        <div class="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
          <div class="flex flex-col items-center md:flex-row md:justify-between">
            <div class="flex-shrink-0">
              <span class="text-sm text-gray-500">
                &copy; {{ currentYear }} AutoDeployr. All rights reserved.
              </span>
            </div>
            <div class="mt-4 md:mt-0">
              <div class="flex space-x-6">
                <a href="#" class="text-gray-400 hover:text-gray-500">
                  <span class="sr-only">Support</span>
                  <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 5.636l-3.536 3.536m0 5.656l3.536 3.536M9.172 9.172L5.636 5.636m3.536 9.192l-3.536 3.536M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-5 0a4 4 0 11-8 0 4 4 0 018 0z" />
                  </svg>
                </a>
              </div>
            </div>
          </div>
        </div>
      </footer>
    </div>
  `,
  styles: []
})
export class MainLayoutComponent {
  currentYear = new Date().getFullYear();
  
  constructor(public authService: AuthService) {}
}