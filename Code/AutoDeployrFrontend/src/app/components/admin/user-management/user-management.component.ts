import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { UserService } from '../../../services/user.service';
import { User } from '../../../models/user.model';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, FormsModule],
  providers: [UserService],
  template: `
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
      <div class="md:flex md:items-center md:justify-between">
        <div class="flex-1 min-w-0">
          <h1 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
            User Management
          </h1>
          <p class="mt-1 text-sm text-gray-500">
            Manage user accounts and permissions
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

      <!-- User search and filters -->
      <div class="mt-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
        <div class="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div class="w-full md:w-1/2">
            <label for="search" class="block text-sm font-medium text-gray-700 sr-only">Search</label>
            <div class="relative rounded-md shadow-sm">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <svg class="h-5 w-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" />
                </svg>
              </div>
              <input
                type="text"
                name="search"
                id="search"
                [(ngModel)]="searchTerm"
                (input)="filterUsers()"
                class="focus:ring-indigo-500 focus:border-indigo-500 block w-full pl-10 sm:text-sm border-gray-300 rounded-md"
                placeholder="Search users by username or email"
              />
            </div>
          </div>
          <div class="flex items-center space-x-4">
            <div>
              <label for="role-filter" class="block text-sm font-medium text-gray-700">Filter by role</label>
              <select
                id="role-filter"
                [(ngModel)]="roleFilter"
                (change)="filterUsers()"
                class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
              >
                <option value="">All roles</option>
                <option value="ROLE_USER">User</option>
                <option value="ROLE_ADMIN">Admin</option>
              </select>
            </div>
            <div>
              <label for="status-filter" class="block text-sm font-medium text-gray-700">Status</label>
              <select
                id="status-filter"
                [(ngModel)]="statusFilter"
                (change)="filterUsers()"
                class="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
              >
                <option value="">All</option>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <!-- User list -->
      <div class="mt-6 bg-white shadow overflow-hidden sm:rounded-md">
        <ul role="list" class="divide-y divide-gray-200">
          <li *ngFor="let user of filteredUsers">
            <div class="px-4 py-4 flex items-center sm:px-6">
              <div class="min-w-0 flex-1 sm:flex sm:items-center sm:justify-between">
                <div>
                  <div class="flex text-sm">
                    <p class="font-medium text-indigo-600 truncate">{{ user.username }}</p>
                    <p class="ml-1 flex-shrink-0 font-normal text-gray-500">
                      ({{ user.email }})
                    </p>
                  </div>
                  <div class="mt-2 flex">
                    <div class="flex items-center text-sm text-gray-500">
                      <p>
                        {{ user.firstName || '' }} {{ user.lastName || '' }}
                      </p>
                    </div>
                  </div>
                </div>
                <div class="mt-4 flex-shrink-0 sm:mt-0 sm:ml-5">
                  <div class="flex -space-x-1 overflow-hidden">
                    <span *ngFor="let role of user.roles" 
                          class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium mr-2"
                          [ngClass]="{'bg-indigo-100 text-indigo-800': role === 'ROLE_USER', 'bg-purple-100 text-purple-800': role === 'ROLE_ADMIN'}">
                      {{ role | slice:5 }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="ml-5 flex-shrink-0">
                <button (click)="openEditModal(user)" class="text-indigo-600 hover:text-indigo-900 mr-4">
                  Edit
                </button>
                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                      [ngClass]="{'bg-green-100 text-green-800': user.active, 'bg-red-100 text-red-800': !user.active}">
                  {{ user.active ? 'Active' : 'Inactive' }}
                </span>
              </div>
            </div>
          </li>
          <li *ngIf="filteredUsers.length === 0 && !isLoading" class="px-4 py-6 text-center">
            <p class="text-sm text-gray-500">No users found matching your search criteria.</p>
          </li>
        </ul>
      </div>

      <!-- Edit User Modal -->
      <div *ngIf="showEditModal" class="fixed z-10 inset-0 overflow-y-auto" aria-labelledby="modal-title" role="dialog" aria-modal="true">
        <div class="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
          <!-- Background overlay -->
          <div class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" aria-hidden="true"></div>

          <!-- Modal panel -->
          <div class="inline-block align-bottom bg-white rounded-lg px-4 pt-5 pb-4 text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full sm:p-6">
            <div class="sm:flex sm:items-start">
              <div class="mt-3 text-center sm:mt-0 sm:ml-4 sm:text-left w-full">
                <h3 class="text-lg leading-6 font-medium text-gray-900" id="modal-title">
                  Edit User: {{ selectedUser?.username }}
                </h3>
                <div class="mt-4">
                  <form [formGroup]="userForm" (ngSubmit)="updateUser()" class="space-y-4">
                    <!-- Roles selection -->
                    <div>
                      <label class="block text-sm font-medium text-gray-700">User Roles</label>
                      <div class="mt-2 space-y-2">
                        <div class="flex items-center">
                          <input
                            id="role-user"
                            name="role-user"
                            type="checkbox"
                            formControlName="roleUser"
                            class="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"
                          />
                          <label for="role-user" class="ml-3 text-sm text-gray-700">
                            User
                          </label>
                        </div>
                        <div class="flex items-center">
                          <input
                            id="role-admin"
                            name="role-admin"
                            type="checkbox"
                            formControlName="roleAdmin"
                            class="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"
                          />
                          <label for="role-admin" class="ml-3 text-sm text-gray-700">
                            Admin
                          </label>
                        </div>
                      </div>
                    </div>

                    <!-- Active status -->
                    <div>
                      <label class="block text-sm font-medium text-gray-700">Account Status</label>
                      <div class="mt-2">
                        <div class="flex items-center">
                          <input
                            id="active-status"
                            name="active-status"
                            type="checkbox"
                            formControlName="active"
                            class="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"
                          />
                          <label for="active-status" class="ml-3 text-sm text-gray-700">
                            Active
                          </label>
                        </div>
                      </div>
                    </div>

                    <!-- Modal actions -->
                    <div class="mt-5 sm:mt-4 sm:flex sm:flex-row-reverse">
                      <button
                        type="submit"
                        [disabled]="isSaving || userForm.invalid"
                        class="w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-indigo-600 text-base font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:ml-3 sm:w-auto sm:text-sm disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        <span *ngIf="isSaving" class="mr-2">
                          <!-- Loading spinner -->
                          <svg class="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                          </svg>
                        </span>
                        Save
                      </button>
                      <button
                        type="button"
                        (click)="closeEditModal()"
                        class="mt-3 w-full inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:mt-0 sm:w-auto sm:text-sm"
                      >
                        Cancel
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: []
})
export class UserManagementComponent implements OnInit {
  users: User[] = [];
  filteredUsers: User[] = [];
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  successMessage = '';
  
  // Filters
  searchTerm = '';
  roleFilter = '';
  statusFilter = '';
  
  // Edit User Modal
  showEditModal = false;
  selectedUser: User | null = null;
  userForm: FormGroup;

  constructor(
    private userService: UserService,
    private formBuilder: FormBuilder
  ) {
    this.userForm = this.formBuilder.group({
      roleUser: [false],
      roleAdmin: [false],
      active: [true]
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers() {
    this.isLoading = true;
    this.errorMessage = '';
    
    // This is just a placeholder as we don't have an endpoint to get all users
    // In a real implementation, you would call a service method like:
    // this.userService.getAllUsers()
    
    // Simulate API call with dummy data
    setTimeout(() => {
      this.users = [
        {
          id: '1',
          username: 'admin',
          email: 'admin@example.com',
          firstName: 'Admin',
          lastName: 'User',
          roles: ['ROLE_ADMIN', 'ROLE_USER'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          active: true
        },
        {
          id: '2',
          username: 'user1',
          email: 'user1@example.com',
          firstName: 'Regular',
          lastName: 'User',
          roles: ['ROLE_USER'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          active: true
        },
        {
          id: '3',
          username: 'inactive',
          email: 'inactive@example.com',
          firstName: 'Inactive',
          lastName: 'User',
          roles: ['ROLE_USER'],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          active: false
        }
      ];
      
      this.filteredUsers = [...this.users];
      this.isLoading = false;
    }, 1000);
  }

  filterUsers() {
    this.filteredUsers = this.users.filter(user => {
      // Search term filter
      const searchMatch = !this.searchTerm || 
        user.username.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        user.email.toLowerCase().includes(this.searchTerm.toLowerCase());
        
      // Role filter
      const roleMatch = !this.roleFilter || user.roles.includes(this.roleFilter);
      
      // Status filter
      const statusMatch = !this.statusFilter || 
        (this.statusFilter === 'active' && user.active) || 
        (this.statusFilter === 'inactive' && !user.active);
        
      return searchMatch && roleMatch && statusMatch;
    });
  }

  openEditModal(user: User) {
    this.selectedUser = user;
    
    // Initialize form with user data
    this.userForm.patchValue({
      roleUser: user.roles.includes('ROLE_USER'),
      roleAdmin: user.roles.includes('ROLE_ADMIN'),
      active: user.active
    });
    
    this.showEditModal = true;
  }

  closeEditModal() {
    this.showEditModal = false;
    this.selectedUser = null;
  }

  updateUser() {
    if (!this.selectedUser) return;
    
    this.isSaving = true;
    this.errorMessage = '';
    this.successMessage = '';
    
    // Prepare roles array based on checkboxes
    const roles: string[] = [];
    if (this.userForm.get('roleUser')?.value) roles.push('ROLE_USER');
    if (this.userForm.get('roleAdmin')?.value) roles.push('ROLE_ADMIN');
    
    // Call service to update user roles
    this.userService.changeUserRoles(this.selectedUser.id, roles)
      .pipe(finalize(() => this.isSaving = false))
      .subscribe({
        next: (response) => {
          if (response.success && response.data) {
            // Update local user data
            const updatedUser = response.data;
            const index = this.users.findIndex(u => u.id === updatedUser.id);
            if (index !== -1) {
              this.users[index] = updatedUser;
              
              // Also update active status (not part of changeUserRoles API but simulated here)
              this.users[index].active = this.userForm.get('active')?.value;
              
              // Update filtered users
              this.filterUsers();
            }
            
            this.successMessage = 'User updated successfully';
            this.closeEditModal();
          } else {
            this.errorMessage = response.message || 'Failed to update user';
          }
        },
        error: (error) => {
          console.error('Error updating user:', error);
          this.errorMessage = error.error?.message || 'An unexpected error occurred';
        }
      });
  }
}