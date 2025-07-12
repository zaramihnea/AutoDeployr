import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, of } from 'rxjs';
import { TokenResponse, LoginRequest, SignupRequest, User } from '../models/user.model';
import { ApiResponse } from '../models/api-response.model';
import { Router } from '@angular/router';
import { jwtDecode } from 'jwt-decode';
import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID, Inject } from '@angular/core';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'current_user';
  
  private memoryStorage: Map<string, string> = new Map();
  private isBrowser: boolean;
  
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  currentUser$ = this.currentUserSubject.asObservable();
  
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(false);
  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router,
    @Inject(PLATFORM_ID) platformId: Object
  ) {
    // Check if we're running in a browser
    this.isBrowser = isPlatformBrowser(platformId);
    
    // Initialize state based on stored values
    if (this.isBrowser) {
      try {
        this.currentUserSubject.next(this.getUserFromStorage());
        this.isAuthenticatedSubject.next(this.hasValidToken());
        this.checkTokenExpiration();
      } catch (error) {
        console.error('Error initializing auth state:', error);
        this.currentUserSubject.next(null);
        this.isAuthenticatedSubject.next(false);
      }
    }
  }

  // Storage wrapper methods with error handling
  private getItem(key: string): string | null {
    if (this.isBrowser) {
      try {
        return localStorage.getItem(key);
      } catch (error) {
        console.error(`Error getting item from localStorage (${key}):`, error);
        return this.memoryStorage.get(key) || null;
      }
    }
    return this.memoryStorage.get(key) || null;
  }

  private setItem(key: string, value: string): void {
    // Always store in memory regardless of platform
    this.memoryStorage.set(key, value);
    
    // Additionally store in localStorage if in browser
    if (this.isBrowser) {
      try {
        localStorage.setItem(key, value);
      } catch (error) {
        console.error(`Error setting item in localStorage (${key}):`, error);
      }
    }
  }

  private removeItem(key: string): void {
    // Always remove from memory storage
    this.memoryStorage.delete(key);
    
    // Additionally remove from localStorage if in browser
    if (this.isBrowser) {
      try {
        localStorage.removeItem(key);
      } catch (error) {
        console.error(`Error removing item from localStorage (${key}):`, error);
      }
    }
  }

  login(credentials: LoginRequest): Observable<ApiResponse<TokenResponse>> {
    console.log('Login attempt with:', credentials.username);
    return this.http.post<ApiResponse<TokenResponse>>(`${this.API_URL}/login`, credentials)
      .pipe(
        tap(response => {
          console.log('Login response:', response);
          if (response.success && response.data) {
            try {
              // Log the token data for debugging
              console.log('Token data:', response.data);
              
              // Check for token in the correct field
              const hasToken = response.data.token || response.data.accessToken;
              if (!hasToken) {
                console.error('No token in response data:', response.data);
                return;
              }
              
              // Rest of your code remains the same
              // Test localStorage directly
              if (this.isBrowser) {
                try {
                  // Test localStorage with a simple value first
                  localStorage.setItem('test_key', 'test_value');
                  const testValue = localStorage.getItem('test_key');
                  console.log('LocalStorage test:', testValue === 'test_value' ? 'SUCCESS' : 'FAILED');
                  localStorage.removeItem('test_key');
                } catch (storageError) {
                  console.error('LocalStorage test failed:', storageError);
                }
              }
              
              // Store the token
              this.storeToken(response.data);
              this.updateCurrentUser();
              this.isAuthenticatedSubject.next(true);
              
              // Verify storage worked
              const savedToken = this.getToken();
              console.log('Token saved successfully:', !!savedToken);
            } catch (error) {
              console.error('Error processing login response:', error);
            }
          } else {
            console.warn('Login unsuccessful:', response.message);
          }
        }),
        catchError(error => {
          console.error('Login error:', error);
          return of({ 
            success: false, 
            message: 'An error occurred during login. Please try again.' 
          } as ApiResponse<TokenResponse>);
        })
      );
  }

  signup(userData: SignupRequest): Observable<ApiResponse<{ userId: string, username: string }>> {
    return this.http.post<ApiResponse<{ userId: string, username: string }>>(`${this.API_URL}/signup`, userData)
      .pipe(
        catchError(error => {
          console.error('Signup error:', error);
          return of({ 
            success: false, 
            message: 'An error occurred during signup. Please try again.' 
          } as ApiResponse<{ userId: string, username: string }>);
        })
      );
  }

  logout(): void {
    this.removeItem(this.TOKEN_KEY);
    this.removeItem(this.USER_KEY);
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return this.getItem(this.TOKEN_KEY);
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  hasRole(role: string): boolean {
    const user = this.getCurrentUser();
    return user ? user.roles.includes(role) : false;
  }

  private storeToken(tokenResponse: TokenResponse): void {
    // Check for the correct token field based on backend response
    const token = tokenResponse.token || tokenResponse.accessToken;
    
    if (!token) {
      console.error('Access token is undefined or empty', tokenResponse);
      throw new Error('Invalid token response: token/accessToken is required');
    }
    
    this.setItem(this.TOKEN_KEY, token);
    
    try {
      // Extract user information from token and store
      const decodedToken: any = jwtDecode(token);
      const user: User = {
        id: tokenResponse.userId || '',
        username: tokenResponse.username || '',
        email: decodedToken.email || '',
        roles: tokenResponse.roles || [],
        createdAt: decodedToken.createdAt || new Date().toISOString(),
        updatedAt: decodedToken.updatedAt || new Date().toISOString(),
        active: true
      };
      
      this.setItem(this.USER_KEY, JSON.stringify(user));
      this.currentUserSubject.next(user);
    } catch (error) {
      console.error('Error storing token:', error);
      throw error;
    }
  }

  private updateCurrentUser(): void {
    const user = this.getUserFromStorage();
    this.currentUserSubject.next(user);
  }

  private getUserFromStorage(): User | null {
    try {
      const userStr = this.getItem(this.USER_KEY);
      return userStr ? JSON.parse(userStr) : null;
    } catch (error) {
      console.error('Error getting user from storage:', error);
      return null;
    }
  }

  private hasValidToken(): boolean {
    try {
      const token = this.getToken();
      if (!token) return false;
      
      const decodedToken: any = jwtDecode(token);
      const currentTime = Date.now() / 1000;
      return decodedToken.exp > currentTime;
    } catch (error) {
      console.error('Error validating token:', error);
      return false;
    }
  }

  private checkTokenExpiration(): void {
    if (!this.hasValidToken()) {
      this.logout();
    }
  }
}