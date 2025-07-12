import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '../../../services/user.service';
import { AuthService } from '../../../services/auth.service';
import { User, ResetPasswordRequest } from '../../../models/user.model';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <div class="md:flex md:items-center md:justify-between">
        <div class="flex-1 min-w-0">
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
            My Profile
          </h1>
          <p class="mt-1 text-sm text-gray-500">
            Manage your account settings and preferences
          </p>
        </div>
      </div>

      <!-- Loading spinner -->
      <div *ngIf="isLoading" class="flex justify-center my-8">
        <svg class="animate-spin h-10 w-10 text-indigo-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      </div>

      <!-- Success Message -->
      <div *ngIf="successMessage" class="mt-4 rounded-md bg-green-50 p-4">
        <div class="flex">
          <div class="flex-shrink-0">
            <svg class="h-5 w-5 text-green-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
            </svg>
          </div>
          <div class="ml-3">
            <p class="text-sm font-medium text-green-800">{{ successMessage }}</p>
          </div>
        </div>
      </div>

      <!-- Error Message -->
      <div *ngIf="errorMessage" class="mt-4 rounded-md bg-red-50 p-4">
        <div class="flex">
          <div class="flex-shrink-0">
            <svg class="h-5 w-5 text-red-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
            </svg>
          </div>
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ errorMessage }}</h3>
          </div>
        </div>
      </div>

      <div *ngIf="user && !isLoading" class="mt-6 grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">
        <!-- Profile Card -->
        <div class="sm:col-span-3">
          <div class="bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
            <div class="md:grid md:grid-cols-1 md:gap-6">
              <div class="md:col-span-1">
                <h3 class="text-lg font-medium leading-6 text-gray-900">Profile Information</h3>
                <p class="mt-1 text-sm text-gray-500">
                  Your account details and profile information.
                </p>
              </div>
              <div class="mt-5 md:mt-0 md:col-span-1">
                <div class="flex flex-col space-y-6">
                  <div class="flex items-center">
                    <div class="h-12 w-12 rounded-full bg-indigo-100 flex items-center justify-center">
                      <span class="text-lg font-medium text-indigo-800">{{ getUserInitials() }}</span>
                    </div>
                    <div class="ml-4">
                      <h2 class="text-lg font-medium text-gray-900">{{ user.firstName || '' }} {{ user.lastName || '' }}</h2>
                      <p class="text-sm text-gray-500">{{ user.username }}</p>
                    </div>
                  </div>

                  <div class="border-t border-gray-200 pt-4">
                    <dl class="divide-y divide-gray-200">
                      <div class="py-3 sm:py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                        <dt class="text-sm font-medium text-gray-500">Email</dt>
                        <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ user.email }}</dd>
                      </div>
                      <div class="py-3 sm:py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                        <dt class="text-sm font-medium text-gray-500">Username</dt>
                        <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ user.username }}</dd>
                      </div>
                      <div class="py-3 sm:py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                        <dt class="text-sm font-medium text-gray-500">Account Status</dt>
                        <dd class="mt-1 sm:mt-0 sm:col-span-2">
                          <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full"
                                [ngClass]="{'bg-green-100 text-green-800': user.active, 'bg-red-100 text-red-800': !user.active}">
                            {{ user.active ? 'Active' : 'Inactive' }}
                          </span>
                        </dd>
                      </div>
                      <div class="py-3 sm:py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                        <dt class="text-sm font-medium text-gray-500">Roles</dt>
                        <dd class="mt-1 sm:mt-0 sm:col-span-2">
                          <div class="flex flex-wrap gap-2">
                            <span *ngFor="let role of user.roles" 
                                  class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-indigo-100 text-indigo-800">
                              {{ role | slice:5 }}
                            </span>
                          </div>
                        </dd>
                      </div>
                      <div class="py-3 sm:py-4 sm:grid sm:grid-cols-3 sm:gap-4">
                        <dt class="text-sm font-medium text-gray-500">Created</dt>
                        <dd class="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">{{ user.createdAt | date }}</dd>
                      </div>
                    </dl>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Change Password Card -->
        <div class="sm:col-span-3">
          <div class="bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
            <div class="md:grid md:grid-cols-1 md:gap-6">
              <div class="md:col-span-1">
                <h3 class="text-lg font-medium leading-6 text-gray-900">Change Password</h3>
                <p class="mt-1 text-sm text-gray-500">
                  Update your password for better security.
                </p>
              </div>
              <div class="mt-5 md:mt-0 md:col-span-1">
                <form [formGroup]="passwordForm" (ngSubmit)="onPasswordSubmit()">
                  <div class="grid grid-cols-6 gap-6">
                    <div class="col-span-6">
                      <label for="current-password" class="block text-sm font-medium text-gray-700">Current Password</label>
                      <input type="password" formControlName="currentPassword" id="current-password" 
                             class="mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md">
                      <div *ngIf="currentPassword?.invalid && (currentPassword?.dirty || currentPassword?.touched)" class="mt-1 text-sm text-red-600">
                        <div *ngIf="currentPassword?.errors?.['required']">Current password is required</div>
                      </div>
                    </div>

                    <div class="col-span-6">
                      <label for="new-password" class="block text-sm font-medium text-gray-700">New Password</label>
                      <input type="password" formControlName="newPassword" id="new-password" 
                             class="mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md">
                      <div *ngIf="newPassword?.invalid && (newPassword?.dirty || newPassword?.touched)" class="mt-1 text-sm text-red-600">
                        <div *ngIf="newPassword?.errors?.['required']">New password is required</div>
                        <div *ngIf="newPassword?.errors?.['minlength']">Password must be at least 6 characters</div>
                      </div>
                    </div>

                    <div class="col-span-6">
                      <label for="confirm-password" class="block text-sm font-medium text-gray-700">Confirm Password</label>
                      <input type="password" formControlName="confirmPassword" id="confirm-password" 
                             class="mt-1 focus:ring-indigo-500 focus:border-indigo-500 block w-full shadow-sm sm:text-sm border-gray-300 rounded-md">
                      <div *ngIf="confirmPassword?.invalid && (confirmPassword?.dirty || confirmPassword?.touched)" class="mt-1 text-sm text-red-600">
                        <div *ngIf="confirmPassword?.errors?.['required']">Confirm password is required</div>
                      </div>
                      <div *ngIf="passwordForm.errors?.['passwordMismatch'] && confirmPassword?.touched" class="mt-1 text-sm text-red-600">
                        Passwords do not match
                      </div>
                    </div>
                  </div>

                  <div class="mt-6 flex justify-end">
                    <button type="submit" 
                            [disabled]="passwordForm.invalid || passwordButtonLoading"
                            class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed">
                      <span *ngIf="passwordButtonLoading" class="mr-2">
                        <!-- Loading spinner -->
                        <svg class="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                        </svg>
                      </span>
                      Update Password
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>

        <!-- Danger Zone Card -->
        <div class="sm:col-span-6">
          <div class="bg-white shadow sm:rounded-lg">
            <div class="px-4 py-5 sm:p-6">
              <h3 class="text-lg leading-6 font-medium text-gray-900">Delete Account</h3>
              <div class="mt-2 max-w-xl text-sm text-gray-500">
                <p>
                  Once you delete your account, you will lose all data associated with it.
                </p>
              </div>
              <div class="mt-5">
                <button type="button" (click)="confirmDeleteAccount()"
                        [disabled]="deleteButtonLoading"
                        class="inline-flex items-center justify-center px-4 py-2 border border-transparent font-medium rounded-md text-red-700 bg-red-100 hover:bg-red-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 sm:text-sm disabled:opacity-50 disabled:cursor-not-allowed">
                  <span *ngIf="deleteButtonLoading" class="mr-2">
                    <!-- Loading spinner -->
                    <svg class="animate-spin h-5 w-5 text-red-700" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                      <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                  </span>
                  Delete Account
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class UserProfileComponent implements OnInit {
  user: User | null | undefined = null;
  passwordForm: FormGroup;
  isLoading = false;
  passwordButtonLoading = false;
  deleteButtonLoading = false;
  successMessage = '';
  errorMessage = '';

  constructor(
    private formBuilder: FormBuilder,
    private userService: UserService,
    private authService: AuthService
  ) {
    this.passwordForm = this.formBuilder.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });
  }

  ngOnInit(): void {
    this.loadUserProfile();
  }

  loadUserProfile() {
    this.isLoading = true;
    this.errorMessage = '';
    
    this.userService.getCurrentUser()
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.user = response.data;
          } else {
            this.errorMessage = response.message || 'Failed to load user profile';
          }
        },
        error: (error) => {
          console.error('Error loading user profile:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }

  getUserInitials(): string {
    if (!this.user) return '?';
    
    const firstInitial = this.user.firstName?.charAt(0) || this.user.username.charAt(0);
    const lastInitial = this.user.lastName?.charAt(0) || '';
    
    return (firstInitial + lastInitial).toUpperCase();
  }

  passwordMatchValidator(group: FormGroup) {
    const newPassword = group.get('newPassword')?.value;
    const confirmPassword = group.get('confirmPassword')?.value;
    
    return newPassword === confirmPassword ? null : { passwordMismatch: true };
  }

  get currentPassword() { return this.passwordForm.get('currentPassword'); }
  get newPassword() { return this.passwordForm.get('newPassword'); }
  get confirmPassword() { return this.passwordForm.get('confirmPassword'); }

  onPasswordSubmit() {
    if (this.passwordForm.invalid) {
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';
    this.passwordButtonLoading = true;

    const request: ResetPasswordRequest = {
      currentPassword: this.passwordForm.value.currentPassword,
      newPassword: this.passwordForm.value.newPassword,
      confirmPassword: this.passwordForm.value.confirmPassword
    };

    this.userService.resetPassword(request)
      .pipe(finalize(() => this.passwordButtonLoading = false))
      .subscribe({
        next: (response) => {
          if (response.success) {
            this.successMessage = 'Password updated successfully';
            this.passwordForm.reset();
          } else {
            this.errorMessage = response.message || 'Failed to update password';
          }
        },
        error: (error) => {
          console.error('Error updating password:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }

  confirmDeleteAccount() {
    if (!confirm('Are you sure you want to delete your account? This action cannot be undone.')) {
      return;
    }

    // Ask for double confirmation
    if (!confirm('All your data, including deployed functions, will be permanently deleted. Confirm deletion?')) {
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';
    this.deleteButtonLoading = true;

    this.userService.deleteAccount()
      .pipe(finalize(() => this.deleteButtonLoading = false))
      .subscribe({
        next: (response) => {
          if (response.success) {
            // Logout the user after account deletion
            this.authService.logout();
          } else {
            this.errorMessage = response.message || 'Failed to delete account';
          }
        },
        error: (error) => {
          console.error('Error deleting account:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }
}