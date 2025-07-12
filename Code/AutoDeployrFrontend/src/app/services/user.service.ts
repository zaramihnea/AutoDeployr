import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, ResetPasswordRequest } from '../models/user.model';
import { ApiResponse } from '../models/api-response.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly API_URL = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /**
   * Get current user profile
   */
  getCurrentUser(): Observable<ApiResponse<User>> {
    return this.http.get<ApiResponse<User>>(`${this.API_URL}/users/me`);
  }

  /**
   * Reset user password
   */
  resetPassword(request: ResetPasswordRequest): Observable<ApiResponse<void>> {
    return this.http.put<ApiResponse<void>>(`${this.API_URL}/users/password`, request);
  }

  /**
   * Delete user account
   */
  deleteAccount(): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API_URL}/users/me`);
  }

  /**
   * Get user by ID (admin only)
   */
  getUserById(userId: string): Observable<ApiResponse<User>> {
    return this.http.get<ApiResponse<User>>(`${this.API_URL}/admin/users/${userId}`);
  }

  /**
   * Change user roles (admin only)
   */
  changeUserRoles(userId: string, roles: string[]): Observable<ApiResponse<User>> {
    return this.http.put<ApiResponse<User>>(
      `${this.API_URL}/admin/users/${userId}/roles`,
      { userId, roles }
    );
  }
}