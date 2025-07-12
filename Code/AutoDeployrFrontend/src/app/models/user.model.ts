export interface User {
    id: string;
    username: string;
    email: string;
    firstName?: string;
    lastName?: string;
    roles: string[];
    createdAt: string;
    updatedAt: string;
    active: boolean;
  }
  

export interface TokenResponse {
    token: string;        // Primary token field from backend
    accessToken?: string; // Keep for backward compatibility
    tokenType: string;
    expiresIn: number;
    userId: string;
    username: string;
    roles?: string[];     // This might be populated from JWT claims
  }
  
  export interface LoginRequest {
    username: string;
    password: string;
  }
  
  export interface SignupRequest {
    username: string;
    email: string;
    password: string;
    firstName?: string;
    lastName?: string;
  }
  
  export interface ResetPasswordRequest {
    currentPassword: string;
    newPassword: string;
    confirmPassword: string;
  }